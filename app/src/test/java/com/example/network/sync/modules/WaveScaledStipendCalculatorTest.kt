package com.example.network.sync.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class WaveScaledStipendCalculatorTest {

    @Test
    fun testWave1StipendNominal() {
        val config = WaveScaledStipendConfig(
            joinWaveNumber = 1,
            seed = 12345L,
            activeZombiesCount = 50,
            coopPeerCount = 1,
            elonaRoomsUnlocked = 5,
            dtSeconds = 0.016f
        )
        val result = WaveScaledStipendCalculator.computeStipend(config)
        assertEquals(500, result.startingCurrency) // 500 + 0 * 650
        assertEquals(0, result.catchUpRelicsCount) // 1 / 5
        assertFalse(result.isGracefulDegradationActive)
    }

    @Test
    fun testWave5StipendNominal() {
        val config = WaveScaledStipendConfig(
            joinWaveNumber = 5,
            seed = 12345L,
            activeZombiesCount = 50,
            coopPeerCount = 2,
            elonaRoomsUnlocked = 10,
            dtSeconds = 0.016f
        )
        val result = WaveScaledStipendCalculator.computeStipend(config)
        assertEquals(3100, result.startingCurrency) // 500 + 4 * 650
        assertEquals(1, result.catchUpRelicsCount) // 5 / 5
        assertFalse(result.isGracefulDegradationActive)
    }

    @Test
    fun testWave10StipendNominal() {
        val config = WaveScaledStipendConfig(
            joinWaveNumber = 10,
            seed = 12345L,
            activeZombiesCount = 120,
            coopPeerCount = 3,
            elonaRoomsUnlocked = 25,
            dtSeconds = 0.016f
        )
        val result = WaveScaledStipendCalculator.computeStipend(config)
        assertEquals(6350, result.startingCurrency) // 500 + 9 * 650
        assertEquals(2, result.catchUpRelicsCount) // 10 / 5
        assertFalse(result.isGracefulDegradationActive)
    }

    @Test
    fun testNegativeWaveClampsToOne() {
        val config = WaveScaledStipendConfig(
            joinWaveNumber = -5,
            seed = 555L,
            activeZombiesCount = 10,
            coopPeerCount = 0,
            elonaRoomsUnlocked = 1,
            dtSeconds = 0.016f
        )
        val result = WaveScaledStipendCalculator.computeStipend(config)
        assertEquals(500, result.startingCurrency)
        assertEquals(0, result.catchUpRelicsCount)
    }

    @Test
    fun testMaxWaveClampsTo999() {
        val config = WaveScaledStipendConfig(
            joinWaveNumber = 1500,
            seed = 999L,
            activeZombiesCount = 10,
            coopPeerCount = 0,
            elonaRoomsUnlocked = 1,
            dtSeconds = 0.016f
        )
        val result = WaveScaledStipendCalculator.computeStipend(config)
        assertEquals(500 + 998 * 650, result.startingCurrency)
        assertEquals(999 / 5, result.catchUpRelicsCount)
    }

    @Test
    fun testExtremeLoadZombieThreshold() {
        val config = WaveScaledStipendConfig(
            joinWaveNumber = 15,
            seed = 777L,
            activeZombiesCount = 255, // >= 250
            coopPeerCount = 4,
            elonaRoomsUnlocked = 50,
            dtSeconds = 0.016f
        )
        val result = WaveScaledStipendCalculator.computeStipend(config)
        assertTrue(result.isGracefulDegradationActive)
    }

    @Test
    fun testExtremeLoadRoomsThreshold() {
        val config = WaveScaledStipendConfig(
            joinWaveNumber = 20,
            seed = 888L,
            activeZombiesCount = 100,
            coopPeerCount = 4,
            elonaRoomsUnlocked = 105, // >= 100
            dtSeconds = 0.016f
        )
        val result = WaveScaledStipendCalculator.computeStipend(config)
        assertTrue(result.isGracefulDegradationActive)
    }

    @Test
    fun testDtSecondsNanAndInfinityRecovery() {
        val configNan = WaveScaledStipendConfig(
            joinWaveNumber = 5,
            seed = 111L,
            activeZombiesCount = 10,
            coopPeerCount = 1,
            elonaRoomsUnlocked = 2,
            dtSeconds = Float.NaN
        )
        val resultNan = WaveScaledStipendCalculator.computeStipend(configNan)
        
        val configInf = configNan.copy(dtSeconds = Float.POSITIVE_INFINITY)
        val resultInf = WaveScaledStipendCalculator.computeStipend(configInf)
        
        assertEquals(resultNan.startingCurrency, resultInf.startingCurrency)
        assertEquals(resultNan.catchUpRelicsCount, resultInf.catchUpRelicsCount)
        assertEquals(resultNan.checksum, resultInf.checksum) // Should map to 0.016f internally
    }

    @Test
    fun testConfigSerializationRoundtrip() {
        val config = WaveScaledStipendConfig(
            joinWaveNumber = 7,
            seed = 123456789L,
            activeZombiesCount = 150,
            coopPeerCount = 3,
            elonaRoomsUnlocked = 20,
            dtSeconds = 0.032f
        )
        val map = config.toMap()
        val configRestored = WaveScaledStipendConfig.fromMap(map)
        
        assertEquals(config.joinWaveNumber, configRestored.joinWaveNumber)
        assertEquals(config.seed, configRestored.seed)
        assertEquals(config.activeZombiesCount, configRestored.activeZombiesCount)
        assertEquals(config.coopPeerCount, configRestored.coopPeerCount)
        assertEquals(config.elonaRoomsUnlocked, configRestored.elonaRoomsUnlocked)
        assertEquals(config.dtSeconds, configRestored.dtSeconds)
    }

    @Test
    fun testResultSerializationRoundtrip() {
        val result = WaveScaledStipendResult(
            startingCurrency = 12000,
            catchUpRelicsCount = 4,
            isGracefulDegradationActive = true,
            checksum = 9876543210L
        )
        val map = result.toMap()
        val resultRestored = WaveScaledStipendResult.fromMap(map)
        
        assertEquals(result.startingCurrency, resultRestored.startingCurrency)
        assertEquals(result.catchUpRelicsCount, resultRestored.catchUpRelicsCount)
        assertEquals(result.isGracefulDegradationActive, resultRestored.isGracefulDegradationActive)
        assertEquals(result.checksum, resultRestored.checksum)
    }

    @Test
    fun testDeterministicChecksumSameInputs() {
        val config1 = WaveScaledStipendConfig(12, 54321L, 60, 2, 8, 0.016f)
        val config2 = WaveScaledStipendConfig(12, 54321L, 60, 2, 8, 0.016f)
        
        val result1 = WaveScaledStipendCalculator.computeStipend(config1)
        val result2 = WaveScaledStipendCalculator.computeStipend(config2)
        
        assertEquals(result1.startingCurrency, result2.startingCurrency)
        assertEquals(result1.catchUpRelicsCount, result2.catchUpRelicsCount)
        assertEquals(result1.checksum, result2.checksum)
        assertTrue(result1.checksum != 0L)
    }

    @Test
    fun testDeterministicChecksumDifferentInputs() {
        val config1 = WaveScaledStipendConfig(12, 54321L, 60, 2, 8, 0.016f)
        val config2 = WaveScaledStipendConfig(13, 54321L, 60, 2, 8, 0.016f) // Different wave
        
        val result1 = WaveScaledStipendCalculator.computeStipend(config1)
        val result2 = WaveScaledStipendCalculator.computeStipend(config2)
        
        assertNotEquals(result1.startingCurrency, result2.startingCurrency)
        assertNotEquals(result1.checksum, result2.checksum)
    }
}
