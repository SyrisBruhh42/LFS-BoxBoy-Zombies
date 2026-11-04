package com.example.game.combat.modules

import com.example.game.combat.ChainDetonationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhdBlastSurfingImpulseTest {

    @Test
    fun `applyBlastSurfing should correctly add impulse vectors and zero out self damage`() {
        val detonation = ChainDetonationResult(
            detonatedBarrelIds = listOf(1, 2),
            totalChainDepth = 2,
            blastSurfImpulseVx = 300f,
            blastSurfImpulseVy = 200f,
            playerSelfDamage = 150
        )

        val initialStateVx = 50f
        val initialStateVy = -20f

        val result = PhdBlastSurfingImpulse.applyBlastSurfing(initialStateVx, initialStateVy, detonation)

        assertEquals(350f, result.vx, 0.001f)
        assertEquals(180f, result.vy, 0.001f)
        assertEquals(0, result.selfDamageApplied)
        assertFalse(result.isAirDashReloadTriggered)
    }

    @Test
    fun `applyBlastSurfing should trigger air dash reload when impulse magnitude exceeds threshold`() {
        val detonation = ChainDetonationResult(
            detonatedBarrelIds = listOf(3),
            totalChainDepth = 1,
            blastSurfImpulseVx = 400f,
            blastSurfImpulseVy = 350f, // hypot(400, 350) ≈ 531 > 500
            playerSelfDamage = 250
        )

        val result = PhdBlastSurfingImpulse.applyBlastSurfing(0f, 0f, detonation)

        assertEquals(0, result.selfDamageApplied)
        assertTrue(result.isAirDashReloadTriggered)
    }

    @Test
    fun `applyBlastSurfing should not trigger air dash reload when exactly below threshold`() {
        val detonation = ChainDetonationResult(
            detonatedBarrelIds = listOf(4),
            totalChainDepth = 1,
            blastSurfImpulseVx = 300f,
            blastSurfImpulseVy = 399f, // hypot(300, 399) ≈ 499.2 < 500
            playerSelfDamage = 100
        )

        val result = PhdBlastSurfingImpulse.applyBlastSurfing(10f, 10f, detonation)

        assertEquals(0, result.selfDamageApplied)
        assertFalse(result.isAirDashReloadTriggered)
    }
}
