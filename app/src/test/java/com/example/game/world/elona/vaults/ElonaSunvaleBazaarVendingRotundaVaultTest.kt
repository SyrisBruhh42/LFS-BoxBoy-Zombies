package com.example.game.world.elona.vaults

import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedList
import java.util.Queue

class ElonaSunvaleBazaarVendingRotundaVaultTest {

    @Test
    fun testVendingRotundaVaultDefinition() {
        val vault = ElonaSunvaleBazaarVendingRotundaVault.create()

        assertEquals("elona.vault.sunvale_bazaar.vending_rotunda", vault.componentId)
        assertEquals("COLLAPSED_MALL_BAZAAR", vault.biomeTag)
        assertEquals(12, vault.gridWidth)
        assertEquals(12, vault.gridHeight)
        assertEquals(12, vault.rows.size)

        val northSocket = vault.sockets.find { it.direction == SocketDirection.NORTH }
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, northSocket?.kind)
        
        val eastSocket = vault.sockets.find { it.direction == SocketDirection.EAST }
        assertEquals(SocketKind.BARRED_ZOMBIE_WINDOW, eastSocket?.kind)

        val southSocket = vault.sockets.find { it.direction == SocketDirection.SOUTH }
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, southSocket?.kind)
        
        val westSocket = vault.sockets.find { it.direction == SocketDirection.WEST }
        assertEquals(SocketKind.ARCHED, westSocket?.kind)
    }

    @Test
    fun testBfsSolvabilityVerification() {
        val vault = ElonaSunvaleBazaarVendingRotundaVault.create()
        
        val grid = vault.rows.map { it.toCharArray() }
        val rows = grid.size
        val cols = grid[0].size
        
        var startX = -1
        var startY = -1
        var totalWalkable = 0
        
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (grid[y][x] == '.') {
                    if (startX == -1) {
                        startX = x
                        startY = y
                    }
                    totalWalkable++
                }
            }
        }
        
        assertTrue("Vault must have at least one walkable floor tile ('.')", totalWalkable > 0)
        
        val visited = Array(rows) { BooleanArray(cols) }
        val queue: Queue<Pair<Int, Int>> = LinkedList()
        
        queue.add(Pair(startX, startY))
        visited[startY][startX] = true
        var reachableWalkable = 1
        
        val dirs = arrayOf(Pair(0, -1), Pair(1, 0), Pair(0, 1), Pair(-1, 0))
        
        while (queue.isNotEmpty()) {
            val curr = queue.poll()!!
            val cx = curr.first
            val cy = curr.second
            
            for (dir in dirs) {
                val nx = cx + dir.first
                val ny = cy + dir.second
                
                if (nx in 0 until cols && ny in 0 until rows) {
                    if (!visited[ny][nx] && grid[ny][nx] == '.') {
                        visited[ny][nx] = true
                        queue.add(Pair(nx, ny))
                        reachableWalkable++
                    }
                }
            }
        }
        
        assertEquals(
            "All walkable tiles must be reachable from each other to pass BFS solvability",
            totalWalkable,
            reachableWalkable
        )
    }
}
