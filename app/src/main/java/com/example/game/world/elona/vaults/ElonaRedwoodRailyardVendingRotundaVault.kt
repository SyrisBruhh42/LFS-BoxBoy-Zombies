package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaRedwoodRailyardVendingRotundaVault {

    fun getComponentDef(): SectionComponentDef {
        return SectionComponentDef(
            componentId = "redwood_railyard_vending_rotunda",
            biomeTag = "INDUSTRIAL_RAILYARD",
            gridWidth = 12,
            gridHeight = 12,
            rows = listOf(
                "####D#######",
                "###...######",
                "##.....V####",
                "#.......####",
                "W.......B###",
                "#........###",
                "#........###",
                "##V.......##",
                "###......###",
                "####....V###",
                "####....####",
                "####D#######"
            ),
            sockets = listOf(
                RoomSocketSpec(
                    direction = SocketDirection.NORTH,
                    kind = SocketKind.LOCKED_DEBRIS_DOOR,
                    offsetTiles = 0,
                    unlockCostCurrency = 750
                ),
                RoomSocketSpec(
                    direction = SocketDirection.EAST,
                    kind = SocketKind.BARRED_ZOMBIE_WINDOW,
                    offsetTiles = 0,
                    unlockCostCurrency = 0
                ),
                RoomSocketSpec(
                    direction = SocketDirection.SOUTH,
                    kind = SocketKind.ARCHED,
                    offsetTiles = 0,
                    unlockCostCurrency = 0
                ),
                RoomSocketSpec(
                    direction = SocketDirection.WEST,
                    kind = SocketKind.LOCKED_DEBRIS_DOOR,
                    offsetTiles = 0,
                    unlockCostCurrency = 750
                )
            )
        )
    }
}
