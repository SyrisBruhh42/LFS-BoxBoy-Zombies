package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AabbBarricadePenetrationTest {

    @Test
    fun testNoCollision() {
        val entity = Aabb(0f, 0f, 10f, 10f)
        val barricade = Aabb(20f, 20f, 30f, 30f)

        val result = AabbBarricadePenetration.calculateMtv(entity, barricade)

        assertFalse(result.hasCollision)
    }

    @Test
    fun testCollisionResolveLeft() {
        // Entity overlaps barricade slightly on the right edge of entity (push entity left)
        val entity = Aabb(0f, 0f, 10f, 10f)
        val barricade = Aabb(8f, 0f, 20f, 10f)

        val result = AabbBarricadePenetration.calculateMtv(entity, barricade)

        assertTrue(result.hasCollision)
        assertEquals(-2f, result.mtvX, 0.001f) // Push entity left by 2
        assertEquals(0f, result.mtvY, 0.001f)
    }

    @Test
    fun testCollisionResolveRight() {
        // Entity overlaps barricade slightly on the left edge of entity (push entity right)
        val barricade = Aabb(0f, 0f, 10f, 10f)
        val entity = Aabb(8f, 0f, 20f, 10f)

        val result = AabbBarricadePenetration.calculateMtv(entity, barricade)

        assertTrue(result.hasCollision)
        assertEquals(2f, result.mtvX, 0.001f) // Push entity right by 2
        assertEquals(0f, result.mtvY, 0.001f)
    }

    @Test
    fun testCollisionResolveUp() {
        // Entity overlaps barricade slightly on bottom edge of entity (push entity up)
        val entity = Aabb(0f, 0f, 10f, 10f)
        val barricade = Aabb(0f, 8f, 10f, 20f)

        val result = AabbBarricadePenetration.calculateMtv(entity, barricade)

        assertTrue(result.hasCollision)
        assertEquals(0f, result.mtvX, 0.001f)
        assertEquals(-2f, result.mtvY, 0.001f) // Push entity up by 2
    }

    @Test
    fun testCollisionResolveDown() {
        // Entity overlaps barricade slightly on top edge of entity (push entity down)
        val barricade = Aabb(0f, 0f, 10f, 10f)
        val entity = Aabb(0f, 8f, 10f, 20f)

        val result = AabbBarricadePenetration.calculateMtv(entity, barricade)

        assertTrue(result.hasCollision)
        assertEquals(0f, result.mtvX, 0.001f)
        assertEquals(2f, result.mtvY, 0.001f) // Push entity down by 2
    }

    @Test
    fun testCornerCollisionResolvesShortestDistance() {
        // Overlaps 2 units horizontally and 4 units vertically. Should resolve horizontally.
        val entity = Aabb(0f, 0f, 10f, 10f)
        val barricade = Aabb(8f, 6f, 20f, 20f)

        val result = AabbBarricadePenetration.calculateMtv(entity, barricade)

        assertTrue(result.hasCollision)
        assertEquals(-2f, result.mtvX, 0.001f)
        assertEquals(0f, result.mtvY, 0.001f)
    }
}
