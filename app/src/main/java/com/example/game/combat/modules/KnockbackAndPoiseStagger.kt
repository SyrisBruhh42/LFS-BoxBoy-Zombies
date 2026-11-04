package com.example.game.combat.modules

import kotlin.math.cos
import kotlin.math.sin

data class KnockbackImpulse(val vx: Float, val vy: Float)

data class StaggerState(val isStaggered: Boolean, val remainingStaggerFrames: Int)

object KnockbackAndPoiseStagger {
    private const val SHOTGUN_BASE_FORCE = 800f
    private const val KORSAKOV_BASE_FORCE = 400f

    private const val SHOTGUN_MAX_RANGE = 300f
    private const val KORSAKOV_MAX_RANGE = 600f

    fun calculateKnockback(weaponType: String, distance: Float, angleRad: Float): KnockbackImpulse {
        val (baseForce, maxRange) = when (weaponType) {
            "Shotgun" -> SHOTGUN_BASE_FORCE to SHOTGUN_MAX_RANGE
            "Korsakov" -> KORSAKOV_BASE_FORCE to KORSAKOV_MAX_RANGE
            else -> 0f to 0f
        }

        if (baseForce == 0f || distance > maxRange) {
            return KnockbackImpulse(0f, 0f)
        }

        // Linear falloff based on distance
        val falloff = (1f - (distance / maxRange)).coerceIn(0f, 1f)
        val force = baseForce * falloff

        val vx = force * cos(angleRad)
        val vy = force * sin(angleRad)

        return KnockbackImpulse(vx, vy)
    }

    fun calculateStagger(weaponType: String, currentPoise: Float): StaggerState {
        val staggerFrames = when (weaponType) {
            "Shotgun" -> if (currentPoise < 100f) 30 else 0
            "Korsakov" -> if (currentPoise < 50f) 15 else 0
            else -> 0
        }

        return StaggerState(staggerFrames > 0, staggerFrames)
    }

    fun stepStaggerRecovery(state: StaggerState): StaggerState {
        val nextFrames = (state.remainingStaggerFrames - 1).coerceAtLeast(0)
        return StaggerState(nextFrames > 0, nextFrames)
    }
}
