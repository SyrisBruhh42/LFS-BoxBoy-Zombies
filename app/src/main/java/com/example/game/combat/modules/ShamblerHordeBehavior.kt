package com.example.game.combat.modules

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class ShamblerZombie(
    val id: Int,
    var x: Float,
    var y: Float,
    val radius: Float = 14f,
    var windowClawingId: Int? = null,
    var clawTimerMs: Long = 0L
)

data class WindowBarricade(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float = 24f,
    var planksRemaining: Int = 5
)

object ShamblerHordeBehavior {
    
    data class ShamblerSteeringResult(
        val sepX: Float,
        val sepY: Float,
        val cohX: Float,
        val cohY: Float,
        val clawingWindowId: Int?
    )

    fun computeShamblerSteering(
        zombieIndex: Int,
        shamblers: List<ShamblerZombie>,
        windows: List<WindowBarricade>,
        targetX: Float,
        targetY: Float,
        deltaMs: Long
    ): ShamblerSteeringResult {
        val self = shamblers[zombieIndex]
        
        // 1. Check if we should start/continue clawing a window
        var clawingId = self.windowClawingId
        if (clawingId == null) {
            for (w in windows) {
                if (w.planksRemaining > 0 && hypot(w.x - self.x, w.y - self.y) <= self.radius + w.radius + 8f) {
                    clawingId = w.id
                    break
                }
            }
        } else {
            val targetWindow = windows.find { it.id == clawingId }
            if (targetWindow == null || targetWindow.planksRemaining <= 0 || hypot(targetWindow.x - self.x, targetWindow.y - self.y) > self.radius + targetWindow.radius + 12f) {
                clawingId = null
            }
        }
        
        if (clawingId != null) {
            // If clawing, no steering
            return ShamblerSteeringResult(0f, 0f, 0f, 0f, clawingId)
        }

        // 2. Compute Separation & Cohesion
        var sepX = 0f
        var sepY = 0f
        var cohCenterX = 0f
        var cohCenterY = 0f
        var neighborCount = 0

        val minSepDist = self.radius * 2.3f
        val flockRadius = 140f

        for (i in shamblers.indices) {
            if (i == zombieIndex) continue
            val other = shamblers[i]
            val dx = self.x - other.x
            val dy = self.y - other.y
            val d = hypot(dx, dy)

            // Separation
            if (d > 0.05f && d <= minSepDist) {
                val push = (minSepDist - d) / minSepDist
                sepX += (dx / d) * push
                sepY += (dy / d) * push
            }

            // Cohesion
            if (d > 0.1f && d <= flockRadius) {
                neighborCount++
                cohCenterX += other.x
                cohCenterY += other.y
            }
        }

        val sepMag = hypot(sepX, sepY)
        val sepNormX = if (sepMag > 0.001f) sepX / sepMag else 0f
        val sepNormY = if (sepMag > 0.001f) sepY / sepMag else 0f

        var cohNormX = 0f
        var cohNormY = 0f
        if (neighborCount > 0) {
            val avgX = cohCenterX / neighborCount
            val avgY = cohCenterY / neighborCount
            val cohDx = avgX - self.x
            val cohDy = avgY - self.y
            val cohDist = hypot(cohDx, cohDy)
            if (cohDist > 0.001f) {
                cohNormX = cohDx / cohDist
                cohNormY = cohDy / cohDist
            }
        }

        return ShamblerSteeringResult(
            sepX = sepNormX,
            sepY = sepNormY,
            cohX = cohNormX,
            cohY = cohNormY,
            clawingWindowId = null
        )
    }
}
