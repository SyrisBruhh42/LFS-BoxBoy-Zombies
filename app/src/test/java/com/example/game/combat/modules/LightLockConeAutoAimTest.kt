package com.example.game.combat.modules

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class LightLockConeAutoAimTest {

    @Test
    fun testFindLightLockTarget_illuminated() {
        val zombies = listOf(
            ZombieEntity(1, ZombieArchetype.SHAMBLER, 200f, 0f, 100, 100),
            ZombieEntity(2, ZombieArchetype.SHAMBLER, -200f, 0f, 100, 100)
        )
        // Aiming right (0 radians)
        val (target, isLightLocked) = LightLockConeAutoAim.findLightLockTarget(
            zombies = zombies,
            playerX = 0f,
            playerY = 0f,
            currentAimRad = 0f,
            lanternRange = 400f,
            coneHalfSpreadRad = (PI / 4).toFloat()
        )
        assertTrue(isLightLocked)
        assertEquals(1, target?.id)
    }

    @Test
    fun testFindLightLockTarget_fallback() {
        val zombies = listOf(
            ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 200f, 100, 100)
        )
        // Aiming right (0 radians), but zombie is at 90 degrees (outside cone)
        val (target, isLightLocked) = LightLockConeAutoAim.findLightLockTarget(
            zombies = zombies,
            playerX = 0f,
            playerY = 0f,
            currentAimRad = 0f,
            lanternRange = 400f,
            coneHalfSpreadRad = (PI / 8).toFloat()
        )
        assertFalse(isLightLocked)
        assertEquals(1, target?.id)
    }

    @Test
    fun testFindLightLockTarget_noTargetsInRange() {
        val zombies = listOf(
            ZombieEntity(1, ZombieArchetype.SHAMBLER, 1000f, 1000f, 100, 100)
        )
        val (target, isLightLocked) = LightLockConeAutoAim.findLightLockTarget(
            zombies = zombies,
            playerX = 0f,
            playerY = 0f,
            currentAimRad = 0f,
            lanternRange = 400f,
            coneHalfSpreadRad = (PI / 4).toFloat(),
            maxFallbackRange = 420f
        )
        assertFalse(isLightLocked)
        assertNull(target)
    }

    @Test
    fun testFindLightLockTarget_emptyList() {
        val (target, isLightLocked) = LightLockConeAutoAim.findLightLockTarget(
            zombies = emptyList(),
            playerX = 0f,
            playerY = 0f,
            currentAimRad = 0f,
            lanternRange = 400f,
            coneHalfSpreadRad = (PI / 4).toFloat()
        )
        assertFalse(isLightLocked)
        assertNull(target)
    }

    @Test
    fun testCalculateDamage() {
        val baseDamage = 100
        val critDamage = LightLockConeAutoAim.calculateDamage(baseDamage, true, 1.5f)
        assertEquals(150, critDamage)

        val normalDamage = LightLockConeAutoAim.calculateDamage(baseDamage, false, 1.5f)
        assertEquals(100, normalDamage)
    }
}
