package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

/**
 * Vault Archetype: VendingRotunda (Octagonal concourse housing Dr. Zed, Torgue, and Marcus Vending Machines)
 * Biome: DOWNTOWN_NEON_PLAZA (Brick Shophouses, Neon Arcade & Mill Creek Chokepoints)
 * Size: 12x12
 */
object ElonaOakridgePlazaVendingRotundaVault {
    val def = SectionComponentDef(
        componentId = "OAKRIDGE_VENDING_ROTUNDA_01",
        revision = 1,
        biomeTag = "DOWNTOWN_NEON_PLAZA",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "####WDDW####",
            "###......###",
            "##.B....B.##",
            "#V........V#",
            "W..........W",
            "D..........D",
            "D..........D",
            "W..........W",
            "#V........V#",
            "##.B....B.##",
            "###......###",
            "####WDDW####"
        ),
        sockets = listOf(
            RoomSocketSpec(
                direction = SocketDirection.NORTH,
                kind = SocketKind.LOCKED_DEBRIS_DOOR,
                unlockCostCurrency = 1250
            ),
            RoomSocketSpec(
                direction = SocketDirection.EAST,
                kind = SocketKind.ARCHED,
                unlockCostCurrency = 750
            ),
            RoomSocketSpec(
                direction = SocketDirection.SOUTH,
                kind = SocketKind.BARRED_ZOMBIE_WINDOW,
                unlockCostCurrency = 1000
            ),
            RoomSocketSpec(
                direction = SocketDirection.WEST,
                kind = SocketKind.LOCKED_DEBRIS_DOOR,
                unlockCostCurrency = 1250
            )
        )
    )
}
