package com.example.game.combat.modules

import com.example.game.GameSessionState
import com.example.game.ZombieArchetype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpawnBreachWaveSchedulerTest {

    @Test
    fun testScheduleNextWave_deterministicSpawns() {
        val initialState = GameSessionState(wave = 1)
        val breachPoints = listOf(
            BreachPoint(10f, 10f, BreachType.WINDOW),
            BreachPoint(20f, 20f, BreachType.FRONTIER)
        )
        
        val seed = 42L
        
        val result1 = SpawnBreachWaveScheduler.scheduleNextWave(
            state = initialState,
            breachPoints = breachPoints,
            baseJitterRadius = 1.0f,
            seed = seed
        )
        
        assertEquals(2, result1.nextWaveNumber)
        assertEquals(7, result1.spawnedEntities.size) // wave * 2 + 3 = 2 * 2 + 3 = 7
        
        // Verify jitter is within bounds
        for (entity in result1.spawnedEntities) {
            val bp = breachPoints.find { 
                kotlin.math.abs(it.x - entity.x) <= 1.0f && kotlin.math.abs(it.y - entity.y) <= 1.0f 
            }
            assertTrue("Entity should spawn near a breach point", bp != null)
            assertTrue("X jitter exceeds bounds", kotlin.math.abs(entity.x - bp!!.x) <= 1.0f)
            assertTrue("Y jitter exceeds bounds", kotlin.math.abs(entity.y - bp.y) <= 1.0f)
        }
        
        val result2 = SpawnBreachWaveScheduler.scheduleNextWave(
            state = initialState,
            breachPoints = breachPoints,
            baseJitterRadius = 1.0f,
            seed = seed
        )
        
        // Ensure deterministic behavior
        assertEquals(result1.spawnedEntities.map { it.archetype }, result2.spawnedEntities.map { it.archetype })
        assertEquals(result1.spawnedEntities.map { it.x }, result2.spawnedEntities.map { it.x })
        assertEquals(result1.spawnedEntities.map { it.y }, result2.spawnedEntities.map { it.y })
    }
    
    @Test
    fun testArchetypeProgression() {
        val state = GameSessionState(wave = 4) // next wave = 5
        val breachPoints = listOf(BreachPoint(0f, 0f, BreachType.WINDOW))
        
        val result = SpawnBreachWaveScheduler.scheduleNextWave(
            state = state,
            breachPoints = breachPoints,
            seed = 100L
        )
        
        assertTrue("Wave >= 5 should contain CALCIFIED_STALKER", 
            result.spawnedEntities.any { it.archetype == ZombieArchetype.CALCIFIED_STALKER }
        )
    }
}
