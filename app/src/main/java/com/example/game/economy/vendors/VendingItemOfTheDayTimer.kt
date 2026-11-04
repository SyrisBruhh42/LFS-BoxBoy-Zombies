package com.example.game.economy.vendors

import androidx.annotation.Keep
import kotlin.math.max

/**
 * Unified BoxBoys Track 4 (Single-Currency BO2 + Borderlands 2 Vending Economy)
 * Synchronized 180-second Item of the Day countdown and seed-based stock rotation.
 */
@Keep
data class VendingTimerState(
    val currentSeed: Int,
    val timeRemainingSeconds: Float
) {
    fun toMap(): Map<String, Any> = mapOf(
        "currentSeed" to currentSeed,
        "timeRemainingSeconds" to timeRemainingSeconds
    )

    companion object {
        fun fromMap(map: Map<String, Any>): VendingTimerState {
            val seed = (map["currentSeed"] as? Number)?.toInt() ?: 0
            val time = (map["timeRemainingSeconds"] as? Number)?.toFloat() ?: 0f
            return VendingTimerState(seed, time)
        }
    }
}

/**
 * Deterministic hash combining previous seed and current state using FNV-1a.
 */
fun computeNextSeed(prevSeed: Int): Int {
    var hash = -2128831035 // FNV offset basis
    val data = prevSeed xor (prevSeed ushr 16)
    
    // Hash 4 bytes of data
    hash = (hash xor (data and 0xFF)) * 16777619
    hash = (hash xor ((data ushr 8) and 0xFF)) * 16777619
    hash = (hash xor ((data ushr 16) and 0xFF)) * 16777619
    hash = (hash xor ((data ushr 24) and 0xFF)) * 16777619

    return hash
}

class VendingItemOfTheDayTimer(initialSeed: Int) {
    companion object {
        const val MAX_TIME_SECONDS = 180f
    }

    private var currentSeed = initialSeed
    private var timeRemainingSeconds = MAX_TIME_SECONDS

    /**
     * Steps the timer deterministically based on clamped dtSeconds.
     */
    fun step(dtSeconds: Float) {
        val clampedDt = if (dtSeconds.isNaN() || dtSeconds.isInfinite() || dtSeconds <= 0f) {
            0.001f
        } else if (dtSeconds > 0.100f) {
            0.100f
        } else {
            dtSeconds
        }

        timeRemainingSeconds -= clampedDt
        if (timeRemainingSeconds <= 0f) {
            currentSeed = computeNextSeed(currentSeed)
            timeRemainingSeconds = MAX_TIME_SECONDS
        }
    }

    fun getSnapshot(): VendingTimerState = VendingTimerState(currentSeed, max(0f, timeRemainingSeconds))

    fun restoreFromSnapshot(state: VendingTimerState) {
        currentSeed = state.currentSeed
        timeRemainingSeconds = if (state.timeRemainingSeconds.isNaN() || state.timeRemainingSeconds.isInfinite() || state.timeRemainingSeconds < 0f) {
            0f
        } else {
            state.timeRemainingSeconds
        }
    }
}


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [VendingItemOfTheDayTimer].
 */
data class VendingItemOfTheDayTimerTelemetryReceipt(
    val moduleName: String = "VendingItemOfTheDayTimer",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeVendingItemOfTheDayTimerTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): VendingItemOfTheDayTimerTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "VendingItemOfTheDayTimer") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return VendingItemOfTheDayTimerTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateVendingItemOfTheDayTimerLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "VendingItemOfTheDayTimer") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
