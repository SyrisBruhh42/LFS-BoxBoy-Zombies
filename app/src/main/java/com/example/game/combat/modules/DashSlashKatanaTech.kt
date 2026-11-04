package com.example.game.combat.modules

import com.example.game.ZombieEntity
import com.example.game.contracts.Fnv1a64Hasher
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

data class DashSlashResult(
    val hitZombieIds: List<Int>,
    val newPlayerX: Float,
    val newPlayerY: Float,
    val iframeDurationMs: Long
) {
    val hitCount: Int
        get() = hitZombieIds.size

    fun computeChecksum(): Long {
        var h = Fnv1a64Hasher.initial()
        h = Fnv1a64Hasher.mixDouble(h, newPlayerX.toDouble())
        h = Fnv1a64Hasher.mixDouble(h, newPlayerY.toDouble())
        h = Fnv1a64Hasher.mixLong(h, iframeDurationMs)
        for (id in hitZombieIds) {
            h = Fnv1a64Hasher.mixInt(h, id)
        }
        return h
    }
}

object DashSlashKatanaTech {
    const val DASH_DISTANCE = 250f
    const val SLASH_WIDTH = 32f
    const val BASE_IFRAME_MS = 400L
    const val IFRAME_BONUS_PER_HIT_MS = 100L
    const val BASE_KATANA_DAMAGE = 145

    fun executeDashSlash(
        playerX: Float,
        playerY: Float,
        aimAngleRad: Float,
        zombies: List<ZombieEntity>
    ): DashSlashResult {
        val endX = playerX + cos(aimAngleRad.toDouble()).toFloat() * DASH_DISTANCE
        val endY = playerY + sin(aimAngleRad.toDouble()).toFloat() * DASH_DISTANCE

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

    /**
     * Computes scaled katana slash damage based on wave progression and combo streak multiplier.
     */
    fun computeSlashDamage(
        wave: Int,
        comboMultiplier: Float,
        hitOrderIndex: Int
    ): Int {
        val safeWave = wave.coerceAtLeast(1)
        val safeCombo = comboMultiplier.coerceIn(1.0f, 3.0f)
        val cleaveFalloff = (1.0f - hitOrderIndex.coerceAtLeast(0) * 0.06f).coerceAtLeast(0.55f)
        return (BASE_KATANA_DAMAGE * (1.0f + (safeWave - 1) * 0.12f) * safeCombo * cleaveFalloff)
            .roundToInt()
            .coerceAtLeast(35)
    }

    private fun pointToSegmentDistance(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
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


fun evaluateDashSlashKatanaTechLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "DashSlashKatanaTech") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
