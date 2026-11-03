package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaMistharborPierChokepointAlleyVault {
    val def = SectionComponentDef(
        componentId = "mistharbor_pier_chokepoint_alley",
        biomeTag = "CRYO_HARBOR_PIER",
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "##........##",
            "#.V..##..B.#",
            "##..####..##",
            "#...#..#...#",
            "D...#..#...W",
            "D...#..#...W",
            "#...#..#...#",
            "##..####..##",
            "#.B..##..V.#",
            "##........##",
            "#####..#####"
        ),
        sockets = listOf(
            RoomSocketSpec(
                direction = SocketDirection.NORTH,
                kind = SocketKind.LOCKED_DEBRIS_DOOR,
                unlockCostCurrency = 1000
            ),
            RoomSocketSpec(
                direction = SocketDirection.EAST,
                kind = SocketKind.BARRED_ZOMBIE_WINDOW,
                unlockCostCurrency = 0
            ),
            RoomSocketSpec(
                direction = SocketDirection.SOUTH,
                kind = SocketKind.ARCHED,
                unlockCostCurrency = 0
            ),
            RoomSocketSpec(
                direction = SocketDirection.WEST,
                kind = SocketKind.LOCKED_DEBRIS_DOOR,
                unlockCostCurrency = 1250
            )
        )
    )
}
