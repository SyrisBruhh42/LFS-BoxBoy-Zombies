package com.example.game.world.elona.vaults

import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

/**
 * Vault Archetype: PackAPunchSanctum (High-threat elemental forge room with loop-back shortcut portcullis)
 * Biome: DOWNTOWN_NEON_PLAZA
 * Features:
 * - 12x12 Deterministic layout
 * - N/E/S/W RoomSocketSpec entries (LOCKED_DEBRIS_DOOR, BARRED_ZOMBIE_WINDOW, ARCHED)
 * - Barricadable window tiles (W)
 * - explosive barrel clusters (B)
 * - Vending Machine slots (V)
 */
object ElonaOakridgePlazaPackAPunchSanctumVault {
    val COMPONENT_DEF = SectionComponentDef(
        componentId = "vault_oakridge_plaza_pap_sanctum",
        biomeTag = "DOWNTOWN_NEON_PLAZA",
        rows = listOf(
            "#####DD#####",
            "#...W..W...#",
            "#V..B..B..V#",
            "#...#..#...#",
            "W...B..B...W",
            "D.#.B..B.#.D",
            "D.#.B..B.#.D",
            "W...B..B...W",
            "#...#..#...#",
            "#V..B..B..V#",
            "#...W..W...#",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1500),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 0)
        )
    )
}
