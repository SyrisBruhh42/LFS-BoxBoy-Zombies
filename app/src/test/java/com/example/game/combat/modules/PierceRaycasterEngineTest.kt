package com.example.game.combat.modules

import androidx.compose.ui.geometry.Rect
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PierceRaycasterEngineTest {

    @Test
    fun testRaycastNoObstacles() {
        val startX = 0f
        val startY = 0f
        val endX = 100f
        val endY = 0f

        val walls = emptyList<Rect>()
        val zombies = listOf(
            ZombieEntity(id = 1, archetype = ZombieArchetype.SHAMBLER, x = 10f, y = 0f, hp = 100, maxHp = 100),
            ZombieEntity(id = 2, archetype = ZombieArchetype.SHAMBLER, x = 50f, y = 5f, hp = 100, maxHp = 100),
            ZombieEntity(id = 3, archetype = ZombieArchetype.SHAMBLER, x = 90f, y = 0f, hp = 100, maxHp = 100),
            // Out of range (behind)
            ZombieEntity(id = 4, archetype = ZombieArchetype.SHAMBLER, x = -10f, y = 0f, hp = 100, maxHp = 100),
            // Out of range (too far side)
            ZombieEntity(id = 5, archetype = ZombieArchetype.SHAMBLER, x = 50f, y = 50f, hp = 100, maxHp = 100)
        )

        val result = PierceRaycasterEngine.castPenetratingRay(
            startX = startX, startY = startY,
            endX = endX, endY = endY,
            walls = walls,
            zombies = zombies,
            pierceCount = 3
        )

        assertFalse(result.hitWall)
        assertEquals(100f, result.adjustedEndX, 0.01f)
        assertEquals(0f, result.adjustedEndY, 0.01f)
        assertEquals(3, result.hitZombies.size)
        assertEquals(1, result.hitZombies[0].id)
        assertEquals(2, result.hitZombies[1].id)
        assertEquals(3, result.hitZombies[2].id)
    }

    @Test
    fun testRaycastWithWallOcclusion() {
        val startX = 0f
        val startY = 0f
        val endX = 100f
        val endY = 0f

        val walls = listOf(
            Rect(left = 40f, top = -10f, right = 60f, bottom = 10f)
        )

        val zombies = listOf(
            ZombieEntity(id = 1, archetype = ZombieArchetype.SHAMBLER, x = 10f, y = 0f, hp = 100, maxHp = 100),
            // Behind wall
            ZombieEntity(id = 2, archetype = ZombieArchetype.SHAMBLER, x = 80f, y = 0f, hp = 100, maxHp = 100)
        )

        val result = PierceRaycasterEngine.castPenetratingRay(
            startX = startX, startY = startY,
            endX = endX, endY = endY,
            walls = walls,
            zombies = zombies,
            pierceCount = 3
        )

        assertTrue(result.hitWall)
        assertEquals(40f, result.adjustedEndX, 0.01f)
        assertEquals(0f, result.adjustedEndY, 0.01f)
        assertEquals(1, result.hitZombies.size)
        assertEquals(1, result.hitZombies[0].id)
    }

    @Test
    fun testRaycastPierceLimit() {
        val startX = 0f
        val startY = 0f
        val endX = 100f
        val endY = 0f

        val walls = emptyList<Rect>()
        val zombies = listOf(
            ZombieEntity(id = 1, archetype = ZombieArchetype.SHAMBLER, x = 10f, y = 0f, hp = 100, maxHp = 100),
            ZombieEntity(id = 2, archetype = ZombieArchetype.SHAMBLER, x = 20f, y = 0f, hp = 100, maxHp = 100),
            ZombieEntity(id = 3, archetype = ZombieArchetype.SHAMBLER, x = 30f, y = 0f, hp = 100, maxHp = 100),
            ZombieEntity(id = 4, archetype = ZombieArchetype.SHAMBLER, x = 40f, y = 0f, hp = 100, maxHp = 100)
        )

        val result = PierceRaycasterEngine.castPenetratingRay(
            startX = startX, startY = startY,
            endX = endX, endY = endY,
            walls = walls,
            zombies = zombies,
            pierceCount = 2
        )

        assertFalse(result.hitWall)
        assertEquals(2, result.hitZombies.size)
        assertEquals(1, result.hitZombies[0].id)
        assertEquals(2, result.hitZombies[1].id)
    }
}
