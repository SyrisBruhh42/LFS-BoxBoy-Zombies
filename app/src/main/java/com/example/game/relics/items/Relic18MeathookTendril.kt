package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.abs
import kotlin.math.hypot

@Keep
data class MeathookTendrilConfig(
    val baseChanceParam: Float = 0.25f,
    val pullRadiusPx: Float = 150f,
    val pullSpeedPxPerSec: Float = 300f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "baseChanceParam" to baseChanceParam,
        "pullRadiusPx" to pullRadiusPx,
        "pullSpeedPxPerSec" to pullSpeedPxPerSec
    )

    companion object {
        fun fromMap(map: Map<String, Any>): MeathookTendrilConfig {
            return MeathookTendrilConfig(
                baseChanceParam = (map["baseChanceParam"] as? Number)?.toFloat() ?: 0.25f,
                pullRadiusPx = (map["pullRadiusPx"] as? Number)?.toFloat() ?: 150f,
                pullSpeedPxPerSec = (map["pullSpeedPxPerSec"] as? Number)?.toFloat() ?: 300f
            )
        }
    }
}

@Keep
data class MeathookTendrilState(
    val stackCount: Int,
    val seed: Long
) {
    init {
        require(stackCount >= 1) { "MeathookTendrilState requires stackCount >= 1" }
    }

    val canBePurchasedAtVending: Boolean get() = false

    val checksum: Long
        get() {
            var hash = -2128831035146727633L // FNV-1a offset basis
            hash = (hash xor stackCount.toLong()) * 1099511628211L
            hash = (hash xor seed) * 1099511628211L
            return hash
        }

    fun toMap(): Map<String, Any> = mapOf(
        "stackCount" to stackCount,
        "seed" to seed,
        "canBePurchasedAtVending" to canBePurchasedAtVending
    )

    companion object {
        fun fromMap(map: Map<String, Any>): MeathookTendrilState {
            val count = (map["stackCount"] as? Number)?.toInt() ?: 1
            val s = (map["seed"] as? Number)?.toLong() ?: 0L
            return MeathookTendrilState(maxOf(1, count), s)
        }
    }
}

class MeathookTendrilResult(
    val newState: MeathookTendrilState,
    val triggered: Boolean,
    val pulledIndices: IntArray, // Array of indices mapping to the inputs
    val pullDx: FloatArray,
    val pullDy: FloatArray,
    val numPulled: Int
)

class MeathookTendrilEngine(maxCapacity: Int = 1024) {
    
    private val outIndices = IntArray(maxCapacity)
    private val outDx = FloatArray(maxCapacity)
    private val outDy = FloatArray(maxCapacity)

    // A helper array to sort and select the closest targets
    private val distances = FloatArray(maxCapacity)
    private val sortedIndices = IntArray(maxCapacity)
    
    /**
     * Processes a hit event and calculates tendril pulls.
     * dtSeconds: clamped to [0.001, 0.100]
     * hitX, hitY: The epicenter of the meathook trigger
     * entityX, entityY: Arrays of candidate entity coordinates
     * entityCount: The number of active entities in the arrays
     */
    fun processHit(
        config: MeathookTendrilConfig,
        state: MeathookTendrilState,
        dtSeconds: Float,
        hitX: Float,
        hitY: Float,
        entityX: FloatArray,
        entityY: FloatArray,
        entityCount: Int
    ): MeathookTendrilResult {
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)
        
        // Advance seed via LCG
        val nextSeed = (state.seed * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        val rngFloat = (nextSeed ushr 24).toFloat() / (1 shl 24).toFloat()
        
        val stack = state.stackCount
        val chance = 1f - (1f / (1f + config.baseChanceParam * stack))
        
        if (rngFloat > chance) {
            return MeathookTendrilResult(
                newState = state.copy(seed = nextSeed),
                triggered = false,
                pulledIndices = outIndices,
                pullDx = outDx,
                pullDy = outDy,
                numPulled = 0
            )
        }

        val maxTargets = stack * 5
        var validCount = 0

        for (i in 0 until entityCount) {
            val ex = entityX[i]
            val ey = entityY[i]
            
            if (!ex.isFinite() || !ey.isFinite()) continue

            val dx = hitX - ex
            val dy = hitY - ey
            val dist = hypot(dx, dy)
            
            if (dist > 0.001f && dist <= config.pullRadiusPx) {
                distances[validCount] = dist
                sortedIndices[validCount] = i
                validCount++
            }
        }
        
        // In-place sort (insertion sort is fine for small N, but we'll use a simple selection sort for safety without allocs)
        for (i in 0 until validCount - 1) {
            var minIdx = i
            for (j in i + 1 until validCount) {
                if (distances[j] < distances[minIdx]) {
                    minIdx = j
                }
            }
            if (minIdx != i) {
                val tmpDist = distances[i]
                distances[i] = distances[minIdx]
                distances[minIdx] = tmpDist
                
                val tmpIdx = sortedIndices[i]
                sortedIndices[i] = sortedIndices[minIdx]
                sortedIndices[minIdx] = tmpIdx
            }
        }
        
        val numPulled = minOf(validCount, maxTargets)
        val pullDist = config.pullSpeedPxPerSec * dt
        
        for (i in 0 until numPulled) {
            val idx = sortedIndices[i]
            val dist = distances[i]
            val ex = entityX[idx]
            val ey = entityY[idx]
            
            val normDx = (hitX - ex) / dist
            val normDy = (hitY - ey) / dist
            
            // Do not pull past the epicenter
            val moveDist = minOf(pullDist, dist)
            
            outIndices[i] = idx
            outDx[i] = normDx * moveDist
            outDy[i] = normDy * moveDist
        }
        
        return MeathookTendrilResult(
            newState = state.copy(seed = nextSeed),
            triggered = true,
            pulledIndices = outIndices,
            pullDx = outDx,
            pullDy = outDy,
            numPulled = numPulled
        )
    }
}


fun evaluateRelic18MeathookTendrilLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic18MeathookTendril") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
