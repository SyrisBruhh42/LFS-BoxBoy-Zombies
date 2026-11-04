package com.example.game.combat.modules

import com.example.game.combat.BarrelNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedBarrelChainPhysicsTest {

    @Test
    fun test_multiHopBarrelChainDetonationAndBlastSurfing_deterministically_resolves() {
        val barrels = listOf(
            BarrelNode(id = 1, x = 100f, y = 100f, blastRadiusPx = 145f),
            BarrelNode(id = 2, x = 210f, y = 100f, blastRadiusPx = 145f),
            BarrelNode(id = 3, x = 310f, y = 100f, blastRadiusPx = 145f)
        )

        val result = RedBarrelChainPhysics.resolveBarrelChainReaction(
            initialBarrelId = 1,
            barrels = barrels,
            playerX = 140f,
            playerY = 100f,
            blastSurfBootsStacks = 2
        )

        assertEquals(listOf(1, 2, 3), result.detonatedBarrelIds)
        assertEquals(0, result.playerSelfDamage)
        assertTrue("Expected impulse X to be > 0", result.blastSurfImpulseVx > 0f)
        assertEquals(3, result.totalChainDepth)
    }
}
