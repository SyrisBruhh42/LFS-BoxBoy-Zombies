package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.sqrt

@Keep
data class PaulsGoatHoofConfig(
    val speedMultiplierPerStack: Float = 0.14f,
    val canBePurchasedAtVending: Boolean = false, // Invariant: must be false
    val sectorWorldSizePx: Float = 2400f,
    val dustParticleBaseChance: Float = 0.05f
) {
    init {
        require(!canBePurchasedAtVending) { "Paul's Goat Hoof is a drop-only relic and cannot be purchased at vending." }
    }

    fun toMap(): Map<String, Any> = mapOf(
        "speedMultiplierPerStack" to speedMultiplierPerStack,
        "canBePurchasedAtVending" to canBePurchasedAtVending,
        "sectorWorldSizePx" to sectorWorldSizePx,
        "dustParticleBaseChance" to dustParticleBaseChance
    )

    companion object {
        fun fromMap(map: Map<String, Any>): PaulsGoatHoofConfig {
            return PaulsGoatHoofConfig(
                speedMultiplierPerStack = (map["speedMultiplierPerStack"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0.14f,
                canBePurchasedAtVending = map["canBePurchasedAtVending"] as? Boolean ?: false,
                sectorWorldSizePx = (map["sectorWorldSizePx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 2400f,
                dustParticleBaseChance = (map["dustParticleBaseChance"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0.05f
            )
        }
    }
}

@Keep
data class PaulsGoatHoofState(
    var stackCount: Int = 1,
    var lcgSeed: Long = 123456789L
) {
    init {
        if (stackCount < 1) {
            stackCount = 1
        }
    }

    fun toMap(): Map<String, Any> = mapOf(
        "stackCount" to stackCount,
        "lcgSeed" to lcgSeed
    )

    fun calculateChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor stackCount.toLong()) * 16777619L
        hash = (hash xor lcgSeed) * 16777619L
        return hash
    }

    // Deterministic pseudo-random number generator for seeded variation
    fun nextRandomFloat(): Float {
        lcgSeed = (lcgSeed * 25214903917L + 11L) and ((1L shl 48) - 1)
        return (lcgSeed ushr 24).toFloat() / (1 shl 24).toFloat()
    }

    companion object {
        fun fromMap(map: Map<String, Any>): PaulsGoatHoofState {
            val count = (map["stackCount"] as? Number)?.toInt() ?: 1
            val seed = (map["lcgSeed"] as? Number)?.toLong() ?: 123456789L
            return PaulsGoatHoofState(
                stackCount = if (count < 1) 1 else count,
                lcgSeed = seed
            )
        }
    }
}

@Keep
data class PaulsGoatHoofMovementResult(
    val newX: Float,
    val newY: Float,
    val actualSpeedMultiplier: Float,
    val generatedDustParticles: Int
)

object Relic03PaulsGoatHoofEngine {

    /**
     * Applies Goat Hoof movement logic for a single entity, including +14% speed per stack,
     * dtSeconds stepping, spatial bounds validation against sector sizes,
     * and LCG deterministic variation for particle effect triggers.
     */
    fun computeMovement(
        dtSeconds: Float,
        config: PaulsGoatHoofConfig,
        state: PaulsGoatHoofState,
        currentX: Float,
        currentY: Float,
        inputDx: Float,
        inputDy: Float,
        baseSpeedPxPerSec: Float
    ): PaulsGoatHoofMovementResult {
        // Guard against NaN/Infinity gracefully
        if (!dtSeconds.isFinite() || !currentX.isFinite() || !currentY.isFinite() || 
            !inputDx.isFinite() || !inputDy.isFinite() || !baseSpeedPxPerSec.isFinite()) {
            return PaulsGoatHoofMovementResult(
                newX = if (currentX.isFinite()) currentX else 0f,
                newY = if (currentY.isFinite()) currentY else 0f,
                actualSpeedMultiplier = 1.0f,
                generatedDustParticles = 0
            )
        }

        // Clamp dtSeconds to valid range (frame-rate-independent step)
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)

        // Validate state stack count
        if (state.stackCount < 1) {
            state.stackCount = 1
        }

        // Compute +14% speed multiplier per stack
        val multiplier = 1.0f + (state.stackCount * config.speedMultiplierPerStack)
        val finalSpeed = baseSpeedPxPerSec * multiplier

        // Normalize input vector (zero-allocation)
        var lenSq = inputDx * inputDx + inputDy * inputDy
        var dirX = 0f
        var dirY = 0f
        var isMoving = false
        
        if (lenSq > 0.0001f) {
            val len = sqrt(lenSq.toDouble()).toFloat()
            if (len.isFinite() && len > 0f) {
                dirX = inputDx / len
                dirY = inputDy / len
                isMoving = true
            }
        }

        // Calculate new position using scalar math
        var nextX = currentX + (dirX * finalSpeed * dt)
        var nextY = currentY + (dirY * finalSpeed * dt)

        // Spatial / Topological Validation: clamp to orthogonal world bounds
        nextX = nextX.coerceIn(0f, config.sectorWorldSizePx)
        nextY = nextY.coerceIn(0f, config.sectorWorldSizePx)

        // Deterministic seeded variation for visual kiting feedback (dust particles)
        var dustParticles = 0
        if (isMoving) {
            // Higher stack counts gently increase the chance of kicking up dust
            val chance = config.dustParticleBaseChance + (state.stackCount * 0.01f)
            val roll = state.nextRandomFloat()
            if (roll < chance) {
                // Generate 1 to 3 particles deterministically based on stack count
                dustParticles = 1 + (state.nextRandomFloat() * 3).toInt()
            }
        }

        return PaulsGoatHoofMovementResult(
            newX = nextX,
            newY = nextY,
            actualSpeedMultiplier = multiplier,
            generatedDustParticles = dustParticles
        )
    }

    /**
     * Batch process movement for multiple peers (up to 4 co-op peers or 250+ active zombies),
     * enforcing zero per-frame heap churn via pre-allocated primitive arrays.
     */
    fun processBatchMovement(
        dtSeconds: Float,
        config: PaulsGoatHoofConfig,
        states: Array<PaulsGoatHoofState>,
        currentXs: FloatArray,
        currentYs: FloatArray,
        inputDxs: FloatArray,
        inputDys: FloatArray,
        baseSpeedPxPerSecs: FloatArray,
        outNewXs: FloatArray,
        outNewYs: FloatArray,
        outActualSpeedMultipliers: FloatArray,
        outGeneratedDustParticles: IntArray,
        count: Int
    ) {
        if (!dtSeconds.isFinite()) return
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)

        for (i in 0 until count) {
            val state = states[i]
            val cx = currentXs[i]
            val cy = currentYs[i]
            val idx = inputDxs[i]
            val idy = inputDys[i]
            val baseSpeed = baseSpeedPxPerSecs[i]

            // Graceful degradation against NaN/Infinity
            if (!cx.isFinite() || !cy.isFinite() || !idx.isFinite() || !idy.isFinite() || !baseSpeed.isFinite()) {
                outNewXs[i] = if (cx.isFinite()) cx else 0f
                outNewYs[i] = if (cy.isFinite()) cy else 0f
                outActualSpeedMultipliers[i] = 1.0f
                outGeneratedDustParticles[i] = 0
                continue
            }

            if (state.stackCount < 1) {
                state.stackCount = 1
            }

            val multiplier = 1.0f + (state.stackCount * config.speedMultiplierPerStack)
            val finalSpeed = baseSpeed * multiplier

            var lenSq = idx * idx + idy * idy
            var dirX = 0f
            var dirY = 0f
            var isMoving = false
            
            if (lenSq > 0.0001f) {
                val len = sqrt(lenSq.toDouble()).toFloat()
                if (len.isFinite() && len > 0f) {
                    dirX = idx / len
                    dirY = idy / len
                    isMoving = true
                }
            }

            var nextX = cx + (dirX * finalSpeed * dt)
            var nextY = cy + (dirY * finalSpeed * dt)

            nextX = nextX.coerceIn(0f, config.sectorWorldSizePx)
            nextY = nextY.coerceIn(0f, config.sectorWorldSizePx)

            outNewXs[i] = nextX
            outNewYs[i] = nextY
            outActualSpeedMultipliers[i] = multiplier

            var dustParticles = 0
            if (isMoving) {
                val chance = config.dustParticleBaseChance + (state.stackCount * 0.01f)
                val roll = state.nextRandomFloat()
                if (roll < chance) {
                    dustParticles = 1 + (state.nextRandomFloat() * 3).toInt()
                }
            }
            outGeneratedDustParticles[i] = dustParticles
        }
    }

    /**
     * Utility to safely add stacks ensuring constraints.
     */
    fun addStacks(state: PaulsGoatHoofState, amount: Int) {
        if (amount > 0) {
            state.stackCount += amount
        }
    }
}


fun evaluateRelic03PaulsGoatHoofLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic03PaulsGoatHoof") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
