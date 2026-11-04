package com.example.game.combat.modules

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Unified BoxBoys Track 3 (Boxhead 2Play Combat & Physics):
 * Cloaked stalker zombie revealed by player lantern cone or muzzle flash bloom.
 */

data class PlayerLantern(
    val x: Float,
    val y: Float,
    val facingAngleRad: Float,
    val coneHalfSpreadRad: Float = Math.toRadians(25.0).toFloat(),
    val rangePx: Float = 350f,
    val muzzleFlashBloomRadiusPx: Float = 0f
)

data class StalkerState(
    val x: Float,
    val y: Float,
    val isRevealed: Boolean = false,
    val revelationTimerMs: Long = 0L
)

object ShadowStalkerAssassins {
    const val REVEAL_DURATION_MS = 1500L
    
    fun normalizeAngle(angle: Float): Float {
        var a = angle
        while (a <= -Math.PI) a += (2 * Math.PI).toFloat()
        while (a > Math.PI) a -= (2 * Math.PI).toFloat()
        return a
    }

    fun stepStalkers(
        stalkers: List<StalkerState>,
        lantern: PlayerLantern,
        deltaMs: Long
    ): List<StalkerState> {
        return stalkers.map { stalker ->
            val dist = hypot(stalker.x - lantern.x, stalker.y - lantern.y)
            var currentlyRevealed = false
            
            // Check muzzle flash bloom
            if (dist <= lantern.muzzleFlashBloomRadiusPx) {
                currentlyRevealed = true
            } else if (dist <= lantern.rangePx) {
                // Check lantern cone
                val angleToStalker = atan2(stalker.y - lantern.y, stalker.x - lantern.x)
                val angleDiff = abs(normalizeAngle(angleToStalker - lantern.facingAngleRad))
                if (angleDiff <= lantern.coneHalfSpreadRad) {
                    currentlyRevealed = true
                }
            }
            
            var newRevelationTimer = (stalker.revelationTimerMs - deltaMs).coerceAtLeast(0L)
            var newIsRevealed = stalker.isRevealed
            
            if (currentlyRevealed) {
                newRevelationTimer = REVEAL_DURATION_MS
                newIsRevealed = true
            } else if (newRevelationTimer == 0L) {
                newIsRevealed = false
            }
            
            stalker.copy(
                isRevealed = newIsRevealed,
                revelationTimerMs = newRevelationTimer
            )
        }
    }
}
