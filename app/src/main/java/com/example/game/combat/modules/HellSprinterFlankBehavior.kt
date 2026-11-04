package com.example.game.combat.modules

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Fast Hell Runner lateral flanking vector and sprint burst cooldown module.
 */
object HellSprinterFlankBehavior {
    fun calculateFlankSteering(
        selfX: Float,
        selfY: Float,
        targetX: Float,
        targetY: Float,
        flankOffsetAngle: Float,
        flankRadius: Float
    ): Pair<Float, Float> {
        val baseAngle = kotlin.math.atan2(selfY - targetY, selfX - targetX)
        val flankAngle = baseAngle + flankOffsetAngle

        val flankTargetX = targetX + cos(flankAngle) * flankRadius
        val flankTargetY = targetY + sin(flankAngle) * flankRadius

        val dx = flankTargetX - selfX
        val dy = flankTargetY - selfY
        val dist = hypot(dx, dy).coerceAtLeast(0.001f)

        return Pair(dx / dist, dy / dist)
    }

    fun stepSprintBurst(
        currentCooldownMs: Long,
        deltaMs: Long,
        isSprinting: Boolean
    ): Long {
        if (isSprinting) {
            return (currentCooldownMs + deltaMs).coerceAtMost(3000L) // Example Max Cooldown
        }
        return (currentCooldownMs - deltaMs).coerceAtLeast(0L)
    }
}
