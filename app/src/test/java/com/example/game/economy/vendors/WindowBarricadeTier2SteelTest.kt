package com.example.game.economy.vendors

import com.example.game.economy.BarricadeWindowTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class WindowBarricadeTier2SteelTest {

    @Test
    fun testInitializationAndTierInfo() {
        val barricade = WindowBarricadeTier2Steel("win_01", 520)
        assertEquals("win_01", barricade.windowId)
        assertEquals(520, barricade.currentHp)
        assertEquals(BarricadeWindowTier.TIER_2_STEEL_SHUTTERS, barricade.tierInfo)
        assertFalse(barricade.isBreached)
        assertFalse(barricade.isUpgrading)
    }

    @Test
    fun testNominalDamageApplication() {
        val barricade = WindowBarricadeTier2Steel("win_01", 520)
        barricade.applyDamage(200)
        assertEquals(320, barricade.currentHp)
        assertFalse(barricade.isBreached)
        
        barricade.applyDamage(320)
        assertEquals(0, barricade.currentHp)
        assertTrue(barricade.isBreached)
    }

    @Test
    fun testDamageExcessiveAndNegative() {
        val barricade = WindowBarricadeTier2Steel("win_excess", 520)
        // Negative damage should be ignored (clamped to 0)
        barricade.applyDamage(-50)
        assertEquals(520, barricade.currentHp)

        // Excessive damage should cap at 0
        barricade.applyDamage(1000)
        assertEquals(0, barricade.currentHp)
        assertTrue(barricade.isBreached)
    }

    @Test
    fun testNoDamageWhenBreachedOrUpgrading() {
        val barricade1 = WindowBarricadeTier2Steel("win_b", 0, isBreached = true)
        barricade1.applyDamage(50)
        assertEquals(0, barricade1.currentHp)

        val barricade2 = WindowBarricadeTier2Steel("win_u", 520, isUpgrading = true)
        barricade2.applyDamage(100)
        assertEquals(520, barricade2.currentHp)
    }

    @Test
    fun testNominalRepair() {
        val barricade = WindowBarricadeTier2Steel("win_02", 0, isBreached = true)
        
        barricade.repair(200)
        assertEquals(200, barricade.currentHp)
        assertFalse(barricade.isBreached)
    }

    @Test
    fun testRepairExcessiveAndNegative() {
        val barricade = WindowBarricadeTier2Steel("win_cap", 400)

        // Repair beyond maxHp (520) should cap
        barricade.repair(500)
        assertEquals(520, barricade.currentHp)

        // Negative repair should be ignored
        barricade.repair(-100)
        assertEquals(520, barricade.currentHp)
    }

    @Test
    fun testNoRepairWhenUpgradingOrFull() {
        val barricadeFull = WindowBarricadeTier2Steel("win_full", 520)
        barricadeFull.repair(100)
        assertEquals(520, barricadeFull.currentHp)

        val barricadeUpgrading = WindowBarricadeTier2Steel("win_upg", 100, isUpgrading = true)
        barricadeUpgrading.repair(100)
        assertEquals(100, barricadeUpgrading.currentHp)
    }

    @Test
    fun testStepFunctionLogicAndDeterministicSeed() {
        val barricade = WindowBarricadeTier2Steel("win_step", 100, seed = 12345L)
        val initialSeed = barricade.seed
        barricade.step(0.4f) // Normal dt, sparkTimer=0.4
        assertEquals(initialSeed, barricade.seed)

        barricade.step(0.2f) // sparkTimer = 0.6 (> 0.5f), loops seed
        assertTrue(barricade.seed != initialSeed)

        assertFalse(barricade.isBreached)

        barricade.currentHp = 0
        barricade.step(0.05f)
        assertTrue(barricade.isBreached)

        // Invalid dt (Infinity or NaN) should exit early without changing state
        barricade.currentHp = 100
        barricade.isBreached = true
        barricade.step(Float.NaN)
        assertTrue(barricade.isBreached) // Did not update to false
    }

    @Test
    fun testStepClamping() {
        val barricade = WindowBarricadeTier2Steel("win_step_clamp", 100)
        barricade.step(-5.0f) // should clamp to 0.001
        assertFalse(barricade.isBreached)
        barricade.step(10.0f) // should clamp to 0.100
        assertFalse(barricade.isBreached)
    }

    @Test
    fun testSpatialTopologyValidation() {
        val barricade = WindowBarricadeTier2Steel("win_topo", 520, posX = 100f, posY = 200f)
        assertTrue(barricade.validateTopology(0f, 500f, 0f, 500f))
        assertFalse(barricade.validateTopology(200f, 500f, 0f, 500f)) // X out of bounds

        barricade.posX = Float.NaN
        assertFalse(barricade.validateTopology(0f, 500f, 0f, 500f)) // NaN handling
    }

    @Test
    fun testSerializationRoundtrip() {
        val original = WindowBarricadeTier2Steel("win_net", 350, isBreached = false, isUpgrading = true, posX = 55f, posY = -10f, seed = 987654L)
        val map = original.toMap()

        val reconstructed = WindowBarricadeTier2Steel.fromMap(map)
        assertEquals(original.windowId, reconstructed.windowId)
        assertEquals(original.currentHp, reconstructed.currentHp)
        assertEquals(original.isBreached, reconstructed.isBreached)
        assertEquals(original.isUpgrading, reconstructed.isUpgrading)
        assertEquals(original.posX, reconstructed.posX, 0.001f)
        assertEquals(original.posY, reconstructed.posY, 0.001f)
        assertEquals(original.seed, reconstructed.seed)
    }

    @Test
    fun testSerializationFallbackValues() {
        val emptyMap = emptyMap<String, Any>()
        val reconstructed = WindowBarricadeTier2Steel.fromMap(emptyMap)

        assertEquals("win_default", reconstructed.windowId)
        assertEquals(0, reconstructed.currentHp)
        assertFalse(reconstructed.isBreached)
        assertFalse(reconstructed.isUpgrading)
        assertEquals(0f, reconstructed.posX, 0.001f)
        assertEquals(0f, reconstructed.posY, 0.001f)
        assertEquals(0L, reconstructed.seed)
    }

    @Test
    fun testChecksumDeterminism() {
        val barricade1 = WindowBarricadeTier2Steel("win_hash", 250, isBreached = false, isUpgrading = false, seed = 500L)
        val barricade2 = WindowBarricadeTier2Steel("win_hash", 250, isBreached = false, isUpgrading = false, seed = 500L)

        val checksum1 = barricade1.computeChecksum()
        val checksum2 = barricade2.computeChecksum()

        assertEquals(checksum1, checksum2)

        // Changing any field should alter the checksum
        val barricade3 = WindowBarricadeTier2Steel("win_hash", 251, isBreached = false, isUpgrading = false, seed = 500L)
        assertTrue(checksum1 != barricade3.computeChecksum())

        val barricade4 = WindowBarricadeTier2Steel("win_hash", 250, isBreached = true, isUpgrading = false, seed = 500L)
        assertTrue(checksum1 != barricade4.computeChecksum())

        val barricade5 = WindowBarricadeTier2Steel("win_hash", 250, isBreached = false, isUpgrading = false, seed = 501L)
        assertTrue(checksum1 != barricade5.computeChecksum())
    }

    @Test
    fun testStressLoadMultipleInstances() {
        // Simulating heavy load over many frames
        val barricades = Array(300) { i -> WindowBarricadeTier2Steel("win_$i", 520, seed = i.toLong()) }

        for (frame in 0 until 600) { // 10 seconds at 60fps
            for (i in barricades.indices) {
                if (frame % 2 == 0) {
                    barricades[i].applyDamage(1)
                }
                barricades[i].step(0.016f)
            }
        }
        
        // Each took 300 damage (600 / 2)
        assertEquals(220, barricades[0].currentHp)
        assertEquals(220, barricades[299].currentHp)
        assertFalse(barricades[150].isBreached)
    }
}
