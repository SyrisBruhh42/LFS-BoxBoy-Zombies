package com.example.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.Keep
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Google Pixel 8 Pro (`husky` / Tensor G3 / Immortalis-G715s MC10) Hysteretic Thermal Governor.
 *
 * Implements a deterministic 3-tier quality & frame-pacing step-down ladder (`120 -> 60 -> 30 FPS`):
 * - [ThermalTier.TIER_120_NOMINAL]: `120 FPS` Smooth Display, `1.00f` shader scale, `1.00f` particle density.
 * - [ThermalTier.TIER_60_WARM]: `60 FPS` balanced cap, `0.75f` shader scale, `0.55f` particle density.
 * - [ThermalTier.TIER_30_THROTTLED]: `30 FPS` thermal preservation cap, `0.50f` shader scale, `0.25f` particle density.
 *
 * Downward step-down occurs immediately upon thermal elevation (`MODERATE` -> `60 FPS`, `SEVERE+` -> `30 FPS`)
 * or predictive headroom spike (`> 0.15f/sec`). Upward recovery requires both cooler hysteretic margins AND
 * continuous dwell time ([recoveryDwellMs], default `8,000ms`) in safe thermal conditions, stepping up one
 * tier at a time (`30 -> 60 -> 120 FPS`) to prevent oscillatory thermal thrashing on Tensor G3.
 */
@Keep
class TensorG3ThermalGovernor(
    val recoveryDwellMs: Long = DEFAULT_RECOVERY_DWELL_MS,
    private val monotonicClockMs: () -> Long = { System.currentTimeMillis() }
) {

    @Keep
    enum class ThermalStatusLevel(val androidStatusCode: Int) {
        NONE(0),
        LIGHT(1),
        MODERATE(2),
        SEVERE(3),
        CRITICAL(4),
        EMERGENCY(5),
        SHUTDOWN(6),
        UNKNOWN(-1);

        companion object {
            fun fromAndroidCode(code: Int): ThermalStatusLevel = when (code) {
                0 -> NONE
                1 -> LIGHT
                2 -> MODERATE
                3 -> SEVERE
                4 -> CRITICAL
                5 -> EMERGENCY
                6 -> SHUTDOWN
                else -> UNKNOWN
            }
        }
    }

    @Keep
    enum class ThermalTier(
        val targetFps: Int,
        val shaderComplexityScale: Float,
        val particleDensityScale: Float,
        val audioPolyphonyCap: Int
    ) {
        TIER_120_NOMINAL(
            targetFps = 120,
            shaderComplexityScale = 1.00f,
            particleDensityScale = 1.00f,
            audioPolyphonyCap = 8
        ),
        TIER_60_WARM(
            targetFps = 60,
            shaderComplexityScale = 0.75f,
            particleDensityScale = 0.55f,
            audioPolyphonyCap = 6
        ),
        TIER_30_THROTTLED(
            targetFps = 30,
            shaderComplexityScale = 0.50f,
            particleDensityScale = 0.25f,
            audioPolyphonyCap = 4
        )
    }

    @Keep
    data class ThermalTelemetrySample(
        val status: ThermalStatusLevel = ThermalStatusLevel.NONE,
        val thermalHeadroom: Float = 0.55f,
        val skinTempCelsius: Float = 36.5f,
        val timestampMs: Long = -1L
    )

    @Keep
    data class ThermalEvaluationDecision(
        val activeTier: ThermalTier,
        val targetFps: Int,
        val shaderComplexityScale: Float,
        val particleDensityScale: Float,
        val isPredictiveThrottleActive: Boolean,
        val recoveryDwellElapsedMs: Long,
        val transitionedThisStep: Boolean
    )

    @Keep
    fun interface ThermalTierListener {
        fun onThermalTierChanged(
            previousTier: ThermalTier,
            newTier: ThermalTier,
            decision: ThermalEvaluationDecision
        )
    }

    private val listeners = CopyOnWriteArrayList<ThermalTierListener>()

    @Volatile
    var currentTier: ThermalTier = ThermalTier.TIER_120_NOMINAL
        private set

    val targetFps: Int
        get() = currentTier.targetFps

    val shaderComplexityScale: Float
        get() = currentTier.shaderComplexityScale

    val particleDensityScale: Float
        get() = currentTier.particleDensityScale

    private var recoveryCandidateTier: ThermalTier? = null
    private var recoveryCandidateSinceMs: Long? = null
    private var lastSampleTimestampMs: Long? = null
    private var lastHeadroomSample: Float = Float.NaN
    private var lastHeadroomTimestampMs: Long = 0L
    private var predictiveSpikeDetected: Boolean = false

    fun addListener(listener: ThermalTierListener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: ThermalTierListener) {
        listeners.remove(listener)
    }

    /**
     * Evaluates a pure-Kotlin [ThermalTelemetrySample] and applies asymmetric hysteresis (`120 -> 60 -> 30 FPS`).
     */
    fun evaluateSample(sample: ThermalTelemetrySample): ThermalEvaluationDecision {
        val nowMs = if (sample.timestampMs >= 0L) sample.timestampMs else monotonicClockMs()
        val prevTs = lastSampleTimestampMs
        if (prevTs != null && nowMs < prevTs) {
            // Monotonic clock reset guard
            recoveryCandidateTier = null
            recoveryCandidateSinceMs = null
            lastHeadroomSample = Float.NaN
            predictiveSpikeDetected = false
        }
        lastSampleTimestampMs = nowMs

        // 1. Update predictive headroom slope (dHeadroom / dt)
        val headroom = sample.thermalHeadroom
        if (headroom.isFinite() && headroom >= 0f && sample.status != ThermalStatusLevel.UNKNOWN) {
            if (lastHeadroomSample.isNaN() || lastHeadroomTimestampMs == 0L) {
                lastHeadroomSample = headroom
                lastHeadroomTimestampMs = nowMs
            } else {
                val dtSec = (nowMs - lastHeadroomTimestampMs) / 1000.0f
                if (dtSec >= 0.5f) {
                    val slopePerSec = (headroom - lastHeadroomSample) / dtSec
                    if (slopePerSec.isFinite() && slopePerSec in -1.0f..1.0f) {
                        predictiveSpikeDetected = slopePerSec > PREDICTIVE_HEADROOM_SLOPE_THRESHOLD
                    }
                    lastHeadroomSample = headroom
                    lastHeadroomTimestampMs = nowMs
                }
            }
        }

        // 2. Compute immediate downward demand vs hysteretic upward eligibility
        val immediateFloorTier = computeImmediateStepDownTier(
            status = sample.status,
            headroom = sample.thermalHeadroom,
            skinTempCelsius = sample.skinTempCelsius,
            isPredictiveSpike = predictiveSpikeDetected
        )

        val previousTier = currentTier
        var transitioned = false
        var dwellElapsed = 0L

        if (immediateFloorTier.ordinal > currentTier.ordinal) {
            // Immediate downward throttle (120 -> 60, 120 -> 30, or 60 -> 30)
            recoveryCandidateTier = null
            recoveryCandidateSinceMs = null
            currentTier = immediateFloorTier
            transitioned = true
        } else if (currentTier.ordinal > ThermalTier.TIER_120_NOMINAL.ordinal) {
            // Check if current telemetry satisfies the cooler hysteretic recovery gate for stepping up 1 tier
            val nextBetterTier = when (currentTier) {
                ThermalTier.TIER_30_THROTTLED -> ThermalTier.TIER_60_WARM
                ThermalTier.TIER_60_WARM -> ThermalTier.TIER_120_NOMINAL
                ThermalTier.TIER_120_NOMINAL -> ThermalTier.TIER_120_NOMINAL
            }
            val eligibleForStepUp = isEligibleForHystereticStepUp(
                fromTier = currentTier,
                toTier = nextBetterTier,
                status = sample.status,
                headroom = sample.thermalHeadroom,
                skinTempCelsius = sample.skinTempCelsius,
                isPredictiveSpike = predictiveSpikeDetected
            )
            if (eligibleForStepUp) {
                if (recoveryCandidateTier != nextBetterTier || recoveryCandidateSinceMs == null) {
                    recoveryCandidateTier = nextBetterTier
                    recoveryCandidateSinceMs = nowMs
                }
                dwellElapsed = (nowMs - (recoveryCandidateSinceMs ?: nowMs)).coerceAtLeast(0L)
                if (dwellElapsed >= recoveryDwellMs) {
                    currentTier = nextBetterTier
                    recoveryCandidateTier = null
                    recoveryCandidateSinceMs = null
                    dwellElapsed = recoveryDwellMs
                    transitioned = true
                }
            } else {
                recoveryCandidateTier = null
                recoveryCandidateSinceMs = null
            }
        } else {
            recoveryCandidateTier = null
            recoveryCandidateSinceMs = null
        }

        val decision = ThermalEvaluationDecision(
            activeTier = currentTier,
            targetFps = currentTier.targetFps,
            shaderComplexityScale = currentTier.shaderComplexityScale,
            particleDensityScale = currentTier.particleDensityScale,
            isPredictiveThrottleActive = predictiveSpikeDetected,
            recoveryDwellElapsedMs = dwellElapsed,
            transitionedThisStep = transitioned
        )

        if (transitioned) {
            listeners.forEach { listener ->
                runCatching { listener.onThermalTierChanged(previousTier, currentTier, decision) }
            }
        }

        return decision
    }

    /**
     * Convenience overload for evaluating raw status code, headroom, and skin temperature.
     */
    fun evaluateThermalState(
        androidStatusCode: Int,
        thermalHeadroom: Float = 0.55f,
        skinTempCelsius: Float = 36.5f,
        timestampMs: Long = -1L
    ): ThermalEvaluationDecision {
        return evaluateSample(
            ThermalTelemetrySample(
                status = ThermalStatusLevel.fromAndroidCode(androidStatusCode),
                thermalHeadroom = thermalHeadroom,
                skinTempCelsius = skinTempCelsius,
                timestampMs = timestampMs
            )
        )
    }

    /**
     * Optional Android `PowerManager` thermal telemetry poller for Google Pixel 8 Pro (API 30+).
     * Safely degrades to nominal/warm defaults if invoked on older APIs or unit test mocks.
     */
    fun pollAndroidPowerManager(context: Context, estimatedSkinTempCelsius: Float = 36.5f): ThermalEvaluationDecision {
        val sample = readPowerManagerSample(context, estimatedSkinTempCelsius)
        return evaluateSample(sample)
    }

    fun readPowerManagerSample(context: Context, estimatedSkinTempCelsius: Float = 36.5f): ThermalTelemetrySample {
        val nowMs = monotonicClockMs()
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val rawStatus = pm.currentThermalStatus
                val rawHeadroom = pm.getThermalHeadroom(10)
                val cleanHeadroom = if (rawHeadroom.isFinite() && rawHeadroom > 0f) rawHeadroom else 0.55f
                ThermalTelemetrySample(
                    status = ThermalStatusLevel.fromAndroidCode(rawStatus),
                    thermalHeadroom = cleanHeadroom,
                    skinTempCelsius = estimatedSkinTempCelsius,
                    timestampMs = nowMs
                )
            } else {
                ThermalTelemetrySample(
                    status = ThermalStatusLevel.NONE,
                    thermalHeadroom = 0.55f,
                    skinTempCelsius = estimatedSkinTempCelsius,
                    timestampMs = nowMs
                )
            }
        } catch (_: Throwable) {
            ThermalTelemetrySample(
                status = ThermalStatusLevel.NONE,
                thermalHeadroom = 0.55f,
                skinTempCelsius = estimatedSkinTempCelsius,
                timestampMs = nowMs
            )
        }
    }

    fun resetToNominal() {
        currentTier = ThermalTier.TIER_120_NOMINAL
        recoveryCandidateTier = null
        recoveryCandidateSinceMs = null
        lastSampleTimestampMs = null
        lastHeadroomSample = Float.NaN
        lastHeadroomTimestampMs = 0L
        predictiveSpikeDetected = false
    }

    private fun computeImmediateStepDownTier(
        status: ThermalStatusLevel,
        headroom: Float,
        skinTempCelsius: Float,
        isPredictiveSpike: Boolean
    ): ThermalTier {
        val validHeadroom = if (headroom.isFinite()) headroom else 0.55f
        val validTemp = if (skinTempCelsius.isFinite()) skinTempCelsius else 36.5f

        // Severe / Critical / Emergency / Shutdown or >= 45.0C or >= 0.96 headroom -> 30 FPS
        if (
            status == ThermalStatusLevel.SEVERE ||
            status == ThermalStatusLevel.CRITICAL ||
            status == ThermalStatusLevel.EMERGENCY ||
            status == ThermalStatusLevel.SHUTDOWN ||
            validHeadroom >= HEADROOM_STEP_DOWN_30 ||
            validTemp >= TEMP_STEP_DOWN_30_C
        ) {
            return ThermalTier.TIER_30_THROTTLED
        }

        // Moderate status or >= 42.5C or >= 0.85 headroom or predictive spike -> 60 FPS
        if (
            status == ThermalStatusLevel.MODERATE ||
            validHeadroom >= HEADROOM_STEP_DOWN_60 ||
            validTemp >= TEMP_STEP_DOWN_60_C ||
            isPredictiveSpike
        ) {
            return ThermalTier.TIER_60_WARM
        }

        return ThermalTier.TIER_120_NOMINAL
    }

    private fun isEligibleForHystereticStepUp(
        fromTier: ThermalTier,
        toTier: ThermalTier,
        status: ThermalStatusLevel,
        headroom: Float,
        skinTempCelsius: Float,
        isPredictiveSpike: Boolean
    ): Boolean {
        if (status == ThermalStatusLevel.UNKNOWN || isPredictiveSpike) return false
        val validHeadroom = if (headroom.isFinite()) headroom else 0.55f
        val validTemp = if (skinTempCelsius.isFinite()) skinTempCelsius else 36.5f

        return when {
            fromTier == ThermalTier.TIER_30_THROTTLED && toTier == ThermalTier.TIER_60_WARM -> {
                (status == ThermalStatusLevel.NONE ||
                    status == ThermalStatusLevel.LIGHT ||
                    status == ThermalStatusLevel.MODERATE) &&
                    validHeadroom <= HEADROOM_RECOVER_TO_60 &&
                    validTemp <= TEMP_RECOVER_TO_60_C
            }
            fromTier == ThermalTier.TIER_60_WARM && toTier == ThermalTier.TIER_120_NOMINAL -> {
                (status == ThermalStatusLevel.NONE || status == ThermalStatusLevel.LIGHT) &&
                    validHeadroom <= HEADROOM_RECOVER_TO_120 &&
                    validTemp <= TEMP_RECOVER_TO_120_C
            }
            else -> false
        }
    }

    companion object {
        const val DEFAULT_RECOVERY_DWELL_MS = 8_000L

        const val HEADROOM_STEP_DOWN_60 = 0.85f
        const val HEADROOM_STEP_DOWN_30 = 0.96f
        const val HEADROOM_RECOVER_TO_60 = 0.88f
        const val HEADROOM_RECOVER_TO_120 = 0.75f

        const val TEMP_STEP_DOWN_60_C = 42.5f
        const val TEMP_STEP_DOWN_30_C = 45.0f
        const val TEMP_RECOVER_TO_60_C = 43.2f
        const val TEMP_RECOVER_TO_120_C = 40.5f

        const val PREDICTIVE_HEADROOM_SLOPE_THRESHOLD = 0.15f
    }
}
