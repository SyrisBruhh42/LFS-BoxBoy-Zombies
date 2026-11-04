package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.max
import kotlin.math.min

/**
 * [BoxBoys T6-06] RoR2 Passive Relic: Relic06CryoFrostIcicle
 * Orbiting cryo blizzard ring on kill (+36px radius per stack).
 *
 * Implements deterministic dt-stepping, zero-allocation algorithms,
 * and standard BoxBoys Map-based serialization roundtripping.
 */
@Keep
data class Relic06CryoFrostIcicleState(
    val isActive: Boolean = false,
    val activeDurationMs: Float = 0f,
    val checksum: Long = 0L
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "isActive" to isActive,
            "activeDurationMs" to activeDurationMs,
            "checksum" to checksum
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Relic06CryoFrostIcicleState {
            val isActive = (map["isActive"] as? Boolean) ?: false
            val activeDurationMs = ((map["activeDurationMs"] as? Number)?.toFloat()) ?: 0f
            val checksum = ((map["checksum"] as? Number)?.toLong()) ?: 0L
            return Relic06CryoFrostIcicleState(
                isActive = isActive,
                activeDurationMs = if (activeDurationMs.isFinite()) activeDurationMs else 0f,
                checksum = checksum
            )
        }
    }
}

object Relic06CryoFrostIcicleEngine {

    const val BASE_RADIUS_PX = 36f
    const val RADIUS_PER_STACK_PX = 36f
    const val MAX_DURATION_MS = 6000f
    const val DURATION_PER_KILL_MS = 3000f
    const val FNV_PRIME = 1099511628211L
    const val FNV_OFFSET_BASIS = -3750763034362895579L
    
    // Invariant requirement checking (ensured by system, but explicit property here)
    val canBePurchasedAtVending: Boolean = false

    fun calculateStormRadius(stackCount: Int): Float {
        val stacks = max(1, stackCount)
        return BASE_RADIUS_PX + (stacks - 1) * RADIUS_PER_STACK_PX
    }

    fun onKill(currentState: Relic06CryoFrostIcicleState): Relic06CryoFrostIcicleState {
        val newDurationMs = min(
            MAX_DURATION_MS,
            (if (currentState.isActive) currentState.activeDurationMs else 0f) + DURATION_PER_KILL_MS
        )
        val active = newDurationMs > 0f
        return Relic06CryoFrostIcicleState(
            isActive = active,
            activeDurationMs = newDurationMs,
            checksum = calculateChecksum(active, newDurationMs)
        )
    }

    fun tick(currentState: Relic06CryoFrostIcicleState, dtSeconds: Float): Relic06CryoFrostIcicleState {
        if (!currentState.isActive) {
            return currentState
        }

        var safeDt = if (dtSeconds.isNaN() || dtSeconds.isInfinite()) 0f else dtSeconds
        safeDt = max(0.001f, min(0.100f, safeDt)) // clamped dt to [0.001, 0.100]

        val deltaMs = safeDt * 1000f
        val newDurationMs = max(0f, currentState.activeDurationMs - deltaMs)
        val active = newDurationMs > 0f

        return Relic06CryoFrostIcicleState(
            isActive = active,
            activeDurationMs = newDurationMs,
            checksum = calculateChecksum(active, newDurationMs)
        )
    }

    private fun calculateChecksum(isActive: Boolean, activeDurationMs: Float): Long {
        var hash = FNV_OFFSET_BASIS
        
        hash = hash xor (if (isActive) 1L else 0L)
        hash = hash * FNV_PRIME
        
        hash = hash xor activeDurationMs.toRawBits().toLong()
        hash = hash * FNV_PRIME
        
        return hash
    }
}


fun evaluateRelic06CryoFrostIcicleLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic06CryoFrostIcicle") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
