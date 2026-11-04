package com.example.game.combat.modules

import kotlin.math.max
import kotlin.math.min
import kotlin.math.hypot

/**
 * Unshadowed additive laser telegraph projected by Calcified Stalker (Archer) before firing.
 */
data class CalcifiedStalkerLaser(
    val sourceZombieId: Int,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val aimAngleRad: Float,
    var chargeRemainingMs: Long = 850L,
    val totalChargeMs: Long = 850L,
    val colorHex: Long = 0xFFFF2A54
) {
    val chargeFraction: Float
        get() = (1f - (chargeRemainingMs.toFloat() / totalChargeMs.coerceAtLeast(1L).toFloat())).coerceIn(0f, 1f)

    /**
     * The player can dodge the laser safely during the last 200ms of charge time
     * when the Stalker 'locks in' its aim and doesn't rotate anymore.
     */
    fun isWithinDodgeWindow(): Boolean {
        return chargeRemainingMs <= 200L
    }

    /**
     * Line segment to circle collision detection.
     * Computes the closest point on the line segment to the circle's center,
     * then checks if the distance is within the circle's radius.
     */
    fun intersectsTarget(targetX: Float, targetY: Float, targetRadius: Float): Boolean {
        val dx = endX - startX
        val dy = endY - startY
        val lengthSquared = dx * dx + dy * dy

        if (lengthSquared == 0f) {
            // Line segment is a single point
            val d = hypot(targetX - startX, targetY - startY)
            return d <= targetRadius
        }

        // Calculate the t that minimizes the distance
        var t = ((targetX - startX) * dx + (targetY - startY) * dy) / lengthSquared
        t = max(0f, min(1f, t))

        // Find the closest point on the line segment
        val closestX = startX + t * dx
        val closestY = startY + t * dy

        val d = hypot(targetX - closestX, targetY - closestY)
        return d <= targetRadius
    }
}
