package com.example.game.ai.director.modules

import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretVaultRiddleGeneratorTest {

    @Test
    fun `verifyBfsSolvability correctly identifies connected floors`() {
        val rows = listOf(
            "############",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "############"
        )
        assertTrue(SecretVaultRiddleGenerator.verifyBfsSolvability(rows))
    }

    @Test
    fun `verifyBfsSolvability correctly identifies disconnected floors`() {
        val rows = listOf(
            "############",
            "#.##.......#",
            "#.##.......#",
            "#.##.......#",
            "#.##.......#",
            "#.##.......#",
            "#.##.......#",
            "#.##.......#",
            "#.##.......#",
            "#.##.......#",
            "#.##.......#",
            "############"
        )
        assertFalse(SecretVaultRiddleGenerator.verifyBfsSolvability(rows))
    }

    @Test
    fun `generateSecretVault includes high rarity rewards and secret barrel seam socket`() {
        val vaultDef = SecretVaultRiddleGenerator.generateSecretVault(1, "OAKRIDGE_PLAZA", 12345L)

        assertEquals("OAKRIDGE_PLAZA", vaultDef.biomeTag)
        assertTrue(vaultDef.componentId.startsWith("boxboys.vault.oakridge_plaza_w1_"))

        assertEquals(1, vaultDef.sockets.size)
        val socket = vaultDef.sockets[0]
        assertEquals(SocketDirection.SOUTH, socket.direction)
        assertEquals(SocketKind.SECRET_BARREL_SEAM, socket.kind)

        val joinedRows = vaultDef.rows.joinToString("")
        assertTrue("Contains Pack-a-Punch", joinedRows.contains("U"))
        assertTrue("Contains Perk-a-Cola", joinedRows.contains("P"))
        assertTrue("Contains Vending Machine", joinedRows.contains("V"))

        // South boundary should have 'B'
        assertTrue("Contains destructible barrel seam", vaultDef.rows[11].contains("B"))
    }
}
