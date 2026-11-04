package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Test

class VoltaicScreamerZombieTest {

    @Test
    fun testEmpDischargeSlowsTurrets() {
        val screamer = ScreamerZombie("s1", 0f, 0f, isCharging = true)
        val turret1 = Turret("t1", 100f, 0f, baseFireRateDelayMs = 1000L, currentFireRateDelayMs = 1000L)
        val turret2 = Turret("t2", 500f, 0f, baseFireRateDelayMs = 1000L, currentFireRateDelayMs = 1000L) // Out of range

        val turrets = listOf(turret1, turret2)

        VoltaicScreamerZombieLogic.updateScreamer(screamer, VoltaicScreamerZombieLogic.EMP_CHARGE_REQUIRED_MS, turrets)

        // Screamer should finish charging
        assertEquals(false, screamer.isCharging)
        assertEquals(0L, screamer.empChargeTimeMs)

        // Turret 1 should be debuffed (within 300f)
        assertEquals(VoltaicScreamerZombieLogic.EMP_DEBUFF_DURATION_MS, turret1.empDebuffRemainingMs)
        assertEquals(2000L, turret1.currentFireRateDelayMs)

        // Turret 2 should NOT be debuffed (outside 300f)
        assertEquals(0L, turret2.empDebuffRemainingMs)
        assertEquals(1000L, turret2.currentFireRateDelayMs)
    }

    @Test
    fun testEmpInterruption() {
        val screamer = ScreamerZombie("s1", 0f, 0f, isCharging = true, isInterrupted = true)
        val turret = Turret("t1", 100f, 0f, baseFireRateDelayMs = 1000L, currentFireRateDelayMs = 1000L)
        
        VoltaicScreamerZombieLogic.updateScreamer(screamer, VoltaicScreamerZombieLogic.EMP_CHARGE_REQUIRED_MS, listOf(turret))

        // Interrupted before or during charge
        assertEquals(false, screamer.isCharging)
        assertEquals(0L, screamer.empChargeTimeMs)

        // Turret shouldn't be debuffed
        assertEquals(0L, turret.empDebuffRemainingMs)
        assertEquals(1000L, turret.currentFireRateDelayMs)
    }

    @Test
    fun testTurretRecoveryOverTime() {
        val turret = Turret("t1", 0f, 0f, baseFireRateDelayMs = 1000L, currentFireRateDelayMs = 2000L, empDebuffRemainingMs = VoltaicScreamerZombieLogic.EMP_DEBUFF_DURATION_MS)

        // Partial recovery
        VoltaicScreamerZombieLogic.updateTurret(turret, 2000L)
        assertEquals(VoltaicScreamerZombieLogic.EMP_DEBUFF_DURATION_MS - 2000L, turret.empDebuffRemainingMs)
        assertEquals(2000L, turret.currentFireRateDelayMs)

        // Full recovery
        VoltaicScreamerZombieLogic.updateTurret(turret, 3000L)
        assertEquals(0L, turret.empDebuffRemainingMs)
        assertEquals(1000L, turret.currentFireRateDelayMs) // Back to normal
    }
}
