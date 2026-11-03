package com.example.game.world.elona.vaults

import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class ElonaSunvaleBazaarSpawnFortressVaultTest {

    @Test
    fun test_vaultProperties() {
        val def = ElonaSunvaleBazaarSpawnFortressVault.DEF

        assertEquals("boxboys.spawn.sunvale_bazaar_fortress", def.componentId)
        assertEquals("COLLAPSED_MALL_BAZAAR", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)

        val northSocket = def.sockets.find { it.direction == SocketDirection.NORTH }
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, northSocket?.kind)

        val eastSocket = def.sockets.find { it.direction == SocketDirection.EAST }
        assertEquals(SocketKind.ARCHED, eastSocket?.kind)

        val southSocket = def.sockets.find { it.direction == SocketDirection.SOUTH }
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, southSocket?.kind)

        val westSocket = def.sockets.find { it.direction == SocketDirection.WEST }
        assertEquals(SocketKind.BARRED_ZOMBIE_WINDOW, westSocket?.kind)

        var wCount = 0
        var bCount = 0
        var vCount = 0
        for (row in def.rows) {
            wCount += row.count { it == 'W' }
            bCount += row.count { it == 'B' }
            vCount += row.count { it == 'V' }
        }
        assertEquals(4, wCount)
        assertEquals(4, bCount)
        assertEquals(4, vCount)
    }

    @Test
    fun test_bfsSolvability_walkableFloorIsConnected() {
        val def = ElonaSunvaleBazaarSpawnFortressVault.DEF
        val walkableTiles = mutableSetOf<Pair<Int, Int>>()

        for (y in def.rows.indices) {
            val row = def.rows[y]
            for (x in row.indices) {
                if (row[x] == '.') {
                    walkableTiles.add(x to y)
                }
            }
        }

        assertTrue("Should have walkable tiles", walkableTiles.isNotEmpty())

        val startNode = walkableTiles.first()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(startNode)
        visited.add(startNode)

        val directions = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            for ((dx, dy) in directions) {
                val nextNode = (current.first + dx) to (current.second + dy)
                if (walkableTiles.contains(nextNode) && visited.add(nextNode)) {
                    queue.add(nextNode)
                }
            }
        }

        assertEquals(
            "All walkable floor tiles should form a single connected component",
            walkableTiles.size,
            visited.size
        )
    }
}
