package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaRedwoodRailyardDevilBossArenaVault {
    val COMPONENT_DEF = SectionComponentDef(
        componentId = "boxboys.vault.redwood_railyard_devil_boss_arena",
        biomeTag = "INDUSTRIAL_RAILYARD",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#..V....V..#",
            "#.B..WW..B.#",
            "W...B..B...W",
            "W.W......W.W",
            "D..........D",
            "D..........D",
            "W.W......W.W",
            "W...B..B...W",
            "#.B..WW..B.#",
            "#..V....V..#",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 750),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000)
        )
    )
}
