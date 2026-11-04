package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep

@Keep
data class TurtleShieldConfig(
    val baseCapacity: Float = 100f,
    val capacityMultiplier: Float = 2.2f, // +120% capacity
    val hpPenaltyMultiplier: Float = 0.8f, // -20% HP
    val rechargeDelaySeconds: Float = 3.0f,
    val rechargeRatePerSecond: Float = 20f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "baseCapacity" to baseCapacity,
        "capacityMultiplier" to capacityMultiplier,
        "hpPenaltyMultiplier" to hpPenaltyMultiplier,
        "rechargeDelaySeconds" to rechargeDelaySeconds,
        "rechargeRatePerSecond" to rechargeRatePerSecond
    )

    companion object {
        fun fromMap(map: Map<String, Any>): TurtleShieldConfig {
            return TurtleShieldConfig(
                baseCapacity = (map["baseCapacity"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 100f,
                capacityMultiplier = (map["capacityMultiplier"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 2.2f,
                hpPenaltyMultiplier = (map["hpPenaltyMultiplier"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0.8f,
                rechargeDelaySeconds = (map["rechargeDelaySeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 3.0f,
                rechargeRatePerSecond = (map["rechargeRatePerSecond"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 20f
            )
        }
    }
}

@Keep
data class TurtleShieldState(
    var currentCapacity: Float = 220f,
    var timeSinceLastDamage: Float = 0f,
    var isFullyRecharged: Boolean = true
) {
    fun toMap(): Map<String, Any> = mapOf(
        "currentCapacity" to currentCapacity,
        "timeSinceLastDamage" to timeSinceLastDamage,
        "isFullyRecharged" to isFullyRecharged
    )

    fun calculateChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor currentCapacity.toBits().toLong()) * 16777619L
        hash = (hash xor timeSinceLastDamage.toBits().toLong()) * 16777619L
        hash = (hash xor (if (isFullyRecharged) 1L else 0L)) * 16777619L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): TurtleShieldState {
            return TurtleShieldState(
                currentCapacity = (map["currentCapacity"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 220f,
                timeSinceLastDamage = (map["timeSinceLastDamage"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                isFullyRecharged = map["isFullyRecharged"] as? Boolean ?: true
            )
        }
    }
}

@Keep
data class TurtleEquipResult(
    val modifiedMaxHp: Float,
    val modifiedMaxCapacity: Float
)

object TurtleFortressShieldGenerator {

    fun calculateModifiers(baseHp: Float, config: TurtleShieldConfig): TurtleEquipResult {
        val safeBaseHp = if (baseHp.isFinite() && baseHp > 0f) baseHp else 100f

        val modifiedMaxHp = safeBaseHp * config.hpPenaltyMultiplier
        val modifiedMaxCapacity = config.baseCapacity * config.capacityMultiplier

        return TurtleEquipResult(
            modifiedMaxHp = if (modifiedMaxHp.isFinite() && modifiedMaxHp > 1f) modifiedMaxHp else 1f,
            modifiedMaxCapacity = if (modifiedMaxCapacity.isFinite() && modifiedMaxCapacity > 0f) modifiedMaxCapacity else 1f
        )
    }

    fun processDamage(
        damageAmount: Float,
        config: TurtleShieldConfig,
        state: TurtleShieldState
    ): Float {
        if (!damageAmount.isFinite() || damageAmount <= 0f) {
            return 0f
        }

        state.timeSinceLastDamage = 0f
        state.isFullyRecharged = false

        if (state.currentCapacity >= damageAmount) {
            state.currentCapacity -= damageAmount
            return 0f
        } else {
            val overflowDamage = damageAmount - state.currentCapacity
            state.currentCapacity = 0f
            return overflowDamage
        }
    }

    fun stepRecharge(
        dtSeconds: Float,
        config: TurtleShieldConfig,
        state: TurtleShieldState
    ) {
        if (!dtSeconds.isFinite()) return
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)

        val maxCapacity = if (config.baseCapacity.isFinite() && config.capacityMultiplier.isFinite()) {
            config.baseCapacity * config.capacityMultiplier
        } else {
            100f * 2.2f
        }

        if (state.currentCapacity < maxCapacity) {
            state.timeSinceLastDamage += dt

            if (state.timeSinceLastDamage >= config.rechargeDelaySeconds) {
                state.currentCapacity += config.rechargeRatePerSecond * dt

                if (state.currentCapacity >= maxCapacity) {
                    state.currentCapacity = maxCapacity
                    state.isFullyRecharged = true
                }
            }
        } else {
            state.currentCapacity = maxCapacity
            state.isFullyRecharged = true
        }
    }
}
