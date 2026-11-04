package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

/**
 * Vault Archetype: ChokepointAlley
 * Biome: OVERGROWTH_CRATER_PARK (Alpine Pine Groves, Cabin Barricades & Mirror Lake Bridge)
 * Description: Narrow Boxhead2 barrel-lined corridor with crossfire window shutters.
 * Sockets: N/W (LOCKED_DEBRIS_DOOR), E (BARRED_ZOMBIE_WINDOW), S (ARCHED).
 */
val ElonaSilverlakeWoodsChokepointAlleyVault = SectionComponentDef(
    componentId = "chokepoint_alley",
    revision = 1,
    biomeTag = "OVERGROWTH_CRATER_PARK",
    selectionWeight = 1.0f,
    gridWidth = 12,
    gridHeight = 12,
    rows = listOf(
        "#####DD#####",
        "#V........V#",
        "#.B......B.#",
        "W..........W",
        "W..........W",
        "#.B......B.#",
        "#.B......B.#",
        "W..........W",
        "W..........W",
        "#.B......B.#",
        "#V........V#",
        "#####DD#####"
    ),
    sockets = listOf(
        RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250),
        RoomSocketSpec(SocketDirection.EAST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 1000),
        RoomSocketSpec(SocketDirection.SOUTH, SocketKind.ARCHED, unlockCostCurrency = 0),
        RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250)
    )
)
