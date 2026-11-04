package com.example.game.combat.modules

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.game.ZombieEntity
import kotlin.math.hypot

object PierceRaycasterEngine {

    /**
     * Data class to represent the result of a single raycast calculation.
     */
    data class RaycastResult(
        val hitZombies: List<ZombieEntity>,
        val hitWall: Boolean,
        val adjustedEndX: Float,
        val adjustedEndY: Float
    )

    /**
     * Casts a ray, checks for wall occlusion, and determines which zombies are hit along the line.
     * Wall occlusion truncates the ray, and zombies are filtered based on distance to the ray
     * and sorted by proximity to the origin. Pierce count limits the number of zombies returned.
     */
    fun castPenetratingRay(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        walls: List<Rect>,
        zombies: List<ZombieEntity>,
        pierceCount: Int
    ): RaycastResult {
        var currentEndX = endX
        var currentEndY = endY
        var hitWall = false

        // 1. Check for wall intersections to truncate the ray
        // This is a simplified AABB line intersection check
        for (wall in walls) {
            val intersection = segmentIntersectRect(startX, startY, currentEndX, currentEndY, wall)
            if (intersection != null) {
                hitWall = true
                currentEndX = intersection.first
                currentEndY = intersection.second
            }
        }

        // 2. Find and filter zombies along the (possibly truncated) ray
        val hitCandidates = zombies
            .filter { z -> pointToSegmentDistance(z.x, z.y, startX, startY, currentEndX, currentEndY) <= z.archetype.radius + 8f }
            .filter { z -> 
                // Ensure the zombie is roughly between the start and end point (not strictly behind)
                val rayLen = hypot(currentEndX - startX, currentEndY - startY)
                val distToStart = hypot(z.x - startX, z.y - startY)
                val distToEnd = hypot(z.x - currentEndX, z.y - currentEndY)
                // We use distance sum check: if the point is exactly on the line segment,
                // distToStart + distToEnd == rayLen. We add some tolerance.
                val maxAllowedDist = rayLen + (z.archetype.radius + 8f) * 2 // generous bounding
                distToStart <= maxAllowedDist && distToEnd <= maxAllowedDist &&
                // More precise check using dot product to ensure it's not behind the ray
                ((z.x - startX) * (currentEndX - startX) + (z.y - startY) * (currentEndY - startY)) >= 0
            }
            .sortedBy { hypot(it.x - startX, it.y - startY) }

        // 3. Limit by pierce count
        val finalHitZombies = hitCandidates.take(pierceCount)

        return RaycastResult(
            hitZombies = finalHitZombies,
            hitWall = hitWall,
            adjustedEndX = currentEndX,
            adjustedEndY = currentEndY
        )
    }

    private fun pointToSegmentDistance(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot(px - projX, py - projY)
    }

    /**
     * Finds the closest intersection point of a line segment with a rectangle.
     * Returns null if they do not intersect.
     */
    private fun segmentIntersectRect(x1: Float, y1: Float, x2: Float, y2: Float, rect: Rect): Pair<Float, Float>? {
        // Simplified check: if start is inside, intersection is at start
        if (rect.contains(Offset(x1, y1))) {
            return Pair(x1, y1)
        }

        val intersections = mutableListOf<Pair<Float, Float>>()

        // Top edge
        intersectLines(x1, y1, x2, y2, rect.left, rect.top, rect.right, rect.top)?.let { intersections.add(it) }
        // Bottom edge
        intersectLines(x1, y1, x2, y2, rect.left, rect.bottom, rect.right, rect.bottom)?.let { intersections.add(it) }
        // Left edge
        intersectLines(x1, y1, x2, y2, rect.left, rect.top, rect.left, rect.bottom)?.let { intersections.add(it) }
        // Right edge
        intersectLines(x1, y1, x2, y2, rect.right, rect.top, rect.right, rect.bottom)?.let { intersections.add(it) }

        if (intersections.isEmpty()) return null

        return intersections.minByOrNull { hypot(it.first - x1, it.second - y1) }
    }

    /**
     * Finds the intersection of two line segments.
     */
    private fun intersectLines(
        x1: Float, y1: Float, x2: Float, y2: Float,
        x3: Float, y3: Float, x4: Float, y4: Float
    ): Pair<Float, Float>? {
        val denom = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1)
        if (denom == 0f) return null // Parallel

        val ua = ((x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3)) / denom
        val ub = ((x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3)) / denom

        if (ua in 0f..1f && ub in 0f..1f) {
            val ix = x1 + ua * (x2 - x1)
            val iy = y1 + ua * (y2 - y1)
            return Pair(ix, iy)
        }
        return null
    }
}
