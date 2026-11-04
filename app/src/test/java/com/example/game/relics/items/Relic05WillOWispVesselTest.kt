package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test

class Relic05WillOWispVesselTest {

    @Test
    fun testConfigDefaultsAndInvariant() {
        val config = WillOWispVesselConfig()
        assertFalse("Invariant: canBePurchasedAtVending must be false", config.canBePurchasedAtVending)
        assertEquals(3.5f, config.baseDamageMultiplier, 0.001f)
        assertEquals(2.8f, config.perStackDamageMultiplier, 0.001f)
        assertEquals(48f, config.baseRadiusPx, 0.001f)
        assertEquals(24f, config.perStackRadiusPx, 0.001f)
        assertEquals(0.5f, config.fuseTimeSeconds, 0.001f)
    }

    @Test
    fun testConfigSerialization() {
        val config = WillOWispVesselConfig(
            baseDamageMultiplier = 4.0f,
            fuseTimeSeconds = 1.0f
        )
        val map = config.toMap()
        val restored = WillOWispVesselConfig.fromMap(map)
        assertEquals(config, restored)
    }

    @Test
    fun testStateSerialization() {
        val state = WillOWispVesselState()
        val engine = Relic05WillOWispVesselEngine()
        engine.registerKill(state, 10f, 20f, 100f, 1)
        engine.registerKill(state, 30f, 40f, 50f, 2)
        
        val map = state.toMap()
        val restored = WillOWispVesselState.fromMap(map)
        
        assertEquals(state, restored)
        assertEquals(state.getChecksum(), restored.getChecksum())
    }

    @Test
    fun testEmptyStateChecksum() {
        val state1 = WillOWispVesselState()
        val state2 = WillOWispVesselState()
        assertEquals(state1.getChecksum(), state2.getChecksum())
    }

    @Test
    fun testDeterministicChecksum() {
        val state1 = WillOWispVesselState()
        val state2 = WillOWispVesselState()
        val engine = Relic05WillOWispVesselEngine()
        
        engine.registerKill(state1, 15f, 25f, 100f, 1)
        engine.registerKill(state2, 15f, 25f, 100f, 1)
        
        assertEquals(state1.getChecksum(), state2.getChecksum())
        
        engine.registerKill(state2, 15f, 26f, 100f, 1)
        assertNotEquals(state1.getChecksum(), state2.getChecksum())
    }

    @Test
    fun testStackScalingLogic() {
        val engine = Relic05WillOWispVesselEngine()
        
        // Stack 1
        assertEquals(3.5f * 100f, engine.calculateDamage(100f, 1), 0.001f)
        assertEquals(48f, engine.calculateRadius(1), 0.001f)
        
        // Stack 2
        assertEquals((3.5f + 2.8f) * 100f, engine.calculateDamage(100f, 2), 0.001f)
        assertEquals(48f + 24f, engine.calculateRadius(2), 0.001f)
        
        // Stack 3
        assertEquals((3.5f + 2.8f * 2) * 100f, engine.calculateDamage(100f, 3), 0.001f)
        assertEquals(48f + 24f * 2, engine.calculateRadius(3), 0.001f)
    }

    @Test
    fun testBoundaryInputs() {
        val engine = Relic05WillOWispVesselEngine()
        
        // Invalid stack counts should be coerced to 1
        assertEquals(engine.calculateDamage(100f, 1), engine.calculateDamage(100f, 0), 0.001f)
        assertEquals(engine.calculateDamage(100f, 1), engine.calculateDamage(100f, -5), 0.001f)
        
        // NaN base damage
        assertEquals(0f, engine.calculateDamage(Float.NaN, 1), 0.001f)
        
        // Infinity base damage
        assertEquals(0f, engine.calculateDamage(Float.POSITIVE_INFINITY, 1), 0.001f)
        
        // Negative base damage
        assertEquals(0f, engine.calculateDamage(-100f, 1), 0.001f)
    }

    @Test
    fun testRegisterKillNaNAndInfinity() {
        val state = WillOWispVesselState()
        val engine = Relic05WillOWispVesselEngine()
        
        assertFalse(engine.registerKill(state, Float.NaN, 10f, 100f, 1))
        assertFalse(engine.registerKill(state, 10f, Float.NaN, 100f, 1))
        assertFalse(engine.registerKill(state, Float.POSITIVE_INFINITY, 10f, 100f, 1))
        assertFalse(engine.registerKill(state, 10f, Float.NEGATIVE_INFINITY, 100f, 1))
        
        assertEquals(0, state.activeCount)
    }

    @Test
    fun testMaxCapacityGracefulDegradation() {
        val state = WillOWispVesselState()
        val engine = Relic05WillOWispVesselEngine()
        
        for (i in 0 until WillOWispVesselState.MAX_CAPACITY + 10) {
            val result = engine.registerKill(state, 10f, 10f, 100f, 1)
            if (i < WillOWispVesselState.MAX_CAPACITY) {
                assertTrue(result)
            } else {
                assertFalse(result)
            }
        }
        
        assertEquals(WillOWispVesselState.MAX_CAPACITY, state.activeCount)
    }

    @Test
    fun testDtClamping() {
        val state = WillOWispVesselState()
        val engine = Relic05WillOWispVesselEngine()
        
        engine.registerKill(state, 10f, 10f, 100f, 1)
        val initialFuse = state.timeRemaining[0]
        
        // Try passing a huge dt
        engine.tick(state, 100f) // Should clamp to 0.100f
        
        assertEquals(initialFuse - 0.100f, state.timeRemaining[0], 0.001f)
        
        // Try passing a negative dt
        engine.tick(state, -10f) // Should clamp to 0.001f
        assertEquals(initialFuse - 0.101f, state.timeRemaining[0], 0.001f)
    }

    @Test
    fun testSwapAndPopFuseExecution() {
        val state = WillOWispVesselState()
        val engine = Relic05WillOWispVesselEngine()
        
        engine.registerKill(state, 10f, 10f, 100f, 1)
        engine.registerKill(state, 20f, 20f, 100f, 1)
        engine.registerKill(state, 30f, 30f, 100f, 1)
        
        // Force the middle one to detonate quickly
        state.timeRemaining[1] = 0.05f
        
        val detonations = engine.tick(state, 0.1f)
        
        assertEquals(1, detonations.size)
        assertEquals(20f, detonations[0].x, 0.001f)
        
        assertEquals(2, state.activeCount)
        // The last element (30, 30) should have been swapped into index 1
        assertEquals(30f, state.xCoords[1], 0.001f)
    }

    @Test
    fun testApplyAoeDamageSpatialLogic() {
        val engine = Relic05WillOWispVesselEngine()
        val detonation = Relic05WillOWispVesselEngine.DetonationResult(
            x = 0f, y = 0f, radius = 50f, damage = 100f
        )
        
        val targetX = floatArrayOf(0f, 40f, 60f, 0f)
        val targetY = floatArrayOf(0f, 0f, 0f, 40f)
        val targetHp = floatArrayOf(200f, 200f, 200f, 50f)
        
        engine.applyAoeDamage(detonation, targetX, targetY, targetHp, 4)
        
        // Target 0 is at center (dist = 0 <= 50)
        assertEquals(100f, targetHp[0], 0.001f)
        
        // Target 1 is within radius (dist = 40 <= 50)
        assertEquals(100f, targetHp[1], 0.001f)
        
        // Target 2 is outside radius (dist = 60 > 50)
        assertEquals(200f, targetHp[2], 0.001f)
        
        // Target 3 is within radius and should be killed but clamped to 0
        assertEquals(0f, targetHp[3], 0.001f)
    }

    @Test
    fun testApplyAoeDamageIgnoresDeadTargets() {
        val engine = Relic05WillOWispVesselEngine()
        val detonation = Relic05WillOWispVesselEngine.DetonationResult(
            x = 0f, y = 0f, radius = 50f, damage = 100f
        )
        
        val targetX = floatArrayOf(0f)
        val targetY = floatArrayOf(0f)
        val targetHp = floatArrayOf(0f) // Already dead
        
        engine.applyAoeDamage(detonation, targetX, targetY, targetHp, 1)
        
        assertEquals(0f, targetHp[0], 0.001f)
    }
}
