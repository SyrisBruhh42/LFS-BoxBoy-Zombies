package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaMistharborPierSpawnFortressVaultTest {
    @Test
    fun testMistharborPierSpawnFortressVaultProperties() {
        val vault = ElonaMistharborPierSpawnFortressVault.def
        assertEquals("boxboys.mistharbor_pier.spawn_fortress", vault.componentId)
        assertEquals("CRYO_HARBOR_PIER", vault.biomeTag)
        assertEquals(12, vault.gridWidth)
        assertEquals(12, vault.gridHeight)
        
        // Verify dimensions and contents
        assertEquals(12, vault.rows.size)
        assertTrue(vault.rows.all { it.length == 12 })
        
        // Verify sockets
        assertEquals(4, vault.sockets.size)
        
        // Count specific characters
        val allChars = vault.rows.joinToString("")
        val vCount = allChars.count { it == 'V' }
        val bCount = allChars.count { it == 'B' }
        val dCount = allChars.count { it == 'D' }
        val wCount = allChars.count { it == 'W' }
        
        assertTrue("Expected Vending Machines", vCount >= 1)
        assertTrue("Expected Explosive Barrels", bCount >= 1)
        assertEquals("Expected 4 Debris Doors (Sockets)", 8, dCount) // 4 pairs of 'D'
        assertEquals("Expected 4 Window Barricades", 8, wCount) // 8 instances of W in the grid
    }

    @Test
    fun testMistharborPierSpawnFortressVaultSolvability() {
        val vault = ElonaMistharborPierSpawnFortressVault.def
        val catalog = listOf(vault)
        val receipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(
            seed = 12345L,
            initialUnlockedRooms = 3,
            catalog = catalog
        )
        
        assertTrue("Vault topology should be solvable (reachable)", receipt.solvable)
        assertTrue("Placed nodes should be connected", receipt.placedNodes.size >= 1)
    }
}
