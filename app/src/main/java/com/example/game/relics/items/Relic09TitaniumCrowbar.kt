package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.max

/**
 * Track 6: Risk of Rain 2 Rare-Drop-Only Passive Skill Relic.
 * Relic09: Titanium Crowbar
 * Effect: +75% damage to zombies above 90% HP per stack.
 */
@Keep
data class TitaniumCrowbarConfig(
    val hpThresholdPercent: Float = 0.90f,
    val damageBonusPerStack: Float = 0.75f,
    val canBePurchasedAtVending: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "hpThresholdPercent" to hpThresholdPercent,
        "damageBonusPerStack" to damageBonusPerStack,
        "canBePurchasedAtVending" to canBePurchasedAtVending
    )

    companion object {
        fun fromMap(map: Map<String, Any>): TitaniumCrowbarConfig = TitaniumCrowbarConfig(
            hpThresholdPercent = (map["hpThresholdPercent"] as? Number)?.toFloat() ?: 0.90f,
            damageBonusPerStack = (map["damageBonusPerStack"] as? Number)?.toFloat() ?: 0.75f,
            canBePurchasedAtVending = (map["canBePurchasedAtVending"] as? Boolean) ?: false
        )
    }
}

@Keep
data class TitaniumCrowbarSnapshot(
    val stackCount: Int,
    val lastAppliedBonusDamage: Float
) {
    fun toMap(): Map<String, Any> = mapOf(
        "stackCount" to stackCount,
        "lastAppliedBonusDamage" to lastAppliedBonusDamage
    )

    fun getFnv1aChecksum(): Long {
        var hash = -3750763034362895579L // FNV offset basis
        val prime = 1099511628211L

        hash = (hash xor stackCount.toLong()) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(lastAppliedBonusDamage).toLong()) * prime

        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): TitaniumCrowbarSnapshot = TitaniumCrowbarSnapshot(
            stackCount = (map["stackCount"] as? Number)?.toInt() ?: 1,
            lastAppliedBonusDamage = (map["lastAppliedBonusDamage"] as? Number)?.toFloat() ?: 0f
        )
    }
}

object TitaniumCrowbarEngine {

    /**
     * Computes the final damage multiplier against a single target.
     * Enforces explicit bounds clamping and Float.isFinite() guards for zero-allocation engine loops.
     */
    fun computeDamageMultiplier(
        targetHp: Int,
        targetMaxHp: Int,
        stackCount: Int,
        config: TitaniumCrowbarConfig = TitaniumCrowbarConfig()
    ): Float {
        val stacks = max(1, stackCount)
        if (targetMaxHp <= 0 || targetHp <= 0) {
            return 1.0f
        }

        val hpRatio = targetHp.toFloat() / targetMaxHp.toFloat()

        if (!hpRatio.isFinite()) {
            return 1.0f
        }

        if (hpRatio > config.hpThresholdPercent) {
            return 1.0f + (config.damageBonusPerStack * stacks)
        }

        return 1.0f
    }

    /**
     * Applies crowbar damage efficiently over pre-allocated parallel primitive arrays
     * to ensure zero per-frame heap churn.
     * `outDamageAccumulator` will contain the final computed damage to apply for each target.
     */
    fun applyCrowbarDamageBatch(
        baseDamageArray: FloatArray,
        targetHpArray: IntArray,
        targetMaxHpArray: IntArray,
        stackCount: Int,
        outDamageAccumulator: FloatArray,
        config: TitaniumCrowbarConfig = TitaniumCrowbarConfig()
    ) {
        val size = baseDamageArray.size
        val stacks = max(1, stackCount)
        val bonusDamage = config.damageBonusPerStack * stacks

        for (i in 0 until size) {
            val baseDamage = baseDamageArray[i]
            val maxHp = targetMaxHpArray[i]
            val hp = targetHpArray[i]

            if (maxHp <= 0 || hp <= 0 || !baseDamage.isFinite()) {
                outDamageAccumulator[i] = if (baseDamage.isFinite()) baseDamage else 0f
                continue
            }

            val hpRatio = hp.toFloat() / maxHp.toFloat()
            if (!hpRatio.isFinite()) {
                 outDamageAccumulator[i] = baseDamage
                 continue
            }

            val multiplier = if (hpRatio > config.hpThresholdPercent) {
                1.0f + bonusDamage
            } else {
                1.0f
            }

            outDamageAccumulator[i] = baseDamage * multiplier
        }
    }
}
