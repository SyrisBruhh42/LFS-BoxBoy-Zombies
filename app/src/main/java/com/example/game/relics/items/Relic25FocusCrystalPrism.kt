package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.hypot

/**
 * Configuration for [BoxBoys T6-25] RoR2 Passive Relic: Focus Crystal Prism.
 * +20% damage per stack to zombies within 130px close-quarters radius.
 */
@Keep
data class Relic25FocusCrystalPrismConfig(
    val baseMultiplierPerStack: Float = 0.20f,
    val triggerRadiusPx: Float = 130.0f
) {
    val canBePurchasedAtVending: Boolean
        get() = false

    init {
        require(baseMultiplierPerStack >= 0f && baseMultiplierPerStack.isFinite()) { "baseMultiplierPerStack must be finite positive." }
        require(triggerRadiusPx >= 0f && triggerRadiusPx.isFinite()) { "triggerRadiusPx must be finite positive." }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "baseMultiplierPerStack" to baseMultiplierPerStack,
            "triggerRadiusPx" to triggerRadiusPx,
            "canBePurchasedAtVending" to canBePurchasedAtVending
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Relic25FocusCrystalPrismConfig {
            val baseMult = (map["baseMultiplierPerStack"] as? Number)?.toFloat() ?: 0.20f
            val rad = (map["triggerRadiusPx"] as? Number)?.toFloat() ?: 130.0f
            return Relic25FocusCrystalPrismConfig(
                baseMultiplierPerStack = baseMult,
                triggerRadiusPx = rad
            )
        }
    }
}

/**
 * State for [BoxBoys T6-25] RoR2 Passive Relic: Focus Crystal Prism.
 */
@Keep
data class Relic25FocusCrystalPrismState(
    val stackCount: Int
) {
    init {
        require(stackCount >= 1) { "Relic stackCount must be >= 1" }
    }

    fun computeChecksum(): Long {
        var hash = -2128831035152065819L // FNV-1a offset basis
        hash = hash xor stackCount.toLong()
        hash *= 1099511628211L // FNV-1a prime
        return hash
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "stackCount" to stackCount,
            "checksum" to computeChecksum()
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Relic25FocusCrystalPrismState {
            val count = (map["stackCount"] as? Number)?.toInt() ?: 1
            return Relic25FocusCrystalPrismState(stackCount = count.coerceAtLeast(1))
        }
    }
}

/**
 * Zero-allocation engine for Focus Crystal Prism.
 */
object Relic25FocusCrystalPrismEngine {

    /**
     * Calculates the damage multiplier against a single target.
     * Guaranteed to not allocate in hot loops if using primitives.
     *
     * @param config The static configuration for the relic.
     * @param state The active state (e.g. stack count) for the relic.
     * @param playerX Player world X coordinate.
     * @param playerY Player world Y coordinate.
     * @param targetX Target world X coordinate.
     * @param targetY Target world Y coordinate.
     * @return Multiplier applied to the base damage. 1.0f means no bonus. 1.2f means +20% bonus.
     */
    fun evaluateDamageMultiplier(
        config: Relic25FocusCrystalPrismConfig,
        state: Relic25FocusCrystalPrismState,
        playerX: Float,
        playerY: Float,
        targetX: Float,
        targetY: Float
    ): Float {
        if (!playerX.isFinite() || !playerY.isFinite() || !targetX.isFinite() || !targetY.isFinite()) {
            return 1.0f // Graceful degradation for NaN / Infinity
        }

        val dx = targetX - playerX
        val dy = targetY - playerY
        val distSq = dx * dx + dy * dy

        val radiusSq = config.triggerRadiusPx * config.triggerRadiusPx

        return if (distSq <= radiusSq) {
            1.0f + (config.baseMultiplierPerStack * state.stackCount)
        } else {
            1.0f
        }
    }

    /**
     * Batch evaluates multipliers for an array of targets, modifying the `outMultipliers` array in-place.
     *
     * @param config The static configuration for the relic.
     * @param state The active state.
     * @param playerX Player world X coordinate.
     * @param playerY Player world Y coordinate.
     * @param targetXs Array of target world X coordinates.
     * @param targetYs Array of target world Y coordinates.
     * @param outMultipliers Pre-allocated array to write results to. Must be at least `count` size.
     * @param count Number of targets to process.
     */
    fun evaluateDamageMultiplierBatch(
        config: Relic25FocusCrystalPrismConfig,
        state: Relic25FocusCrystalPrismState,
        playerX: Float,
        playerY: Float,
        targetXs: FloatArray,
        targetYs: FloatArray,
        outMultipliers: FloatArray,
        count: Int
    ) {
        val radiusSq = config.triggerRadiusPx * config.triggerRadiusPx
        val bonus = config.baseMultiplierPerStack * state.stackCount

        for (i in 0 until count) {
            val tx = targetXs[i]
            val ty = targetYs[i]

            if (!playerX.isFinite() || !playerY.isFinite() || !tx.isFinite() || !ty.isFinite()) {
                outMultipliers[i] = 1.0f
                continue
            }

            val dx = tx - playerX
            val dy = ty - playerY
            val distSq = dx * dx + dy * dy

            if (distSq <= radiusSq) {
                outMultipliers[i] = 1.0f + bonus
            } else {
                outMultipliers[i] = 1.0f
            }
        }
    }
}
