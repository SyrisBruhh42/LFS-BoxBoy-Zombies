package com.example.game.world.elona.vaults

import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class ElonaSilverlakeWoodsSpawnFortressVaultTest {

    @Test
    fun testVaultDefinition() {
        val def = ElonaSilverlakeWoodsSpawnFortressVault.componentDef

        // Check core properties
        assertEquals("SpawnFortress_Silverlake", def.componentId)
        assertEquals("OVERGROWTH_CRATER_PARK", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)

        // Check rows match expected size
        assertEquals(12, def.rows.size)
        def.rows.forEach { row ->
            assertEquals(12, row.length)
        }

        // Check sockets
        assertEquals(4, def.sockets.size)
        val dirs = def.sockets.map { it.direction }.toSet()
        assertEquals(setOf(SocketDirection.NORTH, SocketDirection.EAST, SocketDirection.SOUTH, SocketDirection.WEST), dirs)

        // Verify requested tiles exist in the layout
        val allTiles = def.rows.joinToString("")
        assertTrue("Expected 'W' for Barricadable Zombie Window", allTiles.contains("W"))
        assertTrue("Expected 'B' for Explosive Barrel Cluster", allTiles.contains("B"))
        assertTrue("Expected 'V' for Vending Machine", allTiles.contains("V"))
        assertTrue("Expected 'D' for Debris Door", allTiles.contains("D"))
    }

    @Test
    fun testBfsSolvability() {
        val def = ElonaSilverlakeWoodsSpawnFortressVault.componentDef
        val walkableTiles = mutableSetOf<Pair<Int, Int>>()

        for (y in def.rows.indices) {
            for (x in def.rows[y].indices) {
                val tile = def.rows[y][x]
                if (tile != '#') { // Treat all non-wall tiles as walkable/interactable for reachability
                    walkableTiles.add(x to y)
                }
            }
        }

        assertTrue("Vault must have walkable area", walkableTiles.isNotEmpty())

        val startTile = walkableTiles.first()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(startTile)
        visited.add(startTile)

        val dirs = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0) // N, E, S, W

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()

            for (d in dirs) {
                val nx = curr.first + d.first
                val ny = curr.second + d.second
                val npos = nx to ny

                if (walkableTiles.contains(npos) && visited.add(npos)) {
                    queue.add(npos)
                }
            }
        }

        assertEquals("All non-wall tiles should be reachable (BFS connected)", walkableTiles.size, visited.size)
    }
}
