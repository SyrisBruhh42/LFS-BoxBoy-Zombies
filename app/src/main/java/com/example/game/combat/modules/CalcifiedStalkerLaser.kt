package com.example.game.combat.modules

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Represents the Calcified Stalker's laser telegraph attack logic, encapsulating
 * sightline trajectory, charge duration, and the critical dodge window timing.
 */
class CalcifiedStalkerLaser(
    val sourceId: Int,
    var startX: Float,
    var startY: Float,
    var aimAngleRad: Float,
    val range: Float = 440f,
    val totalChargeMs: Long = 850L,
    val dodgeWindowMs: Long = 200L // The final X ms before firing where the player can dodge.
) {
    var chargeRemainingMs: Long = totalChargeMs

    val endX: Float
        get() = startX + cos(aimAngleRad) * range

    val endY: Float
        get() = startY + sin(aimAngleRad) * range

    val chargeFraction: Float
        get() = (1f - (chargeRemainingMs.toFloat() / totalChargeMs.coerceAtLeast(1L).toFloat())).coerceIn(0f, 1f)

    /**
     * Steps the laser charge forward by the given deltaMs.
     * Returns true if the charge has completed (time to fire).
     */
    fun step(deltaMs: Long): Boolean {
        chargeRemainingMs -= deltaMs
        if (chargeRemainingMs < 0L) {
            chargeRemainingMs = 0L
        }
        return chargeRemainingMs == 0L
    }

    /**
     * Checks if the laser is within the critical dodge window.
     */
    fun isInDodgeWindow(): Boolean {
        return chargeRemainingMs <= dodgeWindowMs && chargeRemainingMs > 0L
    }

    /**
     * Checks if the target (player or object) intersects with the laser sightline.
     * Uses a simple distance to line segment algorithm.
     */
    fun intersectsTarget(targetX: Float, targetY: Float, targetRadius: Float): Boolean {
        val dx = endX - startX
        val dy = endY - startY
        val lengthSq = dx * dx + dy * dy

        if (lengthSq == 0f) {
            // Start and end are the same
            val dist = hypot(targetX - startX, targetY - startY)
            return dist <= targetRadius
        }

        // Project target onto line segment
        var t = ((targetX - startX) * dx + (targetY - startY) * dy) / lengthSq
        t = t.coerceIn(0f, 1f)

        val closestX = startX + t * dx
        val closestY = startY + t * dy

        val distToClosest = hypot(targetX - closestX, targetY - closestY)
        return distToClosest <= targetRadius
    }
}
