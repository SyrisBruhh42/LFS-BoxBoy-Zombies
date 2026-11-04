package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.max

/**
 * Track 6: Risk of Rain 2 Rare-Drop-Only Passive Skill Relic
 * Relic 13: Hopoo Feather Jet
 *
 * Description: Grants +1 air-dash / blast-hop charge per stack with brief invulnerability.
 * Invariant: `canBePurchasedAtVending = false`
 */

@Keep
data class HopooFeatherJetConfig(
    val stackCount: Int
) {
    init {
        require(stackCount >= 1) { "HopooFeatherJetConfig stackCount must be >= 1" }
    }

    val canBePurchasedAtVending: Boolean
        get() = false

    val maxCharges: Int
        get() = stackCount

    val invulnerabilityDurationSeconds: Float
        get() = 0.2f
}

@Keep
data class HopooFeatherJetState(
    val chargesRemaining: Int,
    val invulnerabilityTimerSeconds: Float,
    val checksum: Long = 0L
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "chargesRemaining" to chargesRemaining,
            "invulnerabilityTimerSeconds" to invulnerabilityTimerSeconds,
            "checksum" to checksum
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): HopooFeatherJetState {
            val chargesRemaining = (map["chargesRemaining"] as? Number)?.toInt() ?: 0
            val invulnerabilityTimerSeconds = (map["invulnerabilityTimerSeconds"] as? Number)?.toFloat() ?: 0f
            val checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            return HopooFeatherJetState(
                chargesRemaining = chargesRemaining,
                invulnerabilityTimerSeconds = invulnerabilityTimerSeconds,
                checksum = checksum
            )
        }
    }
}

data class AirDashResult(
    val success: Boolean,
    val newState: HopooFeatherJetState
)

object Relic13HopooFeatherJetEngine {

    fun generateChecksum(state: HopooFeatherJetState): Long {
        var hash = -2128831035142011030L
        hash = (hash xor state.chargesRemaining.toLong()) * 1099511628211L
        hash = (hash xor state.invulnerabilityTimerSeconds.toBits().toLong()) * 1099511628211L
        return hash
    }

    fun tick(
        config: HopooFeatherJetConfig,
        state: HopooFeatherJetState,
        dtSeconds: Float
    ): HopooFeatherJetState {
        if (!dtSeconds.isFinite() || dtSeconds.isNaN()) {
            return state // Graceful degradation on NaN/Infinity
        }

        if (state.invulnerabilityTimerSeconds <= 0f) {
            return state // Zero allocation on idle frame loop
        }

        val clampedDt = dtSeconds.coerceIn(0.001f, 0.100f)
        var newInvulnTimer = state.invulnerabilityTimerSeconds - clampedDt
        if (newInvulnTimer < 0f || !newInvulnTimer.isFinite()) {
            newInvulnTimer = 0f
        }

        val tempState = HopooFeatherJetState(
            chargesRemaining = state.chargesRemaining,
            invulnerabilityTimerSeconds = newInvulnTimer
        )
        return tempState.copy(checksum = generateChecksum(tempState))
    }

    fun attemptAirDash(
        config: HopooFeatherJetConfig,
        state: HopooFeatherJetState
    ): AirDashResult {
        if (state.chargesRemaining <= 0) {
            return AirDashResult(false, state)
        }

        val tempState = HopooFeatherJetState(
            chargesRemaining = state.chargesRemaining - 1,
            invulnerabilityTimerSeconds = max(state.invulnerabilityTimerSeconds, config.invulnerabilityDurationSeconds)
        )
        return AirDashResult(true, tempState.copy(checksum = generateChecksum(tempState)))
    }

    fun onGroundLanded(
        config: HopooFeatherJetConfig,
        state: HopooFeatherJetState
    ): HopooFeatherJetState {
        val tempState = HopooFeatherJetState(
            chargesRemaining = config.maxCharges,
            invulnerabilityTimerSeconds = state.invulnerabilityTimerSeconds
        )
        return tempState.copy(checksum = generateChecksum(tempState))
    }
}
