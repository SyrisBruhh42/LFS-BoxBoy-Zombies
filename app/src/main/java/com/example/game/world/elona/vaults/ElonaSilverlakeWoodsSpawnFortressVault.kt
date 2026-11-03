package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaSilverlakeWoodsSpawnFortressVault {
    val componentDef = SectionComponentDef(
        componentId = "SpawnFortress_Silverlake",
        revision = 1,
        biomeTag = "OVERGROWTH_CRATER_PARK",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#..........#",
            "#..B....B..#",
            "#..........#",
            "W..........W",
            "...........#",
            "...........#",
            "W..........W",
            "#..........#",
            "#..V....V..#",
            "#..........#",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 750),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 500),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 750),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.ARCHED, unlockCostCurrency = 0)
        )
    )
}
