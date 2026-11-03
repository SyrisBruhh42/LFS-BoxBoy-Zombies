package com.example.game.ai.director.modules

/**
 * Unified BoxBoys Track 2 (Local Open-Source AI Director):
 * Left 4 Dead / BO2 style AI intensity director scaling zombie spawn cadence
 * by squad HP/ammo telemetry.
 */
object DynamicHordePacingDirector {

    data class PacingMetrics(
        val intensityScore: Float, // 0.0 = completely relaxed, 1.0 = maximum panic
        val recommendedSpawnDelayMs: Long,
        val maxSimultaneousZombies: Int
    )

    /**
     * Analyzes squad health and ammo to determine the current intensity score
     * and recommend zombie spawn pacing.
     *
     * @param avgSquadHpPercent The average squad health (0.0 to 1.0)
     * @param avgSquadAmmoPercent The average squad ammo (0.0 to 1.0)
     * @param wave The current wave number (1+)
     * @param baseSpawnDelayMs The baseline spawn delay (e.g. 2000ms)
     * @param baseMaxZombies The baseline max zombies
     * @return PacingMetrics containing intensity and spawn recommendations
     */
    fun calculatePacing(
        avgSquadHpPercent: Float,
        avgSquadAmmoPercent: Float,
        wave: Int,
        baseSpawnDelayMs: Long = 2000L,
        baseMaxZombies: Int = 24
    ): PacingMetrics {
        val hpFactor = (1.0f - avgSquadHpPercent.coerceIn(0.01f, 1.0f))
        val ammoFactor = (1.0f - avgSquadAmmoPercent.coerceIn(0.01f, 1.0f))

        // Intensity is higher when HP is low or Ammo is low
        var rawIntensity = (hpFactor * 0.6f) + (ammoFactor * 0.4f)

        // Slight intensity bump per wave
        rawIntensity += (wave * 0.05f)

        val intensityScore = rawIntensity.coerceIn(0.0f, 1.0f)

        // When intensity is high, director relaxes pacing (gives players a break)
        // When intensity is low, director ramps up pacing (punishes doing well)
        val pacingMultiplier = if (intensityScore > 0.8f) {
            // Panic state! Slow down spawns to give them a chance
            2.5f
        } else if (intensityScore > 0.5f) {
            // Stressed state. Slight slowdown.
            1.5f
        } else if (intensityScore < 0.2f) {
            // Breezing through. Ramp up spawns.
            0.5f
        } else {
            // Normal pacing
            1.0f
        }

        val recommendedSpawnDelayMs = (baseSpawnDelayMs * pacingMultiplier).toLong().coerceAtLeast(100L)

        // Max zombies scales with wave, but drops if they are overwhelmed
        val waveMultiplier = 1.0f + (wave * 0.1f)
        val limitMultiplier = if (intensityScore > 0.85f) 0.6f else 1.0f
        val maxSimultaneousZombies = (baseMaxZombies * waveMultiplier * limitMultiplier).toInt().coerceAtLeast(1)

        return PacingMetrics(
            intensityScore = intensityScore,
            recommendedSpawnDelayMs = recommendedSpawnDelayMs,
            maxSimultaneousZombies = maxSimultaneousZombies
        )
    }
}
