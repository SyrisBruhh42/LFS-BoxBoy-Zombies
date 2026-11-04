package com.example.game.loot.borderlands.catalog

import com.example.game.ArmorPiece
import com.example.game.ArmorSetArchetype
import com.example.game.ArmorSlot
import com.example.game.LootPayloadKind
import com.example.game.LootRarity
import com.example.game.ModularWeaponGenerator
import com.example.game.loot.borderlands.EquippedOnlyLoadout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldLootDropSpawnerTest {

    @Test
    fun testSpawnWeaponDrop_CreatesValidState() {
        val state = WorldLootDropSpawner.spawnWeaponDrop(
            id = 1,
            x = 100f,
            y = 200f,
            seed = 42L,
            wave = 5,
            guaranteedMinRarity = LootRarity.RARE
        )

        assertEquals(1, state.entity.id)
        assertEquals(100f, state.entity.x)
        assertEquals(200f, state.entity.y)
        assertEquals(LootPayloadKind.MODULAR_WEAPON, state.entity.payloadKind)
        assertNotNull(state.entity.weaponPayload)
        assertNull(state.entity.armorPayload)
        assertTrue(state.entity.rarity.ordinal >= LootRarity.RARE.ordinal)
        assertEquals(0f, state.timeAliveMs)
        assertFalse(state.isPickedUp)
        assertTrue(state.checksum != 0L)
        assertEquals(42L, state.seed)
    }

    @Test
    fun testSpawnArmorDrop_CreatesValidState() {
        val armor = ArmorPiece(
            id = "armor_1",
            slot = ArmorSlot.CHEST_CARAPACE,
            displayName = "Test Chest",
            rarity = LootRarity.EPIC,
            setArchetype = ArmorSetArchetype.CHIAROSCURO_STALKER
        )

        val state = WorldLootDropSpawner.spawnArmorDrop(
            id = 2,
            x = -50f,
            y = 300f,
            armorPiece = armor,
            wave = 3
        )

        assertEquals(2, state.entity.id)
        assertEquals(-50f, state.entity.x)
        assertEquals(300f, state.entity.y)
        assertEquals(LootPayloadKind.RPG_ARMOR_PIECE, state.entity.payloadKind)
        assertNull(state.entity.weaponPayload)
        assertEquals(armor, state.entity.armorPayload)
        assertEquals(LootRarity.EPIC, state.entity.rarity)
        assertEquals(3, state.entity.spawnedAtWave)
    }

    @Test
    fun testTickDropsInPlace_UpdatesTimeAliveAndFiltersExpired() {
        val config = WorldLootDropConfig(dropLifetimeMs = 5000f)
        val state1 = WorldLootDropSpawner.spawnWeaponDrop(1, 0f, 0f, 1L, 1)
        // state2 is expired
        val state2 = WorldLootDropSpawner.spawnWeaponDrop(2, 0f, 0f, 2L, 1)
        state2.timeAliveMs = 4950f

        val drops = mutableListOf(state1, state2)

        // Tick by 0.1s -> 100ms. State2 will become 5050ms and should be removed.
        WorldLootDropSpawner.tickDropsInPlace(drops, 0.1f, config)

        assertEquals(1, drops.size)
        assertEquals(1, drops[0].entity.id)
        assertEquals(100f, drops[0].timeAliveMs)
        assertTrue(drops[0].checksum != 0L) // checksum should change
    }

    @Test
    fun testTickDropsInPlace_FiltersPickedUp() {
        val config = WorldLootDropConfig()
        val state1 = WorldLootDropSpawner.spawnWeaponDrop(1, 0f, 0f, 1L, 1)
        val state2 = WorldLootDropSpawner.spawnWeaponDrop(2, 0f, 0f, 2L, 1)
        state2.isPickedUp = true

        val drops = mutableListOf(state1, state2)

        WorldLootDropSpawner.tickDropsInPlace(drops, 0.05f, config)

        assertEquals(1, drops.size)
        assertEquals(1, drops[0].entity.id)
    }

    @Test
    fun testTickDropsInPlace_ClampsDtAndMaxDrops() {
        val config = WorldLootDropConfig(maxActiveDrops = 2)
        val state1 = WorldLootDropSpawner.spawnWeaponDrop(1, 0f, 0f, 1L, 1)
        val state2 = WorldLootDropSpawner.spawnWeaponDrop(2, 0f, 0f, 2L, 1)
        val state3 = WorldLootDropSpawner.spawnWeaponDrop(3, 0f, 0f, 3L, 1)

        val drops = mutableListOf(state1, state2, state3)

        // dt=1.0s should be clamped to 0.1s
        WorldLootDropSpawner.tickDropsInPlace(drops, 1.0f, config)

        assertEquals(2, drops.size)
        // Check that time has updated, effectively clamped to 100ms
        assertEquals(100f, drops[0].timeAliveMs)
        assertEquals(100f, drops[1].timeAliveMs)
    }

    @Test
    fun testGetLootSwapComparisonCard_InRange_ReturnsCard() {
        val config = WorldLootDropConfig(interactionRadiusPx = 100f)
        val drop = WorldLootDropSpawner.spawnWeaponDrop(1, 100f, 100f, 1L, 1)
        val weapon = ModularWeaponGenerator.generateProceduralWeapon(2L, 1)
        val loadout = EquippedOnlyLoadout(primaryWeapon = weapon)

        val card = WorldLootDropSpawner.getLootSwapComparisonCard(drop, loadout, 150f, 100f, config)

        assertNotNull(card)
        assertEquals(drop.entity.weaponPayload?.instanceId, card?.candidateId)
        assertEquals(4, card?.statDeltas?.size)
    }

    @Test
    fun testGetLootSwapComparisonCard_OutOfRange_ReturnsNull() {
        val config = WorldLootDropConfig(interactionRadiusPx = 50f)
        val drop = WorldLootDropSpawner.spawnWeaponDrop(1, 0f, 0f, 1L, 1)
        val weapon = ModularWeaponGenerator.generateProceduralWeapon(2L, 1)
        val loadout = EquippedOnlyLoadout(primaryWeapon = weapon)

        val card = WorldLootDropSpawner.getLootSwapComparisonCard(drop, loadout, 100f, 100f, config)

        assertNull(card)
    }

    @Test
    fun testProcessLootSwapInteraction_Weapon_InRange() {
        val config = WorldLootDropConfig(interactionRadiusPx = 100f)
        val drop = WorldLootDropSpawner.spawnWeaponDrop(1, 0f, 0f, 1L, 1)
        val playerWeapon = ModularWeaponGenerator.generateProceduralWeapon(2L, 1)
        val loadout = EquippedOnlyLoadout(primaryWeapon = playerWeapon)

        val (newDrop, result) = WorldLootDropSpawner.processLootSwapInteraction(drop, loadout, 10f, 10f, config)
        val (newLoadout, displacedDrop) = result

        assertTrue(newDrop.isPickedUp)
        assertEquals(drop.entity.weaponPayload, newLoadout.primaryWeapon)
        assertNotNull(displacedDrop)
        assertEquals(playerWeapon, displacedDrop?.entity?.weaponPayload)
        assertEquals(10f, displacedDrop?.entity?.x)
        assertEquals(10f, displacedDrop?.entity?.y)
    }

    @Test
    fun testProcessLootSwapInteraction_Armor_InRange() {
        val config = WorldLootDropConfig(interactionRadiusPx = 100f)
        val dropArmor = ArmorPiece("a1", ArmorSlot.CHEST_CARAPACE, "Drop", LootRarity.RARE, ArmorSetArchetype.CHIAROSCURO_STALKER)
        val drop = WorldLootDropSpawner.spawnArmorDrop(1, 0f, 0f, dropArmor, 1)

        val playerWeapon = ModularWeaponGenerator.generateProceduralWeapon(2L, 1)
        val playerArmor = ArmorPiece("a2", ArmorSlot.CHEST_CARAPACE, "Player", LootRarity.COMMON, ArmorSetArchetype.CHIAROSCURO_STALKER)
        val loadout = EquippedOnlyLoadout(primaryWeapon = playerWeapon, equippedShieldArmor = playerArmor)

        val (newDrop, result) = WorldLootDropSpawner.processLootSwapInteraction(drop, loadout, 10f, 10f, config)
        val (newLoadout, displacedDrop) = result

        assertTrue(newDrop.isPickedUp)
        assertEquals(dropArmor, newLoadout.equippedShieldArmor)
        assertNotNull(displacedDrop)
        assertEquals(playerArmor, displacedDrop?.entity?.armorPayload)
    }

    @Test
    fun testProcessLootSwapInteraction_OutOfRange() {
        val config = WorldLootDropConfig(interactionRadiusPx = 10f)
        val drop = WorldLootDropSpawner.spawnWeaponDrop(1, 0f, 0f, 1L, 1)
        val playerWeapon = ModularWeaponGenerator.generateProceduralWeapon(2L, 1)
        val loadout = EquippedOnlyLoadout(primaryWeapon = playerWeapon)

        val (newDrop, result) = WorldLootDropSpawner.processLootSwapInteraction(drop, loadout, 100f, 100f, config)
        val (newLoadout, displacedDrop) = result

        assertFalse(newDrop.isPickedUp)
        assertEquals(loadout.primaryWeapon, newLoadout.primaryWeapon)
        assertNull(displacedDrop)
    }

    @Test
    fun testStateSerialization_RoundtripWeapon() {
        val state = WorldLootDropSpawner.spawnWeaponDrop(1, 10f, 20f, 42L, 3, LootRarity.EPIC)
        val map = state.toMap()
        val deserialized = WorldLootDropState.fromMap(map)

        assertEquals(state.entity.id, deserialized.entity.id)
        assertEquals(state.entity.x, deserialized.entity.x)
        assertEquals(state.entity.y, deserialized.entity.y)
        assertEquals(state.entity.rarity, deserialized.entity.rarity)
        assertEquals(state.entity.payloadKind, deserialized.entity.payloadKind)
        assertEquals(state.timeAliveMs, deserialized.timeAliveMs)
        assertEquals(state.isPickedUp, deserialized.isPickedUp)
        assertEquals(state.entity.spawnedAtWave, deserialized.entity.spawnedAtWave)
        assertEquals(state.checksum, deserialized.checksum)
        assertEquals(state.seed, deserialized.seed)

        // Assert payload completeness
        assertNotNull("Weapon payload should be recreated via seed hash", deserialized.entity.weaponPayload)
        assertEquals(state.entity.rarity, deserialized.entity.weaponPayload?.rarity)
    }

    @Test
    fun testDeterminism_SameSeedSameChecksum() {
        val state1 = WorldLootDropSpawner.spawnWeaponDrop(1, 0f, 0f, 99L, 1)
        val state2 = WorldLootDropSpawner.spawnWeaponDrop(1, 0f, 0f, 99L, 1)

        assertEquals(state1.checksum, state2.checksum)
        assertEquals(state1.entity.weaponPayload?.instanceId, state2.entity.weaponPayload?.instanceId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSpawnWeaponDrop_NaNCoordinates() {
        WorldLootDropSpawner.spawnWeaponDrop(1, Float.NaN, 0f, 1L, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSpawnArmorDrop_NegativeWave() {
        val armor = ArmorPiece("a", ArmorSlot.HELMET_VISOR, "A", LootRarity.COMMON, ArmorSetArchetype.VOID_ALCHEMIST)
        WorldLootDropSpawner.spawnArmorDrop(1, 0f, 0f, armor, 0)
    }
}
