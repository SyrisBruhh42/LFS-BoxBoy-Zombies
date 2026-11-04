package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaSilverlakeWoodsDevilBossArenaVault {
    val Def = SectionComponentDef(
        componentId = "boxboys.vault.silverlake_woods_devil_boss_arena",
        biomeTag = "OVERGROWTH_CRATER_PARK",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "#####DD#####",
            "##.W....W.##",
            "#V...BB...V#",
            "W...####...W",
            "W...####...W",
            "D..........D",
            "D..........D",
            "W...####...W",
            "W...####...W",
            "#V...BB...V#",
            "##.W....W.##",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 500)
        )
    )
}
