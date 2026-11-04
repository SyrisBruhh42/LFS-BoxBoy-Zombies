package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class CalcifiedStalkerLaserTest {

    @Test
    fun testSightlineTrajectory() {
        val laser = CalcifiedStalkerLaser(
            sourceId = 1,
            startX = 0f,
            startY = 0f,
            aimAngleRad = 0f, // Pointing straight right
            range = 100f
        )
        assertEquals(100f, laser.endX, 0.001f)
        assertEquals(0f, laser.endY, 0.001f)

        val laser2 = CalcifiedStalkerLaser(
            sourceId = 2,
            startX = 0f,
            startY = 0f,
            aimAngleRad = (PI / 2).toFloat(), // Pointing straight down/up depending on axis, mathematically Y increases
            range = 100f
        )
        assertEquals(0f, laser2.endX, 0.001f)
        assertEquals(100f, laser2.endY, 0.001f)
    }

    @Test
    fun testChargeStateProgression() {
        val laser = CalcifiedStalkerLaser(
            sourceId = 1,
            startX = 0f,
            startY = 0f,
            aimAngleRad = 0f,
            totalChargeMs = 850L
        )
        assertEquals(850L, laser.chargeRemainingMs)
        assertEquals(0f, laser.chargeFraction, 0.001f)

        assertFalse(laser.step(400L))
        assertEquals(450L, laser.chargeRemainingMs)
        assertEquals(400f / 850f, laser.chargeFraction, 0.001f)

        assertTrue(laser.step(450L))
        assertEquals(0L, laser.chargeRemainingMs)
        assertEquals(1f, laser.chargeFraction, 0.001f)
    }

    @Test
    fun testDodgeWindowTiming() {
        val laser = CalcifiedStalkerLaser(
            sourceId = 1,
            startX = 0f,
            startY = 0f,
            aimAngleRad = 0f,
            totalChargeMs = 850L,
            dodgeWindowMs = 200L
        )

        // Initial state, far from firing
        assertFalse(laser.isInDodgeWindow())

        // Step just before dodge window
        laser.step(649L)
        assertEquals(201L, laser.chargeRemainingMs)
        assertFalse(laser.isInDodgeWindow())

        // Step into dodge window
        laser.step(1L)
        assertEquals(200L, laser.chargeRemainingMs)
        assertTrue(laser.isInDodgeWindow())

        // Deep in dodge window
        laser.step(100L)
        assertEquals(100L, laser.chargeRemainingMs)
        assertTrue(laser.isInDodgeWindow())

        // Fired
        laser.step(100L)
        assertEquals(0L, laser.chargeRemainingMs)
        assertFalse(laser.isInDodgeWindow())
    }

    @Test
    fun testTargetIntersectionLogic() {
        val laser = CalcifiedStalkerLaser(
            sourceId = 1,
            startX = 0f,
            startY = 0f,
            aimAngleRad = 0f, // Straight along X axis
            range = 100f
        )

        // Target exactly on the line
        assertTrue(laser.intersectsTarget(50f, 0f, 10f))

        // Target slightly off, but within radius
        assertTrue(laser.intersectsTarget(50f, 9f, 10f))

        // Target slightly off, outside radius
        assertFalse(laser.intersectsTarget(50f, 11f, 10f))

        // Target before start
        assertTrue(laser.intersectsTarget(-5f, 0f, 10f))
        assertFalse(laser.intersectsTarget(-15f, 0f, 10f))

        // Target after end
        assertTrue(laser.intersectsTarget(105f, 0f, 10f))
        assertFalse(laser.intersectsTarget(115f, 0f, 10f))
    }
}
