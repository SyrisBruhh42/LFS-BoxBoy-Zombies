package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaSilverlakeWoodsPackAPunchSanctumVaultTest {

    @Test
    fun `test vault solvability and components`() {
        val vaultDef = ElonaSilverlakeWoodsPackAPunchSanctumVault.COMPONENT_DEF
        
        // Generate the receipt to verify solvability
        val receipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(
            seed = 12345L,
            catalog = listOf(vaultDef)
        )
        
        assertTrue("Vault must be BFS solvable", receipt.solvable)
        assertTrue("Vault must contain reachable vending machines", receipt.reachableVendingCount >= 1)
        
        // Verify dimensions and components
        assertEquals(12, vaultDef.gridWidth)
        assertEquals(12, vaultDef.gridHeight)
        assertEquals(12, vaultDef.rows.size)
        
        // Verify biome tag and sockets count
        assertEquals("OVERGROWTH_CRATER_PARK", vaultDef.biomeTag)
        assertEquals(4, vaultDef.sockets.size)
        
        // Verify specific elements exist in the rows
        val allRows = vaultDef.rows.joinToString("")
        assertTrue("Must contain Barred Zombie Window (W)", allRows.contains("W"))
        assertTrue("Must contain Locked Debris Door (D)", allRows.contains("D"))
        assertTrue("Must contain Explosive Barrel (B)", allRows.contains("B"))
        assertTrue("Must contain Vending Machine (V)", allRows.contains("V"))
    }
}
