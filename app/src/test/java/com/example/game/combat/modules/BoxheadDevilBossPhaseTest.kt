package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BoxheadDevilBossPhaseTest {

    @Test
    fun testPhaseTransitions() {
        val maxHp = 1000
        val bossPhase1 = DevilBossState(entityId = 1, hp = 800, maxHp = maxHp)
        assertEquals(1, bossPhase1.phase)
        assertFalse(bossPhase1.isEnraged)

        val bossPhase2 = DevilBossState(entityId = 1, hp = 600, maxHp = maxHp)
        assertEquals(2, bossPhase2.phase)
        assertTrue(bossPhase2.isEnraged)

        val bossPhase3 = DevilBossState(entityId = 1, hp = 300, maxHp = maxHp)
        assertEquals(3, bossPhase3.phase)
        assertTrue(bossPhase3.isEnraged)
    }

    @Test
    fun testBarrelImmunityScaling() {
        val maxHp = 1000
        val baseDamage = 400

        val bossP1 = DevilBossState(entityId = 1, hp = 1000, maxHp = maxHp)
        assertEquals(400, BoxheadDevilBossPhase.applyBarrelDamage(bossP1, baseDamage))

        val bossP2 = DevilBossState(entityId = 1, hp = 600, maxHp = maxHp)
        assertEquals(200, BoxheadDevilBossPhase.applyBarrelDamage(bossP2, baseDamage))

        val bossP3 = DevilBossState(entityId = 1, hp = 300, maxHp = maxHp)
        assertEquals(100, BoxheadDevilBossPhase.applyBarrelDamage(bossP3, baseDamage))
    }

    @Test
    fun testFireballVolleySpread() {
        val boss = DevilBossState(
            entityId = 1, hp = 1000, maxHp = 1000,
            x = 0f, y = 0f, fireballCooldownMs = 0L
        )
        val result = BoxheadDevilBossPhase.stepBoss(boss, 16L, 100f, 0f) // player to the right

        assertEquals(1, result.spawnedFireballs.size)
        assertEquals(BoxheadDevilBossPhase.FIREBALL_COOLDOWN_MS, result.updatedBoss.fireballCooldownMs)

        // Test Phase 3 spread
        val bossP3 = DevilBossState(
            entityId = 1, hp = 300, maxHp = 1000,
            x = 0f, y = 0f, fireballCooldownMs = 0L, chargeCooldownMs = 9999L // prevent charge
        )
        val resultP3 = BoxheadDevilBossPhase.stepBoss(bossP3, 16L, 100f, 0f)

        assertEquals(5, resultP3.spawnedFireballs.size)
        assertEquals(BoxheadDevilBossPhase.FIREBALL_COOLDOWN_MS, resultP3.updatedBoss.fireballCooldownMs)
    }

    @Test
    fun testChargeSlam() {
        // Enraged boss ready to charge
        val boss = DevilBossState(
            entityId = 1, hp = 500, maxHp = 1000, // Phase 2
            x = 0f, y = 0f, chargeCooldownMs = 0L
        )

        // Initiate charge
        val initResult = BoxheadDevilBossPhase.stepBoss(boss, 16L, 100f, 0f)
        assertTrue(initResult.updatedBoss.isCharging)
        assertEquals(BoxheadDevilBossPhase.CHARGE_DURATION_MS, initResult.updatedBoss.chargeDurationRemainingMs)
        assertEquals(0, initResult.chargeSlamDamage)

        // Complete charge
        val chargeBoss = initResult.updatedBoss
        val finalResult = BoxheadDevilBossPhase.stepBoss(chargeBoss, BoxheadDevilBossPhase.CHARGE_DURATION_MS + 16L, 100f, 0f)

        assertFalse(finalResult.updatedBoss.isCharging)
        assertEquals(BoxheadDevilBossPhase.CHARGE_COOLDOWN_MS, finalResult.updatedBoss.chargeCooldownMs)
        // Phase 2 damage: 150 + (2 * 50) = 250
        assertEquals(250, finalResult.chargeSlamDamage)
    }
}
