package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class AbyssalWeaverBlinkMageTest {

    @Test
    fun testBlinkStateChangesAwayFromPlayer() {
        val mage = AbyssalWeaverBlinkMage(x = 100f, y = 100f)
        val playerX = 150f
        val playerY = 100f // Distance 50 < 400 (preferred engagement range)

        mage.updateBlink(playerX, playerY, 0)

        // Mage should blink away. Direction from player to mage is (-50, 0) -> normalized (-1, 0)
        // Expected new pos = 100 + (-1 * 300) = -200
        assertEquals(-200f, mage.x, 0.1f)
        assertEquals(100f, mage.y, 0.1f)
    }

    @Test
    fun testBlinkStateChangesTowardsPlayer() {
        val mage = AbyssalWeaverBlinkMage(x = -400f, y = 100f)
        val playerX = 100f
        val playerY = 100f // Distance 500 > 400

        mage.updateBlink(playerX, playerY, 0)

        // Mage should blink towards. Direction from mage to player is (500, 0) -> normalized (1, 0)
        // Expected new pos = -400 + (1 * 300) = -100
        assertEquals(-100f, mage.x, 0.1f)
        assertEquals(100f, mage.y, 0.1f)
    }

    @Test
    fun testBlinkCooldownConstraints() {
        val mage = AbyssalWeaverBlinkMage(x = 0f, y = 0f)

        // Initial blink
        mage.updateBlink(100f, 0f, 0L)
        val xAfterFirstBlink = mage.x

        // Attempt to blink before cooldown expires
        mage.updateBlink(100f, 0f, 1000L)
        assertEquals(xAfterFirstBlink, mage.x, 0.1f)

        // Cooldown expires
        mage.updateBlink(100f, 0f, 4000L) // Total 5000L
        assertTrue(mage.x != xAfterFirstBlink)
    }

    @Test
    fun testFireSlowOrbProperties() {
        val mage = AbyssalWeaverBlinkMage(x = 0f, y = 0f)

        val orb = mage.fireSlowOrb(100f, 0f)
        assertNotNull(orb)
        assertEquals(0f, orb!!.x, 0.1f)
        assertEquals(0f, orb.y, 0.1f)
        assertEquals(150f, orb.vx, 0.1f)
        assertEquals(0f, orb.vy, 0.1f)
        assertEquals(24f, orb.radiusPx, 0.1f)
        assertEquals(0.5f, orb.slowFactor, 0.1f)
        assertEquals(3000L, orb.durationMs)

        // Cooldown constraint
        val orb2 = mage.fireSlowOrb(100f, 0f)
        assertNull(orb2)

        mage.updateBlink(100f, 0f, 3000L) // Wait for orb cooldown
        val orb3 = mage.fireSlowOrb(100f, 0f)
        assertNotNull(orb3)
    }
}
