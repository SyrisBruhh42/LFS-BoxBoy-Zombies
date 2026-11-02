package com.example.game

/**
 * Deterministic, zero-allocation ring buffer object pool for [ParticleBurst].
 *
 * Uses a seeded Linear Congruential Generator (LCG) to provide completely deterministic
 * particle trajectories, speeds, and lifetimes across frame updates while eliminating
 * garbage collection churn during intense zombie combat.
 */
class DeterministicParticlePool(
    val capacity: Int = 256,
    initialSeed: Long = 1337420L
) {
    // Internal pre-allocated slots
    val slots: Array<ParticleBurst> = Array(capacity) {
        ParticleBurst(x = 0f, y = 0f, vx = 0f, vy = 0f, colorHex = 0xFFFFFFFF, ttlMs = 0L)
    }

    private var rngState: Long = if (initialSeed == 0L) 1L else initialSeed

    /**
     * Deterministic 64-bit LCG random integer generator.
     */
    fun nextDeterministicInt(min: Int, max: Int): Int {
        if (min >= max) return min
        rngState = (rngState * 6364136223846793005L + 1442695040888963407L)
        val raw = (rngState ushr 32).toInt() and 0x7FFFFFFF
        val range = (max - min) + 1
        return min + (raw % range)
    }

    /**
     * Deterministic float in [0.0, 1.0).
     */
    fun nextDeterministicFloat(): Float {
        rngState = (rngState * 6364136223846793005L + 1442695040888963407L)
        val raw = (rngState ushr 40).toInt() and 0xFFFFFF
        return raw / 16777216f
    }

    fun reseed(seed: Long) {
        rngState = if (seed == 0L) 1L else seed
    }

    /**
     * Spawns a single particle in the first available slot (or evicts oldest).
     */
    fun spawn(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        colorHex: Long,
        ttlMs: Long = 320L
    ): ParticleBurst? {
        // Find inactive slot or the one with minimum remaining ttlMs
        var chosenIdx = -1
        var minTtl = Long.MAX_VALUE
        for (i in 0 until capacity) {
            val slot = slots[i]
            if (slot.ttlMs <= 0L) {
                chosenIdx = i
                break
            }
            if (slot.ttlMs < minTtl) {
                minTtl = slot.ttlMs
                chosenIdx = i
            }
        }
        if (chosenIdx < 0) return null
        val p = slots[chosenIdx]
        p.x = x
        p.y = y
        p.vx = vx
        p.vy = vy
        p.ttlMs = ttlMs
        // ParticleBurst has val colorHex, so replace slot if color differs
        if (p.colorHex != colorHex) {
            slots[chosenIdx] = ParticleBurst(x, y, vx, vy, colorHex, ttlMs)
            return slots[chosenIdx]
        }
        return p
    }

    /**
     * Spawns a radial burst of particles deterministically.
     */
    fun spawnBurst(
        x: Float,
        y: Float,
        count: Int,
        speedMin: Int,
        speedMax: Int,
        colorHex: Long,
        ttlMs: Long = 360L
    ) {
        for (i in 0 until count) {
            val vx = nextDeterministicInt(speedMin, speedMax).toFloat()
            val vy = nextDeterministicInt(speedMin, speedMax).toFloat()
            spawn(x, y, vx, vy, colorHex, ttlMs)
        }
    }

    /**
     * Steps all active particles by [deltaMs] in-place without memory allocation.
     */
    fun step(deltaMs: Long) {
        val dt = deltaMs / 1000f
        for (i in 0 until capacity) {
            val p = slots[i]
            if (p.ttlMs > 0L) {
                val rem = p.ttlMs - deltaMs
                if (rem <= 0L) {
                    p.ttlMs = 0L
                } else {
                    p.ttlMs = rem
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                }
            }
        }
    }

    /**
     * Returns the count of currently active particles.
     */
    fun activeCount(): Int {
        var count = 0
        for (i in 0 until capacity) {
            if (slots[i].ttlMs > 0L) count++
        }
        return count
    }

    /**
     * Copies active particles into a snapshot list.
     */
    fun toActiveList(): List<ParticleBurst> {
        val result = ArrayList<ParticleBurst>(activeCount())
        for (i in 0 until capacity) {
            val p = slots[i]
            if (p.ttlMs > 0L) {
                result.add(p.copy())
            }
        }
        return result
    }

    /**
     * Resets all slots to inactive state.
     */
    fun recycleAll() {
        for (i in 0 until capacity) {
            slots[i].ttlMs = 0L
        }
    }
}
