package com.example.game.world.elona.vaults

import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaSunvaleBazaarDevilBossArenaVaultTest {

    @Test
    fun testDevilBossArenaVaultProperties() {
        val def = ElonaSunvaleBazaarDevilBossArenaVault.COMPONENT_DEF

        assertEquals("COLLAPSED_MALL_BAZAAR", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)
        assertEquals(12, def.rows.size)
        def.rows.forEach { row ->
            assertEquals(12, row.length)
        }
    }

    @Test
    fun testDevilBossArenaVaultSockets() {
        val def = ElonaSunvaleBazaarDevilBossArenaVault.COMPONENT_DEF
        
        val northSocket = def.sockets.find { it.direction == SocketDirection.NORTH }
        assertTrue(northSocket != null)
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, northSocket!!.kind)

        val eastSocket = def.sockets.find { it.direction == SocketDirection.EAST }
        assertTrue(eastSocket != null)
        assertEquals(SocketKind.BARRED_ZOMBIE_WINDOW, eastSocket!!.kind)

        val southSocket = def.sockets.find { it.direction == SocketDirection.SOUTH }
        assertTrue(southSocket != null)
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, southSocket!!.kind)

        val westSocket = def.sockets.find { it.direction == SocketDirection.WEST }
        assertTrue(westSocket != null)
        assertEquals(SocketKind.ARCHED, westSocket!!.kind)
    }

    @Test
    fun testDevilBossArenaVaultSolvability() {
        val def = ElonaSunvaleBazaarDevilBossArenaVault.COMPONENT_DEF
        
        // Find all walkable tiles
        val walkableTiles = mutableSetOf<Pair<Int, Int>>()
        for (y in 0 until def.gridHeight) {
            for (x in 0 until def.gridWidth) {
                if (def.rows[y][x] == '.') {
                    walkableTiles.add(Pair(x, y))
                }
            }
        }
        
        assertTrue("Vault must have at least one walkable floor tile", walkableTiles.isNotEmpty())
        
        // BFS to check connectivity
        val startTile = walkableTiles.first()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        
        queue.add(startTile)
        visited.add(startTile)
        
        val directions = listOf(
            Pair(0, -1), Pair(1, 0), Pair(0, 1), Pair(-1, 0)
        )
        
        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            
            for ((dx, dy) in directions) {
                val nx = cx + dx
                val ny = cy + dy
                
                if (nx in 0 until def.gridWidth && ny in 0 until def.gridHeight) {
                    if (def.rows[ny][nx] == '.') {
                        val neighbor = Pair(nx, ny)
                        if (visited.add(neighbor)) {
                            queue.add(neighbor)
                        }
                    }
                }
            }
        }
        
        assertEquals(
            "All walkable floor tiles must form a single connected component",
            walkableTiles.size,
            visited.size
        )
    }
}
