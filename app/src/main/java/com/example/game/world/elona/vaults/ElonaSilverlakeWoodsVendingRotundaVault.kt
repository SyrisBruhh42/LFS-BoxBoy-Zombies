package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaSilverlakeWoodsVendingRotundaVault {
    val VENDING_ROTUNDA_DEF = SectionComponentDef(
        componentId = "silverlake.vault.vending_rotunda",
        biomeTag = "OVERGROWTH_CRATER_PARK",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "###V....V###",
            "##........##",
            "#...BBBB...#",
            "W..........W",
            "D..........D",
            "D..........D",
            "W..........W",
            "#...BBBB...#",
            "##........##",
            "###V....V###",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 750),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.ARCHED, unlockCostCurrency = 750),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1000),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 1000)
        )
    )
}
