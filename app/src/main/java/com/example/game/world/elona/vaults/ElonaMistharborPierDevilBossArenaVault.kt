package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

/**
 * Biome: CRYO_HARBOR_PIER (Coastal Boardwalks, Cryo Boathouses & Foggy Docks).
 * Vault Archetype: DevilBossArena (Expansive pillar-ringed plaza with chain-reaction red/cryo barrel clusters).
 */
object ElonaMistharborPierDevilBossArenaVault {
    val def = SectionComponentDef(
        componentId = "cryo_harbor_pier_devil_boss_arena",
        biomeTag = "CRYO_HARBOR_PIER",
        rows = listOf(
            "#####DD#####",
            "#W........W#",
            "#.BB....BB.#",
            "#.B......B.#",
            "#....VV....#",
            "D..........D",
            "D..........D",
            "#....VV....#",
            "#.B......B.#",
            "#.BB....BB.#",
            "#W........W#",
            "#####DD#####"
        ),
        sockets = listOf(
            RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, 1000),
            RoomSocketSpec(SocketDirection.EAST, SocketKind.BARRED_ZOMBIE_WINDOW, 500),
            RoomSocketSpec(SocketDirection.SOUTH, SocketKind.ARCHED, 0),
            RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR, 1000)
        )
    )
}
