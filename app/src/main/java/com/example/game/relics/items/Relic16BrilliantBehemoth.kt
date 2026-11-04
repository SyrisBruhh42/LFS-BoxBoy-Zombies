package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.max
import kotlin.math.hypot

@Keep
data class BrilliantBehemothConfig(
    val baseRadius: Float = 48f,
    val radiusPerStack: Float = 24f,
    val bonusDamageRatio: Float = 0.60f
) {
    val canBePurchasedAtVending: Boolean
        get() = false
}

@Keep
data class BrilliantBehemothState(
    val stackCount: Int
) {
    init {
        require(stackCount >= 1) { "Brilliant Behemoth requires at least 1 stack (got $stackCount)" }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "stackCount" to stackCount
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): BrilliantBehemothState {
            val count = (map["stackCount"] as? Number)?.toInt() ?: 1
            return BrilliantBehemothState(max(1, count))
        }
    }

    fun checksum(): Long {
        var hash = -2128831035146727653L // FNV-1a 64-bit offset
        hash = hash xor stackCount.toLong()
        hash = hash * 1099511628211L
        return hash
    }
}

object BrilliantBehemothEngine {

    fun calculateExplosionRadius(config: BrilliantBehemothConfig, state: BrilliantBehemothState): Float {
        val radius = config.baseRadius + config.radiusPerStack * (state.stackCount - 1)
        if (!radius.isFinite() || radius < 0f) return 0f
        return radius
    }

    fun calculateBonusDamage(baseDamage: Float, config: BrilliantBehemothConfig): Float {
        if (!baseDamage.isFinite() || baseDamage <= 0f) return 0f
        val bonus = baseDamage * config.bonusDamageRatio
        if (!bonus.isFinite() || bonus < 0f) return 0f
        return bonus
    }

    fun processExplosion(
        x: Float,
        y: Float,
        radius: Float,
        bonusDamage: Float,
        targetX: FloatArray,
        targetY: FloatArray,
        targetHp: FloatArray,
        targetCount: Int
    ): Int {
        if (!x.isFinite() || !y.isFinite() || !radius.isFinite() || radius <= 0f) return 0
        if (!bonusDamage.isFinite() || bonusDamage <= 0f) return 0

        var hitCount = 0
        for (i in 0 until targetCount) {
            val tx = targetX[i]
            val ty = targetY[i]
            val hp = targetHp[i]

            if (!tx.isFinite() || !ty.isFinite() || !hp.isFinite() || hp <= 0f) continue

            val dx = tx - x
            val dy = ty - y
            val dist = hypot(dx, dy)
            if (dist <= radius) {
                targetHp[i] = max(0f, hp - bonusDamage)
                hitCount++
            }
        }
        return hitCount
    }
}
