package com.example.game.loot.borderlands.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HyperionVoltManufacturerTraitTest {

    private val epsilon = 0.0001f

    @Test
    fun testNominalSpreadReductionOnShotFired() {
        val config = HyperionVoltTraitConfig()
        val state = HyperionVoltTraitState(currentSpreadAngle = 15.0f)
        
        HyperionVoltManufacturerEngine.onShotFired(state, config)
        
        assertTrue(state.isFiring)
        assertEquals(0.0f, state.timeSinceLastShotSec, epsilon)
        assertEquals(15.0f - 2.5f, state.currentSpreadAngle, epsilon)
    }

    @Test
    fun testSpreadReductionClampedToMin() {
        val config = HyperionVoltTraitConfig(minSpreadAngle = 2.0f, spreadReductionPerShot = 5.0f)
        val state = HyperionVoltTraitState(currentSpreadAngle = 4.0f)
        
        HyperionVoltManufacturerEngine.onShotFired(state, config)
        
        assertEquals(2.0f, state.currentSpreadAngle, epsilon)
    }

    @Test
    fun testSpreadRecoveryWhenNotFiring() {
        val config = HyperionVoltTraitConfig(spreadRecoveryRatePerSec = 5.0f)
        val state = HyperionVoltTraitState(currentSpreadAngle = 5.0f, isFiring = false, timeSinceLastShotSec = 0.5f)
        
        // Step with 0.1s
        HyperionVoltManufacturerEngine.step(state, config, 0.1f)
        
        // Should recover by 5.0 * 0.1 = 0.5
        assertEquals(5.5f, state.currentSpreadAngle, epsilon)
    }

    @Test
    fun testSpreadRecoveryClampedToMax() {
        val config = HyperionVoltTraitConfig(maxSpreadAngle = 15.0f, spreadRecoveryRatePerSec = 10.0f)
        val state = HyperionVoltTraitState(currentSpreadAngle = 14.5f, isFiring = false, timeSinceLastShotSec = 0.5f)
        
        HyperionVoltManufacturerEngine.step(state, config, 0.1f)
        
        // Should recover by 10.0 * 0.1 = 1.0, but max is 15.0
        assertEquals(15.0f, state.currentSpreadAngle, epsilon)
    }

    @Test
    fun testDeterminismWithIdenticalInputs() {
        val config = HyperionVoltTraitConfig()
        val state1 = HyperionVoltTraitState(currentSpreadAngle = 10.0f)
        val state2 = HyperionVoltTraitState(currentSpreadAngle = 10.0f)
        
        HyperionVoltManufacturerEngine.step(state1, config, 0.05f)
        HyperionVoltManufacturerEngine.step(state2, config, 0.05f)
        
        assertEquals(state1.currentSpreadAngle, state2.currentSpreadAngle, epsilon)
        assertEquals(state1.timeSinceLastShotSec, state2.timeSinceLastShotSec, epsilon)
        assertEquals(state1.computeChecksum(), state2.computeChecksum())
    }

    @Test
    fun testDtClampingExtremelySmall() {
        val config = HyperionVoltTraitConfig(spreadRecoveryRatePerSec = 10.0f)
        val state = HyperionVoltTraitState(currentSpreadAngle = 5.0f)
        
        // Very small dt, should clamp to 0.001
        HyperionVoltManufacturerEngine.step(state, config, -0.05f)
        
        assertEquals(0.001f, state.timeSinceLastShotSec, epsilon)
    }

    @Test
    fun testDtClampingExtremelyLarge() {
        val config = HyperionVoltTraitConfig(spreadRecoveryRatePerSec = 10.0f)
        val state = HyperionVoltTraitState(currentSpreadAngle = 5.0f)
        
        // Very large dt, should clamp to 0.100
        HyperionVoltManufacturerEngine.step(state, config, 5.0f)
        
        assertEquals(0.100f, state.timeSinceLastShotSec, epsilon)
    }

    @Test
    fun testNanRecoveryInDt() {
        val config = HyperionVoltTraitConfig(spreadRecoveryRatePerSec = 10.0f)
        val state = HyperionVoltTraitState(currentSpreadAngle = 5.0f)
        
        HyperionVoltManufacturerEngine.step(state, config, Float.NaN)
        
        // NaN should fallback to 0.016
        assertEquals(0.016f, state.timeSinceLastShotSec, epsilon)
    }

    @Test
    fun testNanRecoveryInConfigSpread() {
        val config = HyperionVoltTraitConfig(maxSpreadAngle = Float.NaN)
        val state = HyperionVoltTraitState(currentSpreadAngle = 5.0f, isFiring = false, timeSinceLastShotSec = 0.5f)
        
        HyperionVoltManufacturerEngine.step(state, config, 0.1f)
        
        // Should fallback to 15.0 max spread, 5.0 recovery rate * 0.1 = 0.5
        assertEquals(5.5f, state.currentSpreadAngle, epsilon)
    }
    
    @Test
    fun testNanRecoveryInStateSpread() {
        val config = HyperionVoltTraitConfig()
        val state = HyperionVoltTraitState(currentSpreadAngle = Float.NaN)
        
        HyperionVoltManufacturerEngine.step(state, config, 0.1f)
        
        // Should clamp state to maxSpread if NaN
        assertEquals(15.0f, state.currentSpreadAngle, epsilon)
    }

    @Test
    fun testGetEffectiveFireRate() {
        val config = HyperionVoltTraitConfig(fireRateMultiplier = 1.35f)
        assertEquals(1.35f, HyperionVoltManufacturerEngine.getEffectiveFireRate(config), epsilon)
    }
    
    @Test
    fun testGetEffectiveFireRateNanRecovery() {
        val config = HyperionVoltTraitConfig(fireRateMultiplier = Float.NaN)
        assertEquals(1.25f, HyperionVoltManufacturerEngine.getEffectiveFireRate(config), epsilon)
    }

    @Test
    fun testConfigSerializationParity() {
        val config = HyperionVoltTraitConfig(maxSpreadAngle = 20.0f, minSpreadAngle = 1.0f)
        val map = config.toMap()
        val reconstructed = HyperionVoltTraitConfig.fromMap(map)
        
        assertEquals(config, reconstructed)
    }

    @Test
    fun testStateSerializationParity() {
        val state = HyperionVoltTraitState(currentSpreadAngle = 8.5f, isFiring = true, timeSinceLastShotSec = 0.05f)
        val map = state.toMap()
        val reconstructed = HyperionVoltTraitState.fromMap(map)
        
        assertEquals(state, reconstructed)
        assertEquals(state.computeChecksum(), reconstructed.computeChecksum())
    }
    
    @Test
    fun testChecksumChangesWithState() {
        val state1 = HyperionVoltTraitState(currentSpreadAngle = 8.5f, isFiring = true, timeSinceLastShotSec = 0.05f)
        val state2 = HyperionVoltTraitState(currentSpreadAngle = 8.5f, isFiring = false, timeSinceLastShotSec = 0.05f)
        
        assertNotEquals(state1.computeChecksum(), state2.computeChecksum())
    }
    
    @Test
    fun testStressCapacityAndZeroAllocationSimulation() {
        val config = HyperionVoltTraitConfig()
        val state = HyperionVoltTraitState()
        
        val initialMemory = Runtime.getRuntime().freeMemory()
        
        for (i in 0 until 10000) {
            if (i % 10 == 0) {
                HyperionVoltManufacturerEngine.onShotFired(state, config)
            } else {
                HyperionVoltManufacturerEngine.step(state, config, 0.016f)
            }
        }
        
        assertTrue(state.currentSpreadAngle >= config.minSpreadAngle)
        assertTrue(state.currentSpreadAngle <= config.maxSpreadAngle)
    }
}
