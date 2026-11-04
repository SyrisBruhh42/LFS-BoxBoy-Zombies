package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaRedwoodRailyardPackAPunchSanctumVault {
    val SANCTUM_VAULT = SectionComponentDef(
        componentId = "boxboys.redwood.packapunch_sanctum",
        revision = 1,
        biomeTag = "INDUSTRIAL_RAILYARD",
        selectionWeight = 0.5f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#..W....W..#",
            "#.B......B.#",
            "#...U..V...#",
            "W..........W",
            "D..........D",
            "D..........D",
            "W..........W",
            "#...B..B...#",
            "#..W....W..#",
            "#####DD#####",
            "############"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 2500),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 2500)
        )
    )
}
