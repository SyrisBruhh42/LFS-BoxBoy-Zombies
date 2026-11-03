package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaSunvaleBazaarDevilBossArenaVault {
    val COMPONENT_DEF = SectionComponentDef(
        componentId = "boxboys.vault.sunvale_bazaar.devil_boss_arena",
        biomeTag = "COLLAPSED_MALL_BAZAAR",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#V..W..W..V#",
            "#.B......B.#",
            "#..B....B..#",
            "W..........W",
            "D..........D",
            "D..........D",
            "W..........W",
            "#..B....B..#",
            "#.B......B.#",
            "#####DD#####",
            "############"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 500),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.ARCHED, unlockCostCurrency = 0)
        )
    )
}
