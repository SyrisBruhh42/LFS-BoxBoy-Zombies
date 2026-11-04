package com.example.game.relics.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.math.abs

class Relic24TopazBroochBarrierTest {

    @Test
    fun testNominalBarrierGrantedOnKill() {
        val config = TopazBroochConfig(stackCount = 1)
        val state = TopazBroochState()
        
        Relic24TopazBroochBarrierEngine.registerKill(config, state, maxHp = 100f)
        
        assertEquals(15.0f, state.currentBarrier, 0.001f)
    }

    @Test
    fun testBarrierStackScaling() {
        val config = TopazBroochConfig(stackCount = 3)
        val state = TopazBroochState()
        
        Relic24TopazBroochBarrierEngine.registerKill(config, state, maxHp = 100f)
        
        assertEquals(45.0f, state.currentBarrier, 0.001f)
    }

    @Test
    fun testBarrierClampedToMaxHp() {
        val config = TopazBroochConfig(stackCount = 10) // 150 barrier
        val state = TopazBroochState()
        
        Relic24TopazBroochBarrierEngine.registerKill(config, state, maxHp = 100f)
        
        // Clamped to 100% of maxHp which is 100f
        assertEquals(100.0f, state.currentBarrier, 0.001f)
    }

    @Test
    fun testBarrierDecayOverTime() {
        val config = TopazBroochConfig(stackCount = 1)
        val state = TopazBroochState(currentBarrier = 15.0f)
        
        // Decay is maxHp * 0.0333 * dt
        // 100 * 0.0333 * 0.1 = 0.333
        Relic24TopazBroochBarrierEngine.tick(config, state, dtSeconds = 0.1f, maxHp = 100f)
        
        assertEquals(15.0f - 0.333f, state.currentBarrier, 0.001f)
    }

    @Test
    fun testBarrierDoesNotDecayBelowZero() {
        val config = TopazBroochConfig(stackCount = 1)
        val state = TopazBroochState(currentBarrier = 0.1f)
        
        Relic24TopazBroochBarrierEngine.tick(config, state, dtSeconds = 0.1f, maxHp = 100f)
        
        assertEquals(0.0f, state.currentBarrier, 0.001f)
    }

    @Test
    fun testDamageAbsorbedEntirelyByBarrier() {
        val state = TopazBroochState(currentBarrier = 30.0f)
        
        val remainingDamage = Relic24TopazBroochBarrierEngine.processDamage(state, 20.0f)
        
        assertEquals(0.0f, remainingDamage, 0.001f)
        assertEquals(10.0f, state.currentBarrier, 0.001f)
    }

    @Test
    fun testDamagePartiallyAbsorbedByBarrier() {
        val state = TopazBroochState(currentBarrier = 20.0f)
        
        val remainingDamage = Relic24TopazBroochBarrierEngine.processDamage(state, 50.0f)
        
        assertEquals(30.0f, remainingDamage, 0.001f)
        assertEquals(0.0f, state.currentBarrier, 0.001f)
    }

    @Test
    fun testInvalidDtIsClamped() {
        val config = TopazBroochConfig(stackCount = 1)
        
        val stateMaxDt = TopazBroochState(currentBarrier = 15.0f)
        Relic24TopazBroochBarrierEngine.tick(config, stateMaxDt, dtSeconds = 1.0f, maxHp = 100f) // clamped to 0.1
        assertEquals(15.0f - 0.333f, stateMaxDt.currentBarrier, 0.001f)
        
        val stateMinDt = TopazBroochState(currentBarrier = 15.0f)
        Relic24TopazBroochBarrierEngine.tick(config, stateMinDt, dtSeconds = -0.5f, maxHp = 100f) // clamped to 0.001
        assertEquals(15.0f - 0.00333f, stateMinDt.currentBarrier, 0.001f)
    }

    @Test
    fun testSerializationRoundtrip() {
        val config = TopazBroochConfig(stackCount = 5)
        val configMap = config.toMap()
        val restoredConfig = TopazBroochConfig.fromMap(configMap)
        
        assertEquals(config.stackCount, restoredConfig.stackCount)
        assertEquals(config.canBePurchasedAtVending, restoredConfig.canBePurchasedAtVending)
        
        val state = TopazBroochState(currentBarrier = 42.5f)
        val stateMap = state.toMap()
        val restoredState = TopazBroochState.fromMap(stateMap)
        
        assertEquals(state.currentBarrier, restoredState.currentBarrier, 0.001f)
    }

    @Test
    fun testDeterminismAndChecksum() {
        val state1 = TopazBroochState(currentBarrier = 12.34f)
        val state2 = TopazBroochState(currentBarrier = 12.34f)
        val state3 = TopazBroochState(currentBarrier = 12.35f)
        
        assertEquals(state1.computeChecksum(), state2.computeChecksum())
        assertTrue(state1.computeChecksum() != state3.computeChecksum())
    }

    @Test
    fun testBoundsCheckingAndExceptions() {
        var caught1 = false
        try {
            TopazBroochConfig(stackCount = 0)
        } catch (e: IllegalArgumentException) {
            caught1 = true
        }
        assertTrue("Expected exception for stackCount < 1", caught1)
        
        var caught2 = false
        try {
            TopazBroochConfig(stackCount = 1, canBePurchasedAtVending = true)
        } catch (e: IllegalArgumentException) {
            caught2 = true
        }
        assertTrue("Expected exception for canBePurchasedAtVending = true", caught2)
    }

    @Test
    fun testNaNAndInfinityResilience() {
        val config = TopazBroochConfig(stackCount = 1)
        val state = TopazBroochState(currentBarrier = 10f)
        
        // registerKill with NaN maxHp should do nothing
        Relic24TopazBroochBarrierEngine.registerKill(config, state, maxHp = Float.NaN)
        assertEquals(10f, state.currentBarrier, 0.001f)
        
        // tick with NaN dt should use 0.001f
        Relic24TopazBroochBarrierEngine.tick(config, state, dtSeconds = Float.NaN, maxHp = 100f)
        assertEquals(10f - 0.00333f, state.currentBarrier, 0.001f)
        
        // processDamage with Infinity should return 0f remaining if not handled, 
        // our code returns 0f for invalid damage, so no damage processed.
        val stateInf = TopazBroochState(currentBarrier = 20f)
        val remaining = Relic24TopazBroochBarrierEngine.processDamage(stateInf, Float.POSITIVE_INFINITY)
        assertEquals(0f, remaining, 0.001f)
        assertEquals(20f, stateInf.currentBarrier, 0.001f)
    }
    
    @Test
    fun testMaxCapacityStress() {
        val config = TopazBroochConfig(stackCount = 100) // 1500 barrier per kill
        // simulate 300 entities
        for (i in 0 until 300) {
            val state = TopazBroochState()
            Relic24TopazBroochBarrierEngine.registerKill(config, state, maxHp = 500f)
            assertEquals(500f, state.currentBarrier, 0.001f)
            
            val remaining = Relic24TopazBroochBarrierEngine.processDamage(state, 100f)
            assertEquals(0f, remaining, 0.001f)
            assertEquals(400f, state.currentBarrier, 0.001f)
            
            Relic24TopazBroochBarrierEngine.tick(config, state, dtSeconds = 0.1f, maxHp = 500f)
            // 500 * 0.0333 * 0.1 = 1.665 decay
            assertEquals(400f - 1.665f, state.currentBarrier, 0.001f)
        }
    }
}