package com.example.game.loot.borderlands.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeaponOpticPartCatalogTest {

    @Test
    fun `test catalog size is exactly 16`() {
        assertEquals("Catalog should contain exactly 16 optic parts", 16, WeaponOpticPartCatalog.CATALOG.size)
    }

    @Test
    fun `test getOpticById returns correct optic`() {
        val optic = WeaponOpticPartCatalog.getOpticById("OPTIC_16")
        assertEquals("Abyssal Lens", optic.displayName)
        assertEquals(1000f, optic.lanternRangePx, 0.001f)
    }

    @Test
    fun `test getOpticById fallback for invalid id`() {
        val fallback = WeaponOpticPartCatalog.getOpticById("INVALID_ID_999")
        assertEquals("OPTIC_01", fallback.id)
    }

    @Test
    fun `test getRandomOptic determinism`() {
        val seed = 12345L
        val optic1 = WeaponOpticPartCatalog.getRandomOptic(seed)
        val optic2 = WeaponOpticPartCatalog.getRandomOptic(seed)
        assertEquals("Deterministic LCG should return identical results for same seed", optic1.id, optic2.id)
    }

    @Test
    fun `test WeaponOpticPart serialization roundtrip`() {
        val original = WeaponOpticPart("TEST_ID", "Test Optic", 150f, 30f, 1.5f)
        val map = original.toMap()
        val deserialized = WeaponOpticPart.fromMap(map)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.displayName, deserialized.displayName)
        assertEquals(original.lanternRangePx, deserialized.lanternRangePx, 0.001f)
        assertEquals(original.lightLockConeDegrees, deserialized.lightLockConeDegrees, 0.001f)
        assertEquals(original.critMultiplier, deserialized.critMultiplier, 0.001f)
    }

    @Test
    fun `test WeaponOpticPart fromMap adversarial conditions`() {
        val badMap = mapOf(
            "id" to "BAD_OPTIC",
            "lanternRangePx" to -500f, // Negative, should clamp
            "lightLockConeDegrees" to 900f, // Excess, should clamp
            "critMultiplier" to Float.NaN // NaN, should recover
        )
        val recovered = WeaponOpticPart.fromMap(badMap)
        
        assertEquals("BAD_OPTIC", recovered.id)
        assertEquals("Default Iron Sights", recovered.displayName) // Missing field default
        assertEquals(10f, recovered.lanternRangePx, 0.001f) // Clamped to min 10
        assertEquals(360f, recovered.lightLockConeDegrees, 0.001f) // Clamped to max 360
        assertEquals(1.0f, recovered.critMultiplier, 0.001f) // Recovered from NaN
    }

    @Test
    fun `test computeChecksum determinism and uniqueness`() {
        val optic1 = WeaponOpticPart("ID_1", "Optic 1", 100f, 10f, 1.0f)
        val optic2 = WeaponOpticPart("ID_1", "Optic 1", 100f, 10f, 1.0f)
        val optic3 = WeaponOpticPart("ID_2", "Optic 2", 200f, 20f, 2.0f)

        assertEquals("Same data should produce same checksum", optic1.computeChecksum(), optic2.computeChecksum())
        assertNotEquals("Different data should produce different checksum", optic1.computeChecksum(), optic3.computeChecksum())
    }

    @Test
    fun `test OpticSimulationState serialization roundtrip`() {
        val original = OpticSimulationState(99, 2.5f, 300f, 90f)
        val map = original.toMap()
        val deserialized = OpticSimulationState.fromMap(map)

        assertEquals(original.entityId, deserialized.entityId)
        assertEquals(original.currentCritMultiplier, deserialized.currentCritMultiplier, 0.001f)
        assertEquals(original.activeLanternRangePx, deserialized.activeLanternRangePx, 0.001f)
        assertEquals(original.activeLightLockConeDegrees, deserialized.activeLightLockConeDegrees, 0.001f)
    }

    @Test
    fun `test OpticSimulationState fromMap adversarial`() {
        val badMap = mapOf(
            "entityId" to "not-a-number",
            "currentCritMultiplier" to Float.POSITIVE_INFINITY,
            "activeLanternRangePx" to -100f,
            "activeLightLockConeDegrees" to 1000f
        )
        val recovered = OpticSimulationState.fromMap(badMap)

        assertEquals(-1, recovered.entityId) // parse failure fallback
        assertEquals(1.0f, recovered.currentCritMultiplier, 0.001f) // Infinity recovery
        assertEquals(10f, recovered.activeLanternRangePx, 0.001f) // Clamped
        assertEquals(360f, recovered.activeLightLockConeDegrees, 0.001f) // Clamped
    }

    @Test
    fun `test processOpticStates normal execution`() {
        val state = OpticSimulationState(1, 2.0f, 500f, 45f)
        val states = arrayOf(state)
        
        WeaponOpticPartCatalog.processOpticStates(states, 1, 0.1f) // max dt
        
        assertEquals(499.5f, state.activeLanternRangePx, 0.001f) // 500 - (0.1 * 5)
        assertEquals(45.1f, state.activeLightLockConeDegrees, 0.001f) // 45 + (0.1 * 1)
        assertEquals(2.0f, state.currentCritMultiplier, 0.001f) // unchanged
    }

    @Test
    fun `test processOpticStates extreme load and stress capacity`() {
        val count = 300
        val states = Array(count) { OpticSimulationState(it, 1.5f, 300f, 30f) }
        
        WeaponOpticPartCatalog.processOpticStates(states, count, 0.016f) // ~60fps dt
        
        for (i in 0 until count) {
            assertEquals(1.5f, states[i].currentCritMultiplier, 0.001f)
            assertTrue("Range should decrease", states[i].activeLanternRangePx < 300f)
            assertTrue("Cone should increase", states[i].activeLightLockConeDegrees > 30f)
        }
    }

    @Test
    fun `test processOpticStates dt bounds clamping`() {
        val state1 = OpticSimulationState(1, 2.0f, 500f, 45f)
        val state2 = OpticSimulationState(2, 2.0f, 500f, 45f)
        
        // Negative dt should be clamped to 0.001f
        WeaponOpticPartCatalog.processOpticStates(arrayOf(state1), 1, -1.0f)
        assertEquals(500f - (0.001f * 5f), state1.activeLanternRangePx, 0.001f)
        
        // Massive dt should be clamped to 0.100f
        WeaponOpticPartCatalog.processOpticStates(arrayOf(state2), 1, 100.0f)
        assertEquals(500f - (0.100f * 5f), state2.activeLanternRangePx, 0.001f)
    }

    @Test
    fun `test processOpticStates NaN recovery`() {
        val state = OpticSimulationState(1, Float.NaN, Float.NaN, Float.NaN)
        val states = arrayOf(state)
        
        WeaponOpticPartCatalog.processOpticStates(states, 1, 0.016f)
        
        // Values should recover to defaults and clamp
        assertEquals(200f, state.activeLanternRangePx, 0.001f)
        assertEquals(45f, state.activeLightLockConeDegrees, 0.001f)
        assertEquals(1.0f, state.currentCritMultiplier, 0.001f)
    }
}
