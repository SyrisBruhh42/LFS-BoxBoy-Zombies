package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaSilverlakeWoodsDevilBossArenaVaultTest {

    @Test
    fun testVaultDefinition() {
        val def = ElonaSilverlakeWoodsDevilBossArenaVault.Def

        assertEquals("OVERGROWTH_CRATER_PARK", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)
        assertEquals(12, def.rows.size)

        var hasWindow = false
        var hasBarrel = false
        var hasVending = false

        def.rows.forEach { row ->
            assertEquals(12, row.length)
            if (row.contains('W')) hasWindow = true
            if (row.contains('B')) hasBarrel = true
            if (row.contains('V')) hasVending = true
        }

        assertTrue("Vault must have barricadable windows", hasWindow)
        assertTrue("Vault must have explosive barrel clusters", hasBarrel)
        assertTrue("Vault must have Vending Machine slots", hasVending)

        val northSocket = def.sockets.find { it.direction == SocketDirection.NORTH }
        val eastSocket = def.sockets.find { it.direction == SocketDirection.EAST }
        val southSocket = def.sockets.find { it.direction == SocketDirection.SOUTH }
        val westSocket = def.sockets.find { it.direction == SocketDirection.WEST }

        assertTrue(northSocket != null)
        assertTrue(eastSocket != null)
        assertTrue(southSocket != null)
        assertTrue(westSocket != null)
        
        val socketKinds = def.sockets.map { it.kind }
        assertTrue(socketKinds.contains(SocketKind.LOCKED_DEBRIS_DOOR))
        assertTrue(socketKinds.contains(SocketKind.BARRED_ZOMBIE_WINDOW))
        assertTrue(socketKinds.contains(SocketKind.ARCHED))
    }

    @Test
    fun testVaultSolvability() {
        val receipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(
            seed = 12345L,
            initialUnlockedRooms = 3,
            catalog = listOf(ElonaSilverlakeWoodsDevilBossArenaVault.Def)
        )
        
        assertTrue("Vault layout should be solvable/reachable", receipt.solvable)
        
        val allValid = receipt.placedNodes.all { node ->
            node.componentId == ElonaSilverlakeWoodsDevilBossArenaVault.Def.componentId
        }
        assertTrue("All nodes should use the defined vault", allValid)
    }
}
