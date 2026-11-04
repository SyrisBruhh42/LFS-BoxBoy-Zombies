package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.hypot

class HellSprinterFlankBehaviorTest {
    @Test
    fun testFlankSteeringVectorNormalized() {
        val (vx, vy) = HellSprinterFlankBehavior.calculateFlankSteering(
            selfX = 100f, selfY = 100f,
            targetX = 200f, targetY = 200f,
            flankOffsetAngle = 0.5f,
            flankRadius = 50f
        )
        val mag = hypot(vx, vy)
        assertEquals(1.0f, mag, 0.01f)
    }

    @Test
    fun testSprintBurstCooldown() {
        val cooldown1 = HellSprinterFlankBehavior.stepSprintBurst(1000L, 500L, isSprinting = true)
        assertEquals(1500L, cooldown1)

        val cooldown2 = HellSprinterFlankBehavior.stepSprintBurst(1000L, 500L, isSprinting = false)
        assertEquals(500L, cooldown2)

        val cooldown3 = HellSprinterFlankBehavior.stepSprintBurst(1000L, 1500L, isSprinting = false)
        assertEquals(0L, cooldown3)
    }
}
