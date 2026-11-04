package com.example.game.combat.modules

import com.example.game.FortificationEntity
import com.example.game.FortificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoltaicScreamerZombieTest {

    @Test
    fun testScreamerActivatesScreechWhenCooldownZero() {
        val screamer = VoltaicScreamerZombie(
            id = 1,
            x = 0f,
            y = 0f,
            hp = 100,
            maxHp = 100,
            isScreaming = false,
            screechDurationMs = 0L,
            screechCooldownMs = 100L
        )

        val (updatedScreamers, _) = VoltaicScreamerEngine.stepScreamers(
            listOf(screamer),
            emptyList(),
            100L
        )

        assertEquals(1, updatedScreamers.size)
        assertTrue(updatedScreamers[0].isScreaming)
        assertEquals(VoltaicScreamerEngine.MAX_SCREECH_DURATION_MS, updatedScreamers[0].screechDurationMs)
    }

    @Test
    fun testScreamerSlowsTurretFireRateWithinRadius() {
        val screamer = VoltaicScreamerZombie(
            id = 1,
            x = 100f,
            y = 100f,
            hp = 100,
            maxHp = 100,
            isScreaming = true,
            screechDurationMs = 1000L,
            screechCooldownMs = 0L
        )

        val turret = FortificationEntity(
            id = 1,
            kind = FortificationType.AUTO_TURRET_BEACON,
            x = 150f, // Within 300f radius
            y = 150f,
            roomIndex = 0,
            fireCooldownMs = 500L
        )

        val outOfRangeTurret = FortificationEntity(
            id = 2,
            kind = FortificationType.AUTO_TURRET_BEACON,
            x = 1000f, // Outside 300f radius
            y = 1000f,
            roomIndex = 0,
            fireCooldownMs = 500L
        )

        val (_, updatedTurrets) = VoltaicScreamerEngine.stepScreamers(
            listOf(screamer),
            listOf(turret, outOfRangeTurret),
            100L
        )

        assertEquals(2, updatedTurrets.size)
        val updatedTurret = updatedTurrets.find { it.id == 1 }!!
        val updatedOutOfRangeTurret = updatedTurrets.find { it.id == 2 }!!

        // In range turret should have increased fireCooldownMs
        assertTrue(updatedTurret.fireCooldownMs > 500L)
        // Out of range turret should not be affected
        assertEquals(500L, updatedOutOfRangeTurret.fireCooldownMs)
    }

    @Test
    fun testInterruptedScreamerStopsScreechEffect() {
        // "unless interrupted" - in this simple implementation, interruption is just death (hp = 0)
        // or we could test that a dead screamer doesn't screech.
        val screamer = VoltaicScreamerZombie(
            id = 1,
            x = 100f,
            y = 100f,
            hp = 0, // Dead
            maxHp = 100,
            isScreaming = true,
            screechDurationMs = 1000L,
            screechCooldownMs = 0L
        )

        val turret = FortificationEntity(
            id = 1,
            kind = FortificationType.AUTO_TURRET_BEACON,
            x = 150f,
            y = 150f,
            roomIndex = 0,
            fireCooldownMs = 500L
        )

        val (updatedScreamers, updatedTurrets) = VoltaicScreamerEngine.stepScreamers(
            listOf(screamer),
            listOf(turret),
            100L
        )

        assertEquals(0, updatedScreamers.size) // HP <= 0 are removed
        assertEquals(1, updatedTurrets.size)
        // Turret unaffected
        assertEquals(500L, updatedTurrets[0].fireCooldownMs)
    }
}
