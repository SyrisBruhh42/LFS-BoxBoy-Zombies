package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaSunvaleBazaarChokepointAlleyVault {

    val componentDef = SectionComponentDef(
        componentId = "vault.bazaar.chokepoint_alley",
        biomeTag = "COLLAPSED_MALL_BAZAAR",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#V..W..W..V#",
            "#..........#",
            "#..B....B..#",
            "W..........W",
            "D..........D",
            "D..........D",
            "W..........W",
            "#..B....B..#",
            "#..........#",
            "#V..W..W..V#",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 750)
        )
    )
}
