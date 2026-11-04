package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaRedwoodRailyardSpawnFortressVault {
    val DEFINITION = SectionComponentDef(
        componentId = "elona_redwood_railyard_spawn_fortress",
        revision = 1,
        biomeTag = "INDUSTRIAL_RAILYARD",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#...B..B...#",
            "#V........V#",
            "#W........W#",
            "#..........#",
            "D..........D",
            "#..........#",
            "#W........W#",
            "#V........V#",
            "#..........#",
            "#...B..B...#",
            "############"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 750),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 500),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.ARCHED, unlockCostCurrency = 0)
        )
    )
}
