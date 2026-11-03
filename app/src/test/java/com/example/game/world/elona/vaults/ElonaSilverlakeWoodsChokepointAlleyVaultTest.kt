package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaSilverlakeWoodsChokepointAlleyVaultTest {

    @Test
    fun testVaultProperties() {
        val vault = ElonaSilverlakeWoodsChokepointAlleyVault
        assertEquals("chokepoint_alley", vault.componentId)
        assertEquals("OVERGROWTH_CRATER_PARK", vault.biomeTag)
        assertEquals(12, vault.gridWidth)
        assertEquals(12, vault.gridHeight)
        assertEquals(12, vault.rows.size)

        // Verify specific tile counts
        val allTiles = vault.rows.joinToString("")
        val vendingCount = allTiles.count { it == 'V' }
        val barricadeCount = allTiles.count { it == 'W' }
        val barrelCount = allTiles.count { it == 'B' }

        assertTrue("Should have Vending Machine slots", vendingCount > 0)
        assertTrue("Should have barricadable window tiles", barricadeCount > 0)
        assertTrue("Should have explosive barrel clusters", barrelCount > 0)
    }

    @Test
    fun testSocketConfiguration() {
        val vault = ElonaSilverlakeWoodsChokepointAlleyVault
        val sockets = vault.sockets

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
    fun testBfsSolvabilityAndReachability() {
        val catalog = listOf(ElonaSilverlakeWoodsChokepointAlleyVault)
        val receipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(
            seed = 12345L,
            initialUnlockedRooms = 4,
            catalog = catalog
        )

        // BFS verify
        assertTrue("Topology should be reachable/solvable", receipt.solvable)

        // At least root node is placed
        assertTrue(receipt.placedNodes.isNotEmpty())

        // Also verify independently
        assertTrue(ElonaSocketFrontierEngine.verifyTopologicalReachability(receipt.placedNodes))
    }
}
