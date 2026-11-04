package com.example.game.economy.vendors

import androidx.annotation.Keep
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Keep
data class WaveClearBonusConfig(
    val waveNumber: Int,
    val totalBarricades: Int,
    val intactBarricades: Int,
    val expectedClearTimeSeconds: Float,
    val actualClearTimeSeconds: Float,
    val coopPeerCount: Int,
    val noDownsThisWave: Boolean
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "waveNumber" to waveNumber,
            "totalBarricades" to totalBarricades,
            "intactBarricades" to intactBarricades,
            "expectedClearTimeSeconds" to expectedClearTimeSeconds,
            "actualClearTimeSeconds" to actualClearTimeSeconds,
            "coopPeerCount" to coopPeerCount,
            "noDownsThisWave" to noDownsThisWave
        )
    }

    fun computeChecksum(): Long {
        var hash = -3750763034362895579L // 64-bit FNV offset basis
        hash = (hash xor waveNumber.toLong()) * 1099511628211L // FNV prime
        hash = (hash xor totalBarricades.toLong()) * 1099511628211L
        hash = (hash xor intactBarricades.toLong()) * 1099511628211L
        hash = (hash xor expectedClearTimeSeconds.toRawBits().toLong()) * 1099511628211L
        hash = (hash xor actualClearTimeSeconds.toRawBits().toLong()) * 1099511628211L
        hash = (hash xor coopPeerCount.toLong()) * 1099511628211L
        hash = (hash xor (if (noDownsThisWave) 1L else 0L)) * 1099511628211L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): WaveClearBonusConfig {
            return WaveClearBonusConfig(
                waveNumber = (map["waveNumber"] as? Number)?.toInt() ?: 1,
                totalBarricades = (map["totalBarricades"] as? Number)?.toInt() ?: 0,
                intactBarricades = (map["intactBarricades"] as? Number)?.toInt() ?: 0,
                expectedClearTimeSeconds = (map["expectedClearTimeSeconds"] as? Number)?.toFloat() ?: 60f,
                actualClearTimeSeconds = (map["actualClearTimeSeconds"] as? Number)?.toFloat() ?: 60f,
                coopPeerCount = (map["coopPeerCount"] as? Number)?.toInt() ?: 0,
                noDownsThisWave = map["noDownsThisWave"] as? Boolean ?: false
            )
        }
    }
}

@Keep
data class WaveClearBonusResult(
    val baseSurvivalBonus: Int,
    val barricadeBonus: Int,
    val speedClearBonus: Int,
    val noDownsBonus: Int,
    val coopMultiplier: Float,
    val totalBonusCurrency: Int,
    val checksum: Long
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "baseSurvivalBonus" to baseSurvivalBonus,
            "barricadeBonus" to barricadeBonus,
            "speedClearBonus" to speedClearBonus,
            "noDownsBonus" to noDownsBonus,
            "coopMultiplier" to coopMultiplier,
            "totalBonusCurrency" to totalBonusCurrency,
            "checksum" to checksum
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): WaveClearBonusResult {
            return WaveClearBonusResult(
                baseSurvivalBonus = (map["baseSurvivalBonus"] as? Number)?.toInt() ?: 0,
                barricadeBonus = (map["barricadeBonus"] as? Number)?.toInt() ?: 0,
                speedClearBonus = (map["speedClearBonus"] as? Number)?.toInt() ?: 0,
                noDownsBonus = (map["noDownsBonus"] as? Number)?.toInt() ?: 0,
                coopMultiplier = (map["coopMultiplier"] as? Number)?.toFloat() ?: 1.0f,
                totalBonusCurrency = (map["totalBonusCurrency"] as? Number)?.toInt() ?: 0,
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

object WaveClearCurrencyBonus {

    private const val BASE_WAVE_REWARD = 100
    private const val WAVE_MULTIPLIER = 10
    private const val MAX_BASE_REWARD = 2500

    private const val BARRICADE_BONUS_PER_INTACT = 25
    private const val MAX_BARRICADE_BONUS = 1000

    private const val MAX_SPEED_CLEAR_BONUS = 1500
    private const val NO_DOWNS_BONUS_FLAT = 250
    private const val COOP_MULTIPLIER_PER_PEER = 0.15f
    private const val MAX_COOP_MULTIPLIER = 1.45f

    fun calculateBonus(config: WaveClearBonusConfig): WaveClearBonusResult {
        // 1. Sanitize inputs and clamp bounds
        val wave = max(1, config.waveNumber)
        val totalBarr = max(0, config.totalBarricades)
        val intactBarr = min(totalBarr, max(0, config.intactBarricades))
        
        val expTime = if (config.expectedClearTimeSeconds.isFinite() && config.expectedClearTimeSeconds > 0f) config.expectedClearTimeSeconds else 60f
        val actTime = if (config.actualClearTimeSeconds.isFinite() && config.actualClearTimeSeconds > 0f) config.actualClearTimeSeconds else expTime
        
        val peers = min(3, max(0, config.coopPeerCount))

        // 2. Base Survival Bonus (Caps at Wave 241 to reach 2500 max)
        val baseBonus = min(MAX_BASE_REWARD, BASE_WAVE_REWARD + (wave - 1) * WAVE_MULTIPLIER)

        // 3. Untouchable Barricade Bonus
        val barricadeRatio = if (totalBarr > 0) intactBarr.toFloat() / totalBarr.toFloat() else 0f
        val rawBarricadeBonus = intactBarr * BARRICADE_BONUS_PER_INTACT
        val perfectBarricadeMultiplier = if (barricadeRatio >= 0.999f && totalBarr > 0) 2.0f else 1.0f
        val barricadeBonus = min(MAX_BARRICADE_BONUS, (rawBarricadeBonus * perfectBarricadeMultiplier).toInt())

        // 4. Speed Clear Bonus (Linear scaling based on how much faster actual is compared to expected)
        val timeRatio = if (actTime < expTime) (expTime - actTime) / expTime else 0f
        val clampedTimeRatio = max(0f, min(1f, timeRatio))
        val speedClearBonus = min(MAX_SPEED_CLEAR_BONUS, (MAX_SPEED_CLEAR_BONUS * clampedTimeRatio).toInt())

        // 5. No Downs Bonus
        val noDownsBonus = if (config.noDownsThisWave) NO_DOWNS_BONUS_FLAT else 0

        // 6. Co-op Multiplier
        val coopMult = min(MAX_COOP_MULTIPLIER, 1.0f + (peers * COOP_MULTIPLIER_PER_PEER))

        // 7. Total Calculation
        val totalPreMultiplier = baseBonus + barricadeBonus + speedClearBonus + noDownsBonus
        val totalBonusCurrency = (totalPreMultiplier * coopMult).roundToInt()

        val partialResult = WaveClearBonusResult(
            baseSurvivalBonus = baseBonus,
            barricadeBonus = barricadeBonus,
            speedClearBonus = speedClearBonus,
            noDownsBonus = noDownsBonus,
            coopMultiplier = coopMult,
            totalBonusCurrency = totalBonusCurrency,
            checksum = 0L
        )

        val checksum = computeResultChecksum(partialResult, config.computeChecksum())
        
        return partialResult.copy(checksum = checksum)
    }

    private fun computeResultChecksum(result: WaveClearBonusResult, configChecksum: Long): Long {
        var hash = configChecksum
        hash = (hash xor result.baseSurvivalBonus.toLong()) * 1099511628211L
        hash = (hash xor result.barricadeBonus.toLong()) * 1099511628211L
        hash = (hash xor result.speedClearBonus.toLong()) * 1099511628211L
        hash = (hash xor result.noDownsBonus.toLong()) * 1099511628211L
        hash = (hash xor result.coopMultiplier.toRawBits().toLong()) * 1099511628211L
        hash = (hash xor result.totalBonusCurrency.toLong()) * 1099511628211L
        return hash
    }
}
