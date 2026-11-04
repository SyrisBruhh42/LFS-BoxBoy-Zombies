package com.example.game.combat.modules

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.abs

class HellSprinterFlankBehaviorTest {

    private lateinit var behavior: HellSprinterFlankBehavior

    @Before
    fun setUp() {
        behavior = HellSprinterFlankBehavior()
    }

    @Test
    fun `test sprint activation and cooldown`() {
        assertFalse(behavior.isSprinting)

        // Advance time to trigger sprint
        behavior.updateAndCalculateFlankVector(0f, 0f, 100f, 100f, 0L)
        assertTrue(behavior.isSprinting)
        assertEquals(HellSprinterFlankBehavior.SPRINT_BURST_DURATION_MS, behavior.sprintRemainingMs)
        assertEquals(HellSprinterFlankBehavior.SPRINT_COOLDOWN_MS, behavior.sprintCooldownMs)

        // Advance by half sprint duration
        behavior.updateAndCalculateFlankVector(0f, 0f, 100f, 100f, 750L)
        assertTrue(behavior.isSprinting)
        assertEquals(750L, behavior.sprintRemainingMs)

        // Advance past sprint duration
        behavior.updateAndCalculateFlankVector(0f, 0f, 100f, 100f, 750L)
        assertFalse(behavior.isSprinting)
        assertEquals(0L, behavior.sprintRemainingMs)
        assertEquals(HellSprinterFlankBehavior.SPRINT_COOLDOWN_MS - 750L, behavior.sprintCooldownMs)

        // Advance past cooldown
        behavior.updateAndCalculateFlankVector(0f, 0f, 100f, 100f, HellSprinterFlankBehavior.SPRINT_COOLDOWN_MS)
        assertTrue(behavior.isSprinting) // Sprint triggers again
    }

    @Test
    fun `test flank vector calculation`() {
        val playerX = 100f
        val playerY = 0f
        val runnerX = 0f
        val runnerY = 0f

        val (vx, vy) = behavior.updateAndCalculateFlankVector(runnerX, runnerY, playerX, playerY, 0L, 1.0f)

        // Calculate magnitude
        val mag = hypot(vx, vy)
        assertTrue("Magnitude should be approximately 1.0", abs(mag - 1.0f) < 0.01f)
    }
}
