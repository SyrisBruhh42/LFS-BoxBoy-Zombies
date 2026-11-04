package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaOakridgePlazaSpawnFortressVaultTest {

    @Test
    fun testComponentStructure() {
        val def = ElonaOakridgePlazaSpawnFortressVault.def

        assertEquals("DOWNTOWN_NEON_PLAZA", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)

        val joinedRows = def.rows.joinToString("")
        val debrisDoors = joinedRows.count { it == 'D' }
        val windows = joinedRows.count { it == 'W' }
        val barrels = joinedRows.count { it == 'B' }
        val vending = joinedRows.count { it == 'V' }

        // We defined 4 'DD' on North/South, 2 'D' and 2 'D' on East/West = 8 'D' chars total representing doors
        // Actually wait, 4 BO2 Debris doors in total. 2 top (DD), 2 bottom (DD), 4 sides (DD x 2). Total 8 D's.
        // Windows: 4
        // Barrels: 4
        // Vending: 4
        assertEquals(8, debrisDoors)
        assertEquals(4, windows)
        assertTrue("At least explosive barrel clusters", barrels >= 1)
        assertTrue("At least vending machine slots", vending >= 1)
    }

    @Test
    fun testBfsSolvabilityVerification() {
        val def = ElonaOakridgePlazaSpawnFortressVault.def
        val receipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(
            seed = 12345L,
            initialUnlockedRooms = 3,
            catalog = listOf(def)
        )

        assertTrue(receipt.solvable)
        assertTrue(receipt.reachableVendingCount >= 1)
    }
}
