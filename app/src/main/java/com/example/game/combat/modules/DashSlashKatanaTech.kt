package com.example.game.combat.modules

import com.example.game.ZombieEntity
import kotlin.math.hypot

data class DashSlashResult(
    val hitZombieIds: List<Int>,
    val newPlayerX: Float,
    val newPlayerY: Float,
    val iframeDurationMs: Long
)

object DashSlashKatanaTech {
    const val DASH_DISTANCE = 250f
    const val SLASH_WIDTH = 32f
    const val BASE_IFRAME_MS = 400L
    const val IFRAME_BONUS_PER_HIT_MS = 100L

    fun executeDashSlash(
        playerX: Float,
        playerY: Float,
        aimAngleRad: Float,
        zombies: List<ZombieEntity>
    ): DashSlashResult {
        val endX = playerX + kotlin.math.cos(aimAngleRad).toFloat() * DASH_DISTANCE
        val endY = playerY + kotlin.math.sin(aimAngleRad).toFloat() * DASH_DISTANCE

        val hitZombies = zombies.filter { zombie ->
            val dist = pointToSegmentDistance(zombie.x, zombie.y, playerX, playerY, endX, endY)
            dist <= SLASH_WIDTH + zombie.archetype.radius
        }.sortedBy { hypot(it.x - playerX, it.y - playerY) }

        val hitZombieIds = hitZombies.map { it.id }
        val iframeDurationMs = BASE_IFRAME_MS + (hitZombies.size * IFRAME_BONUS_PER_HIT_MS)

        return DashSlashResult(
            hitZombieIds = hitZombieIds,
            newPlayerX = endX,
            newPlayerY = endY,
            iframeDurationMs = iframeDurationMs
        )
    }

    private fun pointToSegmentDistance(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot(px - projX, py - projY)
    }
}
