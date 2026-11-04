package com.example.game.economy.vendors

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceableBarricadeWallBlockTest {

    @Test
    fun testInitializationAndTakeDamage() {
        val barricade = PlaceableBarricadeWallBlock(
            id = "wall_1",
            x = 100f,
            y = 100f,
            width = 50f,
            height = 50f,
            type = BarricadeBlockType.CONCRETE
        )

        assertEquals(1500, barricade.currentHp)
        assertFalse(barricade.isDestroyed)

        barricade.takeDamage(500)
        assertEquals(1000, barricade.currentHp)

        barricade.takeDamage(1500)
        assertEquals(0, barricade.currentHp)
        assertTrue(barricade.isDestroyed)
    }

    @Test
    fun testAabbRoutingNoCollision() {
        val barricade = PlaceableBarricadeWallBlock(
            id = "wall_2",
            x = 100f,
            y = 100f,
            width = 50f,
            height = 50f,
            type = BarricadeBlockType.HARD_LIGHT_ENERGY
        )

        val entityBounds = Rect(10f, 10f, 50f, 50f)
        val result = barricade.resolveAabbRouting(entityBounds)

        assertFalse(result.hasCollision)
        assertEquals(Offset.Zero, result.mtv)
    }

    @Test
    fun testAabbRoutingWithCollisionLeft() {
        val barricade = PlaceableBarricadeWallBlock(
            id = "wall_3",
            x = 100f,
            y = 100f,
            width = 50f,
            height = 50f,
            type = BarricadeBlockType.CONCRETE
        )

        // Entity overlaps slightly from the left
        val entityBounds = Rect(90f, 110f, 110f, 130f)
        val result = barricade.resolveAabbRouting(entityBounds)

        assertTrue(result.hasCollision)
        assertEquals(Offset(-10f, 0f), result.mtv)
    }

    @Test
    fun testAabbRoutingWithCollisionTop() {
        val barricade = PlaceableBarricadeWallBlock(
            id = "wall_4",
            x = 100f,
            y = 100f,
            width = 50f,
            height = 50f,
            type = BarricadeBlockType.CONCRETE
        )

        // Entity overlaps slightly from the top
        val entityBounds = Rect(110f, 90f, 130f, 110f)
        val result = barricade.resolveAabbRouting(entityBounds)

        assertTrue(result.hasCollision)
        assertEquals(Offset(0f, -10f), result.mtv)
    }

    @Test
    fun testAabbRoutingDestroyedBarricade() {
        val barricade = PlaceableBarricadeWallBlock(
            id = "wall_5",
            x = 100f,
            y = 100f,
            width = 50f,
            height = 50f,
            type = BarricadeBlockType.TITANIUM
        )

        barricade.takeDamage(3000)

        // Entity overlaps
        val entityBounds = Rect(90f, 90f, 120f, 120f)
        val result = barricade.resolveAabbRouting(entityBounds)

        assertFalse(result.hasCollision)
    }
}
