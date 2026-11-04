package com.example.game.combat.modules

import kotlin.math.abs

/**
 * Track 3: Boxhead 2Play Combat Feel & Physics
 * Crisp AABB minimum translation vector (MTV) collision resolution against walls and barricades.
 */
data class Aabb(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

data class MtvResult(
    val hasCollision: Boolean,
    val mtvX: Float = 0f,
    val mtvY: Float = 0f
)

object AabbBarricadePenetration {

    /**
     * Calculates the Minimum Translation Vector (MTV) to resolve a collision between an entity (AABB)
     * and a static barricade/wall (AABB).
     *
     * Returns an MtvResult containing whether a collision occurred and the vector to push the entity out.
     */
    fun calculateMtv(entity: Aabb, barricade: Aabb): MtvResult {
        // Check for overlap on x axis
        val overlapXLeft = entity.maxX - barricade.minX
        val overlapXRight = barricade.maxX - entity.minX

        if (overlapXLeft <= 0 || overlapXRight <= 0) {
            return MtvResult(false) // No collision on X axis
        }

        // Check for overlap on y axis
        val overlapYTop = entity.maxY - barricade.minY
        val overlapYBottom = barricade.maxY - entity.minY

        if (overlapYTop <= 0 || overlapYBottom <= 0) {
            return MtvResult(false) // No collision on Y axis
        }

        // Collision exists, find the axis of least penetration
        val mtvX = if (overlapXLeft < overlapXRight) -overlapXLeft else overlapXRight
        val mtvY = if (overlapYTop < overlapYBottom) -overlapYTop else overlapYBottom

        // Return only the smallest translation to push the entity out
        return if (abs(mtvX) < abs(mtvY)) {
            MtvResult(true, mtvX = mtvX, mtvY = 0f)
        } else {
            MtvResult(true, mtvX = 0f, mtvY = mtvY)
        }
    }
}
