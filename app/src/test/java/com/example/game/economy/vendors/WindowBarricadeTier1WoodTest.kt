package com.example.game.economy.vendors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class WindowBarricadeTier1WoodTest {

    @Test
    fun testRepairOneBoard_Success() {
        val currentPlanks = 3
        val maxPlanks = 5
        val currentCurrency = 500

        val (newPlanks, newCurrency) = WindowBarricadeTier1Wood.repairOneBoard(
            currentPlanks = currentPlanks,
            maxPlanks = maxPlanks,
            currentCurrency = currentCurrency
        )

        // Tier 1 wood gives +20 per board
        assertEquals(4, newPlanks)
        assertEquals(520, newCurrency)
    }

    @Test
    fun testRepairOneBoard_AlreadyFull() {
        val currentPlanks = 5
        val maxPlanks = 5
        val currentCurrency = 500

        val (newPlanks, newCurrency) = WindowBarricadeTier1Wood.repairOneBoard(
            currentPlanks = currentPlanks,
            maxPlanks = maxPlanks,
            currentCurrency = currentCurrency
        )

        assertEquals(5, newPlanks)
        assertEquals(500, newCurrency)
    }

    @Test
    fun testRepairOneBoard_NegativeInputs() {
        val (newPlanks, newCurrency) = WindowBarricadeTier1Wood.repairOneBoard(
            currentPlanks = -5,
            maxPlanks = 0,
            currentCurrency = 500
        )

        // Current planks clamped to 0, maxPlanks clamped to 1.
        // It successfully added a board to get to 1.
        assertEquals(1, newPlanks)
        assertEquals(520, newCurrency)
    }

    @Test
    fun testRepairOneBoard_OverLimit() {
        val (newPlanks, newCurrency) = WindowBarricadeTier1Wood.repairOneBoard(
            currentPlanks = 10,
            maxPlanks = 5,
            currentCurrency = 500
        )

        // Planks clamped to 5
        assertEquals(5, newPlanks)
        assertEquals(500, newCurrency)
    }

    @Test
    fun testTick_NoZombies_TimerDecay() {
        val state = Tier1WoodSnapshot("w1", 5, 5, false, 0.5f, 0L)

        val result = WindowBarricadeTier1Wood.tick(state, 0.1f, 0)

        assertEquals(5, result.nextState.currentPlanks)
        assertEquals(0, result.planksBrokenThisTick)
        assertEquals(0.4f, result.nextState.rebuildCooldownTimer, 0.001f)
    }

    @Test
    fun testTick_ZombiesClawing_NoBreak() {
        val state = Tier1WoodSnapshot("w1", 5, 5, false, 0f, 0L)

        // 1 zombie, dt = 0.1s -> timer increases by 0.1s
        val result = WindowBarricadeTier1Wood.tick(state, 0.1f, 1)

        assertEquals(5, result.nextState.currentPlanks)
        assertEquals(0, result.planksBrokenThisTick)
        assertEquals(0.1f, result.nextState.rebuildCooldownTimer, 0.001f)
    }

    @Test
    fun testTick_ZombiesClawing_PlankBreaks() {
        // PLANK_BREAK_INTERVAL is 1.5f
        // Start near break interval
        val state = Tier1WoodSnapshot("w1", 5, 5, false, 1.45f, 0L)

        // 1 zombie, dt = 0.1s -> timer increases by 0.1s to 1.55f, one plank should break
        val result = WindowBarricadeTier1Wood.tick(state, 0.1f, 1)

        assertEquals(4, result.nextState.currentPlanks)
        assertEquals(1, result.planksBrokenThisTick)
        assertEquals(0.05f, result.nextState.rebuildCooldownTimer, 0.001f)
    }

    @Test
    fun testTick_MaxZombiesLimit() {
        // Test graceful degradation limit (MAX_ZOMBIE_CLAWING_LIMIT = 6)
        val state = Tier1WoodSnapshot("w1", 5, 5, false, 0f, 0L)

        // 100 zombies, but capped at 6. dt = 0.1s -> timer increases by 6 * 0.1s = 0.6s
        val result = WindowBarricadeTier1Wood.tick(state, 0.1f, 100)

        assertEquals(5, result.nextState.currentPlanks)
        assertEquals(0, result.planksBrokenThisTick)
        assertEquals(0.6f, result.nextState.rebuildCooldownTimer, 0.001f)
    }

    @Test
    fun testTick_AllPlanksBreak_BecomesBreached() {
        // 1 plank left, timer almost full
        val state = Tier1WoodSnapshot("w1", 1, 5, false, 1.4f, 0L)

        // 2 zombies, dt = 0.1s -> timer adds 0.2s -> 1.6s. Breaks plank, timer resets to 0, breached
        val result = WindowBarricadeTier1Wood.tick(state, 0.1f, 2)

        assertEquals(0, result.nextState.currentPlanks)
        assertEquals(1, result.planksBrokenThisTick)
        assertTrue(result.nextState.isBreached)
        assertEquals(0f, result.nextState.rebuildCooldownTimer, 0.001f)
    }

    @Test
    fun testTick_InvalidDt_BoundsClamped() {
        val state = Tier1WoodSnapshot("w1", 5, 5, false, 0.5f, 0L)

        // dtSeconds is too high, should clamp to 0.100f
        val result = WindowBarricadeTier1Wood.tick(state, 100f, 0)

        // timer decays by clamped dt (0.1f)
        assertEquals(0.4f, result.nextState.rebuildCooldownTimer, 0.001f)

        // Negative dt, should clamp to 0
        val result2 = WindowBarricadeTier1Wood.tick(state, -1f, 0)
        assertEquals(0.5f, result2.nextState.rebuildCooldownTimer, 0.001f)

        // NaN dt, should clamp to 0
        val result3 = WindowBarricadeTier1Wood.tick(state, Float.NaN, 0)
        assertEquals(0.5f, result3.nextState.rebuildCooldownTimer, 0.001f)
    }

    @Test
    fun testDeterminism_SameInputs_SameOutputs() {
        val state = Tier1WoodSnapshot("test_win", 4, 6, false, 0.8f, 12345L)

        val result1 = WindowBarricadeTier1Wood.tick(state, 0.05f, 3)
        val result2 = WindowBarricadeTier1Wood.tick(state, 0.05f, 3)

        assertEquals(result1, result2)
        assertEquals(result1.nextState.checksum, result2.nextState.checksum)
    }

    @Test
    fun testSerializationRoundtrip() {
        val state = Tier1WoodSnapshot("w_room_3", 2, 5, false, 1.1f, 987654321L)

        val map = state.toMap()
        val restoredState = Tier1WoodSnapshot.fromMap(map)

        assertEquals(state.windowId, restoredState.windowId)
        assertEquals(state.currentPlanks, restoredState.currentPlanks)
        assertEquals(state.maxPlanks, restoredState.maxPlanks)
        assertEquals(state.isBreached, restoredState.isBreached)
        assertEquals(state.rebuildCooldownTimer, restoredState.rebuildCooldownTimer, 0.0001f)
        assertEquals(state.checksum, restoredState.checksum)
        assertEquals(state, restoredState)
    }
}
