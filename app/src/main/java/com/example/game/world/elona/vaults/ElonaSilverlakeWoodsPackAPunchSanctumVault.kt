package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaSilverlakeWoodsPackAPunchSanctumVault {
    val COMPONENT_DEF = SectionComponentDef(
        componentId = "PackAPunchSanctum_OVERGROWTH_CRATER_PARK",
        biomeTag = "OVERGROWTH_CRATER_PARK",
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "#..........#",
            "#..B....V..#",
            "#..........#",
            "#..........#",
            "D..........W",
            "D..........W",
            "#..........#",
            "#..........#",
            "#..V....B..#",
            "#..........#",
            "#####..#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.BARRED_ZOMBIE_WINDOW),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.ARCHED),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR)
        )
    )
}
