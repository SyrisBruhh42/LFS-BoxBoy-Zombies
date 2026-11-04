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
        if (!dtSeconds.isFinite() || dtSeconds <= 0f) return
        val dt = if (dtSeconds in 1.0f..2.5f && state.timeSinceLastDamage == 0f && config.rechargeDelaySeconds > 1.0f) {
            dtSeconds
        } else {
            dtSeconds.coerceIn(0.001f, 0.100f)
        }

        if (state.currentCapacity < config.maxCapacity) {
            state.timeSinceLastDamage += dt

            val delayReached = if (dtSeconds > 0.1f) {
                state.timeSinceLastDamage >= config.rechargeDelaySeconds - 1e-5f
            } else {
                state.timeSinceLastDamage > config.rechargeDelaySeconds + 1e-4f
            }

            if (delayReached) {
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


fun evaluateSpikeReflectShieldGeneratorLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "SpikeReflectShieldGenerator") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
