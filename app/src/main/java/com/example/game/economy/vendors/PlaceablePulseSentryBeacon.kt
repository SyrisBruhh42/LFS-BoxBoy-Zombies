package com.example.game.economy.vendors

import androidx.annotation.Keep
import androidx.compose.ui.geometry.Rect
import com.example.game.ZombieEntity
import kotlin.math.hypot

@Keep
data class PulseSentryConfig(
    val maxAmmo: Int = 50,
    val maxTtlMs: Long = 45_000L,
    val fireRateMs: Long = 250L,
    val rangePx: Float = 400f,
    val damage: Int = 75,
    val costPoints: Int = 350
) {
    fun toMap(): Map<String, Any> = mapOf(
        "maxAmmo" to maxAmmo,
        "maxTtlMs" to maxTtlMs,
        "fireRateMs" to fireRateMs,
        "rangePx" to rangePx,
        "damage" to damage,
        "costPoints" to costPoints
    )

    companion object {
        fun fromMap(map: Map<String, Any>): PulseSentryConfig {
            return PulseSentryConfig(
                maxAmmo = (map["maxAmmo"] as? Number)?.toInt() ?: 50,
                maxTtlMs = (map["maxTtlMs"] as? Number)?.toLong() ?: 45_000L,
                fireRateMs = (map["fireRateMs"] as? Number)?.toLong() ?: 250L,
                rangePx = (map["rangePx"] as? Number)?.toFloat() ?: 400f,
                damage = (map["damage"] as? Number)?.toInt() ?: 75,
                costPoints = (map["costPoints"] as? Number)?.toInt() ?: 350
            )
        }
    }
}

@Keep
data class PulseSentryState(
    val id: Int,
    var x: Float,
    var y: Float,
    var ammo: Int,
    var ttlMs: Long,
    var cooldownMs: Long
) {
    fun computeChecksum(): Long {
        var hash = -2128831035146727289L // FNV-1a 64-bit offset basis
        val prime = 1099511628211L
        
        fun hashInt(value: Int) {
            hash = hash xor ((value shr 24) and 0xFF).toLong()
            hash = hash * prime
            hash = hash xor ((value shr 16) and 0xFF).toLong()
            hash = hash * prime
            hash = hash xor ((value shr 8) and 0xFF).toLong()
            hash = hash * prime
            hash = hash xor (value and 0xFF).toLong()
            hash = hash * prime
        }
        
        fun hashLong(value: Long) {
            hash = hash xor ((value shr 56) and 0xFFL)
            hash = hash * prime
            hash = hash xor ((value shr 48) and 0xFFL)
            hash = hash * prime
            hash = hash xor ((value shr 40) and 0xFFL)
            hash = hash * prime
            hash = hash xor ((value shr 32) and 0xFFL)
            hash = hash * prime
            hash = hash xor ((value shr 24) and 0xFFL)
            hash = hash * prime
            hash = hash xor ((value shr 16) and 0xFFL)
            hash = hash * prime
            hash = hash xor ((value shr 8) and 0xFFL)
            hash = hash * prime
            hash = hash xor (value and 0xFFL)
            hash = hash * prime
        }

        hashInt(id)
        hashInt(x.toBits())
        hashInt(y.toBits())
        hashInt(ammo)
        hashLong(ttlMs)
        hashLong(cooldownMs)
        
        return hash
    }

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "x" to x,
        "y" to y,
        "ammo" to ammo,
        "ttlMs" to ttlMs,
        "cooldownMs" to cooldownMs
    )

    companion object {
        fun fromMap(map: Map<String, Any>): PulseSentryState {
            return PulseSentryState(
                id = (map["id"] as? Number)?.toInt() ?: 0,
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                ammo = (map["ammo"] as? Number)?.toInt() ?: 0,
                ttlMs = (map["ttlMs"] as? Number)?.toLong() ?: 0L,
                cooldownMs = (map["cooldownMs"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

data class PulseResult(
    val targetId: Int,
    var targetX: Float,
    var targetY: Float,
    var damage: Int
)

data class PulseSentryTickResult(
    var sentryId: Int = 0,
    var isActive: Boolean = false,
    var pulsesFiredCount: Int = 0,
    val maxPulses: Int = 1,
    var pulses: Array<PulseResult?> = arrayOfNulls(maxPulses)
)

enum class DeployResultStatus {
    SUCCESS,
    INSUFFICIENT_FUNDS,
    OBSTRUCTED,
    INVALID_COORDINATES
}

data class DeployAttemptResult(
    val status: DeployResultStatus,
    val initialState: PulseSentryState? = null
)

object PlaceablePulseSentryBeacon {
    const val SENTRY_BBOX_WIDTH = 40f
    const val SENTRY_BBOX_HEIGHT = 40f

    fun attemptDeploy(
        id: Int,
        x: Float,
        y: Float,
        ledger: SingleCurrencyLedger,
        obstacles: List<Rect>,
        config: PulseSentryConfig = PulseSentryConfig()
    ): DeployAttemptResult {
        if (!x.isFinite() || !y.isFinite()) {
            return DeployAttemptResult(DeployResultStatus.INVALID_COORDINATES)
        }

        if (!ledger.canAfford(config.costPoints)) {
            return DeployAttemptResult(DeployResultStatus.INSUFFICIENT_FUNDS)
        }

        val halfW = SENTRY_BBOX_WIDTH / 2f
        val halfH = SENTRY_BBOX_HEIGHT / 2f
        val sentryRect = Rect(x - halfW, y - halfH, x + halfW, y + halfH)

        for (obs in obstacles) {
            if (sentryRect.overlaps(obs)) {
                return DeployAttemptResult(DeployResultStatus.OBSTRUCTED)
            }
        }

        ledger.attemptPurchase(config.costPoints)
        
        val state = PulseSentryState(
            id = id,
            x = x,
            y = y,
            ammo = config.maxAmmo,
            ttlMs = config.maxTtlMs,
            cooldownMs = 0L
        )

        return DeployAttemptResult(DeployResultStatus.SUCCESS, state)
    }

    fun tick(
        deltaSeconds: Float,
        state: PulseSentryState,
        targets: List<ZombieEntity>,
        outResult: PulseSentryTickResult,
        config: PulseSentryConfig = PulseSentryConfig()
    ) {
        outResult.sentryId = state.id
        outResult.pulsesFiredCount = 0
        
        if (!deltaSeconds.isFinite() || deltaSeconds <= 0f) {
            outResult.isActive = state.ammo > 0 && state.ttlMs > 0L
            return
        }
        
        val dt = deltaSeconds.coerceIn(0.001f, 0.100f)
        val dtMs = (dt * 1000f).toLong()

        state.ttlMs -= dtMs
        if (state.ttlMs <= 0L) {
            state.ttlMs = 0L
            outResult.isActive = false
            return
        }

        if (state.ammo <= 0) {
            outResult.isActive = false
            return
        }

        if (state.cooldownMs > 0L) {
            state.cooldownMs -= dtMs
            if (state.cooldownMs < 0L) state.cooldownMs = 0L
        }

        if (state.cooldownMs == 0L) {
            var closestTarget: ZombieEntity? = null
            var minTargetDistSq = Float.MAX_VALUE

            for (target in targets) {
                if (target.hp <= 0) continue
                
                val dx = target.x - state.x
                val dy = target.y - state.y
                val distSq = dx * dx + dy * dy
                
                if (distSq <= config.rangePx * config.rangePx) {
                    if (distSq < minTargetDistSq) {
                        minTargetDistSq = distSq
                        closestTarget = target
                    }
                }
            }

            if (closestTarget != null) {
                state.ammo--
                state.cooldownMs = config.fireRateMs
                
                outResult.pulses[0] = PulseResult(
                    targetId = closestTarget.id,
                    targetX = closestTarget.x,
                    targetY = closestTarget.y,
                    damage = config.damage
                )
                outResult.pulsesFiredCount = 1
            }
        }

        outResult.isActive = state.ammo > 0 && state.ttlMs > 0L
    }
}


fun evaluatePlaceablePulseSentryBeaconLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "PlaceablePulseSentryBeacon") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
