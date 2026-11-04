package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.max
import kotlin.math.hypot

@Keep
data class AtgPlasmaMissileConfig(
    val procChance: Float = 0.10f,
    val baseDamageMultiplierPerStack: Float = 3.0f,
    val canBePurchasedAtVending: Boolean = false
) {
    init {
        require(!canBePurchasedAtVending) { "Relic02AtgPlasmaMissile is a drop-only invariant and cannot be purchased at vending." }
    }
}

@Keep
data class AtgPlasmaMissileState(
    val stackCount: Int
) {
    init {
        require(stackCount >= 1) { "Relic02AtgPlasmaMissile stackCount must be >= 1" }
    }

    fun toMap(): Map<String, Any> {
        return mapOf("stackCount" to stackCount)
    }

    companion object {
        fun fromMap(map: Map<String, Any>): AtgPlasmaMissileState {
            val rawStack = map["stackCount"]
            val parsedStack = when (rawStack) {
                is Number -> rawStack.toInt()
                is String -> rawStack.toIntOrNull() ?: 1
                else -> 1
            }
            return AtgPlasmaMissileState(stackCount = max(1, parsedStack))
        }
    }
}

@Keep
data class AtgMissileProcResult(
    val didProc: Boolean,
    val missileDamage: Float
)

object Relic02AtgPlasmaMissileEngine {
    
    private val FNV_PRIME_32 = 16777619
    private val FNV_OFFSET_32 = 2166136261.toInt()

    /**
     * Computes deterministic FNV-1a checksum for the state.
     */
    fun computeChecksum(state: AtgPlasmaMissileState): Int {
        var hash = FNV_OFFSET_32
        
        hash = hash xor state.stackCount
        hash = hash * FNV_PRIME_32
        
        return hash
    }

    /**
     * Processes a hit to determine if an Atg Plasma Missile should be fired.
     * @param config The static configuration.
     * @param state The current stack state of the relic.
     * @param randomFloat A random float in range [0.0, 1.0) for proc evaluation.
     * @param baseDamage The base damage of the hit that triggered the proc.
     * @return Result containing whether a missile fired and its damage.
     */
    fun processHit(
        config: AtgPlasmaMissileConfig,
        state: AtgPlasmaMissileState,
        randomFloat: Float,
        baseDamage: Float
    ): AtgMissileProcResult {
        var validRandom = randomFloat
        if (validRandom.isNaN() || validRandom.isInfinite()) {
            validRandom = 0.0f
        }
        
        var validBaseDamage = baseDamage
        if (validBaseDamage.isNaN() || validBaseDamage.isInfinite() || validBaseDamage < 0f) {
            validBaseDamage = 0.0f
        }

        val didProc = validRandom < config.procChance
        val missileDamage = if (didProc) {
            validBaseDamage * config.baseDamageMultiplierPerStack * state.stackCount.toFloat()
        } else {
            0.0f
        }
        
        var finalDamage = missileDamage
        if (finalDamage.isNaN() || finalDamage.isInfinite()) {
            finalDamage = 0.0f
        }

        return AtgMissileProcResult(didProc = didProc, missileDamage = finalDamage)
    }

    /**
     * Finds the closest alive target for the homing missile.
     * Enforces zero-allocation via primitive parallel arrays.
     * 
     * @param missileX The current X position of the missile.
     * @param missileY The current Y position of the missile.
     * @param targetXs Array of target X positions.
     * @param targetYs Array of target Y positions.
     * @param targetAlive Array of target alive statuses.
     * @param targetCount Number of active targets in the arrays.
     * @param maxRange Maximum range to acquire a target.
     * @return The index of the closest valid target, or -1 if none found.
     */
    fun findHomingTarget(
        missileX: Float,
        missileY: Float,
        targetXs: FloatArray,
        targetYs: FloatArray,
        targetAlive: BooleanArray,
        targetCount: Int,
        maxRange: Float
    ): Int {
        var validMissileX = missileX
        var validMissileY = missileY
        if (validMissileX.isNaN() || validMissileX.isInfinite()) validMissileX = 0f
        if (validMissileY.isNaN() || validMissileY.isInfinite()) validMissileY = 0f

        var validMaxRange = maxRange
        if (validMaxRange.isNaN() || validMaxRange.isInfinite() || validMaxRange < 0f) validMaxRange = 0f

        val maxRangeSq = validMaxRange * validMaxRange
        var closestIndex = -1
        var closestDistanceSq = Float.POSITIVE_INFINITY

        val count = targetCount.coerceAtMost(targetXs.size).coerceAtMost(targetYs.size).coerceAtMost(targetAlive.size)

        for (i in 0 until count) {
            if (!targetAlive[i]) continue

            var tx = targetXs[i]
            var ty = targetYs[i]
            if (tx.isNaN() || tx.isInfinite()) tx = 0f
            if (ty.isNaN() || ty.isInfinite()) ty = 0f

            val dx = tx - validMissileX
            val dy = ty - validMissileY
            val distSq = dx * dx + dy * dy

            if (distSq <= maxRangeSq && distSq < closestDistanceSq) {
                closestDistanceSq = distSq
                closestIndex = i
            }
        }

        return closestIndex
    }
}


fun evaluateRelic02AtgPlasmaMissileLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic02AtgPlasmaMissile") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
