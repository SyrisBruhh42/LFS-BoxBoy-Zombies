package com.example.game.ai.director.modules

import com.example.game.GameSessionState
import com.example.game.FortificationEntity
import com.example.game.FortificationType
import com.example.game.LootDropEntity
import com.example.game.LootPayloadKind
import com.example.game.LootRarity
import com.example.game.ModularWeaponGenerator

/**
 * Unified BoxBoys Track 2 (Local Open-Source AI Director):
 * Detects low-ammo/high-threat states and schedules tactical barrel/ammo drops.
 */
object EmergencySupplyDropDirector {

    private const val THREAT_ZOMBIE_THRESHOLD = 15
    private const val AMMO_LOW_PERCENTAGE = 30
    private const val CRITICAL_HP_THRESHOLD = 40

    fun evaluateAndDropSupplies(state: GameSessionState): GameSessionState {
        var needsDrop = false

        // 1. High Threat check
        if (state.zombies.size >= THREAT_ZOMBIE_THRESHOLD) {
            needsDrop = true
        }

        // 2. Low Ammo check
        val activeWeapon = state.equippedModularWeapon
        val currentAmmo = state.currentMagAmmo[activeWeapon.baseWeaponId] ?: 0
        if (activeWeapon.effectiveMagSize > 0) {
            val ammoPercent = (currentAmmo * 100) / activeWeapon.effectiveMagSize
            if (ammoPercent <= AMMO_LOW_PERCENTAGE) {
                needsDrop = true
            }
        }

        // 3. Critical HP check
        if (state.hp <= CRITICAL_HP_THRESHOLD) {
            needsDrop = true
        }

        if (!needsDrop) {
            return state
        }

        val newFortifications = state.fortifications.toMutableList()
        val newLootDrops = state.lootDrops.toMutableList()

        // Find current room
        val roomIndex = state.map.rooms.firstOrNull {
            it.bounds.contains(androidx.compose.ui.geometry.Offset(state.playerX, state.playerY))
        }?.index ?: 0

        // Drop a Red Barrel to help with the crowd
        val barrel = FortificationEntity(
            id = (state.fortifications.maxOfOrNull { it.id } ?: 0) + 1,
            kind = FortificationType.RED_BARREL,
            x = state.playerX + 1.5f,
            y = state.playerY + 1.5f,
            roomIndex = roomIndex
        )
        newFortifications.add(barrel)

        // Drop a Tactical Forged Weapon for ammo/upgrades
        val weaponDrop = LootDropEntity(
            id = (state.lootDrops.maxOfOrNull { it.id } ?: 0) + 1,
            x = state.playerX - 1.5f,
            y = state.playerY - 1.5f,
            rarity = LootRarity.UNCOMMON,
            payloadKind = LootPayloadKind.MODULAR_WEAPON,
            weaponPayload = ModularWeaponGenerator.generateProceduralWeapon(
                seed = state.mapSeed + state.wave,
                wave = state.wave,
                guaranteedMinRarity = LootRarity.UNCOMMON
            ),
            spawnedAtWave = state.wave
        )
        newLootDrops.add(weaponDrop)

        return state.copy(
            fortifications = newFortifications,
            lootDrops = newLootDrops
        )
    }
}
