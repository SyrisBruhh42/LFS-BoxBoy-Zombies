package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import com.example.game.ZombieEntity

@Keep
data class NovaShieldConfig(
    val novaRadiusPx: Float = 150f,
    val novaBaseDamage: Int = 120,
    val isCryoNova: Boolean = false,
    val rechargeDelaySeconds: Float = 3.0f,
    val rechargeRatePerSecond: Float = 15f,
    val maxCapacity: Float = 100f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "novaRadiusPx" to novaRadiusPx,
        "novaBaseDamage" to novaBaseDamage,
        "isCryoNova" to isCryoNova,
        "rechargeDelaySeconds" to rechargeDelaySeconds,
        "rechargeRatePerSecond" to rechargeRatePerSecond,
        "maxCapacity" to maxCapacity
    )
    
    companion object {
        fun fromMap(map: Map<String, Any>): NovaShieldConfig {
            return NovaShieldConfig(
                novaRadiusPx = (map["novaRadiusPx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 150f,
                novaBaseDamage = (map["novaBaseDamage"] as? Number)?.toInt() ?: 120,
                isCryoNova = map["isCryoNova"] as? Boolean ?: false,
                rechargeDelaySeconds = (map["rechargeDelaySeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 3.0f,
                rechargeRatePerSecond = (map["rechargeRatePerSecond"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 15f,
                maxCapacity = (map["maxCapacity"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 100f
            )
        }
    }
}

@Keep
data class NovaShieldState(
    var currentCapacity: Float = 100f,
    var timeSinceLastDamage: Float = 0f,
    var isFullyRecharged: Boolean = true,
    var hasNovaTriggered: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "currentCapacity" to currentCapacity,
        "timeSinceLastDamage" to timeSinceLastDamage,
        "isFullyRecharged" to isFullyRecharged,
        "hasNovaTriggered" to hasNovaTriggered
    )

    fun calculateChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor currentCapacity.toBits().toLong()) * 16777619L
        hash = (hash xor timeSinceLastDamage.toBits().toLong()) * 16777619L
        hash = (hash xor (if (isFullyRecharged) 1L else 0L)) * 16777619L
        hash = (hash xor (if (hasNovaTriggered) 1L else 0L)) * 16777619L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): NovaShieldState {
            return NovaShieldState(
                currentCapacity = (map["currentCapacity"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 100f,
                timeSinceLastDamage = (map["timeSinceLastDamage"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                isFullyRecharged = map["isFullyRecharged"] as? Boolean ?: true,
                hasNovaTriggered = map["hasNovaTriggered"] as? Boolean ?: false
            )
        }
    }
}

@Keep
data class NovaBlastResult(
    val triggered: Boolean,
    val centerX: Float,
    val centerY: Float,
    val hitCount: Int,
    val damageDealt: Int,
    val isCryoNova: Boolean
)

object NovaBlastShieldGenerator {

    fun processDamage(
        damageAmount: Float,
        config: NovaShieldConfig,
        state: NovaShieldState,
        playerX: Float,
        playerY: Float,
        zombies: List<ZombieEntity>
    ): NovaBlastResult {
        if (!damageAmount.isFinite() || damageAmount <= 0f) {
            return NovaBlastResult(false, 0f, 0f, 0, 0, config.isCryoNova)
        }
        
        state.timeSinceLastDamage = 0f
        
        val wasGreaterThanZero = state.currentCapacity > 0f
        state.currentCapacity -= damageAmount
        
        if (state.currentCapacity <= 0f) {
            state.currentCapacity = 0f
            
            if (wasGreaterThanZero && !state.hasNovaTriggered && state.isFullyRecharged) {
                state.hasNovaTriggered = true
                state.isFullyRecharged = false
                
                var hitCount = 0
                val radiusSq = config.novaRadiusPx * config.novaRadiusPx
                
                for (i in zombies.indices) {
                    val zombie = zombies[i]
                    val dx = zombie.x - playerX
                    val dy = zombie.y - playerY
                    val distSq = dx * dx + dy * dy
                    
                    if (distSq <= radiusSq) {
                        zombie.hp -= config.novaBaseDamage
                        hitCount++
                    }
                }
                
                return NovaBlastResult(
                    triggered = true,
                    centerX = playerX,
                    centerY = playerY,
                    hitCount = hitCount,
                    damageDealt = config.novaBaseDamage,
                    isCryoNova = config.isCryoNova
                )
            }
        }
        
        if (state.currentCapacity < config.maxCapacity) {
            state.isFullyRecharged = false
        }
        
        return NovaBlastResult(false, 0f, 0f, 0, 0, config.isCryoNova)
    }

    fun stepRecharge(
        dtSeconds: Float,
        config: NovaShieldConfig,
        state: NovaShieldState
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
                    state.hasNovaTriggered = false
                }
            }
        } else {
            state.currentCapacity = config.maxCapacity
            state.isFullyRecharged = true
            state.hasNovaTriggered = false
        }
    }
}


fun evaluateNovaBlastShieldGeneratorLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "NovaBlastShieldGenerator") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
