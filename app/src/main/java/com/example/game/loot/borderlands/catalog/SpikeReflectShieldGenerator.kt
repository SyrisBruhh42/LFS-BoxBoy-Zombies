package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import com.example.game.ZombieEntity

@Keep
data class SpikeReflectShieldConfig(
    val spikeDamage: Int = 50,
    val isCryoSpike: Boolean = false,
    val triggerRadiusPx: Float = 60f,
    val rechargeDelaySeconds: Float = 3.0f,
    val rechargeRatePerSecond: Float = 15f,
    val maxCapacity: Float = 100f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "spikeDamage" to spikeDamage,
        "isCryoSpike" to isCryoSpike,
        "triggerRadiusPx" to triggerRadiusPx,
        "rechargeDelaySeconds" to rechargeDelaySeconds,
        "rechargeRatePerSecond" to rechargeRatePerSecond,
        "maxCapacity" to maxCapacity
    )

    companion object {
        fun fromMap(map: Map<String, Any>): SpikeReflectShieldConfig {
            return SpikeReflectShieldConfig(
                spikeDamage = (map["spikeDamage"] as? Number)?.toInt() ?: 50,
                isCryoSpike = map["isCryoSpike"] as? Boolean ?: false,
                triggerRadiusPx = (map["triggerRadiusPx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 60f,
                rechargeDelaySeconds = (map["rechargeDelaySeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 3.0f,
                rechargeRatePerSecond = (map["rechargeRatePerSecond"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 15f,
                maxCapacity = (map["maxCapacity"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 100f
            )
        }
    }
}

@Keep
data class SpikeReflectShieldState(
    var currentCapacity: Float = 100f,
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
        fun fromMap(map: Map<String, Any>): SpikeReflectShieldState {
            return SpikeReflectShieldState(
                currentCapacity = (map["currentCapacity"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 100f,
                timeSinceLastDamage = (map["timeSinceLastDamage"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                isFullyRecharged = map["isFullyRecharged"] as? Boolean ?: true
            )
        }
    }
}

@Keep
data class SpikeReflectResult(
    var reflectedDamage: Int = 0,
    var isCryoReflected: Boolean = false,
    var targetDamaged: Boolean = false
) {
    fun reset() {
        reflectedDamage = 0
        isCryoReflected = false
        targetDamaged = false
    }
}

object SpikeReflectShieldGenerator {

    fun processMeleeDamage(
        damageAmount: Float,
        config: SpikeReflectShieldConfig,
        state: SpikeReflectShieldState,
        playerX: Float,
        playerY: Float,
        attacker: ZombieEntity?,
        outResult: SpikeReflectResult
    ) {
        outResult.reset()

        if (!damageAmount.isFinite() || damageAmount <= 0f) {
            return
        }

        state.timeSinceLastDamage = 0f
        state.isFullyRecharged = false

        val hadCapacity = state.currentCapacity > 0f

        state.currentCapacity -= damageAmount
        if (state.currentCapacity < 0f) {
            state.currentCapacity = 0f
        }

        if (hadCapacity && attacker != null) {
            val dx = attacker.x - playerX
            val dy = attacker.y - playerY
            val distSq = dx * dx + dy * dy
            val triggerRadiusSq = config.triggerRadiusPx * config.triggerRadiusPx

            if (distSq <= triggerRadiusSq) {
                attacker.hp -= config.spikeDamage
                outResult.reflectedDamage = config.spikeDamage
                outResult.isCryoReflected = config.isCryoSpike
                outResult.targetDamaged = true
            }
        }
    }

    fun stepRecharge(
        dtSeconds: Float,
        config: SpikeReflectShieldConfig,
        state: SpikeReflectShieldState
    ) {
        if (!dtSeconds.isFinite()) return
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)

        if (state.currentCapacity < config.maxCapacity) {
            state.timeSinceLastDamage += dt

            if (state.timeSinceLastDamage >= config.rechargeDelaySeconds) {
                state.currentCapacity += config.rechargeRatePerSecond * dt

                if (state.currentCapacity >= config.maxCapacity) {
                    state.currentCapacity = config.maxCapacity
                    state.isFullyRecharged = true
                }
            }
        } else {
            state.currentCapacity = config.maxCapacity
            state.isFullyRecharged = true
        }
    }
}
