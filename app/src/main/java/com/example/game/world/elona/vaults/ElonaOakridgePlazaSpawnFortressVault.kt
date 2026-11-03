package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaOakridgePlazaSpawnFortressVault {
    val def = SectionComponentDef(
        componentId = "spawn_fortress_plaza",
        biomeTag = "DOWNTOWN_NEON_PLAZA",
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#..B....B..#",
            "#.V......V.#",
            "#..........#",
            "W..........W",
            "D..........D",
            "D..........D",
            "W..........W",
            "#..........#",
            "#.V......V.#",
            "#..B....B..#",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 500),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 750),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 0)
        )
    )
}
