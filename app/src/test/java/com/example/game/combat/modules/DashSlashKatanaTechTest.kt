package com.example.game.combat.modules

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class DashSlashKatanaTechTest {

    @Test
    fun testDashSlashLinearHitDetection() {
        val zombies = listOf(
            ZombieEntity(id = 1, x = 100f, y = 0f, hp = 100, maxHp = 100, archetype = ZombieArchetype.SHAMBLER),
            ZombieEntity(id = 2, x = 200f, y = 0f, hp = 100, maxHp = 100, archetype = ZombieArchetype.SPRINTER),
            ZombieEntity(id = 3, x = 300f, y = 0f, hp = 100, maxHp = 100, archetype = ZombieArchetype.SHAMBLER) // Out of range
        )

        val result = DashSlashKatanaTech.executeDashSlash(
            playerX = 0f,
            playerY = 0f,
            aimAngleRad = 0f,
            zombies = zombies
        )

        assertEquals(250f, result.newPlayerX, 0.01f)
        assertEquals(0f, result.newPlayerY, 0.01f)
        assertEquals(2, result.hitZombieIds.size)
        assertTrue(result.hitZombieIds.contains(1))
        assertTrue(result.hitZombieIds.contains(2))

        // Base 400 + 2 * 100 = 600ms
        assertEquals(600L, result.iframeDurationMs)
    }

    @Test
    fun testDashSlashMiss() {
        val zombies = listOf(
            ZombieEntity(id = 1, x = 0f, y = 100f, hp = 100, maxHp = 100, archetype = ZombieArchetype.SHAMBLER)
        )

        val result = DashSlashKatanaTech.executeDashSlash(
            playerX = 0f,
            playerY = 0f,
            aimAngleRad = 0f,
            zombies = zombies
        )

        assertEquals(250f, result.newPlayerX, 0.01f)
        assertEquals(0f, result.newPlayerY, 0.01f)
        assertTrue(result.hitZombieIds.isEmpty())
        assertEquals(400L, result.iframeDurationMs)
    }

    @Test
    fun testDashSlashDiagonalHitDetection() {
        val aimAngle = (PI / 4).toFloat() // 45 degrees
        val dist = 150f
        val x = dist * kotlin.math.cos(aimAngle)
        val y = dist * kotlin.math.sin(aimAngle)

        val zombies = listOf(
            ZombieEntity(id = 1, x = x, y = y, hp = 100, maxHp = 100, archetype = ZombieArchetype.SHAMBLER)
        )

        val result = DashSlashKatanaTech.executeDashSlash(
            playerX = 0f,
            playerY = 0f,
            aimAngleRad = aimAngle,
            zombies = zombies
        )

        val expectedX = 250f * kotlin.math.cos(aimAngle)
        val expectedY = 250f * kotlin.math.sin(aimAngle)

        assertEquals(expectedX, result.newPlayerX, 0.01f)
        assertEquals(expectedY, result.newPlayerY, 0.01f)
        assertEquals(1, result.hitZombieIds.size)
        assertEquals(500L, result.iframeDurationMs)
    }
}
