package com.example.game.combat.modules

import com.example.game.InteractiveStation
import com.example.game.StationKind
import com.example.game.ZombieEntity
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

    data class ShamblerBehaviorVector(
        val dx: Float,
        val dy: Float,
        val isClawingWindow: Boolean
    )

    fun computeShamblerBehavior(
        zombie: ZombieEntity,
        allZombies: List<ZombieEntity>,
        targetX: Float,
        targetY: Float,
        stations: List<InteractiveStation>
    ): ShamblerBehaviorVector {
        val nearbyWindow = stations.firstOrNull { station ->
            station.kind == StationKind.BARRICADE_WINDOW &&
                station.barricadePlanks > 0 &&
                hypot(station.x - zombie.x, station.y - zombie.y) <= 40f
        }
        if (nearbyWindow != null) {
            return ShamblerBehaviorVector(dx = 0f, dy = 0f, isClawingWindow = true)
        }

        val toTargetX = targetX - zombie.x
        val toTargetY = targetY - zombie.y
        val targetDist = hypot(toTargetX, toTargetY)
        val seekDx = if (targetDist > 0.001f) toTargetX / targetDist else 0f
        val seekDy = if (targetDist > 0.001f) toTargetY / targetDist else 0f

        var sepX = 0f
        var sepY = 0f
        val sepRadius = 32f
        for (other in allZombies) {
            if (other.id == zombie.id) continue
            val diffX = zombie.x - other.x
            val diffY = zombie.y - other.y
            val dist = hypot(diffX, diffY)
            if (dist in 0.05f..sepRadius) {
                val weight = (sepRadius - dist) / sepRadius
                sepX += (diffX / dist) * weight
                sepY += (diffY / dist) * weight
            }
        }

        return ShamblerBehaviorVector(
            dx = seekDx + sepX * 0.8f,
            dy = seekDy + sepY * 0.8f,
            isClawingWindow = false
        )
    }
    
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


fun evaluateShamblerHordeBehaviorLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "ShamblerHordeBehavior") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
