package com.example.game.combat.modules

import org.junit.Assert.*
import org.junit.Test

class ShamblerHordeBehaviorTest {

    @Test
    fun testWindowClawingBehavior() {
        val window = WindowBarricade(id = 1, x = 100f, y = 100f, planksRemaining = 3)
        // Zombie within 8f of radius sum (14 + 24 = 38, so within 46f)
        val zombie = ShamblerZombie(id = 10, x = 100f, y = 140f) // distance 40f
        
        val result = ShamblerHordeBehavior.computeShamblerSteering(
            zombieIndex = 0,
            shamblers = listOf(zombie),
            windows = listOf(window),
            targetX = 0f,
            targetY = 0f,
            deltaMs = 16L
        )
        
        assertEquals(1, result.clawingWindowId)
        assertEquals(0f, result.sepX, 0.001f)
        assertEquals(0f, result.cohX, 0.001f)
    }

    @Test
    fun testSeparationBehavior() {
        // Two zombies very close to each other
        val z1 = ShamblerZombie(id = 1, x = 0f, y = 0f)
        val z2 = ShamblerZombie(id = 2, x = 10f, y = 0f)
        
        val result = ShamblerHordeBehavior.computeShamblerSteering(
            zombieIndex = 0,
            shamblers = listOf(z1, z2),
            windows = emptyList(),
            targetX = 100f,
            targetY = 0f,
            deltaMs = 16L
        )
        
        assertNull(result.clawingWindowId)
        // z1 should be pushed away from z2, so separation vector should be negative in x
        assertTrue(result.sepX < 0)
        assertEquals(0f, result.sepY, 0.001f)
    }

    @Test
    fun testCohesionBehavior() {
        // z1 and z2 are far enough not to separate but close enough to flock
        val z1 = ShamblerZombie(id = 1, x = 0f, y = 0f)
        val z2 = ShamblerZombie(id = 2, x = 50f, y = 0f)
        
        val result = ShamblerHordeBehavior.computeShamblerSteering(
            zombieIndex = 0,
            shamblers = listOf(z1, z2),
            windows = emptyList(),
            targetX = 100f,
            targetY = 0f,
            deltaMs = 16L
        )
        
        assertNull(result.clawingWindowId)
        // z1 should want to move towards the center of the flock (which is z2's position)
        // so cohesion vector should be positive in x
        assertTrue(result.cohX > 0)
        assertEquals(0f, result.cohY, 0.001f)
        // Should not separate
        assertEquals(0f, result.sepX, 0.001f)
        assertEquals(0f, result.sepY, 0.001f)
    }
}
