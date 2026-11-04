package com.example.game.loot.borderlands.catalog

import org.junit.Assert.*
import org.junit.Test

class TeslaArcGrenadeModTest {

    @Test
    fun `deploy creates correct initial state`() {
        val state = TeslaArcGrenadeEngine.deploy(100f, 200f, 12345L)
        assertEquals(100f, state.x, 0.001f)
        assertEquals(200f, state.y, 0.001f)
        assertEquals(0f, state.activeTimeSeconds, 0.001f)
        assertTrue(state.isActive)
        assertEquals(12345L, state.rngSeed)
        assertEquals(0f, state.totalDamageDealt, 0.001f)
    }

    @Test
    fun `deploy handles NaN inputs safely`() {
        val state = TeslaArcGrenadeEngine.deploy(Float.NaN, Float.POSITIVE_INFINITY, 12345L)
        assertEquals(0f, state.x, 0.001f)
        assertEquals(0f, state.y, 0.001f)
    }

    @Test
    fun `tick damages targets within radius`() {
        val config = TeslaArcGrenadeConfig(radiusPx = 100f, damagePerSecond = 100f)
        val state = TeslaArcGrenadeEngine.deploy(0f, 0f, 123L)
        
        val targetX = floatArrayOf(50f, 150f, 0f)
        val targetY = floatArrayOf(0f, 0f, 50f)
        val targetHp = floatArrayOf(200f, 200f, 200f)
        
        val hitCount = TeslaArcGrenadeEngine.tick(0.1f, config, state, targetX, targetY, targetHp, 3)
        
        assertEquals(2, hitCount)
        assertTrue(targetHp[0] < 200f) // damaged
        assertEquals(200f, targetHp[1], 0.001f) // outside radius
        assertTrue(targetHp[2] < 200f) // damaged
    }

    @Test
    fun `tick enforces max targets per tick limit`() {
        val config = TeslaArcGrenadeConfig(radiusPx = 500f, maxTargetsPerTick = 2, damagePerSecond = 100f)
        val state = TeslaArcGrenadeEngine.deploy(0f, 0f, 123L)
        
        val targetX = floatArrayOf(10f, 20f, 30f, 40f)
        val targetY = floatArrayOf(10f, 20f, 30f, 40f)
        val targetHp = floatArrayOf(100f, 100f, 100f, 100f)
        
        val hitCount = TeslaArcGrenadeEngine.tick(0.1f, config, state, targetX, targetY, targetHp, 4)
        
        assertEquals(2, hitCount)
        assertTrue(targetHp[0] < 100f)
        assertTrue(targetHp[1] < 100f)
        assertEquals(100f, targetHp[2], 0.001f)
        assertEquals(100f, targetHp[3], 0.001f)
    }

    @Test
    fun `tick clamps dtSeconds and respects duration`() {
        val config = TeslaArcGrenadeConfig(durationSeconds = 2.0f)
        val state = TeslaArcGrenadeEngine.deploy(0f, 0f, 123L)
        
        val emptyX = floatArrayOf()
        val emptyY = floatArrayOf()
        val emptyHp = floatArrayOf()
        
        // Tick with very large dtSeconds
        TeslaArcGrenadeEngine.tick(1.0f, config, state, emptyX, emptyY, emptyHp, 0)
        // Should be clamped to 0.1
        assertEquals(0.1f, state.activeTimeSeconds, 0.001f)
        
        // Push over duration
        state.activeTimeSeconds = 1.95f
        TeslaArcGrenadeEngine.tick(0.1f, config, state, emptyX, emptyY, emptyHp, 0)
        assertFalse(state.isActive)
        
        // Once inactive, tick does nothing
        val hitCount = TeslaArcGrenadeEngine.tick(0.1f, config, state, emptyX, emptyY, emptyHp, 0)
        assertEquals(0, hitCount)
    }

    @Test
    fun `tick ignores NaN or dead targets`() {
        val config = TeslaArcGrenadeConfig(radiusPx = 100f, damagePerSecond = 100f)
        val state = TeslaArcGrenadeEngine.deploy(0f, 0f, 123L)
        
        val targetX = floatArrayOf(10f, Float.NaN, 10f, 10f)
        val targetY = floatArrayOf(10f, 10f, Float.NaN, 10f)
        val targetHp = floatArrayOf(-10f, 100f, 100f, Float.NaN)
        
        val hitCount = TeslaArcGrenadeEngine.tick(0.1f, config, state, targetX, targetY, targetHp, 4)
        
        assertEquals(0, hitCount)
    }

    @Test
    fun `determinism test identical inputs yield identical outputs`() {
        val config = TeslaArcGrenadeConfig(radiusPx = 100f, damagePerSecond = 100f)
        
        val state1 = TeslaArcGrenadeEngine.deploy(0f, 0f, 999L)
        val targetX1 = floatArrayOf(10f, 20f)
        val targetY1 = floatArrayOf(10f, 20f)
        val targetHp1 = floatArrayOf(100f, 100f)
        TeslaArcGrenadeEngine.tick(0.1f, config, state1, targetX1, targetY1, targetHp1, 2)
        
        val state2 = TeslaArcGrenadeEngine.deploy(0f, 0f, 999L)
        val targetX2 = floatArrayOf(10f, 20f)
        val targetY2 = floatArrayOf(10f, 20f)
        val targetHp2 = floatArrayOf(100f, 100f)
        TeslaArcGrenadeEngine.tick(0.1f, config, state2, targetX2, targetY2, targetHp2, 2)
        
        assertEquals(state1.rngSeed, state2.rngSeed)
        assertEquals(targetHp1[0], targetHp2[0], 0.0001f)
        assertEquals(targetHp1[1], targetHp2[1], 0.0001f)
        assertEquals(state1.calculateChecksum(), state2.calculateChecksum())
    }

    @Test
    fun `stress test 300 targets bounds check`() {
        val config = TeslaArcGrenadeConfig(radiusPx = 1000f, maxTargetsPerTick = 50, damagePerSecond = 100f)
        val state = TeslaArcGrenadeEngine.deploy(0f, 0f, 42L)
        
        val count = 300
        val targetX = FloatArray(count) { it.toFloat() }
        val targetY = FloatArray(count) { it.toFloat() }
        val targetHp = FloatArray(count) { 100f }
        
        val hitCount = TeslaArcGrenadeEngine.tick(0.1f, config, state, targetX, targetY, targetHp, count)
        
        assertEquals(50, hitCount) // Hits max limit
        assertTrue(state.totalDamageDealt > 0f)
    }

    @Test
    fun `serialization roundtrip for Config`() {
        val original = TeslaArcGrenadeConfig(radiusPx = 123f, damagePerSecond = 456f, durationSeconds = 7.5f, maxTargetsPerTick = 15)
        val map = original.toMap()
        val restored = TeslaArcGrenadeConfig.fromMap(map)
        
        assertEquals(original.radiusPx, restored.radiusPx, 0.001f)
        assertEquals(original.damagePerSecond, restored.damagePerSecond, 0.001f)
        assertEquals(original.durationSeconds, restored.durationSeconds, 0.001f)
        assertEquals(original.maxTargetsPerTick, restored.maxTargetsPerTick)
    }

    @Test
    fun `serialization roundtrip for State`() {
        val original = TeslaArcGrenadeState(x = 10f, y = 20f, activeTimeSeconds = 1.5f, isActive = false, rngSeed = 555L, totalDamageDealt = 999f)
        val map = original.toMap()
        val restored = TeslaArcGrenadeState.fromMap(map)
        
        assertEquals(original.x, restored.x, 0.001f)
        assertEquals(original.y, restored.y, 0.001f)
        assertEquals(original.activeTimeSeconds, restored.activeTimeSeconds, 0.001f)
        assertEquals(original.isActive, restored.isActive)
        assertEquals(original.rngSeed, restored.rngSeed)
        assertEquals(original.totalDamageDealt, restored.totalDamageDealt, 0.001f)
    }

    @Test
    fun `state checksum is stable and changes appropriately`() {
        val state = TeslaArcGrenadeState(x = 10f, y = 20f, activeTimeSeconds = 1.5f, isActive = false, rngSeed = 555L, totalDamageDealt = 999f)
        val checksum1 = state.calculateChecksum()
        
        // Exact identical object
        val state2 = TeslaArcGrenadeState(x = 10f, y = 20f, activeTimeSeconds = 1.5f, isActive = false, rngSeed = 555L, totalDamageDealt = 999f)
        assertEquals(checksum1, state2.calculateChecksum())
        
        // Mutate one field
        state2.x = 11f
        assertNotEquals(checksum1, state2.calculateChecksum())
    }

    @Test
    fun `graceful degradation with mismatched array sizes`() {
        val config = TeslaArcGrenadeConfig(radiusPx = 100f, damagePerSecond = 100f)
        val state = TeslaArcGrenadeEngine.deploy(0f, 0f, 123L)
        
        val targetX = floatArrayOf(10f, 20f, 30f) // size 3
        val targetY = floatArrayOf(10f, 20f)       // size 2
        val targetHp = floatArrayOf(100f, 100f, 100f, 100f) // size 4
        
        // Should clamp count to the minimum array size which is 2
        val hitCount = TeslaArcGrenadeEngine.tick(0.1f, config, state, targetX, targetY, targetHp, 5)
        
        assertEquals(2, hitCount)
    }
}
