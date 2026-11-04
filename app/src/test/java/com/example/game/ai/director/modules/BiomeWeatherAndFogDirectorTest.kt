package com.example.game.ai.director.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiomeWeatherAndFogDirectorTest {

    @Test
    fun `test CRYO_TUNDRA generates valid fog and lighting`() {
        val state = BiomeWeatherAndFogDirector.determineWeatherAndFog("CRYO_TUNDRA", 1, 12345L)
        assertEquals("CRYO_TUNDRA", state.biomeTag)
        assertTrue(state.fogDensity >= 0.5f && state.fogDensity <= 1.0f)
        assertEquals(0xFF88CCFF, state.lightingModifierHex)
        assertEquals(false, state.isRaining)
        assertEquals(false, state.isAshStorm)
    }

    @Test
    fun `test NEON_DISTRICT generates valid rain and lighting`() {
        val state = BiomeWeatherAndFogDirector.determineWeatherAndFog("NEON_DISTRICT", 1, 12345L)
        assertEquals("NEON_DISTRICT", state.biomeTag)
        assertEquals(0.2f, state.fogDensity, 0.001f)
        assertEquals(0xFFFF00FF, state.lightingModifierHex)
        // Checking behavior, which depends on deterministic seed
        val expectedRain = (kotlin.math.abs((12345L xor 1L).hashCode()) % 10) > 4
        assertEquals(expectedRain, state.isRaining)
        assertEquals(false, state.isAshStorm)
    }

    @Test
    fun `test VOLCANIC_WASTES generates valid ash storm and lighting`() {
        val state = BiomeWeatherAndFogDirector.determineWeatherAndFog("VOLCANIC_WASTES", 1, 12345L)
        assertEquals("VOLCANIC_WASTES", state.biomeTag)
        assertEquals(0xFFFF5500, state.lightingModifierHex)
        assertEquals(false, state.isRaining)
        val expectedAsh = (kotlin.math.abs((12345L xor 1L).hashCode()) % 10) > 2
        assertEquals(expectedAsh, state.isAshStorm)
    }

    @Test
    fun `test default biome behavior`() {
        val state = BiomeWeatherAndFogDirector.determineWeatherAndFog("UNKNOWN_BIOME", 1, 12345L)
        assertEquals("UNKNOWN_BIOME", state.biomeTag)
        assertEquals(0.1f, state.fogDensity, 0.001f)
        assertEquals(0xFFFFFFFF, state.lightingModifierHex)
        assertEquals(false, state.isRaining)
        assertEquals(false, state.isAshStorm)
    }
}
