package com.example.game.relics.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

class Relic16BrilliantBehemothTest {

    @Test
    fun testConfigInvariants() {
        val config = BrilliantBehemothConfig()
        assertEquals(48f, config.baseRadius, 0.001f)
        assertEquals(24f, config.radiusPerStack, 0.001f)
        assertEquals(0.60f, config.bonusDamageRatio, 0.001f)
        assertFalse("Brilliant Behemoth is drop-only!", config.canBePurchasedAtVending)
    }

    @Test
    fun testStateClamping() {
        val state = BrilliantBehemothState(1)
        assertEquals(1, state.stackCount)

        try {
            BrilliantBehemothState(0)
            fail("Expected exception for stack count 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("at least 1 stack"))
        }

        try {
            BrilliantBehemothState(-5)
            fail("Expected exception for negative stack count")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("at least 1 stack"))
        }
    }

    @Test
    fun testSerializationRoundtrip() {
        val original = BrilliantBehemothState(3)
        val map = original.toMap()
        val deserialized = BrilliantBehemothState.fromMap(map)

        assertEquals(original.stackCount, deserialized.stackCount)
        assertEquals(original.checksum(), deserialized.checksum())
    }

    @Test
    fun testSerializationWithMissingKeys() {
        val map = emptyMap<String, Any>()
        val deserialized = BrilliantBehemothState.fromMap(map)
        assertEquals(1, deserialized.stackCount) // default fallback
    }

    @Test
    fun testFnv1aChecksumDeterminism() {
        val state1 = BrilliantBehemothState(5)
        val state2 = BrilliantBehemothState(5)
        val state3 = BrilliantBehemothState(6)

        assertEquals(state1.checksum(), state2.checksum())
        assertTrue(state1.checksum() != state3.checksum())

        // Hardcoded checksum verification for 5 stacks
        var hash = -2128831035146727653L // FNV-1a 64-bit offset
        hash = hash xor 5L
        hash = hash * 1099511628211L
        assertEquals(hash, state1.checksum())
    }

    @Test
    fun testCalculateExplosionRadiusNominal() {
        val config = BrilliantBehemothConfig()

        val r1 = BrilliantBehemothEngine.calculateExplosionRadius(config, BrilliantBehemothState(1))
        assertEquals(48f, r1, 0.001f) // base

        val r2 = BrilliantBehemothEngine.calculateExplosionRadius(config, BrilliantBehemothState(2))
        assertEquals(72f, r2, 0.001f) // base + 24

        val r5 = BrilliantBehemothEngine.calculateExplosionRadius(config, BrilliantBehemothState(5))
        assertEquals(144f, r5, 0.001f) // base + 4 * 24 = 48 + 96 = 144
    }

    @Test
    fun testCalculateBonusDamageNominal() {
        val config = BrilliantBehemothConfig()
        val bonus = BrilliantBehemothEngine.calculateBonusDamage(100f, config)
        assertEquals(60f, bonus, 0.001f)
    }

    @Test
    fun testCalculateBonusDamageNaNInfinity() {
        val config = BrilliantBehemothConfig()

        assertEquals(0f, BrilliantBehemothEngine.calculateBonusDamage(Float.NaN, config), 0.001f)
        assertEquals(0f, BrilliantBehemothEngine.calculateBonusDamage(Float.POSITIVE_INFINITY, config), 0.001f)
        assertEquals(0f, BrilliantBehemothEngine.calculateBonusDamage(Float.NEGATIVE_INFINITY, config), 0.001f)
        assertEquals(0f, BrilliantBehemothEngine.calculateBonusDamage(-50f, config), 0.001f)
        assertEquals(0f, BrilliantBehemothEngine.calculateBonusDamage(0f, config), 0.001f)
    }

    @Test
    fun testCalculateExplosionRadiusNaNInfinityConfig() {
        val badConfig = BrilliantBehemothConfig(baseRadius = Float.NaN)
        val state = BrilliantBehemothState(2)
        assertEquals(0f, BrilliantBehemothEngine.calculateExplosionRadius(badConfig, state), 0.001f)

        val badConfig2 = BrilliantBehemothConfig(baseRadius = Float.POSITIVE_INFINITY)
        assertEquals(0f, BrilliantBehemothEngine.calculateExplosionRadius(badConfig2, state), 0.001f)
    }

    @Test
    fun testProcessExplosionNominal() {
        val x = 10f
        val y = 10f
        val radius = 48f
        val bonusDmg = 60f

        val targetX = floatArrayOf(10f, 10f + 40f, 10f + 50f, Float.NaN)
        val targetY = floatArrayOf(10f, 10f, 10f, 10f)
        val targetHp = floatArrayOf(100f, 100f, 100f, 100f)

        val hits = BrilliantBehemothEngine.processExplosion(
            x, y, radius, bonusDmg, targetX, targetY, targetHp, targetX.size
        )

        assertEquals(2, hits) // Only first two are in range and valid
        assertEquals(40f, targetHp[0], 0.001f) // 100 - 60 = 40
        assertEquals(40f, targetHp[1], 0.001f) // 100 - 60 = 40
        assertEquals(100f, targetHp[2], 0.001f) // Out of range
        assertEquals(100f, targetHp[3], 0.001f) // NaN X skipped
    }

    @Test
    fun testProcessExplosionZeroOrNegativeRadiusBonus() {
        val x = 10f
        val y = 10f

        val targetX = floatArrayOf(10f)
        val targetY = floatArrayOf(10f)
        val targetHp = floatArrayOf(100f)

        val h1 = BrilliantBehemothEngine.processExplosion(x, y, 0f, 60f, targetX, targetY, targetHp, 1)
        assertEquals(0, h1)

        val h2 = BrilliantBehemothEngine.processExplosion(x, y, -10f, 60f, targetX, targetY, targetHp, 1)
        assertEquals(0, h2)

        val h3 = BrilliantBehemothEngine.processExplosion(x, y, 48f, 0f, targetX, targetY, targetHp, 1)
        assertEquals(0, h3)

        val h4 = BrilliantBehemothEngine.processExplosion(x, y, 48f, -60f, targetX, targetY, targetHp, 1)
        assertEquals(0, h4)
    }

    @Test
    fun testProcessExplosionMaxCapacityStress() {
        val targetCount = 1000
        val targetX = FloatArray(targetCount)
        val targetY = FloatArray(targetCount)
        val targetHp = FloatArray(targetCount) { 100f }

        for (i in 0 until targetCount) {
            targetX[i] = i.toFloat()
            targetY[i] = 0f
        }

        val startNano = System.nanoTime()
        val hits = BrilliantBehemothEngine.processExplosion(
            x = 0f, y = 0f, radius = 500f, bonusDamage = 60f,
            targetX, targetY, targetHp, targetCount
        )
        val elapsed = System.nanoTime() - startNano

        // Distance from 0 to 500 includes 0..500 (501 targets)
        assertEquals(501, hits)
        assertTrue("Execution took too long: $elapsed ns", elapsed < 5_000_000) // 5ms max
    }
}
