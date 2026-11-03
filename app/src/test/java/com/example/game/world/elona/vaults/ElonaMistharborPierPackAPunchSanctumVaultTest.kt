package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaMistharborPierPackAPunchSanctumVaultTest {

    @Test
    fun verifyVaultPropertiesAndLayout() {
        val def = ElonaMistharborPierPackAPunchSanctumVault.DEF

        assertEquals("boxboys.vault.pack_a_punch_sanctum", def.componentId)
        assertEquals("CRYO_HARBOR_PIER", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)
        assertEquals(12, def.rows.size)

        // Verify some known symbols exist
        assertTrue(def.rows.any { it.contains('W') })
        assertTrue(def.rows.any { it.contains('B') })
        assertTrue(def.rows.any { it.contains('V') })
    }

    @Test
    fun verifyVaultSockets() {
        val def = ElonaMistharborPierPackAPunchSanctumVault.DEF
        val sockets = def.sockets

        val north = sockets.find { it.direction == SocketDirection.NORTH }
        val east = sockets.find { it.direction == SocketDirection.EAST }
        val south = sockets.find { it.direction == SocketDirection.SOUTH }
        val west = sockets.find { it.direction == SocketDirection.WEST }

        assertTrue(north != null && north.kind == SocketKind.LOCKED_DEBRIS_DOOR)
        assertTrue(east != null && east.kind == SocketKind.ARCHED)
        assertTrue(south != null && south.kind == SocketKind.LOCKED_DEBRIS_DOOR)
        assertTrue(west != null && west.kind == SocketKind.BARRED_ZOMBIE_WINDOW)
    }

    @Test
    fun verifyVaultSolvability() {
        val def = ElonaMistharborPierPackAPunchSanctumVault.DEF

        // Generate an initial receipt where our vault is in the catalog.
        val receipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(
            seed = 1L,
            initialUnlockedRooms = 1,
            catalog = listOf(def)
        )

        // 1 unlocked room means only our vault should be placed initially.
        assertTrue(receipt.placedNodes.size >= 1)

        // Since verifyTopologicalReachability checks reachability among PlacedSectionNodes,
        // and initial generation guarantees a single connected component, solvable should be true.
        assertTrue(receipt.solvable)

        // Check Vending Count. V is in the vault layout.
        assertTrue(receipt.reachableVendingCount >= 1)
    }
}
