package com.example.game.ai.director.modules

import com.example.game.GameSessionState
import com.example.game.ZombieEntity
import com.example.game.ZombieArchetype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmergencySupplyDropDirectorTest {

    @Test
    fun `evaluateSupplyDrop returns false when no threat and full ammo`() {
        val state = GameSessionState(
            hp = 100,
            maxHp = 100,
            currentMagAmmo = mapOf("pistol" to 12),
            zombies = emptyList(),
            mapSeed = 1000L
        )
        val result = EmergencySupplyDropDirector.evaluateSupplyDrop(state)
        assertFalse(result.shouldDropAmmo)
        assertFalse(result.shouldDropBarrel)
    }

    @Test
    fun `evaluateSupplyDrop returns dropBarrel when low HP`() {
        val state = GameSessionState(
            hp = 20,
            maxHp = 100,
            currentMagAmmo = mapOf("pistol" to 12),
            zombies = emptyList(),
            mapSeed = 1000L
        )
        val result = EmergencySupplyDropDirector.evaluateSupplyDrop(state)
        assertTrue(result.shouldDropBarrel)
        assertFalse(result.shouldDropAmmo)
    }

    @Test
    fun `evaluateSupplyDrop returns dropBarrel when high zombies`() {
        val zombies = List(5) {
            ZombieEntity(it, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        }
        val state = GameSessionState(
            hp = 100,
            maxHp = 100,
            currentMagAmmo = mapOf("pistol" to 12),
            zombies = zombies,
            mapSeed = 1000L
        )
        val result = EmergencySupplyDropDirector.evaluateSupplyDrop(state)
        assertTrue(result.shouldDropBarrel)
        assertFalse(result.shouldDropAmmo)
    }

    @Test
    fun `evaluateSupplyDrop returns dropAmmo when low ammo`() {
        val state = GameSessionState(
            hp = 100,
            maxHp = 100,
            currentMagAmmo = mapOf("pistol" to 2),
            zombies = emptyList(),
            mapSeed = 1000L
        )
        val result = EmergencySupplyDropDirector.evaluateSupplyDrop(state)
        assertFalse(result.shouldDropBarrel)
        assertTrue(result.shouldDropAmmo)
    }
}
