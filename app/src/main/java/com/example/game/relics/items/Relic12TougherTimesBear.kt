package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.max

/**
 * [BoxBoys T6-12] RoR2 Passive Relic: Relic12TougherTimesBear
 * Hyperbolic block chance `1 - 1 / (1 + 0.15 * stacks)` to negate incoming hit.
 * Enforces drop-only invariant (canBePurchasedAtVending = false).
 */

@Keep
data class TougherTimesBearConfig(
    val baseBlockChanceScale: Float = 0.15f,
    val canBePurchasedAtVending: Boolean = false,
    val postBlockInvulnDurationSeconds: Float = 0.1f
) {
    init {
        require(baseBlockChanceScale.isFinite() && baseBlockChanceScale > 0f) { "baseBlockChanceScale must be finite positive" }
        require(!canBePurchasedAtVending) { "TougherTimesBear is drop-only and cannot be purchased at vending!" }
        require(postBlockInvulnDurationSeconds.isFinite() && postBlockInvulnDurationSeconds >= 0f) { "postBlockInvulnDurationSeconds must be finite and >= 0" }
    }

    fun toMap(): Map<String, Any> = mapOf(
        "baseBlockChanceScale" to baseBlockChanceScale,
        "canBePurchasedAtVending" to canBePurchasedAtVending,
        "postBlockInvulnDurationSeconds" to postBlockInvulnDurationSeconds
    )

    companion object {
        fun fromMap(map: Map<String, Any>): TougherTimesBearConfig {
            return TougherTimesBearConfig(
                baseBlockChanceScale = (map["baseBlockChanceScale"] as? Number)?.toFloat() ?: 0.15f,
                canBePurchasedAtVending = (map["canBePurchasedAtVending"] as? Boolean) ?: false,
                postBlockInvulnDurationSeconds = (map["postBlockInvulnDurationSeconds"] as? Number)?.toFloat() ?: 0.1f
            )
        }
    }
}

@Keep
data class TougherTimesBearState(
    val stackCount: Int,
    val invulnTimeRemaining: Float = 0f
) {
    init {
        require(stackCount >= 1) { "TougherTimesBear stackCount must be >= 1" }
        require(invulnTimeRemaining.isFinite() && invulnTimeRemaining >= 0f) { "invulnTimeRemaining must be finite and >= 0" }
    }

    fun toMap(): Map<String, Any> = mapOf(
        "stackCount" to stackCount,
        "invulnTimeRemaining" to invulnTimeRemaining,
        "checksum" to computeChecksum()
    )

    fun computeChecksum(): Int {
        var hash = -2128831035 // FNV offset basis
        hash = (hash xor stackCount) * 16777619 // FNV prime
        hash = (hash xor invulnTimeRemaining.toBits()) * 16777619
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): TougherTimesBearState {
            val state = TougherTimesBearState(
                stackCount = max(1, (map["stackCount"] as? Number)?.toInt() ?: 1),
                invulnTimeRemaining = max(0f, (map["invulnTimeRemaining"] as? Number)?.toFloat() ?: 0f)
            )
            return state
        }
    }
}

@Keep
data class TougherTimesBearTickResult(
    val newState: TougherTimesBearState,
    val hitsBlocked: Int,
    val damageTaken: Float,
    val nextSeed: Long
)

object TougherTimesBearEngine {

    /**
     * Core algorithmic engine for TougherTimesBear.
     * Evaluates a batch of incoming hits for hyperbolic chance-based damage negation.
     * Implements frame-rate-independent dt-stepping and spatial bounds validation.
     */
    fun processHits(
        dtSeconds: Float,
        state: TougherTimesBearState,
        config: TougherTimesBearConfig,
        randomSeed: Long,
        targetX: Float,
        targetY: Float,
        hitCount: Int,
        hitDamages: FloatArray,
        hitSourceXs: FloatArray,
        hitSourceYs: FloatArray
    ): TougherTimesBearTickResult {
        // Clamp delta-time
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)
        var currentInvuln = max(0f, state.invulnTimeRemaining - dt)

        val blockChance = 1f - (1f / (1f + config.baseBlockChanceScale * state.stackCount))
        
        var hitsBlocked = 0
        var totalDamageTaken = 0f
        var currentSeed = randomSeed

        // Process up to hitCount to gracefully handle extreme load
        val safeHitCount = hitCount.coerceIn(0, hitDamages.size)

        for (i in 0 until safeHitCount) {
            val dmg = hitDamages[i]
            if (!dmg.isFinite() || dmg <= 0f) continue
            
            // Manual bounds checking to avoid boxing primitive array items via getOrNull
            if (i >= hitSourceXs.size || i >= hitSourceYs.size) continue

            val sx = hitSourceXs[i]
            val sy = hitSourceYs[i]

            // Spatial/topological validation
            if (!sx.isFinite() || !sy.isFinite()) continue
            
            val dx = sx - targetX
            val dy = sy - targetY
            if (!dx.isFinite() || !dy.isFinite()) continue
            
            // Ignore attacks beyond sensible world chunk bounds (> 10000 units squared ~100M)
            val distSq = dx * dx + dy * dy
            if (distSq > 100_000_000f) continue

            if (currentInvuln > 0f) {
                hitsBlocked++
                continue
            }

            // Seeded deterministic LCG (java.util.Random equivalent)
            currentSeed = (currentSeed * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
            val randFloat = ((currentSeed ushr 24).toInt() and 0xFFFFFF) / 16777216f

            if (randFloat < blockChance) {
                hitsBlocked++
                currentInvuln = config.postBlockInvulnDurationSeconds
            } else {
                totalDamageTaken += dmg
            }
        }

        val newState = TougherTimesBearState(
            stackCount = state.stackCount,
            invulnTimeRemaining = currentInvuln
        )

        return TougherTimesBearTickResult(
            newState = newState,
            hitsBlocked = hitsBlocked,
            damageTaken = totalDamageTaken,
            nextSeed = currentSeed
        )
    }
}


fun evaluateRelic12TougherTimesBearLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic12TougherTimesBear") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
