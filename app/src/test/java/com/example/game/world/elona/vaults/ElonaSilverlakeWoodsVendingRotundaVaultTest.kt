package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import org.junit.Assert.*
import org.junit.Test

class ElonaSilverlakeWoodsVendingRotundaVaultTest {

    private val vaultDef = ElonaSilverlakeWoodsVendingRotundaVault.VENDING_ROTUNDA_DEF

    @Test
    fun testDimensions() {
        assertEquals(12, vaultDef.gridWidth)
        assertEquals(12, vaultDef.gridHeight)
        assertEquals(12, vaultDef.rows.size)
        for (row in vaultDef.rows) {
            assertEquals(12, row.length)
        }
    }

    @Test
    fun testRequiredElements() {
        val fullGridString = vaultDef.rows.joinToString("")
        assertTrue("Must contain Vending Machine slots (V)", fullGridString.contains("V"))
        assertTrue("Must contain Barricadable zombie window (W)", fullGridString.contains("W"))
        assertTrue("Must contain Debris doors (D)", fullGridString.contains("D"))
        assertTrue("Must contain Explosive barrels (B)", fullGridString.contains("B"))
    }

    @Test
    fun testSockets() {
        val sockets = vaultDef.sockets
        assertEquals(4, sockets.size)
        
        val directions = sockets.map { it.direction }
        assertTrue(directions.contains(SocketDirection.NORTH))
        assertTrue(directions.contains(SocketDirection.EAST))
        assertTrue(directions.contains(SocketDirection.SOUTH))
        assertTrue(directions.contains(SocketDirection.WEST))
    }

    @Test
    fun testBfsSolvability() {
        val catalog = listOf(vaultDef)
        val receipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(
            seed = 12345L,
            initialUnlockedRooms = 3,
            catalog = catalog
        )
        
        assertTrue("Generated layout should be solvable and fully connected", receipt.solvable)
        assertTrue("Layout must place multiple nodes", receipt.placedNodes.size >= 3)
    }
}
