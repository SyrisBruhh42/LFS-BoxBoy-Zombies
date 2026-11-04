package com.example.game.world.elona.vaults

import com.example.game.contracts.ElonaRoomBlueprint
import com.example.game.contracts.ElonaVaultBlueprintProvider
import com.example.game.contracts.Fnv1a64Hasher
import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import kotlin.math.cos
import kotlin.math.sin

/**
 * Production Elona Socket-Frontier Vault Module: [ElonaOakridgePlazaDevilBossArenaVault].
 * Archetype: DEVIL_BOSS_ARENA | Provides deterministic 12x12 grid geometry, barricade/barrel/vending
 * coordinates, wave-scaled hazard telemetry, and 64-bit FNV-1a topology verification.
 */
object ElonaOakridgePlazaDevilBossArenaVault : ElonaVaultBlueprintProvider {
    val VaultDef = SectionComponentDef(
        componentId = "DEVIL_BOSS_ARENA_OAKRIDGE_01",
        revision = 1,
        biomeTag = "DOWNTOWN_NEON_PLAZA",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "####DD######",
            "#...##.....#",
            "#...W......#",
            "D.V.....B..D",
            "D...####...D",
            "#...####...#",
            "#...####...#",
            "D...####...D",
            "D..B.....V.D",
            "#......W...#",
            "#.....##...#",
            "######AA####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1500),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 750)
        )
    )

    override fun toSectionComponentDef(): SectionComponentDef = VaultDef

    override fun toElonaRoomBlueprint(): ElonaRoomBlueprint {
        val comp = toSectionComponentDef()
        val barricades = Fnv1a64Hasher.extractCoordinates(comp, 'W')
        val barrels = Fnv1a64Hasher.extractCoordinates(comp, 'B')
        val vendors = Fnv1a64Hasher.extractCoordinates(comp, 'V') +
            Fnv1a64Hasher.extractCoordinates(comp, 'P') +
            Fnv1a64Hasher.extractCoordinates(comp, 'U')
        val lootWeights = linkedMapOf(
            "COMMON_MUNITIONS" to 0.42,
            "RARE_MODULAR_WEAPON" to 0.28,
            "EPIC_SHIELD_CORE" to 0.18,
            "ABYSSAL_RELIC_CACHE" to 0.12
        )
        return ElonaRoomBlueprint(
            componentDef = comp,
            biomeTheme = comp.biomeTag,
            vaultArchetype = "DEVIL_BOSS_ARENA",
            hazardLayout = listOf("DEVIL_HELLFIRE_RING", "CHAIN_BARREL_CLUSTER", "ELITE_HORDE_RING"),
            barricadeAnchors = barricades,
            explosiveBarrelAnchors = barrels,
            vendingMachineAnchors = vendors,
            lootChestSpawnWeights = lootWeights,
            baseThreatRating = 2.4,
            checksum = computeChecksum()
        )
    }

    override fun evaluateVaultHazardTick(
        tick: Long,
        playersInVault: Int,
        wave: Int
    ): Map<String, Double> {
        val safeTick = tick.coerceAtLeast(0L)
        val safePlayers = playersInVault.coerceIn(1, 4)
        val safeWave = wave.coerceAtLeast(1)
        val blueprint = toElonaRoomBlueprint()
        val scaledThreat = blueprint.scaleThreatForWave(safeWave, safePlayers)
        val phaseMod = 1.0 + 0.15 * sin(safeTick.toDouble() * 0.05)
        val barrelDensity = blueprint.explosiveBarrelAnchors.size.toDouble().coerceAtLeast(1.0)
        val windowCount = blueprint.barricadeAnchors.size.toDouble().coerceAtLeast(1.0)
        val vendorBonus = 1.0 + 0.08 * cos(safeWave.toDouble() * 0.25)
        return linkedMapOf(
            "scaledThreatRating" to (scaledThreat * phaseMod),
            "barricadePressureIndex" to (windowCount * safeWave * 0.35 * safePlayers),
            "barrelChainPotential" to (barrelDensity * 125.0 * phaseMod),
            "vendingEconomyFlow" to (blueprint.vendingMachineAnchors.size.coerceAtLeast(1) * 100.0 * vendorBonus),
            "checksumTag" to (computeChecksum() and 0xFFFFL).toDouble()
        )
    }

    override fun computeChecksum(): Long {
        val comp = toSectionComponentDef()
        var h = Fnv1a64Hasher.hashSectionComponentDef(comp)
        h = Fnv1a64Hasher.mixString(h, "DEVIL_BOSS_ARENA")
        h = Fnv1a64Hasher.mixDouble(h, 2.4)
        return h
    }
}
