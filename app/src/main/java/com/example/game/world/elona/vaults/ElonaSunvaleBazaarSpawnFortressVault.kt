package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaSunvaleBazaarSpawnFortressVault {
    val DEF = SectionComponentDef(
        componentId = "boxboys.spawn.sunvale_bazaar_fortress",
        biomeTag = "COLLAPSED_MALL_BAZAAR",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#..W....W..#",
            "#.V......V.#",
            "#.B......B.#",
            "#..........#",
            "D..........D",
            "D..........D",
            "#..........#",
            "#.B......B.#",
            "#.V......V.#",
            "#..W....W..#",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 750),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 750),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 500)
        )
    )
}
