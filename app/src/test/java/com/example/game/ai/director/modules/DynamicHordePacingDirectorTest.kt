package com.example.game.ai.director.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicHordePacingDirectorTest {

    @Test
    fun `test calculating pacing when squad is breezing through`() {
        val metrics = DynamicHordePacingDirector.calculatePacing(
            avgSquadHpPercent = 1.0f,
            avgSquadAmmoPercent = 1.0f,
            wave = 1,
            baseSpawnDelayMs = 2000L,
            baseMaxZombies = 24
        )

        // Intensity should be just wave bump (0.05)
        assertEquals(0.05f, metrics.intensityScore, 0.01f)
        
        // Spawn delay should be halved (ramped up)
        assertEquals(1000L, metrics.recommendedSpawnDelayMs)
        
        // Max zombies should be scaled by wave 1.1
        assertEquals(26, metrics.maxSimultaneousZombies)
    }
    
    @Test
    fun `test calculating pacing when squad is panicked`() {
        val metrics = DynamicHordePacingDirector.calculatePacing(
            avgSquadHpPercent = 0.1f, // 90% dead
            avgSquadAmmoPercent = 0.1f, // 90% out of ammo
            wave = 5,
            baseSpawnDelayMs = 2000L,
            baseMaxZombies = 24
        )

        // Intensity should be maxed out (1.0)
        assertEquals(1.0f, metrics.intensityScore, 0.01f)
        
        // Spawn delay should be increased (slowed down spawns)
        assertEquals(5000L, metrics.recommendedSpawnDelayMs)
        
        // Max zombies should be scaled down because they are overwhelmed
        val expectedLimit = (24 * 1.5f * 0.6f).toInt()
        assertEquals(21, metrics.maxSimultaneousZombies)
    }

    @Test
    fun `test calculating pacing normal state`() {
        val metrics = DynamicHordePacingDirector.calculatePacing(
            avgSquadHpPercent = 0.6f, 
            avgSquadAmmoPercent = 0.6f, 
            wave = 3,
            baseSpawnDelayMs = 2000L,
            baseMaxZombies = 24
        )

        // hpFactor = 0.4, ammoFactor = 0.4. rawIntensity = 0.4*0.6 + 0.4*0.4 = 0.24 + 0.16 = 0.4. + wave*0.05 = 0.55
        assertEquals(0.55f, metrics.intensityScore, 0.01f)
        
        // intensity is > 0.5, pacing multiplier is 1.5
        assertEquals(3000L, metrics.recommendedSpawnDelayMs)
        
        // wave multiplier 1.3
        val expectedLimit = (24 * 1.3f).toInt()
        assertEquals(expectedLimit, metrics.maxSimultaneousZombies)
    }
}
