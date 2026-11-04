package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.abs

/**
 * Mutable state snapshot for Unstable Tesla Coil.
 * Uses @Keep and toMap/fromMap to prevent R8 minification stripping
 * and ensure compatibility with BoxBoysNetProtocol-v3.
 */
@Keep
data class TeslaCoilState(
    var accumulatedTime: Float = 0f,
    var seed: Long = 123456789L,
    var checksum: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "accumulatedTime" to accumulatedTime,
        "seed" to seed,
        "checksum" to checksum
    )

    companion object {
        fun fromMap(map: Map<String, Any>): TeslaCoilState {
            return TeslaCoilState(
                accumulatedTime = (map["accumulatedTime"] as? Number)?.toFloat() ?: 0f,
                seed = (map["seed"] as? Number)?.toLong() ?: 123456789L,
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/**
 * Core Algorithmic Engine for Unstable Tesla Coil.
 * Enforces zero per-frame heap churn.
 */
class Relic08UnstableTeslaCoilEngine {
    companion object {
        const val CAN_BE_PURCHASED_AT_VENDING = false
        const val TICK_INTERVAL_SEC = 0.5f
        const val MAX_RADIUS = 300f
        
        fun calculateTargets(stackCount: Int): Int {
            return if (stackCount < 1) 0 else 1 + stackCount * 2
        }

        fun computeChecksum(accumulatedTime: Float, seed: Long): Long {
            var hash = -2128831035142011030L
            hash = hash xor accumulatedTime.toRawBits().toLong()
            hash = hash * 1099511628211L
            hash = hash xor seed
            hash = hash * 1099511628211L
            return hash
        }
    }

    private val maxEntities = 2048
    private val validIndices = IntArray(maxEntities)
    
    // Output array storing the indices of the zombies hit during the current tick
    val hitResultIndices = IntArray(maxEntities)

    /**
     * Executes one tick of the Tesla Coil logic.
     * Returns the number of enemies zapped. Their indices can be read from [hitResultIndices].
     */
    fun tick(
        dtSeconds: Float,
        state: TeslaCoilState,
        stackCount: Int,
        playerX: Float,
        playerY: Float,
        zombieXs: FloatArray,
        zombieYs: FloatArray,
        zombieHps: FloatArray,
        zombieCount: Int
    ): Int {
        if (stackCount < 1) {
            state.accumulatedTime = 0f
            state.checksum = computeChecksum(state.accumulatedTime, state.seed)
            return 0
        }
        
        // Clamp delta-time for frame-rate independence and stability
        val dt = if (dtSeconds.isFinite()) dtSeconds.coerceIn(0.001f, 0.100f) else 0.001f
        state.accumulatedTime += dt
        var hitCount = 0
        
        if (state.seed == 0L) {
            state.seed = 123456789L
        }
        
        if (state.accumulatedTime >= TICK_INTERVAL_SEC) {
            state.accumulatedTime -= TICK_INTERVAL_SEC
            // Prevent spiral of death if dt was extremely large
            if (state.accumulatedTime >= TICK_INTERVAL_SEC) {
                state.accumulatedTime = 0f 
            }
            
            val numTargets = calculateTargets(stackCount)
            val maxRSq = MAX_RADIUS * MAX_RADIUS
            
            var validCount = 0
            val count = if (zombieCount > maxEntities) maxEntities else zombieCount
            
            // Spatial validation
            for (i in 0 until count) {
                if (zombieHps[i] > 0f) {
                    val zx = zombieXs[i]
                    val zy = zombieYs[i]
                    if (zx.isFinite() && zy.isFinite() && playerX.isFinite() && playerY.isFinite()) {
                        val dx = zx - playerX
                        val dy = zy - playerY
                        val distSq = dx * dx + dy * dy
                        if (distSq <= maxRSq) {
                            validIndices[validCount] = i
                            validCount++
                        }
                    }
                }
            }
            
            // Deterministic seeded random target selection
            if (validCount > 0) {
                val lcgA = 1664525L
                val lcgC = 1013904223L
                
                if (validCount <= numTargets) {
                    // Zap all valid targets
                    for (i in 0 until validCount) {
                        hitResultIndices[hitCount++] = validIndices[i]
                    }
                    state.seed = (state.seed * lcgA + lcgC)
                } else {
                    // Fisher-Yates partial shuffle to pick 'numTargets' randomly
                    for (i in 0 until numTargets) {
                        state.seed = (state.seed * lcgA + lcgC)
                        val r = abs((state.seed ushr 16).toInt())
                        val swapIdx = i + (r % (validCount - i))
                        
                        val temp = validIndices[i]
                        validIndices[i] = validIndices[swapIdx]
                        validIndices[swapIdx] = temp
                        
                        hitResultIndices[hitCount++] = validIndices[i]
                    }
                }
            }
        }
        
        state.checksum = computeChecksum(state.accumulatedTime, state.seed)
        return hitCount
    }
}


fun evaluateRelic08UnstableTeslaCoilLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic08UnstableTeslaCoil") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
