package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep

@Keep
data class ShieldStats(
    val capacity: Int,
    val rechargeRate: Int,
    val rechargeDelay: Float,
    val gimmickValue: Int = 0 // e.g., Nova blast damage or Amp shot damage
) {
    fun toMap(): Map<String, Any> = mapOf(
        "capacity" to capacity,
        "rechargeRate" to rechargeRate,
        "rechargeDelay" to rechargeDelay,
        "gimmickValue" to gimmickValue
    )

    fun computeChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor capacity.toLong()) * 16777619L
        hash = (hash xor rechargeRate.toLong()) * 16777619L
        hash = (hash xor rechargeDelay.toBits().toLong()) * 16777619L
        hash = (hash xor gimmickValue.toLong()) * 16777619L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): ShieldStats {
            return ShieldStats(
                capacity = (map["capacity"] as? Number)?.toInt() ?: 0,
                rechargeRate = (map["rechargeRate"] as? Number)?.toInt() ?: 0,
                rechargeDelay = (map["rechargeDelay"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                gimmickValue = (map["gimmickValue"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

@Keep
data class ShieldSwapComparisonResult(
    val capacityDelta: Int,
    val rechargeRateDelta: Int,
    val rechargeDelayDelta: Float,
    val gimmickDelta: Int,
    val isUpgrade: Boolean,
    var presentationPulse: Float = 0f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "capacityDelta" to capacityDelta,
        "rechargeRateDelta" to rechargeRateDelta,
        "rechargeDelayDelta" to rechargeDelayDelta,
        "gimmickDelta" to gimmickDelta,
        "isUpgrade" to isUpgrade,
        "presentationPulse" to presentationPulse
    )

    fun computeChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor capacityDelta.toLong()) * 16777619L
        hash = (hash xor rechargeRateDelta.toLong()) * 16777619L
        hash = (hash xor rechargeDelayDelta.toBits().toLong()) * 16777619L
        hash = (hash xor gimmickDelta.toLong()) * 16777619L
        hash = (hash xor (if (isUpgrade) 1L else 0L)) * 16777619L
        hash = (hash xor presentationPulse.toBits().toLong()) * 16777619L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): ShieldSwapComparisonResult {
            return ShieldSwapComparisonResult(
                capacityDelta = (map["capacityDelta"] as? Number)?.toInt() ?: 0,
                rechargeRateDelta = (map["rechargeRateDelta"] as? Number)?.toInt() ?: 0,
                rechargeDelayDelta = (map["rechargeDelayDelta"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                gimmickDelta = (map["gimmickDelta"] as? Number)?.toInt() ?: 0,
                isUpgrade = map["isUpgrade"] as? Boolean ?: false,
                presentationPulse = (map["presentationPulse"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            )
        }
    }
}

object SwapOnSpotShieldEvaluator {
    const val MAX_INTERACTION_RADIUS = 150f

    /**
     * Evaluates a candidate shield against the currently equipped shield if within interaction radius.
     * Incorporates a deterministic seed for randomized visual variance (e.g. jittering the gimmick delta slightly).
     */
    fun evaluateSwap(
        equipped: ShieldStats,
        candidate: ShieldStats,
        playerX: Float,
        playerY: Float,
        candidateX: Float,
        candidateY: Float,
        seed: Long = 0L
    ): ShieldSwapComparisonResult? {
        val px = if (playerX.isFinite()) playerX else 0f
        val py = if (playerY.isFinite()) playerY else 0f
        val cx = if (candidateX.isFinite()) candidateX else 0f
        val cy = if (candidateY.isFinite()) candidateY else 0f

        val dx = px - cx
        val dy = py - cy
        val distSq = dx * dx + dy * dy

        if (distSq > MAX_INTERACTION_RADIUS * MAX_INTERACTION_RADIUS) {
            return null
        }

        return computeDeltas(equipped, candidate, seed)
    }

    /**
     * Core deterministic comparison logic.
     */
    fun computeDeltas(equipped: ShieldStats, candidate: ShieldStats, seed: Long = 0L): ShieldSwapComparisonResult {
        val capDelta = candidate.capacity - equipped.capacity
        val rateDelta = candidate.rechargeRate - equipped.rechargeRate
        val delayDelta = candidate.rechargeDelay - equipped.rechargeDelay

        val baseGimmickDelta = candidate.gimmickValue - equipped.gimmickValue

        val pseudoRandomJitter = if (seed != 0L) {
            val hash = (seed * 25214903917L + 11L) and ((1L shl 48) - 1)
            ((hash ushr 32) % 5).toInt() // 0 to 4 jitter for rendering
        } else 0

        val gimmickDelta = baseGimmickDelta + if (baseGimmickDelta != 0) pseudoRandomJitter else 0

        var upgrades = 0
        var downgrades = 0

        if (capDelta > 0) upgrades++ else if (capDelta < 0) downgrades++
        if (rateDelta > 0) upgrades++ else if (rateDelta < 0) downgrades++
        if (delayDelta < 0f) upgrades++ else if (delayDelta > 0f) downgrades++ // Lower delay is better
        if (gimmickDelta > 0) upgrades++ else if (gimmickDelta < 0) downgrades++

        val isUpgrade = upgrades > downgrades || (upgrades == downgrades && capDelta > 0)

        return ShieldSwapComparisonResult(
            capacityDelta = capDelta,
            rechargeRateDelta = rateDelta,
            rechargeDelayDelta = delayDelta,
            gimmickDelta = gimmickDelta,
            isUpgrade = isUpgrade,
            presentationPulse = 0f
        )
    }

    /**
     * Frame-rate independent delta-time stepping for the presentation pulse of a comparison card.
     */
    fun stepPresentation(
        result: ShieldSwapComparisonResult,
        dtSeconds: Float
    ) {
        val safeDt = if (dtSeconds.isNaN() || dtSeconds.isInfinite()) {
            0.001f
        } else {
            dtSeconds.coerceIn(0.001f, 0.100f)
        }

        val increment = if (result.isUpgrade) 10f * safeDt else 5f * safeDt
        var newPulse = result.presentationPulse + increment

        if (!newPulse.isFinite()) {
            newPulse = 0f
        }

        result.presentationPulse = newPulse
    }

    /**
     * Processes a large collection of candidate shields, validating maximum capacity bounds
     * for graceful degradation under stress.
     * Returns the count of valid evaluated targets in range.
     */
    fun evaluateBatch(
        equipped: ShieldStats,
        playerX: Float,
        playerY: Float,
        candidateStats: Array<ShieldStats>,
        candidateX: FloatArray,
        candidateY: FloatArray,
        seeds: LongArray,
        count: Int,
        outResults: Array<ShieldSwapComparisonResult?>
    ): Int {
        var validCount = 0
        val maxIter = minOf(count, candidateStats.size, candidateX.size, candidateY.size, seeds.size, outResults.size)

        for (i in 0 until maxIter) {
            val cand = candidateStats[i]
            val cx = candidateX[i]
            val cy = candidateY[i]
            val seed = seeds[i]

            val result = evaluateSwap(equipped, cand, playerX, playerY, cx, cy, seed)
            outResults[i] = result // Might be null if out of range
            if (result != null) {
                validCount++
            }
        }

        return validCount
    }
}
