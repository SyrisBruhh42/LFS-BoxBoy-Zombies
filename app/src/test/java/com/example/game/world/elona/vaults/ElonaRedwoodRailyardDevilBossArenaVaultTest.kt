package com.example.game.world.elona.vaults

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedList

class ElonaRedwoodRailyardDevilBossArenaVaultTest {

    @Test
    fun testComponentProperties() {
        val def = ElonaRedwoodRailyardDevilBossArenaVault.COMPONENT_DEF
        assertEquals("boxboys.vault.redwood_railyard_devil_boss_arena", def.componentId)
        assertEquals("INDUSTRIAL_RAILYARD", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)
        assertEquals(12, def.rows.size)
        
        def.rows.forEach { row ->
            assertEquals(12, row.length)
        }
    }

    @Test
    fun testSockets() {
        val def = ElonaRedwoodRailyardDevilBossArenaVault.COMPONENT_DEF
        val sockets = def.sockets
        assertEquals(4, sockets.size)
        
        val northSocket = sockets.find { it.direction == SocketDirection.NORTH }
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, northSocket?.kind)
        
        val eastSocket = sockets.find { it.direction == SocketDirection.EAST }
        assertEquals(SocketKind.BARRED_ZOMBIE_WINDOW, eastSocket?.kind)
        
        val southSocket = sockets.find { it.direction == SocketDirection.SOUTH }
        assertEquals(SocketKind.ARCHED, southSocket?.kind)
        
        val westSocket = sockets.find { it.direction == SocketDirection.WEST }
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, westSocket?.kind)
    }

    @Test
    fun testBfsSolvability() {
        val def = ElonaRedwoodRailyardDevilBossArenaVault.COMPONENT_DEF
        val walkableTiles = mutableSetOf<Pair<Int, Int>>()
        
        // Find all walkable floors ('.')
        for (y in def.rows.indices) {
            val row = def.rows[y]
            for (x in row.indices) {
                if (row[x] == '.') {
                    walkableTiles.add(Pair(x, y))
                }
            }
        }
        
        assertTrue("Vault must have walkable tiles", walkableTiles.isNotEmpty())
        
        val startTile = walkableTiles.first()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = LinkedList<Pair<Int, Int>>()
        
        queue.add(startTile)
        visited.add(startTile)
        
        val directions = listOf(
            Pair(0, -1), // North
            Pair(1, 0),  // East
            Pair(0, 1),  // South
            Pair(-1, 0)  // West
        )
        
        while (queue.isNotEmpty()) {
            val (x, y) = queue.poll()!!
            
            for ((dx, dy) in directions) {
                val nx = x + dx
                val ny = y + dy
                val neighbor = Pair(nx, ny)
                
                if (neighbor in walkableTiles && neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        
        assertEquals("All walkable tiles should form a single connected component (BFS solvability)", walkableTiles.size, visited.size)
    }

    @Test
    fun testConstraintsAndContent() {
        val def = ElonaRedwoodRailyardDevilBossArenaVault.COMPONENT_DEF
        var windowCount = 0
        var barrelCount = 0
        var vendingCount = 0
        
        for (y in def.rows.indices) {
            val row = def.rows[y]
            for (x in row.indices) {
                when (row[x]) {
                    'W' -> windowCount++
                    'B' -> barrelCount++
                    'V' -> vendingCount++
                }
            }
        }
        
        assertTrue("Vault should have at least one window barricade (W)", windowCount > 0)
        assertTrue("Vault should have at least one explosive barrel cluster (B)", barrelCount > 0)
        assertTrue("Vault should have at least one vending machine slot (V)", vendingCount > 0)
    }
}
