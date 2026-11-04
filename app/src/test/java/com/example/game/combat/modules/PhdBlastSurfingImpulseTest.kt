package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PhdBlastSurfingImpulseTest {

    @Test
    fun testBlastSurfingImpulse_insideRadius_withPerk_takesZeroDamage() {
        val player = PlayerState(
            hp = 100, maxHp = 100, vx = 0f, vy = 0f, currentAmmo = 10, maxAmmo = 30, isBlastSurfingActive = true
        )

        // Player is at (10, 0), Explosion at (0, 0). Distance = 10. Radius = 20.
        PhdBlastSurfingImpulse.applyBlastSurfingImpulse(
            player = player,
            playerX = 10f, playerY = 0f,
            explosionX = 0f, explosionY = 0f,
            blastRadius = 20f,
            baseImpulse = 100f,
            rawDamage = 50
        )

        assertEquals(100, player.hp) // Zero damage taken
        assertTrue(player.vx > 0f) // Pushed in positive X direction
        assertEquals(0f, player.vy) // No Y movement
        
        // Expected impulse magnitude = 100 * (1 - 10/20) = 50
        assertEquals(50f, player.vx)
    }

    @Test
    fun testBlastSurfingImpulse_insideRadius_withoutPerk_takesDamage() {
        val player = PlayerState(
            hp = 100, maxHp = 100, vx = 0f, vy = 0f, currentAmmo = 10, maxAmmo = 30, isBlastSurfingActive = false
        )

        PhdBlastSurfingImpulse.applyBlastSurfingImpulse(
            player = player,
            playerX = 10f, playerY = 0f,
            explosionX = 0f, explosionY = 0f,
            blastRadius = 20f,
            baseImpulse = 100f,
            rawDamage = 50
        )

        assertEquals(50, player.hp) // Took 50 damage
        assertEquals(50f, player.vx) // Pushed in positive X direction
    }

    @Test
    fun testBlastSurfingImpulse_outsideRadius_noEffect() {
        val player = PlayerState(
            hp = 100, maxHp = 100, vx = 0f, vy = 0f, currentAmmo = 10, maxAmmo = 30, isBlastSurfingActive = true
        )

        PhdBlastSurfingImpulse.applyBlastSurfingImpulse(
            player = player,
            playerX = 30f, playerY = 0f, // Distance 30 > Radius 20
            explosionX = 0f, explosionY = 0f,
            blastRadius = 20f,
            baseImpulse = 100f,
            rawDamage = 50
        )

        assertEquals(100, player.hp)
        assertEquals(0f, player.vx)
        assertEquals(0f, player.vy)
    }

    @Test
    fun testAirDashReload_setsVelocityAndRefillsAmmo() {
        val player = PlayerState(
            hp = 100, maxHp = 100, vx = 10f, vy = -5f, currentAmmo = 5, maxAmmo = 30, isBlastSurfingActive = true
        )

        PhdBlastSurfingImpulse.triggerAirDashReload(
            player = player,
            dashDirX = 0.707f, dashDirY = 0.707f, // roughly 45 degrees
            dashSpeed = 200f
        )

        assertEquals(30, player.currentAmmo) // Ammo refilled
        // velocity overridden by dash speed
        assertTrue(abs(player.vx - (0.707f * 200f)) < 0.1f)
        assertTrue(abs(player.vy - (0.707f * 200f)) < 0.1f)
    }
}
