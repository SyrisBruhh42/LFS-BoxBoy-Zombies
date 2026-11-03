package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind

/**
 * Vault Archetype: VendingRotunda (Octagonal concourse housing Dr. Zed, Torgue, and Marcus Vending Machines)
 * Biome: COLLAPSED_MALL_BAZAAR
 */
object ElonaSunvaleBazaarVendingRotundaVault {
    fun create(): SectionComponentDef {
        return SectionComponentDef(
            componentId = "elona.vault.sunvale_bazaar.vending_rotunda",
            biomeTag = "COLLAPSED_MALL_BAZAAR",
            selectionWeight = 1.0f,
            gridWidth = 12,
            gridHeight = 12,
            rows = listOf(
                "#####DD#####",
                "###..W...###",
                "##..V..V..##",
                "#....B.....#",
                "#V........V#",
                "D..........D",
                "D..........D",
                "#V........V#",
                "#....B.....#",
                "##..V..V..##",
                "###..W...###",
                "#####DD#####"
            ),
            sockets = listOf(
                RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR),
                RoomSocketSpec(SocketDirection.EAST, SocketKind.BARRED_ZOMBIE_WINDOW),
                RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR),
                RoomSocketSpec(SocketDirection.WEST, SocketKind.ARCHED)
            )
        )
    }
}
