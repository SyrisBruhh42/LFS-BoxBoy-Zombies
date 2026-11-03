package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaOakridgePlazaChokepointAlleyVaultTest {

    @Test
    fun testVaultDefinitionIsCorrect() {
        val vault = ElonaOakridgePlazaChokepointAlleyVault.def

        assertEquals("boxboys.vault.oakridge_plaza_chokepoint_alley", vault.componentId)
        assertEquals("DOWNTOWN_NEON_PLAZA", vault.biomeTag)
        assertEquals(12, vault.gridWidth)
        assertEquals(12, vault.gridHeight)
        assertEquals(12, vault.rows.size)

        // Verify some content features
        val flatRows = vault.rows.joinToString("")
        assertTrue("Should contain explosive barrels", flatRows.contains('B'))
        assertTrue("Should contain barricadable windows", flatRows.contains('W'))
        assertTrue("Should contain vending machines", flatRows.contains('V'))

        // Verify socket directions and types according to prompt:
        // LOCKED_DEBRIS_DOOR, BARRED_ZOMBIE_WINDOW, ARCHED
        val kinds = vault.sockets.map { it.kind }
        assertTrue(kinds.contains(SocketKind.LOCKED_DEBRIS_DOOR))
        assertTrue(kinds.contains(SocketKind.BARRED_ZOMBIE_WINDOW))
        assertTrue(kinds.contains(SocketKind.ARCHED))

        // Verify we have all 4 directions
        val directions = vault.sockets.map { it.direction }.toSet()
        assertEquals(4, directions.size)
        assertTrue(directions.contains(SocketDirection.NORTH))
        assertTrue(directions.contains(SocketDirection.EAST))
        assertTrue(directions.contains(SocketDirection.SOUTH))
        assertTrue(directions.contains(SocketDirection.WEST))
    }

    @Test
    fun testBfsSolvabilityWithVault() {
        val vault = ElonaOakridgePlazaChokepointAlleyVault.def
        // Add this to our catalog
        val catalog = listOf(vault)

        // Use a fixed seed for deterministic testing
        val receipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(
            seed = 12345L,
            initialUnlockedRooms = 3,
            catalog = catalog
        )

        // Generate and verify topological reachability / BFS solvability
        assertTrue("Receipt should be solvable", receipt.solvable)

        // Verify nodes are generated
        assertEquals(3, receipt.placedNodes.size)
    }
}
