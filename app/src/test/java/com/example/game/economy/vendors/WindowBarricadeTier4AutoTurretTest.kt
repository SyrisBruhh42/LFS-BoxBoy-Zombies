package com.example.game.economy.vendors

import com.example.game.economy.BarricadeWindowTier
import org.junit.Test
import org.junit.Assert.*

class WindowBarricadeTier4AutoTurretTest {

    @Test
    fun testTierProperties() {
        val turret = WindowBarricadeTier4AutoTurret()
        assertEquals(BarricadeWindowTier.TIER_4_AUTO_SENTRY_MOUNT, turret.tier)
        assertEquals(1300, turret.currentHp)
    }

    @Test
    fun testApplyDamageAndDestroy() {
        val turret = WindowBarricadeTier4AutoTurret()
        turret.applyDamage(500)
        assertEquals(800, turret.currentHp)
        assertFalse(turret.isDestroyed())

        turret.applyDamage(1000) // overdamage
        assertEquals(0, turret.currentHp)
        assertTrue(turret.isDestroyed())
    }

    @Test
    fun testFirePulseLaser() {
        val turret = WindowBarricadeTier4AutoTurret()

        // In range
        val dmgInRange = turret.firePulseLaser(100f)
        assertEquals(95, dmgInRange)

        // Out of range
        val dmgOutOfRange = turret.firePulseLaser(200f)
        assertEquals(0, dmgOutOfRange)

        // Destroyed turret cannot fire
        turret.applyDamage(2000)
        val dmgDestroyed = turret.firePulseLaser(50f)
        assertEquals(0, dmgDestroyed)
    }

    @Test
    fun testRepair() {
        val turret = WindowBarricadeTier4AutoTurret(currentHp = 1000)

        // Repair 100 HP, should cost 200 currency
        val cost1 = turret.repair(200)
        assertEquals(200, cost1)
        assertEquals(1100, turret.currentHp)

        // Try to repair remaining 200 HP, but only have 100 currency (can repair 50 HP)
        val cost2 = turret.repair(100)
        assertEquals(100, cost2)
        assertEquals(1150, turret.currentHp)

        // Fully repair remaining 150 HP, have plenty of currency (costs 300)
        val cost3 = turret.repair(1000)
        assertEquals(300, cost3)
        assertEquals(1300, turret.currentHp)

        // Already at max HP
        val cost4 = turret.repair(1000)
        assertEquals(0, cost4)
        assertEquals(1300, turret.currentHp)
    }
}
