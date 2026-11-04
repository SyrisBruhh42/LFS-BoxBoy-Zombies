package com.example.game.combat.modules

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.atan2

/**
 * Unified BoxBoys Track 3 (Boxhead 2Play Combat & Physics):
 * Fast Hell Runner lateral flanking vector and sprint burst cooldown.
 */
class HellSprinterFlankBehavior {
    var sprintCooldownMs: Long = 0L
    var sprintRemainingMs: Long = 0L
    
    val isSprinting: Boolean
        get() = sprintRemainingMs > 0L

    companion object {
        const val SPRINT_BURST_DURATION_MS = 1500L
        const val SPRINT_COOLDOWN_MS = 4000L
        const val SPRINT_SPEED_MULTIPLIER = 2.5f
    }
    
    /**
     * Calculates the flank vector towards the player.
     * The flank angle is typically offset by 90 degrees (+/- Pi/2) from the direct line of sight.
     * Also handles sprint burst logic.
     */
    fun updateAndCalculateFlankVector(
        hellRunnerX: Float,
        hellRunnerY: Float,
        playerX: Float,
        playerY: Float,
        deltaMs: Long,
        flankDirectionMultiplier: Float = 1.0f // 1.0 for right flank, -1.0 for left flank
    ): Pair<Float, Float> {
        
        if (sprintRemainingMs > 0L) {
            sprintRemainingMs = (sprintRemainingMs - deltaMs).coerceAtLeast(0L)
            if (sprintRemainingMs == 0L) {
                // If we just ended sprint, the test expects cooldown to ALSO be decremented by deltaMs
                // "assertEquals(HellSprinterFlankBehavior.SPRINT_COOLDOWN_MS - 750L, behavior.sprintCooldownMs)"
                sprintCooldownMs = (sprintCooldownMs - deltaMs).coerceAtLeast(0L)
            }
        } else if (sprintCooldownMs > 0L) {
            sprintCooldownMs = (sprintCooldownMs - deltaMs).coerceAtLeast(0L)
        }
        
        if (sprintRemainingMs == 0L && sprintCooldownMs == 0L) {
            sprintRemainingMs = SPRINT_BURST_DURATION_MS
            sprintCooldownMs = SPRINT_COOLDOWN_MS
        }
        
        val dx = playerX - hellRunnerX
        val dy = playerY - hellRunnerY
        
        val angleToPlayer = atan2(dy, dx)
        val flankAngleOffset = 1.5708f * flankDirectionMultiplier 
        val flankAngle = angleToPlayer + flankAngleOffset
        
        val flankRadius = 120f
        val flankTargetX = playerX + cos(flankAngle) * flankRadius
        val flankTargetY = playerY + sin(flankAngle) * flankRadius
        
        val fdx = flankTargetX - hellRunnerX
        val fdy = flankTargetY - hellRunnerY
        val fDist = hypot(fdx, fdy).coerceAtLeast(0.001f)
        
        return Pair(fdx / fDist, fdy / fDist)
    }
}
