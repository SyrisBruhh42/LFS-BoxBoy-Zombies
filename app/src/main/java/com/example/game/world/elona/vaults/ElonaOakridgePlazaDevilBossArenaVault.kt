package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

object ElonaOakridgePlazaDevilBossArenaVault {

    val VaultDef = SectionComponentDef(
        componentId = "DEVIL_BOSS_ARENA_OAKRIDGE_01",
        revision = 1,
        biomeTag = "DOWNTOWN_NEON_PLAZA",
        selectionWeight = 1.0f,
        gridWidth = 12,
        gridHeight = 12,
        rows = listOf(
            "####DD######",
            "#...##.....#",
            "#...W......#",
            "D.V.....B..D",
            "D...####...D",
            "#...####...#",
            "#...####...#",
            "D...####...D",
            "D..B.....V.D",
            "#......W...#",
            "#.....##...#",
            "######AA####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1500),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = 1250),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.ARCHED, unlockCostCurrency = 0),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.BARRED_ZOMBIE_WINDOW, unlockCostCurrency = 750)
        )
    )
}
