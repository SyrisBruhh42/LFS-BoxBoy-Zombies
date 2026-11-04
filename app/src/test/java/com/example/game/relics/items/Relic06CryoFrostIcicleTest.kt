package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test

class Relic06CryoFrostIcicleTest {

    @Test
    fun testNominalKillActivation() {
        val state = Relic06CryoFrostIcicleState()
        assertFalse(state.isActive)
        
        val activeState = Relic06CryoFrostIcicleEngine.onKill(state)
        assertTrue(activeState.isActive)
        assertEquals(3000f, activeState.activeDurationMs, 0.001f)
    }

    @Test
    fun testNominalTick() {
        val state = Relic06CryoFrostIcicleState(isActive = true, activeDurationMs = 3000f)
        val tickedState = Relic06CryoFrostIcicleEngine.tick(state, 0.05f) // 50ms
        assertTrue(tickedState.isActive)
        assertEquals(2950f, tickedState.activeDurationMs, 0.001f)
    }

    @Test
    fun testDeactivationOnTick() {
        val state = Relic06CryoFrostIcicleState(isActive = true, activeDurationMs = 50f)
        // Tick with 100ms dt (max allowed) -> 50ms - 100ms = 0 (clamped)
        val tickedState = Relic06CryoFrostIcicleEngine.tick(state, 0.1f)
        assertFalse(tickedState.isActive)
        assertEquals(0f, tickedState.activeDurationMs, 0.001f)
    }

    @Test
    fun testStackScaling() {
        assertEquals(36f, Relic06CryoFrostIcicleEngine.calculateStormRadius(1), 0.001f)
        assertEquals(72f, Relic06CryoFrostIcicleEngine.calculateStormRadius(2), 0.001f)
        assertEquals(180f, Relic06CryoFrostIcicleEngine.calculateStormRadius(5), 0.001f)
        assertEquals(360f, Relic06CryoFrostIcicleEngine.calculateStormRadius(10), 0.001f)
    }

    @Test
    fun testStackBoundClamping() {
        // Stack counts <= 0 should be treated as 1 stack
        assertEquals(36f, Relic06CryoFrostIcicleEngine.calculateStormRadius(0), 0.001f)
        assertEquals(36f, Relic06CryoFrostIcicleEngine.calculateStormRadius(-5), 0.001f)
    }

    @Test
    fun testDeterminismAndChecksum() {
        val state1 = Relic06CryoFrostIcicleEngine.onKill(Relic06CryoFrostIcicleState())
        val state2 = Relic06CryoFrostIcicleEngine.onKill(Relic06CryoFrostIcicleState())
        
        assertEquals(state1.activeDurationMs, state2.activeDurationMs, 0.001f)
        assertEquals(state1.isActive, state2.isActive)
        assertEquals(state1.checksum, state2.checksum)
        assertNotEquals(0L, state1.checksum)
    }

    @Test
    fun testDtSecondsClamping() {
        val state = Relic06CryoFrostIcicleState(isActive = true, activeDurationMs = 3000f)
        // Extremely large dt -> clamped to 0.100 (100ms)
        val tickedState = Relic06CryoFrostIcicleEngine.tick(state, 5.0f) 
        assertEquals(2900f, tickedState.activeDurationMs, 0.001f)
        
        // Extremely small or negative dt -> clamped to 0.001 (1ms)
        val tickedState2 = Relic06CryoFrostIcicleEngine.tick(state, -0.5f)
        assertEquals(2999f, tickedState2.activeDurationMs, 0.001f)
    }

    @Test
    fun testNanAndInfinityGuards() {
        val state = Relic06CryoFrostIcicleState(isActive = true, activeDurationMs = 3000f)
        val nanTicked = Relic06CryoFrostIcicleEngine.tick(state, Float.NaN)
        val infTicked = Relic06CryoFrostIcicleEngine.tick(state, Float.POSITIVE_INFINITY)
        val negInfTicked = Relic06CryoFrostIcicleEngine.tick(state, Float.NEGATIVE_INFINITY)
        
        // Treated as 0, which is clamped to 0.001 (1ms)
        assertEquals(2999f, nanTicked.activeDurationMs, 0.001f)
        assertEquals(2999f, infTicked.activeDurationMs, 0.001f)
        assertEquals(2999f, negInfTicked.activeDurationMs, 0.001f)
    }

    @Test
    fun testSerializationRoundtrip() {
        val original = Relic06CryoFrostIcicleEngine.onKill(Relic06CryoFrostIcicleState())
        val map = original.toMap()
        val deserialized = Relic06CryoFrostIcicleState.fromMap(map)
        
        assertEquals(original.isActive, deserialized.isActive)
        assertEquals(original.activeDurationMs, deserialized.activeDurationMs, 0.001f)
        assertEquals(original.checksum, deserialized.checksum)
        assertEquals(original, deserialized)
    }

    @Test
    fun testMultipleKillsMaxCap() {
        var state = Relic06CryoFrostIcicleState()
        state = Relic06CryoFrostIcicleEngine.onKill(state)
        assertEquals(3000f, state.activeDurationMs, 0.001f)
        
        state = Relic06CryoFrostIcicleEngine.onKill(state)
        assertEquals(6000f, state.activeDurationMs, 0.001f) // maxed out
        
        state = Relic06CryoFrostIcicleEngine.onKill(state)
        assertEquals(6000f, state.activeDurationMs, 0.001f) // should not exceed 6000
    }

    @Test
    fun testMaximumCapacityStress() {
        var state = Relic06CryoFrostIcicleEngine.onKill(Relic06CryoFrostIcicleState())
        state = Relic06CryoFrostIcicleEngine.onKill(state) // max 6000ms
        
        // 300 rapid ticks of 0.016 (16ms per frame ~ 60fps)
        for (i in 0 until 300) {
            state = Relic06CryoFrostIcicleEngine.tick(state, 0.016f)
        }
        
        // 300 * 16ms = 4800ms elapsed
        // 6000ms - 4800ms = 1200ms remaining
        assertTrue(state.isActive)
        assertEquals(1200f, state.activeDurationMs, 0.001f)
        assertNotEquals(0L, state.checksum)
    }

    @Test
    fun testDropOnlyInvariant() {
        assertFalse("Relic06CryoFrostIcicle must not be purchasable at vending", Relic06CryoFrostIcicleEngine.canBePurchasedAtVending)
    }
}
