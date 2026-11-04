package com.example.game.combat.modules

import com.example.game.combat.BarrelNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolatileBoomerZombieTest {

    @Test
    fun testBoomerMovesTowardsTarget() {
        val boomer = VolatileBoomerZombie(id = 1, x = 0f, y = 0f, hp = 100)
        val result = VolatileBoomerEngine.updateBoomer(
            boomer = boomer,
            targetX = 100f,
            targetY = 0f,
            deltaMs = 16L,
            barrels = emptyList()
        )

        // Should move along X axis
        assertTrue(result.updatedBoomer.x > 0f)
        assertEquals(0f, result.updatedBoomer.y, 0.01f)
        assertFalse(result.updatedBoomer.isIgnited)
        assertNull(result.explosionResult)
    }

    @Test
    fun testBoomerIgnitesWhenCloseToTarget() {
        // ignitionRadius is 80f, so at 50f it should ignite
        val boomer = VolatileBoomerZombie(id = 1, x = 0f, y = 0f, hp = 100)
        val result = VolatileBoomerEngine.updateBoomer(
            boomer = boomer,
            targetX = 50f,
            targetY = 0f,
            deltaMs = 16L,
            barrels = emptyList()
        )

        assertTrue(result.updatedBoomer.isIgnited)
        assertNull(result.explosionResult)
    }

    @Test
    fun testBoomerExplodesWhenFuseExpires() {
        val boomer = VolatileBoomerZombie(id = 1, x = 0f, y = 0f, hp = 100, isIgnited = true, fuseRemainingMs = 10L)
        val result = VolatileBoomerEngine.updateBoomer(
            boomer = boomer,
            targetX = 100f,
            targetY = 0f,
            deltaMs = 16L, // 16 > 10, so fuse expires
            barrels = emptyList()
        )

        assertEquals(0, result.updatedBoomer.hp)
        assertNotNull(result.explosionResult)
        // Only the boomer itself (virtual barrel) detonates if there are no other barrels
        assertEquals(1, result.explosionResult!!.detonatedBarrelIds.size)
        assertEquals(-1, result.explosionResult.detonatedBarrelIds[0])
    }

    @Test
    fun testBoomerExplodesOnDeath() {
        val boomer = VolatileBoomerZombie(id = 1, x = 0f, y = 0f, hp = 0)
        val result = VolatileBoomerEngine.updateBoomer(
            boomer = boomer,
            targetX = 100f,
            targetY = 0f,
            deltaMs = 16L,
            barrels = emptyList()
        )

        assertNotNull(result.explosionResult)
        assertEquals(1, result.explosionResult!!.detonatedBarrelIds.size)
    }

    @Test
    fun testBoomerTriggersChainReaction() {
        val boomer = VolatileBoomerZombie(id = 1, x = 0f, y = 0f, hp = 0, blastRadius = 100f)

        // Barrel 1 is within boomer's blast radius
        val barrel1 = BarrelNode(id = 10, x = 50f, y = 0f, blastRadiusPx = 100f)
        // Barrel 2 is within barrel 1's blast radius but outside boomer's blast radius
        val barrel2 = BarrelNode(id = 11, x = 120f, y = 0f)
        // Barrel 3 is far away
        val barrel3 = BarrelNode(id = 12, x = 1000f, y = 1000f)

        val barrels = listOf(barrel1, barrel2, barrel3)

        val result = VolatileBoomerEngine.updateBoomer(
            boomer = boomer,
            targetX = 0f,
            targetY = 0f,
            deltaMs = 16L,
            barrels = barrels
        )

        assertNotNull(result.explosionResult)
        val detonatedIds = result.explosionResult!!.detonatedBarrelIds
        // Boomer virtual barrel (-1), barrel1 (10), barrel2 (11) should detonate
        assertEquals(3, detonatedIds.size)
        assertTrue(detonatedIds.contains(-1))
        assertTrue(detonatedIds.contains(10))
        assertTrue(detonatedIds.contains(11))
        assertFalse(detonatedIds.contains(12))
    }
}
