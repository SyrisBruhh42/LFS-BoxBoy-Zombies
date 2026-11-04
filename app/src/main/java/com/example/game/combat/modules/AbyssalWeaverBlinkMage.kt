package com.example.game.combat.modules

import kotlin.math.hypot

data class SlowOrbProjectile(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val radiusPx: Float = 24f,
    val slowFactor: Float = 0.5f,
    val durationMs: Long = 3000L
)

class AbyssalWeaverBlinkMage(
    var x: Float,
    var y: Float,
    val blinkDistancePx: Float = 300f,
    val preferredEngagementRangePx: Float = 400f,
    val blinkCooldownMs: Long = 5000L,
    val orbCooldownMs: Long = 3000L
) {
    var blinkCooldownRemainingMs: Long = 0L
    var orbCooldownRemainingMs: Long = 0L

    fun updateBlink(playerX: Float, playerY: Float, deltaMs: Long) {
        blinkCooldownRemainingMs = (blinkCooldownRemainingMs - deltaMs).coerceAtLeast(0L)
        orbCooldownRemainingMs = (orbCooldownRemainingMs - deltaMs).coerceAtLeast(0L)

        if (blinkCooldownRemainingMs == 0L) {
            val dist = hypot(playerX - x, playerY - y)
            if (dist < preferredEngagementRangePx) {
                // Blink away
                val dx = x - playerX
                val dy = y - playerY
                val mag = hypot(dx, dy).coerceAtLeast(1f)
                x += (dx / mag) * blinkDistancePx
                y += (dy / mag) * blinkDistancePx
            } else {
                // Blink towards
                val dx = playerX - x
                val dy = playerY - y
                val mag = hypot(dx, dy).coerceAtLeast(1f)
                x += (dx / mag) * blinkDistancePx
                y += (dy / mag) * blinkDistancePx
            }
            blinkCooldownRemainingMs = blinkCooldownMs
        }
    }

    fun fireSlowOrb(playerX: Float, playerY: Float): SlowOrbProjectile? {
        if (orbCooldownRemainingMs == 0L) {
            val dx = playerX - x
            val dy = playerY - y
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            val speed = 150f
            val orb = SlowOrbProjectile(
                x = x,
                y = y,
                vx = (dx / dist) * speed,
                vy = (dy / dist) * speed
            )
            orbCooldownRemainingMs = orbCooldownMs
            return orb
        }
        return null
    }
}
