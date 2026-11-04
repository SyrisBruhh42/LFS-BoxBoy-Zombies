package com.example.game.combat.modules

import com.example.game.AbyssalChiaroscuroLightingEngine
import com.example.game.ZombieEntity

object LightLockConeAutoAim {

    /**
     * Evaluates Light-Lock Auto-Aim: prioritizes enemies inside the player's directional
     * lantern cone first, then falls back to closest proximity enemy.
     * Includes logic to apply weak-point crit multipliers when light-locked.
     */
    fun findLightLockTarget(
        zombies: List<ZombieEntity>,
        playerX: Float,
        playerY: Float,
        currentAimRad: Float,
        lanternRange: Float,
        coneHalfSpreadRad: Float,
        maxFallbackRange: Float = 420f
    ): Pair<ZombieEntity?, Boolean> {
        if (zombies.isEmpty()) return Pair(null, false)

        // First search for enemies illuminated inside the player's lantern cone
        val illuminatedTarget = zombies
            .filter { z ->
                AbyssalChiaroscuroLightingEngine.isPointIlluminatedByPlayer(
                    targetX = z.x,
                    targetY = z.y,
                    playerX = playerX,
                    playerY = playerY,
                    aimAngleRad = currentAimRad,
                    lanternRange = lanternRange,
                    coneHalfSpreadRad = coneHalfSpreadRad
                )
            }
            .minByOrNull { z ->
                val dx = z.x - playerX
                val dy = z.y - playerY
                dx * dx + dy * dy
            }

        if (illuminatedTarget != null) {
            return Pair(illuminatedTarget, true)
        }

        // Fallback to the closest enemy within the maximum fallback range
        val maxFallbackRangeSq = maxFallbackRange * maxFallbackRange
        val fallback = zombies
            .filter { z ->
                val dx = z.x - playerX
                val dy = z.y - playerY
                (dx * dx + dy * dy) <= maxFallbackRangeSq
            }
            .minByOrNull { z ->
                val dx = z.x - playerX
                val dy = z.y - playerY
                dx * dx + dy * dy
            }

        return Pair(fallback, false)
    }

    /**
     * Calculates the final damage considering the Light-Lock crit multiplier.
     */
    fun calculateDamage(
        baseDamage: Int,
        isLightLocked: Boolean,
        illuminatedCritMultiplier: Float
    ): Int {
        if (isLightLocked) {
            return (baseDamage * illuminatedCritMultiplier).toInt()
        }
        return baseDamage
    }
}
