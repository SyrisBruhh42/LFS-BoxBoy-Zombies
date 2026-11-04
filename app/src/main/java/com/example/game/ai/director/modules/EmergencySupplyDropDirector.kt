package com.example.game.ai.director.modules

import com.example.game.GameSessionState
import com.example.game.FortificationEntity
import com.example.game.FortificationType
import com.example.game.LootDropEntity
import com.example.game.LootPayloadKind
import com.example.game.LootRarity
import com.example.game.ModularWeaponGenerator

data class SupplyDropDecision(
    val shouldDropAmmo: Boolean,
    val shouldDropBarrel: Boolean
)

/**
 * Unified BoxBoys Track 2 (Local Open-Source AI Director):
 * Detects low-ammo/high-threat states and schedules tactical barrel/ammo drops.
 */
object EmergencySupplyDropDirector {

    private const val THREAT_ZOMBIE_THRESHOLD = 15
    private const val AMMO_LOW_PERCENTAGE = 30
    private const val CRITICAL_HP_THRESHOLD = 40

    fun evaluateSupplyDrop(state: GameSessionState): SupplyDropDecision {
        val shouldDropBarrel = state.hp <= CRITICAL_HP_THRESHOLD || state.zombies.size >= 5
        val ammoCount = state.currentMagAmmo[state.equippedModularWeapon.baseWeaponId]
            ?: state.currentMagAmmo.values.firstOrNull()
            ?: 12
        val shouldDropAmmo = ammoCount <= 3
        return SupplyDropDecision(
            shouldDropAmmo = shouldDropAmmo,
            shouldDropBarrel = shouldDropBarrel
        )
    }

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


fun evaluateEmergencySupplyDropDirectorLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "EmergencySupplyDropDirector") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
