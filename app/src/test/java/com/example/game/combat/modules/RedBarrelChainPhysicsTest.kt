package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class RedBarrelChainPhysicsTest {

    @Test
    fun test_single_barrel_detonation() {
        val b1 = RedBarrel(id = 1, x = 100f, y = 100f)
        val barrels = listOf(b1)
        
        // Target is right next to barrel
        val result = RedBarrelChainPhysics.resolveChainReaction(1, barrels, 120f, 100f)
        
        assertEquals(1, result.detonatedBarrelIds.size)
        assertTrue(result.detonatedBarrelIds.contains(1))
        assertEquals(1, result.totalChainDepth)
        
        assertTrue("Shockwave stagger should push target right", result.shockwaveStaggerVx > 0)
        assertEquals("Shockwave stagger Vy should be 0", 0f, result.shockwaveStaggerVy, 0.001f)
    }

    @Test
    fun test_multi_hop_chain_reaction() {
        // Line of barrels placed such that 1 triggers 2, 2 triggers 3, 3 triggers 4
        // blastRadiusPx is 145f. distance between each is 120f.
        val b1 = RedBarrel(id = 1, x = 100f, y = 100f)
        val b2 = RedBarrel(id = 2, x = 220f, y = 100f)
        val b3 = RedBarrel(id = 3, x = 340f, y = 100f)
        val b4 = RedBarrel(id = 4, x = 460f, y = 100f)
        // b5 is too far from b4 (300f distance)
        val b5 = RedBarrel(id = 5, x = 760f, y = 100f)

        val barrels = listOf(b1, b2, b3, b4, b5)
        
        // Target is far away, so no stagger
        val result = RedBarrelChainPhysics.resolveChainReaction(1, barrels, -1000f, -1000f)
        
        assertEquals(4, result.detonatedBarrelIds.size)
        assertTrue(result.detonatedBarrelIds.containsAll(listOf(1, 2, 3, 4)))
        assertEquals(4, result.totalChainDepth)
        assertEquals(0f, result.shockwaveStaggerVx, 0.001f)
        assertEquals(0f, result.shockwaveStaggerVy, 0.001f)
    }

    @Test
    fun test_shockwave_stagger() {
        val b1 = RedBarrel(id = 1, x = 0f, y = 0f, blastRadiusPx = 100f)
        val barrels = listOf(b1)
        
        // Target 1: very close
        val result1 = RedBarrelChainPhysics.resolveChainReaction(1, barrels, 10f, 0f)
        // Target 2: further away but within radius
        val result2 = RedBarrelChainPhysics.resolveChainReaction(1, barrels, 80f, 0f)
        
        // Stagger should be greater for the closer target
        assertTrue("Stagger impulse should drop off with distance", result1.shockwaveStaggerVx > result2.shockwaveStaggerVx)
        assertTrue(result1.shockwaveStaggerVx > 0)
        assertTrue(result2.shockwaveStaggerVx > 0)
    }
}
