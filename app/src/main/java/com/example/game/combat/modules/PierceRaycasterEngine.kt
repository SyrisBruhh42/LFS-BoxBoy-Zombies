package com.example.game.combat.modules

import androidx.compose.ui.geometry.Rect
import com.example.game.ZombieEntity
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Unified BoxBoys Track 3 (Boxhead 2Play Combat & Physics):
 * Multi-target penetrating railgun & shotgun pellet raycaster with wall occlusion.
 */
object PierceRaycasterEngine {

    data class RaycastHit(
        val zombie: ZombieEntity,
        val distance: Float
    )

    data class RaycastResult(
        val hits: List<RaycastHit>,
        val endX: Float,
        val endY: Float
    )

    /**
     * Fires a penetrating railgun ray that hits multiple zombies up to [maxPenetrationCount]
     * until it either reaches [maxRange] or hits a wall occlusion bounding box in [wallObstacles].
     */
    fun firePenetratingRay(
        startX: Float,
        startY: Float,
        dirX: Float,
        dirY: Float,
        maxRange: Float,
        zombies: List<ZombieEntity>,
        wallObstacles: List<Rect>,
        maxPenetrationCount: Int = Int.MAX_VALUE
    ): RaycastResult {
        // Normalize direction
        val len = hypot(dirX, dirY)
        val nx = if (len > 0) dirX / len else 1f
        val ny = if (len > 0) dirY / len else 0f

        var currentMaxRange = maxRange

        // 1. Raycast against AABB Walls to find first occlusion and truncate range
        for (wall in wallObstacles) {
            val distToWall = rayIntersectRect(startX, startY, nx, ny, wall)
            if (distToWall != null && distToWall >= 0 && distToWall < currentMaxRange) {
                currentMaxRange = distToWall
            }
        }

        val endX = startX + nx * currentMaxRange
        val endY = startY + ny * currentMaxRange

        // 2. Collect all zombies along the non-occluded ray segment
        val hitZombies = mutableListOf<RaycastHit>()
        for (zombie in zombies) {
            val distToRay = pointToSegmentDistance(zombie.x, zombie.y, startX, startY, endX, endY)
            if (distToRay <= zombie.archetype.radius) {
                // Determine intersection point projection for distance sorting
                val dx = endX - startX
                val dy = endY - startY
                val l2 = dx * dx + dy * dy
                val t = if (l2 == 0f) 0f else {
                    (((zombie.x - startX) * dx + (zombie.y - startY) * dy) / l2).coerceIn(0f, 1f)
                }
                val projX = startX + t * dx
                val projY = startY + t * dy
                val distFromStart = hypot(projX - startX, projY - startY)
                hitZombies.add(RaycastHit(zombie, distFromStart))
            }
        }

        // Sort by distance from start
        hitZombies.sortBy { it.distance }

        // Take up to maxPenetrationCount
        val finalHits = hitZombies.take(maxPenetrationCount)

        // If penetration is limited and we maxed out, adjust the visual end point to the last hit zombie
        val finalEndX = if (finalHits.size == maxPenetrationCount && finalHits.isNotEmpty()) {
            startX + nx * finalHits.last().distance
        } else {
            endX
        }
        val finalEndY = if (finalHits.size == maxPenetrationCount && finalHits.isNotEmpty()) {
            startY + ny * finalHits.last().distance
        } else {
            endY
        }

        return RaycastResult(finalHits, finalEndX, finalEndY)
    }

    /**
     * Fires multiple shotgun pellets distributed along a cone spread.
     */
    fun fireShotgunSpread(
        startX: Float,
        startY: Float,
        centerDirX: Float,
        centerDirY: Float,
        maxRange: Float,
        pelletCount: Int,
        spreadDegrees: Float,
        zombies: List<ZombieEntity>,
        wallObstacles: List<Rect>
    ): List<RaycastResult> {
        val results = mutableListOf<RaycastResult>()
        val centerAngle = kotlin.math.atan2(centerDirY, centerDirX)
        val halfSpread = Math.toRadians(spreadDegrees.toDouble()).toFloat() / 2f

        for (i in 0 until pelletCount) {
            // Distribute angles evenly or randomly depending on feel, here we do evenly for consistency in test
            val angleOffset = if (pelletCount > 1) {
                -halfSpread + (i.toFloat() / (pelletCount - 1)) * (halfSpread * 2)
            } else {
                0f
            }

            val pelletAngle = centerAngle + angleOffset
            val dirX = cos(pelletAngle)
            val dirY = sin(pelletAngle)

            // Shotguns typically have limited penetration, say 1 or 2. We use 1 per pellet for standard scattergun.
            val result = firePenetratingRay(
                startX = startX,
                startY = startY,
                dirX = dirX,
                dirY = dirY,
                maxRange = maxRange,
                zombies = zombies,
                wallObstacles = wallObstacles,
                maxPenetrationCount = 1
            )
            results.add(result)
        }
        return results
    }

    /**
     * Minimum distance from point (px, py) to line segment (x1, y1) -> (x2, y2)
     */
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
     * Ray to AABB Intersection using Liang-Barsky algorithm or similar.
     * Returns the distance to the intersection point, or null if it misses.
     */
    private fun rayIntersectRect(rx: Float, ry: Float, dx: Float, dy: Float, rect: Rect): Float? {
        var tmin = Float.NEGATIVE_INFINITY
        var tmax = Float.POSITIVE_INFINITY

        if (dx != 0f) {
            val tx1 = (rect.left - rx) / dx
            val tx2 = (rect.right - rx) / dx
            tmin = maxOf(tmin, minOf(tx1, tx2))
            tmax = minOf(tmax, maxOf(tx1, tx2))
        } else if (rx < rect.left || rx > rect.right) {
            return null // Parallel and outside
        }

        if (dy != 0f) {
            val ty1 = (rect.top - ry) / dy
            val ty2 = (rect.bottom - ry) / dy
            tmin = maxOf(tmin, minOf(ty1, ty2))
            tmax = minOf(tmax, maxOf(ty1, ty2))
        } else if (ry < rect.top || ry > rect.bottom) {
            return null // Parallel and outside
        }

        if (tmax >= tmin && tmax >= 0) {
            return maxOf(tmin, 0f)
        }
        return null
    }
}
