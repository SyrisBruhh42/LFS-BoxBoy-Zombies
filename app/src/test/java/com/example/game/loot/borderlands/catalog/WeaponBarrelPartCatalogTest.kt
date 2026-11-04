package com.example.game.loot.borderlands.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WeaponBarrelPartCatalogTest {

    @Test
    fun testResolveBaseConfig_ZeroAllocation() {
        val config1 = WeaponBarrelPartCatalog.resolveBaseConfig(BarrelPartType.SAWED_OFF)
        val config2 = WeaponBarrelPartCatalog.resolveBaseConfig(BarrelPartType.SAWED_OFF)

        // Assert same reference returned to prove zero-allocation
        assertSame(config1, config2)
        assertEquals(1.8f, config1.spreadMultiplier, 0.0001f)
        assertEquals(2, config1.pelletCountBonus)
    }

    @Test
    fun testResolveBaseConfig_NullInputFallback() {
        val config = WeaponBarrelPartCatalog.resolveBaseConfig(null)
        val standardConfig = WeaponBarrelPartCatalog.resolveBaseConfig(BarrelPartType.STANDARD)
        assertSame(standardConfig, config)
    }

    @Test
    fun testChecksumCalculation_Integrity() {
        val config = WeaponBarrelPartCatalog.resolveBaseConfig(BarrelPartType.SNIPER)
        val calculated = WeaponBarrelConfig.calculateChecksum(
            config.type, config.seed, config.spreadMultiplier,
            config.pelletCountBonus, config.rangeMultiplier, config.pierceBonus
        )
        assertEquals(config.checksum, calculated)
    }

    @Test
    fun testProceduralGeneration_Determinism() {
        val seed = 42069
        val config1 = WeaponBarrelPartCatalog.generateProceduralBarrel(BarrelPartType.HEAVY, seed)
        val config2 = WeaponBarrelPartCatalog.generateProceduralBarrel(BarrelPartType.HEAVY, seed)

        // Exact equality check
        assertEquals(config1, config2)
        assertEquals(config1.spreadMultiplier, config2.spreadMultiplier, 0.0001f)
        assertEquals(config1.rangeMultiplier, config2.rangeMultiplier, 0.0001f)
        assertEquals(config1.checksum, config2.checksum)

        // Ensure variation occurred from base
        val baseConfig = WeaponBarrelPartCatalog.resolveBaseConfig(BarrelPartType.HEAVY)
        assertTrue(config1.spreadMultiplier != baseConfig.spreadMultiplier || config1.rangeMultiplier != baseConfig.rangeMultiplier)
    }

    @Test
    fun testProceduralGeneration_DifferentSeedsYieldDifferentResults() {
        val config1 = WeaponBarrelPartCatalog.generateProceduralBarrel(BarrelPartType.SPREAD, 100)
        val config2 = WeaponBarrelPartCatalog.generateProceduralBarrel(BarrelPartType.SPREAD, 200)

        assertTrue(config1.spreadMultiplier != config2.spreadMultiplier || config1.rangeMultiplier != config2.rangeMultiplier)
        assertTrue(config1.checksum != config2.checksum)
    }

    @Test
    fun testTickBarrelHeat_ValidInput() {
        val newHeat = WeaponBarrelPartCatalog.tickBarrelHeat(100f, 0.05f, 10f)
        // 100 - (10 * 0.05) = 100 - 0.5 = 99.5
        assertEquals(99.5f, newHeat, 0.0001f)
    }

    @Test
    fun testTickBarrelHeat_ClampDt() {
        val clampedHigh = WeaponBarrelPartCatalog.tickBarrelHeat(100f, 1.0f, 10f) // dt 1.0 clamped to 0.1
        // 100 - (10 * 0.1) = 99.0
        assertEquals(99.0f, clampedHigh, 0.0001f)

        val clampedLow = WeaponBarrelPartCatalog.tickBarrelHeat(100f, -5f, 10f) // dt -5 clamped to 0.001
        // 100 - (10 * 0.001) = 99.99
        assertEquals(99.99f, clampedLow, 0.0001f)

        val nanInput = WeaponBarrelPartCatalog.tickBarrelHeat(100f, Float.NaN, 10f)
        assertEquals(99.99f, nanInput, 0.0001f)
    }

    @Test
    fun testSerializationRoundtrip() {
        val original = WeaponBarrelPartCatalog.generateProceduralBarrel(BarrelPartType.BURST, 999)
        val map = original.toMap()
        val deserialized = WeaponBarrelConfig.fromMap(map)

        assertEquals(original, deserialized)
        assertNotSame(original, deserialized)
    }

    @Test
    fun testSerializationRoundtrip_MissingFieldsFallback() {
        val emptyMap = emptyMap<String, Any>()
        val deserialized = WeaponBarrelConfig.fromMap(emptyMap)

        assertEquals(BarrelPartType.STANDARD, deserialized.type)
        assertEquals(1.0f, deserialized.spreadMultiplier, 0.0001f)
        assertEquals(0, deserialized.pelletCountBonus)
        assertEquals(1.0f, deserialized.rangeMultiplier, 0.0001f)
        assertEquals(0, deserialized.pierceBonus)
    }

    @Test
    fun testSerializationRoundtrip_PartialFields() {
        val map = mapOf<String, Any>(
            "type" to "QUAD_BARREL",
            "spreadMultiplier" to 2.5f,
            "pierceBonus" to 5
        )
        val deserialized = WeaponBarrelConfig.fromMap(map)

        assertEquals(BarrelPartType.QUAD_BARREL, deserialized.type)
        assertEquals(2.5f, deserialized.spreadMultiplier, 0.0001f)
        assertEquals(0, deserialized.pelletCountBonus)
        assertEquals(1.0f, deserialized.rangeMultiplier, 0.0001f)
        assertEquals(5, deserialized.pierceBonus)
    }

    @Test
    fun testMaxCapacityStressLoop() {
        val iterations = 1000
        val types = BarrelPartType.values()
        var totalChecksums = 0

        for (i in 0 until iterations) {
            val type = types[i % types.size]
            val config = WeaponBarrelPartCatalog.resolveBaseConfig(type)
            val procedural = WeaponBarrelPartCatalog.generateProceduralBarrel(type, i)
            totalChecksums = totalChecksums xor config.checksum xor procedural.checksum
        }

        assertTrue("Expected a varied checksum", totalChecksums != 0)
    }

    @Test
    fun testBoundaryAdversarial_NaNInjection() {
        val map = mapOf<String, Any>(
            "spreadMultiplier" to Float.NaN,
            "rangeMultiplier" to Float.NaN
        )
        val deserialized = WeaponBarrelConfig.fromMap(map)

        assertEquals(1.0f, deserialized.spreadMultiplier, 0.0001f)
        assertEquals(1.0f, deserialized.rangeMultiplier, 0.0001f)
    }
}
