package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxheadDevilBossPhaseTest {

    @Test
    fun `evaluatePhase returns PHASE_1 for HP greater than 66 percent`() {
        val state = BoxheadDevilBossPhase.evaluatePhase(hp = 70, maxHp = 100)

        assertEquals(DevilBossPhase.PHASE_1, state.phase)
        assertFalse(state.isEnraged)
        assertEquals(1, state.fireballVolleyCount)
        assertFalse(state.canChargeSlam)
        assertEquals(1.0f, state.barrelDamageMultiplier)
    }

    @Test
    fun `evaluatePhase returns PHASE_2 for HP between 34 and 66 percent`() {
        val state = BoxheadDevilBossPhase.evaluatePhase(hp = 66, maxHp = 100)

        assertEquals(DevilBossPhase.PHASE_2, state.phase)
        assertTrue(state.isEnraged)
        assertEquals(3, state.fireballVolleyCount)
        assertTrue(state.canChargeSlam)
        assertEquals(0.5f, state.barrelDamageMultiplier)
    }

    @Test
    fun `evaluatePhase returns PHASE_3 for HP less than or equal to 33 percent`() {
        val state = BoxheadDevilBossPhase.evaluatePhase(hp = 33, maxHp = 100)

        assertEquals(DevilBossPhase.PHASE_3, state.phase)
        assertTrue(state.isEnraged)
        assertEquals(5, state.fireballVolleyCount)
        assertTrue(state.canChargeSlam)
        assertEquals(0.0f, state.barrelDamageMultiplier)
    }

    @Test
    fun `evaluatePhase handles edge cases correctly`() {
        // Just above 33% should be PHASE_2
        val statePhase2 = BoxheadDevilBossPhase.evaluatePhase(hp = 34, maxHp = 100)
        assertEquals(DevilBossPhase.PHASE_2, statePhase2.phase)

        // Just above 66% should be PHASE_1
        val statePhase1 = BoxheadDevilBossPhase.evaluatePhase(hp = 67, maxHp = 100)
        assertEquals(DevilBossPhase.PHASE_1, statePhase1.phase)

        // 0 HP should be PHASE_3
        val stateZero = BoxheadDevilBossPhase.evaluatePhase(hp = 0, maxHp = 100)
        assertEquals(DevilBossPhase.PHASE_3, stateZero.phase)

        // 100 HP should be PHASE_1
        val stateFull = BoxheadDevilBossPhase.evaluatePhase(hp = 100, maxHp = 100)
        assertEquals(DevilBossPhase.PHASE_1, stateFull.phase)
    }
}
