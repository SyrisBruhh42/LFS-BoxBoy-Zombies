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
    fun testTier2SteelInitialization() {
        val barricade = WindowBarricadeTier2Steel("win_01", 520)

        assertEquals("win_01", barricade.windowId)
        assertEquals(520, barricade.currentHp)
        assertEquals(BarricadeWindowTier.TIER_2_STEEL_SHUTTERS, barricade.tierInfo)
        assertFalse(barricade.isBreached)
    }

    @Test
    fun testDamageApplication() {
        val barricade = WindowBarricadeTier2Steel("win_01", 520)

        barricade.applyDamage(200)
        assertEquals(320, barricade.currentHp)
        assertFalse(barricade.isBreached)

        barricade.applyDamage(400)
        assertEquals(0, barricade.currentHp)
        assertTrue(barricade.isBreached)
    }

    @Test
    fun testRepair() {
        val barricade = WindowBarricadeTier2Steel("win_01", 0, true)

        barricade.repair(200)
        assertEquals(200, barricade.currentHp)
        assertFalse(barricade.isBreached)

        barricade.repair(1000)
        assertEquals(520, barricade.currentHp) // Should be capped at maxHp (520)
    }
}
