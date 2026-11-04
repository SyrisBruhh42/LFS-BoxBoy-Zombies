package com.example.game.economy.vendors

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

enum class BarricadeBlockType(val maxHp: Int, val isEnergyBased: Boolean) {
    CONCRETE(1500, false),
    HARD_LIGHT_ENERGY(800, true),
    TITANIUM(3000, false)
}

data class MtvRoutingResult(
    val hasCollision: Boolean,
    val mtv: Offset = Offset.Zero
)

class PlaceableBarricadeWallBlock(
    val id: String,
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float,
    val type: BarricadeBlockType
) {
    var currentHp: Int = type.maxHp
        private set

    val bounds: Rect
        get() = Rect(x, y, x + width, y + height)

    val isDestroyed: Boolean
        get() = currentHp <= 0

    fun takeDamage(amount: Int) {
        if (!isDestroyed) {
            currentHp = (currentHp - amount).coerceAtLeast(0)
        }
    }

    /**
     * Resolves collision using the Minimum Translation Vector (MTV) algorithm via AABB overlap.
     * Given an incoming moving entity bounds, returns the displacement needed to push it out.
     */
    fun resolveAabbRouting(entityBounds: Rect): MtvRoutingResult {
        if (isDestroyed || !bounds.overlaps(entityBounds)) {
            return MtvRoutingResult(false)
        }

        val overlapLeft = bounds.right - entityBounds.left
        val overlapRight = entityBounds.right - bounds.left
        val overlapTop = bounds.bottom - entityBounds.top
        val overlapBottom = entityBounds.bottom - bounds.top

        val minOverlapX = if (overlapLeft < overlapRight) overlapLeft else -overlapRight
        val minOverlapY = if (overlapTop < overlapBottom) overlapTop else -overlapBottom

        val mtv = if (abs(minOverlapX) < abs(minOverlapY)) {
            Offset(minOverlapX, 0f)
        } else {
            Offset(0f, minOverlapY)
        }

        return MtvRoutingResult(true, mtv)
    }
}
