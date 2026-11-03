package com.example.game.world.elona.vaults

import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.PlacedSectionNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElonaOakridgePlazaDevilBossArenaVaultTest {

    @Test
    fun `test vault properties and dimensions`() {
        val def = ElonaOakridgePlazaDevilBossArenaVault.VaultDef

        assertEquals("DEVIL_BOSS_ARENA_OAKRIDGE_01", def.componentId)
        assertEquals("DOWNTOWN_NEON_PLAZA", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)
        assertEquals(12, def.rows.size)
        assertTrue(def.rows.all { it.length == 12 })
    }

    @Test
    fun `test characters in vault rows`() {
        val def = ElonaOakridgePlazaDevilBossArenaVault.VaultDef
        val allChars = def.rows.joinToString("")

        assertTrue("Should contain explosive barrels (B)", allChars.contains('B'))
        assertTrue("Should contain vending machines (V)", allChars.contains('V'))
        assertTrue("Should contain barricadable windows (W)", allChars.contains('W'))
        assertTrue("Should contain doors (D) and arches (A)", allChars.contains('D') || allChars.contains('A'))
    }

    @Test
    fun `test solvability reachability with single node`() {
        val def = ElonaOakridgePlazaDevilBossArenaVault.VaultDef
        val node = PlacedSectionNode(
            nodeId = "sec_0_0",
            componentId = def.componentId,
            gridX = 0,
            gridY = 0,
            rotationSteps = 0,
            mirrored = false,
            unlockCostCurrency = 0,
            unlocked = true,
            sockets = def.sockets
        )

        // Even a single node should be solvable mathematically in this topology model
        val solvable = ElonaSocketFrontierEngine.verifyTopologicalReachability(listOf(node))
        assertTrue(solvable)
    }
}
