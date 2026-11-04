package com.example.game.combat.modules

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

data class ShadowStalker(
    val zombieId: Int,
    var isCloaked: Boolean = true,
    var revelationDecayTimerMs: Long = 0L,
    var x: Float = 0f,
    var y: Float = 0f
)

object ShadowStalkerAssassinsEngine {

    const val MAX_REVELATION_DECAY_MS = 1500L
    const val LANTERN_RANGE = 440f
    const val LANTERN_CONE_HALF_SPREAD_RAD = 0.56f
    const val PROXIMITY_HALO = 78f
    const val MUZZLE_FLASH_RANGE = 520f

    fun stepVisibility(
        stalkers: List<ShadowStalker>,
        playerX: Float,
        playerY: Float,
        aimAngleRad: Float,
        isMuzzleFlashActive: Boolean,
        deltaMs: Long
    ): List<ShadowStalker> {
        val updatedStalkers = mutableListOf<ShadowStalker>()

        for (stalker in stalkers) {
            val copy = stalker.copy()
            val isRevealed = isIlluminated(
                targetX = copy.x,
                targetY = copy.y,
                playerX = playerX,
                playerY = playerY,
                aimAngleRad = aimAngleRad,
                isMuzzleFlashActive = isMuzzleFlashActive
            )

            if (isRevealed) {
                copy.isCloaked = false
                copy.revelationDecayTimerMs = MAX_REVELATION_DECAY_MS
            } else {
                copy.revelationDecayTimerMs = (copy.revelationDecayTimerMs - deltaMs).coerceAtLeast(0L)
                if (copy.revelationDecayTimerMs == 0L) {
                    copy.isCloaked = true
                }
            }

            updatedStalkers.add(copy)
        }

        return updatedStalkers
    }

    private fun isIlluminated(
        targetX: Float,
        targetY: Float,
        playerX: Float,
        playerY: Float,
        aimAngleRad: Float,
        isMuzzleFlashActive: Boolean
    ): Boolean {
        val dx = targetX - playerX
        val dy = targetY - playerY
        val dist = hypot(dx, dy)

        // Proximity halo
        if (dist <= PROXIMITY_HALO) return true

        // Muzzle flash bloom
        if (isMuzzleFlashActive && dist <= MUZZLE_FLASH_RANGE) return true

        // Lantern range check
        if (dist > LANTERN_RANGE) return false

        // Lantern cone check
        val angleToTarget = atan2(dy, dx)
        val diff = normalizeAngleRad(angleToTarget - aimAngleRad)
        return abs(diff) <= LANTERN_CONE_HALF_SPREAD_RAD
    }

    private fun normalizeAngleRad(angle: Float): Float {
        var a = angle
        while (a > PI.toFloat()) a -= (2.0 * PI).toFloat()
        while (a < -PI.toFloat()) a += (2.0 * PI).toFloat()
        return a
    }
}
