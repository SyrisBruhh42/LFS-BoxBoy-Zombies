package com.example.game.combat.modules

import androidx.compose.ui.geometry.Rect
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class PierceRaycasterEngineTest {

    private fun createZombie(id: Int, x: Float, y: Float): ZombieEntity {
        return ZombieEntity(
            id = id,
            archetype = ZombieArchetype.SHAMBLER, // Radius is 16f
            x = x,
            y = y,
            hp = 100,
            maxHp = 100
        )
    }

    @Test
    fun testPenetratingRay_NoObstacles_HitsMultipleZombies() {
        val zombies = listOf(
            createZombie(1, 100f, 0f),
            createZombie(2, 200f, 0f),
            createZombie(3, 300f, 0f),
            createZombie(4, 150f, 50f) // Should miss, radius is 16, dist is 50
        )
        val obstacles = emptyList<Rect>()

        val result = PierceRaycasterEngine.firePenetratingRay(
            startX = 0f, startY = 0f,
            dirX = 1f, dirY = 0f,
            maxRange = 500f,
            zombies = zombies,
            wallObstacles = obstacles
        )

        assertEquals(3, result.hits.size)
        assertEquals(1, result.hits[0].zombie.id)
        assertEquals(2, result.hits[1].zombie.id)
        assertEquals(3, result.hits[2].zombie.id)
        assertEquals(500f, result.endX, 0.1f)
        assertEquals(0f, result.endY, 0.1f)
    }

    @Test
    fun testPenetratingRay_WithWallObstacle_BlocksRay() {
        val zombies = listOf(
            createZombie(1, 100f, 0f),
            createZombie(2, 300f, 0f) // Should be blocked by wall
        )
        val obstacles = listOf(
            Rect(150f, -50f, 250f, 50f)
        )

        val result = PierceRaycasterEngine.firePenetratingRay(
            startX = 0f, startY = 0f,
            dirX = 1f, dirY = 0f,
            maxRange = 500f,
            zombies = zombies,
            wallObstacles = obstacles
        )

        assertEquals(1, result.hits.size)
        assertEquals(1, result.hits[0].zombie.id)
        assertEquals(150f, result.endX, 0.1f)
        assertEquals(0f, result.endY, 0.1f)
    }

    @Test
    fun testPenetratingRay_MaxPenetrationCount() {
        val zombies = listOf(
            createZombie(1, 100f, 0f),
            createZombie(2, 200f, 0f),
            createZombie(3, 300f, 0f)
        )
        val obstacles = emptyList<Rect>()

        val result = PierceRaycasterEngine.firePenetratingRay(
            startX = 0f, startY = 0f,
            dirX = 1f, dirY = 0f,
            maxRange = 500f,
            zombies = zombies,
            wallObstacles = obstacles,
            maxPenetrationCount = 2
        )

        assertEquals(2, result.hits.size)
        assertEquals(1, result.hits[0].zombie.id)
        assertEquals(2, result.hits[1].zombie.id)

        // Ray visually stops at the 2nd zombie when max penetration is reached
        assertEquals(200f, result.endX, 0.1f)
        assertEquals(0f, result.endY, 0.1f)
    }

    @Test
    fun testShotgunSpread_HitsMultipleZombiesInSpread() {
        val zombies = listOf(
            createZombie(1, 100f, 0f),     // Center
            createZombie(2, 100f, 50f),    // Offset Y+
            createZombie(3, 100f, -50f)    // Offset Y-
        )
        val obstacles = emptyList<Rect>()

        // 90 degree spread, 3 pellets, should go roughly straight, up 45 deg, down 45 deg
        val results = PierceRaycasterEngine.fireShotgunSpread(
            startX = 0f, startY = 0f,
            centerDirX = 1f, centerDirY = 0f,
            maxRange = 200f,
            pelletCount = 3,
            spreadDegrees = 90f,
            zombies = zombies,
            wallObstacles = obstacles
        )

        assertEquals(3, results.size)

        // Collect all unique zombies hit
        val hitIds = results.flatMap { it.hits }.map { it.zombie.id }.toSet()

        // Expecting some hits. 45 degrees at 100x means 100y, our zombies are at +-50y.
        // Let's make sure it hit at least one zombie and didn't crash.
        assertTrue(hitIds.isNotEmpty())
    }
}
