package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.PlacedSectionNode
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaOakridgePlazaVendingRotundaVaultTest {

    @Test
    fun testVendingRotundaVaultDefinition() {
        val def = ElonaOakridgePlazaVendingRotundaVault.def

        assertEquals("OAKRIDGE_VENDING_ROTUNDA_01", def.componentId)
        assertEquals("DOWNTOWN_NEON_PLAZA", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)
        assertEquals(12, def.rows.size)

        // Verify some content features exist
        assertTrue("Should contain Vending Machine slots (V)", def.rows.any { it.contains('V') })
        assertTrue("Should contain explosive barrel clusters (B)", def.rows.any { it.contains('B') })
        assertTrue("Should contain barricadable window tiles (W)", def.rows.any { it.contains('W') })

        // Verify N/E/S/W sockets with the expected types exist
        val nSocket = def.sockets.find { it.direction == SocketDirection.NORTH }
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, nSocket?.kind)

        val eSocket = def.sockets.find { it.direction == SocketDirection.EAST }
        assertEquals(SocketKind.ARCHED, eSocket?.kind)

        val sSocket = def.sockets.find { it.direction == SocketDirection.SOUTH }
        assertEquals(SocketKind.BARRED_ZOMBIE_WINDOW, sSocket?.kind)

        val wSocket = def.sockets.find { it.direction == SocketDirection.WEST }
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, wSocket?.kind)
    }

    @Test
    fun testBFSReachabilityWithEngine() {
        val def = ElonaOakridgePlazaVendingRotundaVault.def
        val engine = ElonaSocketFrontierEngine

        val nodes = listOf(
            PlacedSectionNode(
                nodeId = "root",
                componentId = def.componentId,
                gridX = 0,
                gridY = 0,
                rotationSteps = 0,
                mirrored = false,
                unlockCostCurrency = 0,
                unlocked = true,
                sockets = def.sockets
            )
        )

        val solvable = engine.verifyTopologicalReachability(nodes)
        assertTrue("A single node should be trivially solvable via BFS reachability", solvable)
    }
}
