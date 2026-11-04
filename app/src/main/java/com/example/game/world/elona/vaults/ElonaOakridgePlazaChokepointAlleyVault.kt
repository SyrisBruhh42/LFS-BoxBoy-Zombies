package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

/**
 * Deterministic 12x12 Vault Archetype: ChokepointAlley for DOWNTOWN_NEON_PLAZA
 * Includes N/E/S/W RoomSocketSpecs: LOCKED_DEBRIS_DOOR, BARRED_ZOMBIE_WINDOW, ARCHED.
 * Features barricadable window tiles (W), explosive barrel clusters (B), and Vending Machine slots (V).
 */
object ElonaOakridgePlazaChokepointAlleyVault {
    val def = SectionComponentDef(
        componentId = "boxboys.vault.oakridge_plaza_chokepoint_alley",
        biomeTag = "DOWNTOWN_NEON_PLAZA",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#W...B...W##",
            "#V.......V##",
            "##W.....W###",
            "##.......###",
            "D.........D#",
            "D.........D#",
            "##.......###",
            "##W.....W###",
            "#V.......V##",
            "#W...B...W##",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.ARCHED, unlockCostCurrency = 750),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 500)
        )
    )
}
