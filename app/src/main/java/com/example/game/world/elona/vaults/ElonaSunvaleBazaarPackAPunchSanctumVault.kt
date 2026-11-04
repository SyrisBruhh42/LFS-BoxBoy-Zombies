package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaSunvaleBazaarPackAPunchSanctumVault {
    val componentDef = SectionComponentDef(
        componentId = "elona.vault.sunvale_bazaar_pap_sanctum",
        biomeTag = "COLLAPSED_MALL_BAZAAR",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#..........#",
            "#.B.V..V.B.#",
            "#..........#",
            "#..........#",
            "D..........W",
            "D....U.....W",
            "#..........#",
            "#..........#",
            "#.B.V..V.B.#",
            "#..........#",
            "#####..#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 500),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000)
        )
    )
}
