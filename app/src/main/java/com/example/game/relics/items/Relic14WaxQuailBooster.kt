package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.cos
import kotlin.math.sin

/**
 * Track 6: Risk of Rain 2 Rare-Drop-Only Passive Skill Relic.
 * Relic 14: Wax Quail Booster
 *
 * Dashing or sprinting grants a +120px forward burst per stack.
 * Invariant: `canBePurchasedAtVending = false` for every relic!
 */
@Keep
data class WaxQuailBoosterConfig(
    val baseBurstPxPerStack: Float = 120.0f,
    val canBePurchasedAtVending: Boolean = false
) {
    init {
        require(!canBePurchasedAtVending) { "RoR2 Passive Relics are drop-only (canBePurchasedAtVending must be false)" }
        require(baseBurstPxPerStack > 0.0f && baseBurstPxPerStack.isFinite()) { "Burst config invalid" }
    }
}

@Keep
data class WaxQuailBoosterState(
    val stackCount: Int,
    val isSprinting: Boolean,
    val burstCooldownMs: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val rotationRad: Float = 0f,
    val checksum: Long = 0L
) {
    init {
        require(stackCount >= 1) { "Relic stackCount must be >= 1" }
        require(x.isFinite() && y.isFinite() && rotationRad.isFinite()) { "NaN/Infinity detected in state coordinates" }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "stackCount" to stackCount,
            "isSprinting" to isSprinting,
            "burstCooldownMs" to burstCooldownMs,
            "x" to x,
            "y" to y,
            "rotationRad" to rotationRad,
            "checksum" to checksum
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): WaxQuailBoosterState {
            return WaxQuailBoosterState(
                stackCount = (map["stackCount"] as? Number)?.toInt() ?: 1,
                isSprinting = map["isSprinting"] as? Boolean ?: false,
                burstCooldownMs = (map["burstCooldownMs"] as? Number)?.toFloat() ?: 0f,
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                rotationRad = (map["rotationRad"] as? Number)?.toFloat() ?: 0f,
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }

    fun computeFnv1a(): Long {
        var hash = -2128831035142426939L // FNV offset basis

        fun updateHash(v: Float) {
            val bits = v.toBits()
            hash = hash xor (bits and 0xFF).toLong()
            hash = hash * 1099511628211L
            hash = hash xor ((bits ushr 8) and 0xFF).toLong()
            hash = hash * 1099511628211L
            hash = hash xor ((bits ushr 16) and 0xFF).toLong()
            hash = hash * 1099511628211L
            hash = hash xor ((bits ushr 24) and 0xFF).toLong()
            hash = hash * 1099511628211L
        }

        updateHash(stackCount.toFloat())
        updateHash(if (isSprinting) 1.0f else 0.0f)
        updateHash(burstCooldownMs)
        updateHash(x)
        updateHash(y)
        updateHash(rotationRad)

        return hash
    }
}

@Keep
data class WaxQuailEngineResult(
    val newState: WaxQuailBoosterState,
    val burstAppliedPx: Float,
    val dx: Float,
    val dy: Float
)

object WaxQuailBoosterEngine {
    const val MAX_MAP_BOUNDARY_PX = 2400.0f * 10.0f

    fun processTick(
        dtSeconds: Float,
        config: WaxQuailBoosterConfig,
        state: WaxQuailBoosterState
    ): WaxQuailEngineResult {
        // Clamp dt for frame-rate independence and stability
        val dtSafe = dtSeconds.coerceIn(0.001f, 0.100f)
        val dtMsSafe = dtSafe * 1000f

        var nextCooldown = state.burstCooldownMs - dtMsSafe
        if (nextCooldown < 0f || !nextCooldown.isFinite()) {
            nextCooldown = 0f
        }

        var appliedBurst = 0f
        var dx = 0f
        var dy = 0f
        var nx = state.x
        var ny = state.y

        if (state.isSprinting && nextCooldown <= 0f) {
            appliedBurst = config.baseBurstPxPerStack * state.stackCount
            // Hard cap burst for anti-clipping fallback in massive lag
            appliedBurst = appliedBurst.coerceAtMost(2400f)

            dx = appliedBurst * cos(state.rotationRad)
            dy = appliedBurst * sin(state.rotationRad)

            if (!dx.isFinite()) dx = 0f
            if (!dy.isFinite()) dy = 0f

            nx += dx
            ny += dy

            nextCooldown = 500f // 500ms internal cooldown between dashes
        }

        if (!nx.isFinite()) nx = state.x
        if (!ny.isFinite()) ny = state.y

        nx = nx.coerceIn(-MAX_MAP_BOUNDARY_PX, MAX_MAP_BOUNDARY_PX)
        ny = ny.coerceIn(-MAX_MAP_BOUNDARY_PX, MAX_MAP_BOUNDARY_PX)

        val nextState = state.copy(
            x = nx,
            y = ny,
            burstCooldownMs = nextCooldown
        )

        val finalState = nextState.copy(checksum = nextState.computeFnv1a())

        return WaxQuailEngineResult(
            newState = finalState,
            burstAppliedPx = appliedBurst,
            dx = dx,
            dy = dy
        )
    }
}
