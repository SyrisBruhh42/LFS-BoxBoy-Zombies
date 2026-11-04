package com.example.game.combat.modules

import com.example.game.ZombieEntity
import com.example.game.InteractiveStation
import kotlin.math.hypot

object ShamblerHordeBehavior {

    data class ShamblerSteeringResult(
        val dx: Float,
        val dy: Float,
        val isClawingWindow: Boolean
    )

    fun computeShamblerBehavior(
        zombie: ZombieEntity,
        allZombies: List<ZombieEntity>,
        targetX: Float,
        targetY: Float,
        windows: List<InteractiveStation>
    ): ShamblerSteeringResult {
        // 1. Find nearest window to attack (Window-Clawing behavior)
        var nearestWindow: InteractiveStation? = null
        var minWindowDist = Float.MAX_VALUE

        for (window in windows) {
            if (window.barricadePlanks > 0) {
                val dist = hypot(zombie.x - window.x, zombie.y - window.y)
                if (dist < minWindowDist) {
                    minWindowDist = dist
                    nearestWindow = window
                }
            }
        }

        // If very close to a barricaded window, stop and claw
        if (nearestWindow != null && minWindowDist < 40f) {
            return ShamblerSteeringResult(0f, 0f, true)
        }

        // 2. Reynolds-inspired flocking (Cohesion and Separation)
        var sepX = 0f
        var sepY = 0f
        var cohX = 0f
        var cohY = 0f
        var neighborCount = 0

        for (other in allZombies) {
            if (other.id == zombie.id) continue
            val dist = hypot(zombie.x - other.x, zombie.y - other.y)

            if (dist < 30f && dist > 0.01f) {
                // Separation (close range repulsion)
                sepX += (zombie.x - other.x) / dist
                sepY += (zombie.y - other.y) / dist
            }

            if (dist < 150f && dist > 0.01f) {
                // Cohesion (flock centering)
                cohX += other.x
                cohY += other.y
                neighborCount++
            }
        }

        // 3. Direct Target Seek
        var dirX = targetX - zombie.x
        var dirY = targetY - zombie.y
        val targetDist = hypot(dirX, dirY)

        if (targetDist > 0.01f) {
            dirX /= targetDist
            dirY /= targetDist
        }

        // Normalize Cohesion vector towards center of mass
        if (neighborCount > 0) {
            cohX = (cohX / neighborCount) - zombie.x
            cohY = (cohY / neighborCount) - zombie.y
            val cohDist = hypot(cohX, cohY)
            if (cohDist > 0.01f) {
                cohX /= cohDist
                cohY /= cohDist
            }
        }

        // Normalize Separation vector
        val sepDist = hypot(sepX, sepY)
        if (sepDist > 0.01f) {
            sepX /= sepDist
            sepY /= sepDist
        }

        // 4. Combined steering blend
        var combinedX = dirX * 0.5f + sepX * 0.4f + cohX * 0.1f
        var combinedY = dirY * 0.5f + sepY * 0.4f + cohY * 0.1f

        val mag = hypot(combinedX, combinedY)
        if (mag > 0.01f) {
            combinedX /= mag
            combinedY /= mag
        }

        return ShamblerSteeringResult(combinedX, combinedY, false)
    }
}
