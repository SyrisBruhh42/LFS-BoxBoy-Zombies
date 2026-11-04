package com.example.game.ai.director.modules

import kotlin.math.abs

/**
 * Unified BoxBoys Track 2: Local Open-Source AI Director Module.
 * Procedural cryo-fog, neon rain, and ash-storm lighting modifier director.
 */
data class WeatherState(
    val biomeTag: String,
    val isRaining: Boolean,
    val isAshStorm: Boolean,
    val fogDensity: Float,
    val lightingModifierHex: Long
)

object BiomeWeatherAndFogDirector {

    fun determineWeatherAndFog(biomeTag: String, wave: Int, seed: Long): WeatherState {
        val hash = abs((seed xor wave.toLong()).hashCode())

        return when (biomeTag.uppercase()) {
            "CRYO_TUNDRA" -> {
                val fog = 0.5f + ((hash % 100) / 200f) // 0.5 to 1.0
                WeatherState(biomeTag, isRaining = false, isAshStorm = false, fogDensity = fog, lightingModifierHex = 0xFF88CCFF)
            }
            "NEON_DISTRICT" -> {
                val rain = (hash % 10) > 4 // 50% chance
                WeatherState(biomeTag, isRaining = rain, isAshStorm = false, fogDensity = 0.2f, lightingModifierHex = 0xFFFF00FF)
            }
            "VOLCANIC_WASTES" -> {
                val ash = (hash % 10) > 2 // 70% chance
                val fog = if (ash) 0.8f else 0.3f
                WeatherState(biomeTag, isRaining = false, isAshStorm = ash, fogDensity = fog, lightingModifierHex = 0xFFFF5500)
            }
            else -> {
                WeatherState(biomeTag, isRaining = false, isAshStorm = false, fogDensity = 0.1f, lightingModifierHex = 0xFFFFFFFF)
            }
        }
    }
}
