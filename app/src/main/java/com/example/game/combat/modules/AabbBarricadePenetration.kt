package com.example.game.combat.modules

import kotlin.math.abs

data class Vector2(val x: Float, val y: Float)

data class Aabb(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = minX + width / 2f
    val centerY: Float get() = minY + height / 2f
}

object AabbBarricadePenetration {
    /**
     * Calculates the Minimum Translation Vector (MTV) to resolve a collision
     * between an entity and a barricade/wall.
     *
     * @param entity The Aabb of the moving entity.
     * @param barricade The Aabb of the static barricade.
     * @return A Vector2 representing the translation required to push the entity out of the barricade.
     *         Returns Vector2(0f, 0f) if there is no penetration/collision.
     */
    fun resolvePenetration(entity: Aabb, barricade: Aabb): Vector2 {
        // Calculate penetration depths along each axis
        val overlapXLeft = entity.maxX - barricade.minX
        val overlapXRight = barricade.maxX - entity.minX
        val overlapYTop = entity.maxY - barricade.minY
        val overlapYBottom = barricade.maxY - entity.minY

        // If any overlap is <= 0, there is no collision
        if (overlapXLeft <= 0 || overlapXRight <= 0 || overlapYTop <= 0 || overlapYBottom <= 0) {
            return Vector2(0f, 0f)
        }

        // Find the minimum overlap
        val minOverlapX = if (overlapXLeft < overlapXRight) overlapXLeft else overlapXRight
        val minOverlapY = if (overlapYTop < overlapYBottom) overlapYTop else overlapYBottom

        // Resolve along the axis with the smallest penetration
        return if (minOverlapX < minOverlapY) {
            if (overlapXLeft < overlapXRight) {
                Vector2(-overlapXLeft, 0f) // Push left
            } else {
                Vector2(overlapXRight, 0f) // Push right
            }
        } else {
            if (overlapYTop < overlapYBottom) {
                Vector2(0f, -overlapYTop) // Push up (assuming origin top-left or bottom-left depending on coordinate system, standard 2D often pushes negative Y for 'up' visually or positive, let's stick to standard math)
            } else {
                Vector2(0f, overlapYBottom) // Push down
            }
        }
    }
}
