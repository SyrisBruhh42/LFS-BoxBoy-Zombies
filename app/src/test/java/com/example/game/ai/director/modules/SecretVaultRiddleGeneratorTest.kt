package com.example.game.ai.director.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class SecretVaultRiddleGeneratorTest {

    @Test
    fun testGenerateSecretVault_WalkableTilesAreFullyConnectedBFS() {
        val vaultDef = SecretVaultRiddleGenerator.generateSecretVault(12345L, "CYBERPUNK", 5)
        
        // Assert valid component ID format
        assertTrue(vaultDef.componentId.startsWith("boxboys.vault.cyberpunk_w5_"))
        
        // Assert 12x12 grid
        assertEquals(12, vaultDef.rows.size)
        assertTrue(vaultDef.rows.all { it.length == 12 })
        
        // Collect all walkable floor tiles ('.')
        val walkableTiles = mutableSetOf<Pair<Int, Int>>()
        for (y in 0 until 12) {
            for (x in 0 until 12) {
                if (vaultDef.rows[y][x] == '.') {
                    walkableTiles.add(x to y)
                }
            }
        }
        
        assertTrue("Vault must have walkable tiles", walkableTiles.isNotEmpty())
        
        // Pick one tile to start BFS
        val startTile = walkableTiles.first()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        
        queue.add(startTile)
        visited.add(startTile)
        
        // BFS directions
        val directions = listOf(
            0 to -1, // North
            1 to 0,  // East
            0 to 1,  // South
            -1 to 0  // West
        )
        
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            
            for (dir in directions) {
                val nx = curr.first + dir.first
                val ny = curr.second + dir.second
                val neighbor = nx to ny
                
                // If it's a valid walkable tile and not visited
                if (walkableTiles.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        
        // Verify that all walkable floor tiles were reached via BFS
        assertEquals("Not all walkable tiles form a single connected component", walkableTiles.size, visited.size)
    }
}
