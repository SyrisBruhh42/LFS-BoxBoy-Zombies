package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.game.economy.BarricadeWindowTier
import kotlin.math.roundToInt

@Keep
data class Tier1WoodSnapshot(
    val windowId: String,
    val currentPlanks: Int,
    val maxPlanks: Int,
    val isBreached: Boolean,
    val rebuildCooldownTimer: Float,
    val checksum: Long
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "windowId" to windowId,
            "currentPlanks" to currentPlanks,
            "maxPlanks" to maxPlanks,
            "isBreached" to isBreached,
            "rebuildCooldownTimer" to rebuildCooldownTimer,
            "checksum" to checksum
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Tier1WoodSnapshot {
            return Tier1WoodSnapshot(
                windowId = map["windowId"] as? String ?: "",
                currentPlanks = (map["currentPlanks"] as? Number)?.toInt() ?: 0,
                maxPlanks = (map["maxPlanks"] as? Number)?.toInt() ?: 0,
                isBreached = map["isBreached"] as? Boolean ?: false,
                rebuildCooldownTimer = (map["rebuildCooldownTimer"] as? Number)?.toFloat() ?: 0f,
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

@Keep
data class Tier1WoodTickResult(
    val nextState: Tier1WoodSnapshot,
    val planksBrokenThisTick: Int
)

object WindowBarricadeTier1Wood {
    private const val MAX_ZOMBIE_CLAWING_LIMIT = 6
    private const val PLANK_BREAK_INTERVAL = 1.5f

    /**
     * Attempts to repair one board on a Tier 1 Timber Plank barricade.
     * Rewards the player with +20 currency if a board was successfully added.
     *
     * @param currentPlanks The current number of intact planks.
     * @param maxPlanks The maximum capacity for planks on this window.
     * @param currentCurrency The player's current currency balance.
     * @return A Pair containing the updated plank count and the updated currency balance.
     */
    fun repairOneBoard(currentPlanks: Int, maxPlanks: Int, currentCurrency: Int): Pair<Int, Int> {
        val maxSafePlanks = maxPlanks.coerceAtLeast(1)
        val safePlanks = currentPlanks.coerceIn(0, maxSafePlanks)
        val tier = BarricadeWindowTier.TIER_1_WOOD_BOARDS
        if (safePlanks < maxSafePlanks) {
            return Pair(safePlanks + 1, currentCurrency + tier.repairRewardPerBoard)
        }
        return Pair(safePlanks, currentCurrency)
    }

    fun computeChecksum(
        windowId: String,
        currentPlanks: Int,
        maxPlanks: Int,
        isBreached: Boolean,
        rebuildCooldownTimer: Float
    ): Long {
        var hash = -212883103514042284L // FNV offset basis
        
        fun hashString(s: String) {
            for (char in s) {
                hash = hash xor char.code.toLong()
                hash = hash * 1099511628211L // FNV prime
            }
        }
        
        fun hashInt(v: Int) {
            hash = hash xor (v.toLong() and 0xFFFFFFFFL)
            hash = hash * 1099511628211L
        }
        
        fun hashBoolean(v: Boolean) {
            hash = hash xor (if (v) 1L else 0L)
            hash = hash * 1099511628211L
        }
        
        fun hashFloat(v: Float) {
            hashInt(v.toRawBits())
        }

        hashString(windowId)
        hashInt(currentPlanks)
        hashInt(maxPlanks)
        hashBoolean(isBreached)
        hashFloat(rebuildCooldownTimer)
        
        return hash
    }

    /**
     * Progresses the barricade simulation by dt seconds.
     * Evaluates zombies clawing the window and breaks planks accordingly.
     */
    fun tick(
        state: Tier1WoodSnapshot,
        dtSeconds: Float,
        zombiesClawing: Int
    ): Tier1WoodTickResult {
        // Validation & bounds clamping
        val dtSafe = if (dtSeconds.isFinite() && dtSeconds > 0f) dtSeconds.coerceAtMost(0.100f) else 0f
        val maxPlanksSafe = state.maxPlanks.coerceAtLeast(1)
        val currentPlanksSafe = state.currentPlanks.coerceIn(0, maxPlanksSafe)
        
        // Graceful degradation for extreme load
        val safeZombies = zombiesClawing.coerceIn(0, MAX_ZOMBIE_CLAWING_LIMIT)
        
        var newPlanks = currentPlanksSafe
        var newRebuildTimer = state.rebuildCooldownTimer
        var planksBrokenThisTick = 0

        // If zombies are clawing and there are planks to break
        if (safeZombies > 0 && newPlanks > 0) {
            newRebuildTimer += dtSafe * safeZombies
            
            // Break planks if timer exceeds interval
            while (newRebuildTimer >= PLANK_BREAK_INTERVAL && newPlanks > 0) {
                newPlanks--
                planksBrokenThisTick++
                newRebuildTimer -= PLANK_BREAK_INTERVAL
            }
            
            // Reset timer if all planks broken
            if (newPlanks == 0) {
                newRebuildTimer = 0f
            }
        } else {
            // Decay timer if no zombies are clawing
            newRebuildTimer = (newRebuildTimer - dtSafe).coerceAtLeast(0f)
        }
        
        val newBreached = newPlanks == 0
        
        val newChecksum = computeChecksum(
            state.windowId,
            newPlanks,
            maxPlanksSafe,
            newBreached,
            newRebuildTimer
        )
        
        return Tier1WoodTickResult(
            nextState = Tier1WoodSnapshot(
                windowId = state.windowId,
                currentPlanks = newPlanks,
                maxPlanks = maxPlanksSafe,
                isBreached = newBreached,
                rebuildCooldownTimer = newRebuildTimer,
                checksum = newChecksum
            ),
            planksBrokenThisTick = planksBrokenThisTick
        )
    }
}


fun evaluateWindowBarricadeTier1WoodLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "WindowBarricadeTier1Wood") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
