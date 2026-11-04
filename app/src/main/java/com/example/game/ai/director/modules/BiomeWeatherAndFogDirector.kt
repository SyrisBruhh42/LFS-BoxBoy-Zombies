package com.example.game.ai.director.modules

import com.example.game.LightingAtmosphereMode
import kotlin.math.abs
import kotlin.random.Random

enum class WeatherCondition {
    CLEAR,
    CRYO_FOG,
    NEON_RAIN,
    ASH_STORM
}

data class WeatherLightingModifier(
    val condition: WeatherCondition,
    val lightingMode: LightingAtmosphereMode,
    val fogDensity: Float,
    val particleColorHex: Long,
    val particleCountModifier: Float
)

object BiomeWeatherAndFogDirector {

    /**
     * Deterministically evaluates the weather condition and lighting modifiers for a sector based on
     * its wave, biomeTag, Ollama hazard mutator, and the PRNG seed.
     */
    fun evaluateWeatherForSector(
        wave: Int,
        biomeTag: String,
        hazardMutator: String,
        seed: Long
    ): WeatherLightingModifier {
        // If the Ollama/WFC hazard explicitly forces Cryo-Fog, it overrides biome defaults
        if (hazardMutator == "CRYO_FOG") {
            return WeatherLightingModifier(
                condition = WeatherCondition.CRYO_FOG,
                lightingMode = LightingAtmosphereMode.TACTICAL_NIGHT_VISION, // Phosphor Night-Ops for fog
                fogDensity = 0.85f,
                particleColorHex = 0xFF88CCFF, // Ice blue fog particles
                particleCountModifier = 2.0f
            )
        }

        // Use PRNG for deterministic biome weather
        val rng = Random(seed xor (wave.toLong() * 31L) xor biomeTag.hashCode().toLong())
        val roll = rng.nextInt(100)

        return when {
            biomeTag.contains("cyber", ignoreCase = true) || biomeTag.contains("neon", ignoreCase = true) -> {
                if (roll < 40) {
                    WeatherLightingModifier(
                        condition = WeatherCondition.NEON_RAIN,
                        lightingMode = LightingAtmosphereMode.ABYSSAL_BIO_NOIR,
                        fogDensity = 0.3f,
                        particleColorHex = 0xFFFF00FF, // Magenta rain
                        particleCountModifier = 1.5f
                    )
                } else {
                    defaultClearWeather()
                }
            }
            biomeTag.contains("volcanic", ignoreCase = true) || biomeTag.contains("ash", ignoreCase = true) -> {
                if (roll < 60) {
                    WeatherLightingModifier(
                        condition = WeatherCondition.ASH_STORM,
                        lightingMode = LightingAtmosphereMode.ABYSSAL_BIO_NOIR,
                        fogDensity = 0.7f,
                        particleColorHex = 0xFF444444, // Dark ash
                        particleCountModifier = 2.5f
                    )
                } else {
                    defaultClearWeather()
                }
            }
            else -> defaultClearWeather()
        }
    }

    private fun defaultClearWeather() = WeatherLightingModifier(
        condition = WeatherCondition.CLEAR,
        lightingMode = LightingAtmosphereMode.CLASSIC_ARCADE_BRIGHT,
        fogDensity = 0.0f,
        particleColorHex = 0x00000000L,
        particleCountModifier = 1.0f
    )
}
