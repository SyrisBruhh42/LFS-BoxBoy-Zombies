package com.example.game.combat.modules

enum class RelicTier {
    NONE,
    TIER_25,
    TIER_50,
    TIER_100
}

class BoxheadComboStreakTracker {
    var currentCombo: Int = 0
        private set

    var timeRemainingMs: Long = 0
        private set

    fun onEnemyKilled(): RelicTier {
        currentCombo++
        timeRemainingMs = getMaxTimeForCombo(currentCombo)

        return when (currentCombo) {
            25 -> RelicTier.TIER_25
            50 -> RelicTier.TIER_50
            100 -> RelicTier.TIER_100
            else -> RelicTier.NONE
        }
    }

    fun getMaxTimeForCombo(combo: Int): Long {
        // Starts at 3000ms for combo=1, decreases by 50ms per additional combo kill, minimum 500ms
        val baseTime = 3000L
        val decay = (combo - 1) * 50L
        return (baseTime - decay).coerceAtLeast(500L)
    }

    fun getCurrencyMultiplier(): Float {
        // Multiplier from 1.0x to 3.0x
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
    }
}
