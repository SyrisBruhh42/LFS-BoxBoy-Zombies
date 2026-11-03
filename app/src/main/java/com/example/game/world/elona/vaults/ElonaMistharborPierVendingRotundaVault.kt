package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

val ElonaMistharborPierVendingRotundaVaultDef = SectionComponentDef(
    componentId = "boxboys.vault.mistharbor_pier.vending_rotunda",
    biomeTag = "CRYO_HARBOR_PIER",
    selectionWeight = 1.0f,
    gridWidth = 12,
    gridHeight = 12,
    rows = listOf(
        "#####DD#####",
        "###......###",
        "##..V..V..##",
        "#.BB....BB.#",
        "D..........W",
        "D..........W",
        "D..........W",
        "D..........W",
        "#.BB....BB.#",
        "##..V..V..##",
        "###......###",
        "#####..#####"
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
            unlockCostCurrency = 500
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
