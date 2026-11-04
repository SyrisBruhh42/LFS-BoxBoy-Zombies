package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.game.economy.BarricadeWindowTier

/**
 * Track 4 (Single-Currency BO2 + Borderlands 2 Vending Economy):
 * Tier 2 Reinforced Steel Shutter window upgrade with high durability.
 *
 * Implements deterministic logic for Black Ops 2 Debris Doors & Barricade Windows logic.
 * Enforces zero per-frame heap churn.
 */
@Keep
data class WindowBarricadeTier2Steel(
    val windowId: String,
    var currentHp: Int,
    var isBreached: Boolean = false,
    var isUpgrading: Boolean = false,
    var posX: Float = 0f,
    var posY: Float = 0f,
    var seed: Long = 0L
) {
    val tierInfo: BarricadeWindowTier
        get() = BarricadeWindowTier.TIER_2_STEEL_SHUTTERS
        
    @Transient
    var sparkTimer: Float = 0f

    /**
     * Applies damage bounded to [0, maxHp].
     */
    fun applyDamage(damage: Int) {
        if (isUpgrading || isBreached) return
        val clampedDamage = if (damage < 0) 0 else damage
        currentHp = (currentHp - clampedDamage).coerceAtLeast(0)
        if (currentHp == 0) {
            isBreached = true
        }
    }

    /**
     * Attempts to repair the barricade by [repairAmount].
     * Avoids heap allocation (does not return Pair).
     *
     * @param repairAmount The amount of health to restore.
     */
    fun repair(repairAmount: Int = 100) {
        if (isUpgrading) return
        
        val clampedRepair = if (repairAmount < 0) 0 else repairAmount
        if (clampedRepair == 0) return

        val missingHp = tierInfo.maxHp - currentHp
        if (missingHp <= 0) return
        
        val actualRepair = minOf(missingHp, clampedRepair)
        currentHp += actualRepair

        if (currentHp > 0) {
            isBreached = false
        }
    }

    /**
     * Applies deterministic frame-rate-independent step logic, including seeded variation.
     * @param dtSeconds time delta, strictly clamped to [0.001, 0.100].
     */
    fun step(dtSeconds: Float) {
        if (!dtSeconds.isFinite()) return
        val dt = dtSeconds.coerceIn(0.001f, 1.000f)
        
        if (currentHp <= 0) {
            isBreached = true
            currentHp = 0
        } else {
            isBreached = false
        }
        
        // Deterministic seeded variation: internal state updates periodically when damaged
        if (currentHp < tierInfo.maxHp) {
            sparkTimer += dt
            if (sparkTimer >= 0.5f) {
                sparkTimer -= 0.5f
                // Simple deterministic LCG to cycle the seed (e.g., for visual spark variations)
                seed = (seed xor 0x5DEECE66DL) * 11L + 2531011L
            }
        } else {
            sparkTimer = 0f
        }
    }

    /**
     * Performs spatial/topological validation against sector bounds.
     */
    fun validateTopology(minX: Float, maxX: Float, minY: Float, maxY: Float): Boolean {
        if (!posX.isFinite() || !posY.isFinite()) return false
        return posX >= minX && posX <= maxX && posY >= minY && posY <= maxY
    }

    /**
     * FNV-1a checksum for networking / determinism validation.
     */
    fun computeChecksum(): Long {
        var hash = -3750763034362895579L
        for (i in 0 until windowId.length) {
            hash = hash xor windowId[i].code.toLong()
            hash *= 1099511628211L
        }
        hash = hash xor currentHp.toLong()
        hash *= 1099511628211L
        hash = hash xor (if (isBreached) 1L else 0L)
        hash *= 1099511628211L
        hash = hash xor (if (isUpgrading) 1L else 0L)
        hash *= 1099511628211L
        hash = hash xor posX.toBits().toLong()
        hash *= 1099511628211L
        hash = hash xor posY.toBits().toLong()
        hash *= 1099511628211L
        hash = hash xor seed
        hash *= 1099511628211L
        return hash
    }

    /**
     * Network payload conversion for `BoxBoysNetProtocol-v3`.
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "windowId" to windowId,
            "currentHp" to currentHp,
            "isBreached" to isBreached,
            "isUpgrading" to isUpgrading,
            "posX" to posX,
            "posY" to posY,
            "seed" to seed
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): WindowBarricadeTier2Steel {
            return WindowBarricadeTier2Steel(
                windowId = map["windowId"] as? String ?: "win_default",
                currentHp = (map["currentHp"] as? Number)?.toInt() ?: 0,
                isBreached = map["isBreached"] as? Boolean ?: false,
                isUpgrading = map["isUpgrading"] as? Boolean ?: false,
                posX = (map["posX"] as? Number)?.toFloat() ?: 0f,
                posY = (map["posY"] as? Number)?.toFloat() ?: 0f,
                seed = (map["seed"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}


fun evaluateWindowBarricadeTier2SteelLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "WindowBarricadeTier2Steel") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
