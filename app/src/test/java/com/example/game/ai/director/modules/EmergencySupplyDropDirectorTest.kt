package com.example.game.ai.director.modules

import com.example.game.GameSessionState
import com.example.game.ZombieEntity
import com.example.game.ZombieArchetype
import com.example.game.ModularWeaponGenerator
import com.example.game.FortificationType
import com.example.game.LootPayloadKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencySupplyDropDirectorTest {

    @Test
    fun testNoDropWhenConditionsNotMet() {
        val initialState = GameSessionState(
            hp = 100,
            zombies = emptyList(),
            currentMagAmmo = mapOf("pistol" to 15),
            equippedModularWeapon = ModularWeaponGenerator.createStarterModularWeapon("pistol")
        )

        val newState = EmergencySupplyDropDirector.evaluateAndDropSupplies(initialState)

        assertEquals(0, newState.fortifications.size)
        assertEquals(0, newState.lootDrops.size)
    }

    @Test
    fun testDropWhenHighThreat() {
        val zombies = List(20) { ZombieEntity(id = it, x = 0f, y = 0f, archetype = ZombieArchetype.SHAMBLER, hp = 100, maxHp = 100) }
        val initialState = GameSessionState(
            hp = 100,
            zombies = zombies,
            currentMagAmmo = mapOf("pistol" to 15),
            equippedModularWeapon = ModularWeaponGenerator.createStarterModularWeapon("pistol")
        )

        val newState = EmergencySupplyDropDirector.evaluateAndDropSupplies(initialState)

        assertTrue(newState.fortifications.any { it.kind == FortificationType.RED_BARREL })
        assertTrue(newState.lootDrops.any { it.payloadKind == LootPayloadKind.MODULAR_WEAPON })
    }

    @Test
    fun testDropWhenLowAmmo() {
        val initialState = GameSessionState(
            hp = 100,
            zombies = emptyList(),
            currentMagAmmo = mapOf("pistol" to 1), // Low ammo
            equippedModularWeapon = ModularWeaponGenerator.createStarterModularWeapon("pistol")
        )

        val newState = EmergencySupplyDropDirector.evaluateAndDropSupplies(initialState)

        assertTrue(newState.fortifications.any { it.kind == FortificationType.RED_BARREL })
        assertTrue(newState.lootDrops.any { it.payloadKind == LootPayloadKind.MODULAR_WEAPON })
    }

    @Test
    fun testDropWhenCriticalHp() {
        val initialState = GameSessionState(
            hp = 20, // Critical HP
            zombies = emptyList(),
            currentMagAmmo = mapOf("pistol" to 15),
            equippedModularWeapon = ModularWeaponGenerator.createStarterModularWeapon("pistol")
        )

        val newState = EmergencySupplyDropDirector.evaluateAndDropSupplies(initialState)

        assertTrue(newState.fortifications.any { it.kind == FortificationType.RED_BARREL })
        assertTrue(newState.lootDrops.any { it.payloadKind == LootPayloadKind.MODULAR_WEAPON })
    }
}
