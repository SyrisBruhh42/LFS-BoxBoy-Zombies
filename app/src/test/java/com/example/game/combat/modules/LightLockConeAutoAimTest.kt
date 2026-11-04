package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class LightLockConeAutoAimTest {

    @Test
    fun testNoTargetsReturnsEmptyResult() {
        val result = LightLockConeAutoAim.acquireTarget(
            playerX = 0f, playerY = 0f,
            aimAngleDegrees = 0f,
            targets = emptyList()
        )
        assertNull(result.targetId)
        assertFalse(result.isCritMode)
        assertEquals(0f, result.distance, 0.001f)
    }

    @Test
    fun testTargetOutOfRangeFilteredOut() {
        val target = AutoAimTarget("t1", 400f, 0f, false)
        val result = LightLockConeAutoAim.acquireTarget(
            playerX = 0f, playerY = 0f,
            aimAngleDegrees = 0f,
            maxRangePx = 300f,
            targets = listOf(target)
        )
        assertNull(result.targetId)
    }

    @Test
    fun testTargetOutOfFovFilteredOut() {
        // Target is at (0, 100), angle is 90 degrees
        val target = AutoAimTarget("t1", 0f, 100f, false)
        val result = LightLockConeAutoAim.acquireTarget(
            playerX = 0f, playerY = 0f,
            aimAngleDegrees = 0f, // Aiming right
            coneFovDegrees = 90f, // Covers -45 to +45
            targets = listOf(target)
        )
        assertNull(result.targetId)
    }

    @Test
    fun testClosestToCenterTargetSelected() {
        // Aiming at 0 degrees (right).
        // Target 1: at (100, 10) -> small angle, distance ~100
        // Target 2: at (50, 50) -> 45 degrees, distance ~70
        // Expected: Target 1 should be selected despite being further away because it's closer to the center of FOV.
        val target1 = AutoAimTarget("t1", 100f, 10f, false)
        val target2 = AutoAimTarget("t2", 50f, 50f, false)

        val result = LightLockConeAutoAim.acquireTarget(
            playerX = 0f, playerY = 0f,
            aimAngleDegrees = 0f,
            coneFovDegrees = 120f,
            targets = listOf(target1, target2)
        )

        assertEquals("t1", result.targetId)
    }

    @Test
    fun testCritModeActivatedWhenWithinToleranceAndWeakPointExposed() {
        // Aiming at 0 degrees. Target at (100, 0) is exactly at 0 degrees deviation.
        val target = AutoAimTarget("t1", 100f, 0f, true)
        val result = LightLockConeAutoAim.acquireTarget(
            playerX = 0f, playerY = 0f,
            aimAngleDegrees = 0f,
            critToleranceDegrees = 10f,
            targets = listOf(target)
        )

        assertEquals("t1", result.targetId)
        assertTrue(result.isCritMode)
        assertEquals(0f, result.angleDeviationDegrees, 0.001f)
    }

    @Test
    fun testCritModeFailsIfOutsideTolerance() {
        // Aiming at 0 degrees. Target at (100, 20) -> angle ~ 11.3 degrees
        val target = AutoAimTarget("t1", 100f, 20f, true)
        val result = LightLockConeAutoAim.acquireTarget(
            playerX = 0f, playerY = 0f,
            aimAngleDegrees = 0f,
            critToleranceDegrees = 10f, // 11.3 > 10, so crit fails
            targets = listOf(target)
        )

        assertEquals("t1", result.targetId)
        assertFalse(result.isCritMode)
    }

    @Test
    fun testCritModeFailsIfWeakPointNotExposed() {
        // Target exactly at center, but weak point is false.
        val target = AutoAimTarget("t1", 100f, 0f, false)
        val result = LightLockConeAutoAim.acquireTarget(
            playerX = 0f, playerY = 0f,
            aimAngleDegrees = 0f,
            critToleranceDegrees = 10f,
            targets = listOf(target)
        )

        assertEquals("t1", result.targetId)
        assertFalse(result.isCritMode)
    }

    @Test
    fun testAngleWrapAroundHandling() {
        // Player aims at -170 degrees (or 190 degrees). Target is at +170 degrees.
        // Difference is 20 degrees.
        val aimAngle = -170f
        // target at +170 degrees
        val targetX = 100f * kotlin.math.cos(170f * kotlin.math.PI / 180.0).toFloat()
        val targetY = 100f * kotlin.math.sin(170f * kotlin.math.PI / 180.0).toFloat()
        val target = AutoAimTarget("t1", targetX, targetY, true)

        val result = LightLockConeAutoAim.acquireTarget(
            playerX = 0f, playerY = 0f,
            aimAngleDegrees = aimAngle,
            coneFovDegrees = 60f,
            critToleranceDegrees = 30f,
            targets = listOf(target)
        )

        assertEquals("t1", result.targetId)
        // Angular difference between -170 and +170 is 20 degrees.
        assertEquals(20f, result.angleDeviationDegrees, 0.001f)
        assertTrue(result.isCritMode)
    }
}
