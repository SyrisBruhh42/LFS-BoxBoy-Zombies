package com.example.game.combat.modules

import com.example.game.contracts.Fnv1a64Hasher

enum class RelicTier {
    NONE,
    TIER_25,
    TIER_50,
    TIER_100
}

data class ComboStreakSnapshot(
    val currentCombo: Int,
    val peakCombo: Int,
    val timeRemainingMs: Long,
    val currencyMultiplier: Float,
    val lastUnlockedTier: RelicTier,
    val checksum: Long
)

class BoxheadComboStreakTracker {
    var currentCombo: Int = 0
        private set

    var peakCombo: Int = 0
        private set

    var timeRemainingMs: Long = 0
        private set

    var lastUnlockedTier: RelicTier = RelicTier.NONE
        private set

    fun onEnemyKilled(): RelicTier {
        currentCombo++
        if (currentCombo > peakCombo) {
            peakCombo = currentCombo
        }
        timeRemainingMs = getMaxTimeForCombo(currentCombo)

        val tier = when (currentCombo) {
            25 -> RelicTier.TIER_25
            50 -> RelicTier.TIER_50
            100 -> RelicTier.TIER_100
            else -> RelicTier.NONE
        }
        if (tier != RelicTier.NONE) {
            lastUnlockedTier = tier
        }
        return tier
    }

    fun onMultiKillBatch(kills: Int): List<RelicTier> {
        if (kills <= 0) return emptyList()
        val unlocked = ArrayList<RelicTier>()
        repeat(kills) {
            val tier = onEnemyKilled()
            if (tier != RelicTier.NONE) {
                unlocked.add(tier)
            }
        }
        return unlocked
    }

    fun getMaxTimeForCombo(combo: Int): Long {
        val baseTime = 3000L
        val decay = (combo - 1) * 50L
        return (baseTime - decay).coerceAtLeast(500L)
    }

    fun getCurrencyMultiplier(): Float {
        return (1.0f + (currentCombo * 0.05f)).coerceIn(1.0f, 3.0f)
    }

    fun update(deltaMs: Long) {
        if (currentCombo > 0) {
            timeRemainingMs -= deltaMs
            if (timeRemainingMs <= 0) {
                timeRemainingMs = 0
                currentCombo = 0
            }
        }
    }

    fun reset() {
        currentCombo = 0
        timeRemainingMs = 0
        lastUnlockedTier = RelicTier.NONE
    }

    fun snapshot(): ComboStreakSnapshot {
        var h = Fnv1a64Hasher.initial()
        h = Fnv1a64Hasher.mixInt(h, currentCombo)
        h = Fnv1a64Hasher.mixInt(h, peakCombo)
        h = Fnv1a64Hasher.mixLong(h, timeRemainingMs)
        h = Fnv1a64Hasher.mixString(h, lastUnlockedTier.name)
        return ComboStreakSnapshot(
            currentCombo = currentCombo,
            peakCombo = peakCombo,
            timeRemainingMs = timeRemainingMs,
            currencyMultiplier = getCurrencyMultiplier(),
            lastUnlockedTier = lastUnlockedTier,
            checksum = h
        )
    }
}


fun evaluateBoxheadComboStreakTrackerLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "BoxheadComboStreakTracker") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
