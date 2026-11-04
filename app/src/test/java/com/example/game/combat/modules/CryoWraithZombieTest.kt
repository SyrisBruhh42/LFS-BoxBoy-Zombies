package com.example.game.combat.modules

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CryoWraithZombieTest {

    @Test
    fun testCryoWraithGlidingAndTrailGeneration() {
        val baseEntity = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.SHAMBLER, // Placeholder archetype
            x = 0f,
            y = 0f,
            hp = 100,
            maxHp = 100
        )

        var state = CryoWraithZombieState(baseEntity = baseEntity)
        val puddles = mutableListOf<CryoTrailPuddle>()

        // Target is at 200, 0
        // Initial dist is 200, should trigger glide
        state = CryoWraithZombieModule.stepWraith(state, 200f, 0f, 100L, puddles)

        assertTrue("Wraith should start gliding because dist is between 80 and 350", state.isGliding)
        assertEquals("Glide timer should be set to duration minus delta", CryoWraithZombieModule.GLIDE_DURATION_MS - 100L, state.glideTimerMs)
        assertTrue("Puddle should be generated during glide", puddles.isNotEmpty())
    }

    @Test
    fun testCryoWraithCooldown() {
        val baseEntity = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.SHAMBLER,
            x = 0f,
            y = 0f,
            hp = 100,
            maxHp = 100
        )

        // Start in gliding state with 50ms left
        var state = CryoWraithZombieState(
            baseEntity = baseEntity,
            isGliding = true,
            glideTimerMs = 50L
        )
        val puddles = mutableListOf<CryoTrailPuddle>()

        // Step 100ms, should end glide and start cooldown
        state = CryoWraithZombieModule.stepWraith(state, 200f, 0f, 100L, puddles)

        assertFalse("Wraith should stop gliding after timer expires", state.isGliding)
        assertEquals("Glide timer should be set to cooldown", CryoWraithZombieModule.GLIDE_COOLDOWN_MS, state.glideTimerMs)
    }

    @Test
    fun testPuddleDecay() {
        val puddles = listOf(
            CryoTrailPuddle(0f, 0f, remainingLifeMs = 200L),
            CryoTrailPuddle(10f, 10f, remainingLifeMs = 50L)
        )

        val stepped = CryoWraithZombieModule.stepPuddles(puddles, 100L)

        assertEquals("Only one puddle should remain", 1, stepped.size)
        assertEquals("Remaining puddle should have 100ms left", 100L, stepped[0].remainingLifeMs)
    }

    @Test
    fun testSlipperyIcePhysics() {
        val puddles = listOf(
            CryoTrailPuddle(0f, 0f, radius = 24f)
        )

        // Player on ice
        val (vx1, vy1) = CryoWraithZombieModule.applySlipperyIcePhysics(10f, 0f, 100f, 100f, puddles)
        val expectedReduction = 1f - CryoWraithZombieModule.SLIP_FRICTION_REDUCTION
        assertEquals(100f * expectedReduction, vx1, 0.01f)
        assertEquals(100f * expectedReduction, vy1, 0.01f)

        // Player not on ice
        val (vx2, vy2) = CryoWraithZombieModule.applySlipperyIcePhysics(50f, 0f, 100f, 100f, puddles)
        assertEquals(100f, vx2, 0.01f)
        assertEquals(100f, vy2, 0.01f)
    }

    @Test
    fun testBonusCryoAmmoDrop() {
        val drop = CryoWraithZombieModule.generateBonusCryoAmmoDrop("pistol")
        assertEquals(30, drop["pistol"])
    }
}
