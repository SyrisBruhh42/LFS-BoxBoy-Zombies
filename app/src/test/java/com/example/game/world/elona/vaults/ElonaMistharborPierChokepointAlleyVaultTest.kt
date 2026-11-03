package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaMistharborPierChokepointAlleyVaultTest {

    @Test
    fun `test vault properties and dimensions`() {
        val def = ElonaMistharborPierChokepointAlleyVault.def
        assertEquals("mistharbor_pier_chokepoint_alley", def.componentId)
        assertEquals("CRYO_HARBOR_PIER", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)
        assertEquals(12, def.rows.size)
        def.rows.forEach { row ->
            assertEquals(12, row.length)
        }
    }

    @Test
    fun `test required layout symbols are present`() {
        val def = ElonaMistharborPierChokepointAlleyVault.def
        val allText = def.rows.joinToString("")
        assertTrue("Expected barricadable window 'W'", allText.contains('W'))
        assertTrue("Expected explosive barrel 'B'", allText.contains('B'))
        assertTrue("Expected vending machine 'V'", allText.contains('V'))
        assertTrue("Expected debris door 'D'", allText.contains('D'))
    }

    @Test
    fun `test sockets definitions`() {
        val def = ElonaMistharborPierChokepointAlleyVault.def
        assertEquals(4, def.sockets.size)

        val north = def.sockets.find { it.direction == SocketDirection.NORTH }
        val east = def.sockets.find { it.direction == SocketDirection.EAST }
        val south = def.sockets.find { it.direction == SocketDirection.SOUTH }
        val west = def.sockets.find { it.direction == SocketDirection.WEST }

        assertTrue(north != null && north.kind == SocketKind.LOCKED_DEBRIS_DOOR)
        assertTrue(east != null && east.kind == SocketKind.BARRED_ZOMBIE_WINDOW)
        assertTrue(south != null && south.kind == SocketKind.ARCHED)
        assertTrue(west != null && west.kind == SocketKind.LOCKED_DEBRIS_DOOR)
    }

    @Test
    fun `test single component reachability`() {
        // We verify that the component can form a valid topological structure.
        val receipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(
            seed = 42L,
            initialUnlockedRooms = 3, // Needs to be > 1 to test connection logic, wait!
            catalog = listOf(ElonaMistharborPierChokepointAlleyVault.def)
        )

        // Since the layout has enough connections internally and the engine verifies reachability,
        // we can test if it's solvable.
        assertTrue("Expected world assembly with this vault to be solvable", receipt.solvable)
        assertTrue("Expected at least one reachable vending machine", receipt.reachableVendingCount >= 1)
    }
}
