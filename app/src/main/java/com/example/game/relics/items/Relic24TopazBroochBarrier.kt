package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.max
import kotlin.math.min

@Keep
data class TopazBroochConfig(
    val stackCount: Int,
    val canBePurchasedAtVending: Boolean = false,
    val baseBarrierPerKill: Float = 15.0f,
    val maxBarrierProportionOfMaxHp: Float = 1.0f,
    val barrierDecayFractionOfMaxHpPerSecond: Float = 0.0333f
) {
    init {
        require(!canBePurchasedAtVending) { "Relic24TopazBroochBarrier must enforce canBePurchasedAtVending = false (drop-only invariant)" }
        require(stackCount >= 1) { "Relic24TopazBroochBarrier stackCount must be >= 1" }
    }

    val barrierGrantedPerKill: Float
        get() = baseBarrierPerKill * stackCount

    fun toMap(): Map<String, Any> = mapOf(
        "stackCount" to stackCount,
        "canBePurchasedAtVending" to canBePurchasedAtVending,
        "baseBarrierPerKill" to baseBarrierPerKill,
        "maxBarrierProportionOfMaxHp" to maxBarrierProportionOfMaxHp,
        "barrierDecayFractionOfMaxHpPerSecond" to barrierDecayFractionOfMaxHpPerSecond
    )

    companion object {
        fun fromMap(map: Map<String, Any>): TopazBroochConfig {
            val count = (map["stackCount"] as? Number)?.toInt() ?: 1
            return TopazBroochConfig(
                stackCount = max(1, count),
                canBePurchasedAtVending = (map["canBePurchasedAtVending"] as? Boolean) ?: false,
                baseBarrierPerKill = (map["baseBarrierPerKill"] as? Number)?.toFloat() ?: 15.0f,
                maxBarrierProportionOfMaxHp = (map["maxBarrierProportionOfMaxHp"] as? Number)?.toFloat() ?: 1.0f,
                barrierDecayFractionOfMaxHpPerSecond = (map["barrierDecayFractionOfMaxHpPerSecond"] as? Number)?.toFloat() ?: 0.0333f
            )
        }
    }
}

@Keep
data class TopazBroochState(
    var currentBarrier: Float = 0.0f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "currentBarrier" to currentBarrier
    )

    fun computeChecksum(): Long {
        var hash = -2128831035146728213L
        val currentBarrierBits = currentBarrier.toRawBits().toLong() and 0xFFFFFFFFL
        hash = (hash xor (currentBarrierBits and 0xFF)) * 1099511628211L
        hash = (hash xor ((currentBarrierBits ushr 8) and 0xFF)) * 1099511628211L
        hash = (hash xor ((currentBarrierBits ushr 16) and 0xFF)) * 1099511628211L
        hash = (hash xor ((currentBarrierBits ushr 24) and 0xFF)) * 1099511628211L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): TopazBroochState {
            val barrier = (map["currentBarrier"] as? Number)?.toFloat() ?: 0.0f
            return TopazBroochState(
                currentBarrier = if (barrier.isFinite() && barrier >= 0f) barrier else 0.0f
            )
        }
    }
}

object Relic24TopazBroochBarrierEngine {

    fun registerKill(
        config: TopazBroochConfig,
        state: TopazBroochState,
        maxHp: Float
    ) {
        if (!maxHp.isFinite() || maxHp <= 0f) return
        val maxBarrier = maxHp * config.maxBarrierProportionOfMaxHp
        if (!maxBarrier.isFinite()) return

        var newBarrier = state.currentBarrier + config.barrierGrantedPerKill
        if (!newBarrier.isFinite()) newBarrier = 0f

        state.currentBarrier = min(maxBarrier, newBarrier)
    }

    fun tick(
        config: TopazBroochConfig,
        state: TopazBroochState,
        dtSeconds: Float,
        maxHp: Float
    ) {
        var dt = dtSeconds
        if (!dt.isFinite()) dt = 0.001f
        dt = dt.coerceIn(0.001f, 0.100f)

        if (!maxHp.isFinite() || maxHp <= 0f) return

        if (state.currentBarrier > 0f) {
            val decayAmount = maxHp * config.barrierDecayFractionOfMaxHpPerSecond * dt
            var newBarrier = state.currentBarrier - decayAmount
            if (!newBarrier.isFinite() || newBarrier < 0f) newBarrier = 0f
            state.currentBarrier = newBarrier
        }
    }

    fun processDamage(
        state: TopazBroochState,
        incomingDamage: Float
    ): Float {
        var dmg = incomingDamage
        if (!dmg.isFinite() || dmg <= 0f) return 0f

        val absorbed = min(state.currentBarrier, dmg)
        var newBarrier = state.currentBarrier - absorbed
        if (!newBarrier.isFinite() || newBarrier < 0f) newBarrier = 0f
        state.currentBarrier = newBarrier

        var remainingDamage = dmg - absorbed
        if (!remainingDamage.isFinite() || remainingDamage < 0f) remainingDamage = 0f
        return remainingDamage
    }
}