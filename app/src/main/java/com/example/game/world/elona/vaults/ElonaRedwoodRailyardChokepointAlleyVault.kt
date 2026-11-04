package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaRedwoodRailyardChokepointAlleyVault {
    val def = SectionComponentDef(
        componentId = "INDUSTRIAL_RAILYARD_CHOKEPOINT_ALLEY",
        revision = 1,
        biomeTag = "INDUSTRIAL_RAILYARD",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#...B..B...#",
            "#..........#",
            "W...B..B...W",
            "#..........#",
            "D..........D",
            "D..........D",
            "#..........#",
            "W...B..B...W",
            "#..........#",
            "#V..B..B..V#",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 1000),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250)
        )
    )
}
