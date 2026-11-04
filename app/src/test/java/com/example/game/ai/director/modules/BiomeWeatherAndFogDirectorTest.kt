package com.example.game.ai.director.modules

import com.example.game.LightingAtmosphereMode
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random
import kotlin.math.abs

class BiomeWeatherAndFogDirectorTest {

    @Test
    fun `test CRYO_FOG hazard explicitly overrides biome weather generation`() {
        // Given an arbitrary biome and wave, but with the CRYO_FOG hazard mutator
        val wave = 10
        val biomeTag = "Volcanic_Crater"
        val hazardMutator = "CRYO_FOG"
        val seed = 12345L

        // When evaluating the weather
        val result = BiomeWeatherAndFogDirector.evaluateWeatherForSector(wave, biomeTag, hazardMutator, seed)

        // Then it should strictly be CRYO_FOG, overriding any potential volcanic ash storm
        assertEquals(WeatherCondition.CRYO_FOG, result.condition)
        assertEquals(LightingAtmosphereMode.TACTICAL_NIGHT_VISION, result.lightingMode)
        assertEquals(0.85f, result.fogDensity, 0.01f)
        assertEquals(0xFF88CCFF, result.particleColorHex)
    }

    @Test
    fun `test deterministic NEON_RAIN in cyber biome`() {
        val wave = 5
        val biomeTag = "Cyber_District"
        val hazardMutator = "NONE"
        
        // Find a seed that guarantees the rng roll is < 40 for NEON_RAIN
        var targetSeed = 0L
        while (true) {
            val rng = Random(targetSeed xor (wave.toLong() * 31L) xor biomeTag.hashCode().toLong())
            if (rng.nextInt(100) < 40) {
                break
            }
            targetSeed++
        }

        val result = BiomeWeatherAndFogDirector.evaluateWeatherForSector(wave, biomeTag, hazardMutator, targetSeed)

        assertEquals(WeatherCondition.NEON_RAIN, result.condition)
        assertEquals(LightingAtmosphereMode.ABYSSAL_BIO_NOIR, result.lightingMode)
        assertEquals(0.3f, result.fogDensity, 0.01f)
        assertEquals(0xFFFF00FF, result.particleColorHex)
        
        // Verify determinism with same parameters
        val result2 = BiomeWeatherAndFogDirector.evaluateWeatherForSector(wave, biomeTag, hazardMutator, targetSeed)
        assertEquals(WeatherCondition.NEON_RAIN, result2.condition)
    }

    @Test
    fun `test deterministic ASH_STORM in volcanic biome`() {
        val wave = 8
        val biomeTag = "Volcanic_Wastes"
        val hazardMutator = "NONE"
        
        // Find a seed that guarantees the rng roll is < 60 for ASH_STORM
        var targetSeed = 0L
        while (true) {
            val rng = Random(targetSeed xor (wave.toLong() * 31L) xor biomeTag.hashCode().toLong())
            if (rng.nextInt(100) < 60) {
                break
            }
            targetSeed++
        }

        val result = BiomeWeatherAndFogDirector.evaluateWeatherForSector(wave, biomeTag, hazardMutator, targetSeed)

        assertEquals(WeatherCondition.ASH_STORM, result.condition)
        assertEquals(LightingAtmosphereMode.ABYSSAL_BIO_NOIR, result.lightingMode)
        assertEquals(0.7f, result.fogDensity, 0.01f)
        assertEquals(0xFF444444, result.particleColorHex)
    }
    
    @Test
    fun `test default CLEAR weather for unmapped biome`() {
        val wave = 1
        val biomeTag = "Generic_Forest"
        val hazardMutator = "NONE"
        val seed = 999L

        val result = BiomeWeatherAndFogDirector.evaluateWeatherForSector(wave, biomeTag, hazardMutator, seed)

        assertEquals(WeatherCondition.CLEAR, result.condition)
        assertEquals(LightingAtmosphereMode.CLASSIC_ARCADE_BRIGHT, result.lightingMode)
        assertEquals(0.0f, result.fogDensity, 0.01f)
        assertEquals(0x00000000L, result.particleColorHex)
    }
}
