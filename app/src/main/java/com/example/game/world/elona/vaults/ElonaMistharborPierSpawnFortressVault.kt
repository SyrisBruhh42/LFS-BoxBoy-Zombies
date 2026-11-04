package com.example.game.world.elona.vaults

import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaMistharborPierSpawnFortressVault {
    val def = SectionComponentDef(
        componentId = "boxboys.mistharbor_pier.spawn_fortress",
        revision = 1,
        biomeTag = "CRYO_HARBOR_PIER",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#V..W..W..V#",
            "#.B......B.#",
            "#..........#",
            "W..........W",
            "D..........D",
            "D..........D",
            "W..........W",
            "#..........#",
            "#.B......B.#",
            "#V..W..W..V#",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250)
        )
    )
}
