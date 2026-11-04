package com.example.game.combat.modules

import com.example.game.combat.BoxheadComboAndPhysicsRegistry
import com.example.game.combat.BoxheadComboState

class BoxheadComboStreakTracker {

    var state = BoxheadComboState()
        private set

    val currentStreak: Int
        get() = state.currentStreak

    val comboDecayRemainingMs: Long
        get() = state.comboDecayRemainingMs

    val currencyMultiplier: Float
        get() = state.currencyMultiplier

    /**
     * Registers kills and returns the number of relics dropped from hitting milestones.
     */
    fun registerKills(count: Int): Int {
        val (newState, relicsDropped) = BoxheadComboAndPhysicsRegistry.registerKillStreak(state, count)
        state = newState
        return relicsDropped
    }

    /**
     * Advances the combo timer.
     */
    fun tick(deltaMs: Long) {
        state = BoxheadComboAndPhysicsRegistry.stepComboTimer(state, deltaMs)
    }
}
