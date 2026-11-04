package com.example.game.economy.vendors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class WaveClearCurrencyBonusTest {

    @Test
    fun testNominalWaveClearBonus() {
        val config = WaveClearBonusConfig(
            waveNumber = 5,
            totalBarricades = 10,
            intactBarricades = 5,
            expectedClearTimeSeconds = 120f,
            actualClearTimeSeconds = 120f,
            coopPeerCount = 0,
            noDownsThisWave = false
        )

        val result = WaveClearCurrencyBonus.calculateBonus(config)

        // base = 100 + 4 * 10 = 140
        assertEquals(140, result.baseSurvivalBonus)
        // barricades = 5 * 25 = 125
        assertEquals(125, result.barricadeBonus)
        // speed clear = 0
        assertEquals(0, result.speedClearBonus)
        // no downs = 0
        assertEquals(0, result.noDownsBonus)
        // coop = 1.0
        assertEquals(1.0f, result.coopMultiplier, 0.001f)

        assertEquals(265, result.totalBonusCurrency)
    }

    @Test
    fun testPerfectBarricadeBonus() {
        val config = WaveClearBonusConfig(
            waveNumber = 1,
            totalBarricades = 10,
            intactBarricades = 10,
            expectedClearTimeSeconds = 60f,
            actualClearTimeSeconds = 60f,
            coopPeerCount = 0,
            noDownsThisWave = false
        )
        val result = WaveClearCurrencyBonus.calculateBonus(config)

        // 10 * 25 = 250, perfect multiplier = *2.0 -> 500
        assertEquals(500, result.barricadeBonus)
        assertEquals(600, result.totalBonusCurrency) // 100 base + 500 barricade
    }

    @Test
    fun testMaxBaseBonusCap() {
        val config = WaveClearBonusConfig(
            waveNumber = 500,
            totalBarricades = 0,
            intactBarricades = 0,
            expectedClearTimeSeconds = 10f,
            actualClearTimeSeconds = 10f,
            coopPeerCount = 0,
            noDownsThisWave = false
        )
        val result = WaveClearCurrencyBonus.calculateBonus(config)

        assertEquals(2500, result.baseSurvivalBonus)
    }

    @Test
    fun testMaxBarricadeBonusCap() {
        val config = WaveClearBonusConfig(
            waveNumber = 1,
            totalBarricades = 100,
            intactBarricades = 100,
            expectedClearTimeSeconds = 60f,
            actualClearTimeSeconds = 60f,
            coopPeerCount = 0,
            noDownsThisWave = false
        )
        val result = WaveClearCurrencyBonus.calculateBonus(config)

        // 100 * 25 * 2 = 5000, cap is 1000
        assertEquals(1000, result.barricadeBonus)
    }

    @Test
    fun testSpeedClearBonus() {
        val config = WaveClearBonusConfig(
            waveNumber = 1,
            totalBarricades = 0,
            intactBarricades = 0,
            expectedClearTimeSeconds = 100f,
            actualClearTimeSeconds = 50f, // 50% faster
            coopPeerCount = 0,
            noDownsThisWave = false
        )
        val result = WaveClearCurrencyBonus.calculateBonus(config)

        // max speed bonus is 1500, 50% is 750
        assertEquals(750, result.speedClearBonus)
        assertEquals(850, result.totalBonusCurrency) // 100 base + 750 speed
    }

    @Test
    fun testNoDownsAndCoopMultiplier() {
        val config = WaveClearBonusConfig(
            waveNumber = 1,
            totalBarricades = 0,
            intactBarricades = 0,
            expectedClearTimeSeconds = 60f,
            actualClearTimeSeconds = 60f,
            coopPeerCount = 3, // +45% (3 * 0.15)
            noDownsThisWave = true // +250
        )
        val result = WaveClearCurrencyBonus.calculateBonus(config)

        assertEquals(250, result.noDownsBonus)
        assertEquals(1.45f, result.coopMultiplier, 0.001f)

        // total before mult: 100 + 250 = 350
        // total: 350 * 1.45 = 507.5 -> 508
        assertEquals(508, result.totalBonusCurrency)
    }

    @Test
    fun testCoopMaxPeersCap() {
        val config = WaveClearBonusConfig(
            waveNumber = 1,
            totalBarricades = 0,
            intactBarricades = 0,
            expectedClearTimeSeconds = 60f,
            actualClearTimeSeconds = 60f,
            coopPeerCount = 10, // should cap at 3
            noDownsThisWave = false
        )
        val result = WaveClearCurrencyBonus.calculateBonus(config)

        assertEquals(1.45f, result.coopMultiplier, 0.001f)
    }

    @Test
    fun testNegativeOrExtremeValuesClamping() {
        val config = WaveClearBonusConfig(
            waveNumber = -10, // clamps to 1
            totalBarricades = -5, // clamps to 0
            intactBarricades = -2, // clamps to 0
            expectedClearTimeSeconds = -50f, // uses 60f fallback
            actualClearTimeSeconds = -20f, // uses expected fallback
            coopPeerCount = -5, // clamps to 0
            noDownsThisWave = false
        )
        val result = WaveClearCurrencyBonus.calculateBonus(config)

        assertEquals(100, result.baseSurvivalBonus)
        assertEquals(0, result.barricadeBonus)
        assertEquals(0, result.speedClearBonus)
        assertEquals(0, result.noDownsBonus)
        assertEquals(1.0f, result.coopMultiplier, 0.001f)
        assertEquals(100, result.totalBonusCurrency)
    }

    @Test
    fun testNaNRecovery() {
        val config = WaveClearBonusConfig(
            waveNumber = 1,
            totalBarricades = 0,
            intactBarricades = 0,
            expectedClearTimeSeconds = Float.NaN,
            actualClearTimeSeconds = Float.NaN,
            coopPeerCount = 0,
            noDownsThisWave = false
        )
        val result = WaveClearCurrencyBonus.calculateBonus(config)

        assertEquals(100, result.totalBonusCurrency)
    }

    @Test
    fun testInfinityRecovery() {
        val config = WaveClearBonusConfig(
            waveNumber = 1,
            totalBarricades = 0,
            intactBarricades = 0,
            expectedClearTimeSeconds = Float.POSITIVE_INFINITY,
            actualClearTimeSeconds = Float.POSITIVE_INFINITY,
            coopPeerCount = 0,
            noDownsThisWave = false
        )
        val result = WaveClearCurrencyBonus.calculateBonus(config)

        assertEquals(100, result.totalBonusCurrency)
    }

    @Test
    fun testSerializationRoundTrip() {
        val config = WaveClearBonusConfig(
            waveNumber = 15,
            totalBarricades = 20,
            intactBarricades = 18,
            expectedClearTimeSeconds = 300f,
            actualClearTimeSeconds = 150f,
            coopPeerCount = 2,
            noDownsThisWave = true
        )
        val map = config.toMap()
        val restoredConfig = WaveClearBonusConfig.fromMap(map)

        assertEquals(config, restoredConfig)

        val result = WaveClearCurrencyBonus.calculateBonus(config)
        val resultMap = result.toMap()
        val restoredResult = WaveClearBonusResult.fromMap(resultMap)

        assertEquals(result, restoredResult)
    }

    @Test
    fun testDeterminismAndChecksum() {
        val config = WaveClearBonusConfig(
            waveNumber = 10,
            totalBarricades = 15,
            intactBarricades = 10,
            expectedClearTimeSeconds = 180f,
            actualClearTimeSeconds = 160f,
            coopPeerCount = 1,
            noDownsThisWave = false
        )

        val result1 = WaveClearCurrencyBonus.calculateBonus(config)
        val result2 = WaveClearCurrencyBonus.calculateBonus(config)

        assertEquals(result1, result2)
        assertEquals(result1.checksum, result2.checksum)

        val modifiedConfig = config.copy(waveNumber = 11)
        val result3 = WaveClearCurrencyBonus.calculateBonus(modifiedConfig)

        assertNotEquals(result1.checksum, result3.checksum)
    }
}
