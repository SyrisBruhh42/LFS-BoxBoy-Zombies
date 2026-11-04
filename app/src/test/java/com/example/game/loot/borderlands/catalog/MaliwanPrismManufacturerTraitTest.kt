package com.example.game.loot.borderlands.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaliwanPrismManufacturerTraitTest {

    @Test
    fun testDefaultState() {
        val state = MaliwanPrismState.createDefault()
        assertEquals(MaliwanElement.INCENDIARY, state.currentElement)
        assertFalse(state.isSwitching)
        assertEquals(0f, state.switchProgress, 0.001f)
        assertEquals(state.computeChecksum(), state.stateChecksum)
    }

    @Test
    fun testStartSwitch() {
        var state = MaliwanPrismState.createDefault()
        state = MaliwanPrismManufacturerTrait.startSwitch(state)
        assertTrue(state.isSwitching)
        assertEquals(0f, state.switchProgress, 0.001f)
        assertEquals(state.computeChecksum(), state.stateChecksum)
    }

    @Test
    fun testStartSwitchAlreadySwitching() {
        var state = MaliwanPrismState.createDefault()
        state = MaliwanPrismManufacturerTrait.startSwitch(state)
        val state2 = MaliwanPrismManufacturerTrait.startSwitch(state)
        assertEquals(state, state2)
    }

    @Test
    fun testTickSwitchIntermediate() {
        var state = MaliwanPrismState.createDefault()
        state = MaliwanPrismManufacturerTrait.startSwitch(state)
        
        // duration is 0.5f, tick 0.25f should progress to 0.5f
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, 0.05f) // clamped dt, so 5 ticks of 0.05
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, 0.05f)
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, 0.05f)
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, 0.05f)
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, 0.05f)
        
        assertTrue(state.isSwitching)
        assertEquals(0.5f, state.switchProgress, 0.001f)
        assertEquals(MaliwanElement.INCENDIARY, state.currentElement)
        assertEquals(state.computeChecksum(), state.stateChecksum)
    }

    @Test
    fun testTickSwitchComplete() {
        var state = MaliwanPrismState.createDefault()
        state = MaliwanPrismManufacturerTrait.startSwitch(state)
        
        // duration is 0.5f, let's step dt = 0.1f five times
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, 0.1f)
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, 0.1f)
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, 0.1f)
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, 0.1f)
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, 0.1f) // completes
        
        assertFalse(state.isSwitching)
        assertEquals(0f, state.switchProgress, 0.001f)
        assertEquals(MaliwanElement.VOLTAIC, state.currentElement)
        assertEquals(state.computeChecksum(), state.stateChecksum)
    }

    @Test
    fun testTickSwitchVoltaicToIncendiary() {
        var state = MaliwanPrismState(currentElement = MaliwanElement.VOLTAIC)
        state = MaliwanPrismManufacturerTrait.startSwitch(state)
        
        // Tick until complete
        for (i in 0 until 5) {
            state = MaliwanPrismManufacturerTrait.tickSwitch(state, 0.1f)
        }
        
        assertFalse(state.isSwitching)
        assertEquals(MaliwanElement.INCENDIARY, state.currentElement)
    }

    @Test
    fun testTickSwitchDtClampingAndNaNRecovery() {
        var state = MaliwanPrismState.createDefault()
        state = MaliwanPrismManufacturerTrait.startSwitch(state)
        
        // NaN dt should fallback to 0.016f (which is 0.032 of 0.5f duration)
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, Float.NaN)
        assertEquals(0.032f, state.switchProgress, 0.001f)
        
        // Negative dt should clamp to 0.001f (0.002 of 0.5f duration)
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, -10f)
        assertEquals(0.034f, state.switchProgress, 0.001f)
        
        // > 0.1f dt should clamp to 0.1f (0.2 of 0.5f duration)
        state = MaliwanPrismManufacturerTrait.tickSwitch(state, 100f)
        assertEquals(0.234f, state.switchProgress, 0.001f)
    }

    @Test
    fun testComputeShotNotSwitching() {
        val state = MaliwanPrismState.createDefault()
        
        // Should trigger effect if random < chance (0.25f)
        val resultTrigger = MaliwanPrismManufacturerTrait.computeShot(state, 100f, 0.1f)
        assertEquals(100f, resultTrigger.damageDealt, 0.001f)
        assertTrue(resultTrigger.didTriggerEffect)
        assertEquals(MaliwanElement.INCENDIARY, resultTrigger.appliedElement)
        
        // Should not trigger if random >= chance
        val resultNoTrigger = MaliwanPrismManufacturerTrait.computeShot(state, 100f, 0.3f)
        assertEquals(100f, resultNoTrigger.damageDealt, 0.001f)
        assertFalse(resultNoTrigger.didTriggerEffect)
    }

    @Test
    fun testComputeShotSwitchingPenalty() {
        var state = MaliwanPrismState.createDefault()
        state = MaliwanPrismManufacturerTrait.startSwitch(state)
        
        val result = MaliwanPrismManufacturerTrait.computeShot(state, 100f, 0.1f)
        
        // 50% damage penalty during switching
        assertEquals(50f, result.damageDealt, 0.001f)
        
        // Cannot trigger effect while switching
        assertFalse(result.didTriggerEffect)
        assertEquals(MaliwanElement.INCENDIARY, result.appliedElement) // Element hasn't fully switched yet
    }

    @Test
    fun testComputeShotNaNInfinityRecovery() {
        val state = MaliwanPrismState.createDefault()
        
        // NaN baseDamage should clamp to 0
        val resultNaNBase = MaliwanPrismManufacturerTrait.computeShot(state, Float.NaN, 0.1f)
        assertEquals(0f, resultNaNBase.damageDealt, 0.001f)
        
        // Negative baseDamage should clamp to 0
        val resultNegBase = MaliwanPrismManufacturerTrait.computeShot(state, -50f, 0.1f)
        assertEquals(0f, resultNegBase.damageDealt, 0.001f)
        
        // NaN randomValue should fallback to 0.999f (no trigger)
        val resultNaNRandom = MaliwanPrismManufacturerTrait.computeShot(state, 100f, Float.NaN)
        assertEquals(100f, resultNaNRandom.damageDealt, 0.001f)
        assertFalse(resultNaNRandom.didTriggerEffect)
        
        // Negative randomValue should clamp to 0f (always trigger if > 0 chance)
        val resultNegRandom = MaliwanPrismManufacturerTrait.computeShot(state, 100f, -50f)
        assertTrue(resultNegRandom.didTriggerEffect)
    }

    @Test
    fun testSerializationRoundTrip() {
        val originalState = MaliwanPrismState(
            currentElement = MaliwanElement.VOLTAIC,
            isSwitching = true,
            switchProgress = 0.75f,
            switchDuration = 1.0f,
            baseDamageMultiplier = 1.5f,
            elementalEffectChance = 0.5f
        ).let { it.copy(stateChecksum = it.computeChecksum()) }
        
        val map = originalState.toJsonMap()
        val restoredState = MaliwanPrismState.fromJsonMap(map)
        
        assertEquals(originalState, restoredState)
    }
    
    @Test
    fun testSerializationRoundTripDefault() {
        val originalState = MaliwanPrismState.createDefault()
        val map = originalState.toJsonMap()
        val restoredState = MaliwanPrismState.fromJsonMap(map)
        
        assertEquals(originalState, restoredState)
    }

    @Test
    fun testDeterminismSameInputsSameOutput() {
        val state1 = MaliwanPrismState.createDefault()
        val state2 = MaliwanPrismState.createDefault()
        
        val res1 = MaliwanPrismManufacturerTrait.computeShot(state1, 42.5f, 0.15f)
        val res2 = MaliwanPrismManufacturerTrait.computeShot(state2, 42.5f, 0.15f)
        
        assertEquals(res1, res2)
        
        val s1 = MaliwanPrismManufacturerTrait.tickSwitch(
            MaliwanPrismManufacturerTrait.startSwitch(state1),
            0.05f
        )
        val s2 = MaliwanPrismManufacturerTrait.tickSwitch(
            MaliwanPrismManufacturerTrait.startSwitch(state2),
            0.05f
        )
        
        assertEquals(s1, s2)
        assertEquals(s1.stateChecksum, s2.stateChecksum)
    }
}
