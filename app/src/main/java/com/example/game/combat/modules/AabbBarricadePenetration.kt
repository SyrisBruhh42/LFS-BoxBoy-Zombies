package com.example.game.combat.modules

import com.example.game.contracts.Fnv1a64Hasher
import kotlin.math.hypot

data class Vector2(val x: Float, val y: Float) {
    val magnitude: Float
        get() = hypot(x, y)

    val isZero: Boolean
        get() = x == 0f && y == 0f
}

data class Aabb(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = minX + width / 2f
    val centerY: Float get() = minY + height / 2f

    fun translated(dx: Float, dy: Float): Aabb = Aabb(
        minX = minX + dx,
        minY = minY + dy,
        maxX = maxX + dx,
        maxY = maxY + dy
    )

    companion object {
        fun fromCenterAndRadius(cx: Float, cy: Float, radius: Float): Aabb = Aabb(
            minX = cx - radius,
            minY = cy - radius,
            maxX = cx + radius,
            maxY = cy + radius
        )
    }
}

object AabbBarricadePenetration {
    /**
     * Calculates the Minimum Translation Vector (MTV) to resolve a collision
     * between an entity and a barricade/wall.
     *
     * @param entity The Aabb of the moving entity.
     * @param barricade The Aabb of the static barricade.
     * @return A Vector2 representing the translation required to push the entity out of the barricade.
     *         Returns Vector2(0f, 0f) if there is no penetration/collision.
     */
    fun resolvePenetration(entity: Aabb, barricade: Aabb): Vector2 {
        val overlapXLeft = entity.maxX - barricade.minX
        val overlapXRight = barricade.maxX - entity.minX
        val overlapYTop = entity.maxY - barricade.minY
        val overlapYBottom = barricade.maxY - entity.minY

        if (overlapXLeft <= 0 || overlapXRight <= 0 || overlapYTop <= 0 || overlapYBottom <= 0) {
            return Vector2(0f, 0f)
        }

        val minOverlapX = if (overlapXLeft < overlapXRight) overlapXLeft else overlapXRight
        val minOverlapY = if (overlapYTop < overlapYBottom) overlapYTop else overlapYBottom

        return if (minOverlapX < minOverlapY) {
            if (overlapXLeft < overlapXRight) {
                Vector2(-overlapXLeft, 0f)
            } else {
                Vector2(overlapXRight, 0f)
            }
        } else {
            if (overlapYTop < overlapYBottom) {
                Vector2(0f, -overlapYTop)
            } else {
                Vector2(0f, overlapYBottom)
            }
        }
    }

    /**
     * Iteratively resolves penetration against multiple static barricades and returns the final
     * displaced AABB along with a deterministic FNV-1a checksum.
     */
    fun resolveMultiBarricadePenetration(
        initialEntity: Aabb,
        barricades: List<Aabb>
    ): Pair<Aabb, Long> {
        var current = initialEntity
        var h = Fnv1a64Hasher.initial()
        for (b in barricades) {
            val mtv = resolvePenetration(current, b)
            if (!mtv.isZero) {
                current = current.translated(mtv.x, mtv.y)
            }
            h = Fnv1a64Hasher.mixDouble(h, current.centerX.toDouble())
            h = Fnv1a64Hasher.mixDouble(h, current.centerY.toDouble())
        }
        return current to h
    }
}


fun evaluateAabbBarricadePenetrationLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "AabbBarricadePenetration") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
