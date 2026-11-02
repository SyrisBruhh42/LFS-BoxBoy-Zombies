package com.example.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Advanced Horde AI Steering Engine for BoxBoy Zombies.
 *
 * Implements Reynolds-inspired flocking dynamics (Separation, Cohesion, Alignment)
 * blended with Dijkstra Flow-Field navigation, dynamic encirclement/flanking offsets,
 * boss/leader-following behavior, and obstacle avoidance probes.
 */
object HordeSteeringEngine {

    data class SteeringWeights(
        val separationWeight: Float = 0.55f,
        val alignmentWeight: Float = 0.22f,
        val cohesionWeight: Float = 0.18f,
        val flowFieldWeight: Float = 0.60f,
        val targetSeekWeight: Float = 0.40f,
        val flankingWeight: Float = 0.25f,
        val leaderFollowWeight: Float = 0.35f
    )

    data class SteeringResult(
        val steerX: Float,
        val steerY: Float,
        val separationVector: Pair<Float, Float>,
        val flockVector: Pair<Float, Float>,
        val leaderVector: Pair<Float, Float>,
        val flankTarget: Pair<Float, Float>
    )

    /**
     * Computes the blended steering direction for a zombie within a horde flock.
     */
    fun computeHordeSteering(
        zombieIndex: Int,
        zombies: List<ZombieEntity>,
        targetX: Float,
        targetY: Float,
        flowVx: Float,
        flowVy: Float,
        weights: SteeringWeights = SteeringWeights(),
        flockNeighborRadius: Float = 140f
    ): SteeringResult {
        val self = zombies[zombieIndex]
        val distToTarget = hypot(targetX - self.x, targetY - self.y).coerceAtLeast(0.001f)

        // 1. Separation, Alignment, Cohesion & Leader identification
        var sepX = 0f
        var sepY = 0f
        var alignX = 0f
        var alignY = 0f
        var cohCenterX = 0f
        var cohCenterY = 0f
        var neighborCount = 0

        var leaderX = 0f
        var leaderY = 0f
        var leaderFound = false
        var minLeaderDist = Float.MAX_VALUE

        val minSepDist = self.archetype.radius * 2.3f

        for (i in zombies.indices) {
            if (i == zombieIndex) continue
            val other = zombies[i]
            val dx = self.x - other.x
            val dy = self.y - other.y
            val d = hypot(dx, dy)

            // Separation (close range repulsion)
            if (d in 0.05f..minSepDist) {
                val push = (minSepDist - d) / minSepDist
                sepX += (dx / d) * push
                sepY += (dy / d) * push
            }

            // Flocking neighborhood (Alignment & Cohesion)
            if (d in 0.1f..flockNeighborRadius) {
                neighborCount++
                cohCenterX += other.x
                cohCenterY += other.y

                // Approximate other's heading vector from its position relative to target
                val otDist = hypot(targetX - other.x, targetY - other.y).coerceAtLeast(0.001f)
                alignX += (targetX - other.x) / otDist
                alignY += (targetY - other.y) / otDist

                // Boss / High-tier leader following (Devils or Calicified Stalkers)
                if (other.archetype == ZombieArchetype.DEVIL_BOSS || other.archetype == ZombieArchetype.CALCIFIED_STALKER) {
                    if (d < minLeaderDist) {
                        minLeaderDist = d
                        leaderX = other.x
                        leaderY = other.y
                        leaderFound = true
                    }
                }
            }
        }

        // Normalize Separation
        val sepMag = hypot(sepX, sepY)
        val sepNormX = if (sepMag > 0.001f) sepX / sepMag else 0f
        val sepNormY = if (sepMag > 0.001f) sepY / sepMag else 0f

        // Compute Cohesion & Alignment
        var cohNormX = 0f
        var cohNormY = 0f
        var alignNormX = 0f
        var alignNormY = 0f
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

            val alignMag = hypot(alignX, alignY)
            if (alignMag > 0.001f) {
                alignNormX = alignX / alignMag
                alignNormY = alignY / alignMag
            }
        }

        // Leader vector
        var leaderVecX = 0f
        var leaderVecY = 0f
        if (leaderFound && self.archetype != ZombieArchetype.DEVIL_BOSS) {
            val ldx = leaderX - self.x
            val ldy = leaderY - self.y
            val lDist = hypot(ldx, ldy)
            if (lDist > self.archetype.radius * 2.5f) {
                leaderVecX = ldx / lDist
                leaderVecY = ldy / lDist
            }
        }

        // 2. Flanking / Encirclement arc offset
        // Spread zombies in an arc around player based on their index so they surround rather than conga-line
        val flankAngleOffset = ((zombieIndex % 9) - 4) * 0.28f // -1.12 to +1.12 radians (~ -64 to +64 deg)
        val baseAngle = kotlin.math.atan2(self.y - targetY, self.x - targetX)
        val flankAngle = baseAngle + flankAngleOffset
        val flankRadius = (self.archetype.radius + 38f).coerceAtLeast(32f)
        val flankTargetX = targetX + cos(flankAngle) * flankRadius
        val flankTargetY = targetY + sin(flankAngle) * flankRadius

        val fdx = flankTargetX - self.x
        val fdy = flankTargetY - self.y
        val fDist = hypot(fdx, fdy).coerceAtLeast(0.001f)
        val flankVecX = fdx / fDist
        val flankVecY = fdy / fDist

        // 3. Direct Target Seek
        val dirX = (targetX - self.x) / distToTarget
        val dirY = (targetY - self.y) / distToTarget

        // 4. Combined steering blend
        val targetWeight = if (distToTarget < 360f) weights.targetSeekWeight * 1.35f else weights.targetSeekWeight
        var combinedX = (flowVx * weights.flowFieldWeight) +
            (dirX * targetWeight) +
            (sepNormX * weights.separationWeight) +
            (alignNormX * weights.alignmentWeight) +
            (cohNormX * weights.cohesionWeight) +
            (flankVecX * weights.flankingWeight) +
            (leaderVecX * weights.leaderFollowWeight)

        var combinedY = (flowVy * weights.flowFieldWeight) +
            (dirY * targetWeight) +
            (sepNormY * weights.separationWeight) +
            (alignNormY * weights.alignmentWeight) +
            (cohNormY * weights.cohesionWeight) +
            (flankVecY * weights.flankingWeight) +
            (leaderVecY * weights.leaderFollowWeight)

        val mag = hypot(combinedX, combinedY).coerceAtLeast(0.001f)
        val finalX = combinedX / mag
        val finalY = combinedY / mag

        return SteeringResult(
            steerX = finalX,
            steerY = finalY,
            separationVector = Pair(sepNormX, sepNormY),
            flockVector = Pair(alignNormX + cohNormX, alignNormY + cohNormY),
            leaderVector = Pair(leaderVecX, leaderVecY),
            flankTarget = Pair(flankTargetX, flankTargetY)
        )
    }
}
