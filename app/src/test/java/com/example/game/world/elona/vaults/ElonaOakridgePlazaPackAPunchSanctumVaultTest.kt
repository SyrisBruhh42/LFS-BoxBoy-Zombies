package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.PlacedSectionNode
import com.example.game.world.elona.SocketDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaOakridgePlazaPackAPunchSanctumVaultTest {

    @Test
    fun `verify component definition and grid structure`() {
        val def = ElonaOakridgePlazaPackAPunchSanctumVault.COMPONENT_DEF
        assertEquals("vault_oakridge_plaza_pap_sanctum", def.componentId)
        assertEquals("DOWNTOWN_NEON_PLAZA", def.biomeTag)
        assertEquals(12, def.rows.size)
        def.rows.forEach { row ->
            assertEquals(12, row.length)
        }
        assertEquals(4, def.sockets.size)
    }

    @Test
    fun `verify essential elements are present`() {
        val def = ElonaOakridgePlazaPackAPunchSanctumVault.COMPONENT_DEF
        val grid = def.rows.joinToString("")

        // Count barricadable windows (W)
        val windowCount = grid.count { it == 'W' }
        assertTrue("Should have at least 1 barricadable window", windowCount >= 1)

        // Count explosive barrels (B)
        val barrelCount = grid.count { it == 'B' }
        assertTrue("Should have at least 1 explosive barrel", barrelCount >= 1)

        // Count Vending Machines (V)
        val vendingCount = grid.count { it == 'V' }
        assertTrue("Should have at least 1 vending machine", vendingCount >= 1)

        // Count Doors (D)
        val doorCount = grid.count { it == 'D' }
        assertTrue("Should have at least 1 door representation", doorCount >= 1)
    }

    @Test
    fun `verify BFS solvability and topological reachability`() {
        val engine = ElonaSocketFrontierEngine

        // Use the component to generate a mini receipt
        val receipt = engine.generateInitialWorldReceipt(
            seed = 12345L,
            initialUnlockedRooms = 4,
            catalog = listOf(ElonaOakridgePlazaPackAPunchSanctumVault.COMPONENT_DEF)
        )

        assertTrue("The generated vault layout must be solvable", receipt.solvable)

        // Additional topological reachability verification on the placed nodes
        val isTopologicallyReachable = engine.verifyTopologicalReachability(receipt.placedNodes)
        assertTrue("Nodes should be topologically reachable", isTopologicallyReachable)
    }
}
