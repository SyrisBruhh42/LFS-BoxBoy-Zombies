package com.example.game.combat.modules

import com.example.game.InteractiveStation
import com.example.game.StationKind
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ShamblerHordeBehaviorTest {

    @Test
    fun testShamblerClawsWindowWhenNear() {
        val zombie = ZombieEntity(1, ZombieArchetype.SHAMBLER, 100f, 100f, 100, 100)
        val window = InteractiveStation(
            id = "w1",
            kind = StationKind.BARRICADE_WINDOW,
            label = "Window",
            x = 110f, // Distance 10 (less than 40)
            y = 100f,
            cost = 0,
            roomIndex = 0,
            barricadePlanks = 5
        )

        val result = ShamblerHordeBehavior.computeShamblerBehavior(
            zombie,
            listOf(zombie),
            200f,
            200f,
            listOf(window)
        )

        assertTrue(result.isClawingWindow)
        assertEquals(0f, result.dx, 0.01f)
        assertEquals(0f, result.dy, 0.01f)
    }

    @Test
    fun testShamblerIgnoresWindowWithoutPlanks() {
        val zombie = ZombieEntity(1, ZombieArchetype.SHAMBLER, 100f, 100f, 100, 100)
        val window = InteractiveStation(
            id = "w1",
            kind = StationKind.BARRICADE_WINDOW,
            label = "Window",
            x = 110f,
            y = 100f,
            cost = 0,
            roomIndex = 0,
            barricadePlanks = 0
        )

        val result = ShamblerHordeBehavior.computeShamblerBehavior(
            zombie,
            listOf(zombie),
            200f,
            100f,
            listOf(window)
        )

        assertFalse(result.isClawingWindow)
        assertTrue(result.dx > 0) // Moving towards target (200, 100)
    }

    @Test
    fun testShamblerMovesTowardsTarget() {
        val zombie = ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        
        val result = ShamblerHordeBehavior.computeShamblerBehavior(
            zombie,
            listOf(zombie),
            100f,
            0f,
            emptyList()
        )

        assertFalse(result.isClawingWindow)
        assertEquals(1f, result.dx, 0.01f)
        assertEquals(0f, result.dy, 0.01f)
    }
    
    @Test
    fun testShamblerSeparation() {
        val zombie = ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        val otherZombie = ZombieEntity(2, ZombieArchetype.SHAMBLER, 10f, 0f, 100, 100) // Near on X axis
        
        val result = ShamblerHordeBehavior.computeShamblerBehavior(
            zombie,
            listOf(zombie, otherZombie),
            100f, // Target is in +X direction
            0f,
            emptyList()
        )

        assertFalse(result.isClawingWindow)
        // Since other zombie is at +10x, separation will push in -X direction
        // The combined vector should have a smaller X component than pure target seeking
        // or even be negative depending on weights.
        assertTrue(result.dx < 1f) 
    }
}
