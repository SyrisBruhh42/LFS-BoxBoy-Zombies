package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Test

class AabbBarricadePenetrationTest {

    @Test
    fun testNoCollision() {
        val entity = Aabb(0f, 0f, 10f, 10f)
        val barricade = Aabb(20f, 20f, 30f, 30f)
        
        val mtv = AabbBarricadePenetration.resolvePenetration(entity, barricade)
        
        assertEquals(0f, mtv.x, 0.001f)
        assertEquals(0f, mtv.y, 0.001f)
    }

    @Test
    fun testPushLeft() {
        // Entity is moving right and intersects the left side of the barricade
        val entity = Aabb(5f, 0f, 15f, 10f)
        val barricade = Aabb(10f, 0f, 20f, 10f)
        
        val mtv = AabbBarricadePenetration.resolvePenetration(entity, barricade)
        
        // Entity should be pushed left by 5 units
        assertEquals(-5f, mtv.x, 0.001f)
        assertEquals(0f, mtv.y, 0.001f)
    }

    @Test
    fun testPushRight() {
        // Entity is moving left and intersects the right side of the barricade
        val entity = Aabb(15f, 0f, 25f, 10f)
        val barricade = Aabb(10f, 0f, 20f, 10f)
        
        val mtv = AabbBarricadePenetration.resolvePenetration(entity, barricade)
        
        // Entity should be pushed right by 5 units
        assertEquals(5f, mtv.x, 0.001f)
        assertEquals(0f, mtv.y, 0.001f)
    }

    @Test
    fun testPushUp() {
        // Entity is moving down and intersects the top side of the barricade
        val entity = Aabb(0f, 5f, 10f, 15f)
        val barricade = Aabb(0f, 10f, 10f, 20f)
        
        val mtv = AabbBarricadePenetration.resolvePenetration(entity, barricade)
        
        // Entity should be pushed up (negative Y direction) by 5 units
        assertEquals(0f, mtv.x, 0.001f)
        assertEquals(-5f, mtv.y, 0.001f)
    }

    @Test
    fun testPushDown() {
        // Entity is moving up and intersects the bottom side of the barricade
        val entity = Aabb(0f, 15f, 10f, 25f)
        val barricade = Aabb(0f, 10f, 10f, 20f)
        
        val mtv = AabbBarricadePenetration.resolvePenetration(entity, barricade)
        
        // Entity should be pushed down (positive Y direction) by 5 units
        assertEquals(0f, mtv.x, 0.001f)
        assertEquals(5f, mtv.y, 0.001f)
    }
    
    @Test
    fun testCornerCollisionPrefersSmallestOverlap() {
        // Entity overlaps barricade heavily on X, slightly on Y
        val entity = Aabb(5f, 8f, 15f, 18f)
        val barricade = Aabb(10f, 10f, 20f, 20f)
        
        // Overlap X Left = 15 - 10 = 5
        // Overlap Y Top = 18 - 10 = 8
        // Should push left by 5
        
        val mtv = AabbBarricadePenetration.resolvePenetration(entity, barricade)
        
        assertEquals(-5f, mtv.x, 0.001f)
        assertEquals(0f, mtv.y, 0.001f)
    }
}
