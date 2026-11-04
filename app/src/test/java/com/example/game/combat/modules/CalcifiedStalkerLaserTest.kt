package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class CalcifiedStalkerLaserTest {

    @Test
    fun testChargeFraction() {
        val laser = CalcifiedStalkerLaser(
            sourceZombieId = 1,
            startX = 0f,
            startY = 0f,
            endX = 100f,
            endY = 100f,
            aimAngleRad = 0f,
            chargeRemainingMs = 425L,
            totalChargeMs = 850L
        )

        assertEquals(0.5f, laser.chargeFraction, 0.001f)

        laser.chargeRemainingMs = 0L
        assertEquals(1.0f, laser.chargeFraction, 0.001f)

        laser.chargeRemainingMs = 850L
        assertEquals(0.0f, laser.chargeFraction, 0.001f)

        // Test overcharge and undercharge
        laser.chargeRemainingMs = -100L
        assertEquals(1.0f, laser.chargeFraction, 0.001f)

        laser.chargeRemainingMs = 1000L
        assertEquals(0.0f, laser.chargeFraction, 0.001f)
    }

    @Test
    fun testDodgeWindow() {
        val laser = CalcifiedStalkerLaser(
            sourceZombieId = 1,
            startX = 0f,
            startY = 0f,
            endX = 100f,
            endY = 100f,
            aimAngleRad = 0f,
            chargeRemainingMs = 201L,
            totalChargeMs = 850L
        )

        assertFalse("Should not be in dodge window at > 200ms", laser.isWithinDodgeWindow())

        laser.chargeRemainingMs = 200L
        assertTrue("Should be in dodge window at exactly 200ms", laser.isWithinDodgeWindow())

        laser.chargeRemainingMs = 0L
        assertTrue("Should be in dodge window at 0ms", laser.isWithinDodgeWindow())
    }

    @Test
    fun testIntersectsTarget_DirectHit() {
        val laser = CalcifiedStalkerLaser(
            sourceZombieId = 1,
            startX = 0f,
            startY = 0f,
            endX = 100f,
            endY = 0f, // Horizontal line along X-axis
            aimAngleRad = 0f
        )

        // Target right on the line
        assertTrue(laser.intersectsTarget(50f, 0f, 10f))
        
        // Target slightly off, but within radius
        assertTrue(laser.intersectsTarget(50f, 5f, 10f))
        
        // Target just barely touching
        assertTrue(laser.intersectsTarget(50f, 10f, 10f))
    }

    @Test
    fun testIntersectsTarget_Miss() {
        val laser = CalcifiedStalkerLaser(
            sourceZombieId = 1,
            startX = 0f,
            startY = 0f,
            endX = 100f,
            endY = 0f,
            aimAngleRad = 0f
        )

        // Target completely outside
        assertFalse(laser.intersectsTarget(50f, 20f, 10f))
    }

    @Test
    fun testIntersectsTarget_PastEndpoints() {
        val laser = CalcifiedStalkerLaser(
            sourceZombieId = 1,
            startX = 0f,
            startY = 0f,
            endX = 100f,
            endY = 0f,
            aimAngleRad = 0f
        )

        // Target before startX, but within radius of start point
        assertTrue(laser.intersectsTarget(-5f, 0f, 10f))
        
        // Target before startX, outside radius of start point
        assertFalse(laser.intersectsTarget(-15f, 0f, 10f))

        // Target after endX, but within radius of end point
        assertTrue(laser.intersectsTarget(105f, 0f, 10f))
        
        // Target after endX, outside radius of end point
        assertFalse(laser.intersectsTarget(115f, 0f, 10f))
    }

    @Test
    fun testIntersectsTarget_ZeroLengthSegment() {
        val laser = CalcifiedStalkerLaser(
            sourceZombieId = 1,
            startX = 50f,
            startY = 50f,
            endX = 50f,
            endY = 50f,
            aimAngleRad = 0f
        )

        // Target directly on top
        assertTrue(laser.intersectsTarget(50f, 50f, 10f))
        
        // Target within radius
        assertTrue(laser.intersectsTarget(55f, 50f, 10f))
        
        // Target outside radius
        assertFalse(laser.intersectsTarget(65f, 50f, 10f))
    }
}
