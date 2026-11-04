package com.example.game.economy.vendors

import androidx.annotation.Keep
import kotlin.math.max

/**
 * Unified BoxBoys Track 4 (Single-Currency BO2 + Borderlands 2 Vending Economy)
 * Synchronized 180-second Item of the Day countdown and seed-based stock rotation.
 */
@Keep
data class VendingTimerState(
    val currentSeed: Int,
    val timeRemainingSeconds: Float
) {
    fun toMap(): Map<String, Any> = mapOf(
        "currentSeed" to currentSeed,
        "timeRemainingSeconds" to timeRemainingSeconds
    )

    companion object {
        fun fromMap(map: Map<String, Any>): VendingTimerState {
            val seed = (map["currentSeed"] as? Number)?.toInt() ?: 0
            val time = (map["timeRemainingSeconds"] as? Number)?.toFloat() ?: 0f
            return VendingTimerState(seed, time)
        }
    }
}

/**
 * Deterministic hash combining previous seed and current state using FNV-1a.
 */
fun computeNextSeed(prevSeed: Int): Int {
    var hash = -2128831035 // FNV offset basis
    val data = prevSeed xor (prevSeed ushr 16)
    
    // Hash 4 bytes of data
    hash = (hash xor (data and 0xFF)) * 16777619
    hash = (hash xor ((data ushr 8) and 0xFF)) * 16777619
    hash = (hash xor ((data ushr 16) and 0xFF)) * 16777619
    hash = (hash xor ((data ushr 24) and 0xFF)) * 16777619

    return hash
}

class VendingItemOfTheDayTimer(initialSeed: Int) {
    companion object {
        const val MAX_TIME_SECONDS = 180f
    }

    private var currentSeed = initialSeed
    private var timeRemainingSeconds = MAX_TIME_SECONDS

    /**
     * Steps the timer deterministically based on clamped dtSeconds.
     */
    fun step(dtSeconds: Float) {
        val clampedDt = if (dtSeconds.isNaN() || dtSeconds.isInfinite() || dtSeconds <= 0f) {
            0.001f
        } else if (dtSeconds > 0.100f) {
            0.100f
        } else {
            dtSeconds
        }

        timeRemainingSeconds -= clampedDt
        if (timeRemainingSeconds <= 0f) {
            currentSeed = computeNextSeed(currentSeed)
            timeRemainingSeconds = MAX_TIME_SECONDS
        }
    }

    fun getSnapshot(): VendingTimerState = VendingTimerState(currentSeed, max(0f, timeRemainingSeconds))

    fun restoreFromSnapshot(state: VendingTimerState) {
        currentSeed = state.currentSeed
        timeRemainingSeconds = if (state.timeRemainingSeconds.isNaN() || state.timeRemainingSeconds.isInfinite() || state.timeRemainingSeconds < 0f) {
            0f
        } else {
            state.timeRemainingSeconds
        }
    }
}
