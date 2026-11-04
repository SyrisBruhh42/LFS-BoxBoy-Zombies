package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import com.example.game.ElementalAffinity

@Keep
data class AdaptiveShieldConfig(
    val maxCapacity: Float = 120f,
    val rechargeDelaySeconds: Float = 2.5f,
    val rechargeRatePerSecond: Float = 20f,
    val maxHpBonusPct: Float = 0.25f,
    val elementalResistanceBonusPct: Float = 0.50f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "maxCapacity" to maxCapacity,
        "rechargeDelaySeconds" to rechargeDelaySeconds,
        "rechargeRatePerSecond" to rechargeRatePerSecond,
        "maxHpBonusPct" to maxHpBonusPct,
        "elementalResistanceBonusPct" to elementalResistanceBonusPct
    )

    companion object {
        fun fromMap(map: Map<String, Any>): AdaptiveShieldConfig {
            return AdaptiveShieldConfig(
                maxCapacity = (map["maxCapacity"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 120f,
                rechargeDelaySeconds = (map["rechargeDelaySeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 2.5f,
                rechargeRatePerSecond = (map["rechargeRatePerSecond"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 20f,
                maxHpBonusPct = (map["maxHpBonusPct"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0.25f,
                elementalResistanceBonusPct = (map["elementalResistanceBonusPct"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0.50f
            )
        }
    }
}

@Keep
data class AdaptiveShieldState(
    var currentCapacity: Float = 120f,
    var timeSinceLastDamage: Float = 0f,
    var isFullyRecharged: Boolean = true,
    var lastHitElement: ElementalAffinity? = null
) {
    fun toMap(): Map<String, Any> = mapOf(
        "currentCapacity" to currentCapacity,
        "timeSinceLastDamage" to timeSinceLastDamage,
        "isFullyRecharged" to isFullyRecharged,
        "lastHitElement" to (lastHitElement?.name ?: "")
    )

    fun calculateChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor currentCapacity.toBits().toLong()) * 16777619L
        hash = (hash xor timeSinceLastDamage.toBits().toLong()) * 16777619L
        hash = (hash xor (if (isFullyRecharged) 1L else 0L)) * 16777619L
        val elementStr = lastHitElement?.name ?: ""
        for (char in elementStr) {
            hash = (hash xor char.code.toLong()) * 16777619L
        }
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): AdaptiveShieldState {
            val elementStr = map["lastHitElement"] as? String ?: ""
            val element = if (elementStr.isNotEmpty()) {
                try {
                    ElementalAffinity.valueOf(elementStr)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            return AdaptiveShieldState(
                currentCapacity = (map["currentCapacity"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 120f,
                timeSinceLastDamage = (map["timeSinceLastDamage"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                isFullyRecharged = map["isFullyRecharged"] as? Boolean ?: true,
                lastHitElement = element
            )
        }
    }
}

@Keep
data class AdaptiveDamageResult(
    val shieldDamageTaken: Float,
    val hpSpilloverDamage: Int,
    val resistanceApplied: Boolean,
    val adaptedElement: ElementalAffinity?
)

object AdaptiveResistShieldGenerator {

    fun getEffectiveMaxHp(baseMaxHp: Int, config: AdaptiveShieldConfig): Int {
        if (!config.maxHpBonusPct.isFinite()) return baseMaxHp
        val bonus = (baseMaxHp * config.maxHpBonusPct).toInt()
        return baseMaxHp + bonus
    }

    fun processDamage(
        rawDamage: Float,
        element: ElementalAffinity,
        config: AdaptiveShieldConfig,
        state: AdaptiveShieldState
    ): AdaptiveDamageResult {
        if (!rawDamage.isFinite() || rawDamage <= 0f) {
            return AdaptiveDamageResult(0f, 0, false, state.lastHitElement)
        }

        state.timeSinceLastDamage = 0f
        
        var effectiveDamage = rawDamage
        val resistanceApplied = element == state.lastHitElement
        
        if (resistanceApplied) {
            effectiveDamage *= (1f - config.elementalResistanceBonusPct)
        }

        state.lastHitElement = element

        val availableCapacity = state.currentCapacity
        
        val shieldDamageTaken: Float
        val hpSpilloverDamage: Int

        if (availableCapacity >= effectiveDamage) {
            state.currentCapacity -= effectiveDamage
            shieldDamageTaken = effectiveDamage
            hpSpilloverDamage = 0
            if (state.currentCapacity < config.maxCapacity) {
                state.isFullyRecharged = false
            }
        } else {
            shieldDamageTaken = availableCapacity
            state.currentCapacity = 0f
            state.isFullyRecharged = false
            
            val spillover = effectiveDamage - availableCapacity
            hpSpilloverDamage = spillover.toInt()
        }

        return AdaptiveDamageResult(
            shieldDamageTaken = shieldDamageTaken,
            hpSpilloverDamage = hpSpilloverDamage,
            resistanceApplied = resistanceApplied,
            adaptedElement = state.lastHitElement
        )
    }

    fun stepRecharge(
        dtSeconds: Float,
        config: AdaptiveShieldConfig,
        state: AdaptiveShieldState
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


fun evaluateAdaptiveResistShieldGeneratorLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "AdaptiveResistShieldGenerator") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
