package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.random.Random

/**
 * Track 6: Risk of Rain 2 Rare-Drop-Only Passive Skill Relics.
 * Relic 11: Lens-Maker's Glasses
 * Specification: +10% linear critical strike chance per stack (10 stacks = 100% crit).
 * Invariant: `canBePurchasedAtVending = false` (drop-only).
 */

@Keep
data class Relic11LensMakersGlassesConfig(
    val critChancePerStack: Float = 0.10f,
    val maxStacksForGuaranteedCrit: Int = 10,
    val canBePurchasedAtVending: Boolean = false
)

@Keep
data class Relic11LensMakersGlassesState(
    val stackCount: Int,
    val checksum: Long = 0L
) {
    companion object {
        fun computeChecksum(stackCount: Int): Long {
            var hash = 2166136261L
            // Avoid string allocation in hot path. Mix integer bits directly.
            var bits = stackCount.toLong()
            if (bits == 0L) {
                hash = hash xor 48L // '0'
                hash *= 16777619L
            } else {
                var temp = if (bits < 0) -bits else bits
                if (bits < 0) {
                    hash = hash xor 45L // '-'
                    hash *= 16777619L
                }
                var div = 1000000000L
                var started = false
                while (div > 0) {
                    val d = temp / div
                    if (d > 0 || started) {
                        hash = hash xor (d + 48L)
                        hash *= 16777619L
                        started = true
                        temp %= div
                    }
                    div /= 10
                }
            }
            return hash
        }

        fun fromMap(map: Map<String, Any>): Relic11LensMakersGlassesState {
            val stackCount = (map["stackCount"] as? Number)?.toInt() ?: 0
            val checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            return Relic11LensMakersGlassesState(stackCount, checksum)
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "stackCount" to stackCount,
            "checksum" to checksum
        )
    }
}

object Relic11LensMakersGlassesEngine {
    
    val config = Relic11LensMakersGlassesConfig()

    /**
     * Ensures dtSeconds is bounded within realistic simulation limits for frame-independent stepping.
     */
    fun clampDt(dtSeconds: Float): Float {
        if (!dtSeconds.isFinite()) return 0.001f
        return dtSeconds.coerceIn(0.001f, 0.100f)
    }
    
    /**
     * Retrieves the critical strike chance as a normalized float [0.0, 1.0].
     * 10 stacks guarantees 1.0f (100%).
     */
    fun getCriticalStrikeChance(state: Relic11LensMakersGlassesState): Float {
        val stacks = state.stackCount.coerceAtLeast(0)
        return (stacks * config.critChancePerStack).coerceIn(0.0f, 1.0f)
    }

    /**
     * Checks if the state passes deterministic checksum validation.
     */
    fun isStateValid(state: Relic11LensMakersGlassesState): Boolean {
        if (state.checksum == 0L) return true // Legacy / newly initialized
        return state.checksum == Relic11LensMakersGlassesState.computeChecksum(state.stackCount)
    }

    /**
     * Evaluates a single critical strike roll deterministically based on seed.
     */
    fun evaluateCriticalStrike(state: Relic11LensMakersGlassesState, baseChance: Float, seed: Long): Boolean {
        if (!isStateValid(state)) return false // Corruption guard
        if (!baseChance.isFinite()) return false
        
        val totalChance = (getCriticalStrikeChance(state) + baseChance).coerceIn(0.0f, 1.0f)
        if (totalChance >= 1.0f) return true
        if (totalChance <= 0.0f) return false
        
        // Inline basic LCG to avoid object allocation (kotlin.random.Random)
        var s = seed
        s = (s xor (s ushr 21))
        s = (s xor (s shl 35))
        s = (s xor (s ushr 4))
        s *= 2685821657736338717L
        val floatValue = (s ushr 40).toFloat() / 16777216f
        
        return floatValue <= totalChance
    }

    /**
     * Evaluates critical strikes in batch for zero-allocation performance under load.
     * Processes `count` entities, storing results in `outResults`.
     */
    fun batchEvaluateCriticalStrikes(
        state: Relic11LensMakersGlassesState,
        baseChances: FloatArray,
        seeds: LongArray,
        outResults: BooleanArray,
        count: Int
    ) {
        if (!isStateValid(state)) {
            // Failsafe zero-out
            for (i in 0 until count) outResults[i] = false
            return
        }

        val relicChance = getCriticalStrikeChance(state)

        for (i in 0 until count) {
            val bc = if (baseChances[i].isFinite()) baseChances[i] else 0.0f
            val totalChance = (relicChance + bc).coerceIn(0.0f, 1.0f)
            
            if (totalChance >= 1.0f) {
                outResults[i] = true
            } else if (totalChance <= 0.0f) {
                outResults[i] = false
            } else {
                // Inline Xorshift/LCG to avoid allocating Random instance in the loop
                var s = seeds[i]
                s = (s xor (s ushr 21))
                s = (s xor (s shl 35))
                s = (s xor (s ushr 4))
                s *= 2685821657736338717L
                val floatValue = (s ushr 40).toFloat() / 16777216f
                
                outResults[i] = floatValue <= totalChance
            }
        }
    }
}


fun evaluateRelic11LensMakersGlassesLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic11LensMakersGlasses") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
