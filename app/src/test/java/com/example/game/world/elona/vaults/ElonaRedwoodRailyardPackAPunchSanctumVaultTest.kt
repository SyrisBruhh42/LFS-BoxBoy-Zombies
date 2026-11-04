package com.example.game.world.elona.vaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class ElonaRedwoodRailyardPackAPunchSanctumVaultTest {

    @Test
    fun testPackAPunchSanctumVaultProperties() {
        val vault = ElonaRedwoodRailyardPackAPunchSanctumVault.SANCTUM_VAULT
        assertEquals("boxboys.redwood.packapunch_sanctum", vault.componentId)
        assertEquals("INDUSTRIAL_RAILYARD", vault.biomeTag)
        assertEquals(12, vault.gridWidth)
        assertEquals(12, vault.gridHeight)
        assertEquals(12, vault.rows.size)
        
        // Ensure N/E/S/W have correct kinds (one of them is ARCHED, LOCKED_DEBRIS_DOOR, BARRED_ZOMBIE_WINDOW)
        val northSocket = vault.sockets.find { it.direction.name == "NORTH" }
        val eastSocket = vault.sockets.find { it.direction.name == "EAST" }
        val southSocket = vault.sockets.find { it.direction.name == "SOUTH" }
        val westSocket = vault.sockets.find { it.direction.name == "WEST" }
        
        assertTrue(vault.sockets.any { it.kind.name == "LOCKED_DEBRIS_DOOR" })
        assertTrue(vault.sockets.any { it.kind.name == "BARRED_ZOMBIE_WINDOW" })
        assertTrue(vault.sockets.any { it.kind.name == "ARCHED" })
    }

    @Test
    fun testBFSSolvabilityVerification() {
        val vault = ElonaRedwoodRailyardPackAPunchSanctumVault.SANCTUM_VAULT
        val rows = vault.rows
        val walkable = mutableSetOf<Pair<Int, Int>>()
        
        for (y in rows.indices) {
            for (x in rows[y].indices) {
                if (rows[y][x] == '.') {
                    walkable.add(Pair(x, y))
                }
            }
        }
        
        assertTrue("Vault must have walkable floors", walkable.isNotEmpty())
        
        val start = walkable.first()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        
        queue.add(start)
        visited.add(start)
        
        val dirs = listOf(Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0))
        
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            for (dir in dirs) {
                val next = Pair(curr.first + dir.first, curr.second + dir.second)
                if (next in walkable && !visited.contains(next)) {
                    visited.add(next)
                    queue.add(next)
                }
            }
        }
        
        assertEquals("All walkable floor tiles should be connected", walkable.size, visited.size)
    }
}
