package com.example.game.loot.borderlands.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class AmpShotShieldGeneratorTest {

    @Test
    fun testGenerateShield_scaling_wave1() {
        val s = AmpShotShieldGenerator.generateShield(123L, 1)
        assertEquals(100, s.maxShield)
        assertEquals(100, s.currentShield)
        assertEquals(2000, s.shieldRechargeDelayMs)
        assertEquals(10, s.shieldRechargeRatePerSec)
    }

    @Test
    fun testGenerateShield_scaling_wave5() {
        val s = AmpShotShieldGenerator.generateShield(123L, 5)
        assertEquals(200, s.maxShield)
        assertEquals(200, s.currentShield)
        assertEquals(1800, s.shieldRechargeDelayMs)
        assertEquals(30, s.shieldRechargeRatePerSec)
    }

    @Test
    fun testSerializationParity() {
        val s1 = AmpShieldState(
            maxShield = 150, currentShield = 50,
            ampDamageBonusPct = 40, shieldRechargeDelayMs = 1500,
            shieldRechargeRatePerSec = 20, timeSinceLastDamageMs = 500
        )
        val map = s1.toMap()
        val s2 = AmpShieldState.fromMap(map)
        assertEquals(s1, s2)
        assertEquals(s1.computeChecksum(), s2.computeChecksum())
    }

    @Test
    fun testStep_noRecharge_beforeDelay() {
        val s1 = AmpShieldState(100, 50, 40, 2000, 10, 0)
        val s2 = AmpShotShieldGenerator.step(s1, 1.0f) // 1000ms
        assertEquals(50, s2.currentShield)
        assertEquals(1000, s2.timeSinceLastDamageMs)
    }

    @Test
    fun testStep_recharge_afterDelay() {
        val s1 = AmpShieldState(100, 50, 40, 2000, 10, 1500)
        val s2 = AmpShotShieldGenerator.step(s1, 1.0f) // 1000ms -> total 2500ms
        // Active recharge time = 2500 - 2000 = 500ms.
        // Rate is 10/sec, so 500ms -> 5 shield recharged.
        assertEquals(55, s2.currentShield)
        assertEquals(2500, s2.timeSinceLastDamageMs)
    }

    @Test
    fun testStep_rechargeCap_atMax() {
        val s1 = AmpShieldState(100, 95, 40, 2000, 10, 2000)
        val s2 = AmpShotShieldGenerator.step(s1, 1.0f) // 1000ms active recharge -> 10 shield
        assertEquals(100, s2.currentShield) // Capped at 100
    }

    @Test
    fun testStep_clampingAdversarialDt() {
        val s1 = AmpShieldState(100, 50, 40, 2000, 10, 0)
        val s2 = AmpShotShieldGenerator.step(s1, Float.NaN)
        assertEquals(50, s2.currentShield)
        assertEquals(1, s2.timeSinceLastDamageMs) // MIN_DT_SEC (0.001) -> 1ms

        val s3 = AmpShotShieldGenerator.step(s1, Float.POSITIVE_INFINITY)
        assertEquals(50, s3.currentShield)
        assertEquals(1, s3.timeSinceLastDamageMs) // MIN_DT_SEC (0.001) -> 1ms

        val s4 = AmpShotShieldGenerator.step(s1, 1000000f) // Should clamp to MAX_DT_SEC = 0.1 (100ms)
        assertEquals(100, s4.timeSinceLastDamageMs)
    }

    @Test
    fun testTakeDamage() {
        val s1 = AmpShieldState(100, 100, 40, 2000, 10, 5000)
        val s2 = AmpShotShieldGenerator.takeDamage(s1, 25)
        assertEquals(75, s2.currentShield)
        assertEquals(0, s2.timeSinceLastDamageMs)

        val s3 = AmpShotShieldGenerator.takeDamage(s2, 100)
        assertEquals(0, s3.currentShield) // Should not go below 0
        assertEquals(0, s3.timeSinceLastDamageMs)
    }

    @Test
    fun testTakeDamage_negativeOrZero() {
        val s1 = AmpShieldState(100, 100, 40, 2000, 10, 5000)
        val s2 = AmpShotShieldGenerator.takeDamage(s1, -10)
        assertEquals(100, s2.currentShield)
        assertEquals(5000, s2.timeSinceLastDamageMs) // State unchanged
    }

    @Test
    fun testResolveAmpShot_amped_hit() {
        val state = AmpShieldState(100, 100, 40, 2000, 10, 5000)
        val t = ZombieTarget("z1", 100f, 0f)
        val targets = arrayOf(t)

        val result = AmpShotEvaluationResult(10)
        AmpShotShieldGenerator.resolveAmpShot(
            state, baseDamage = 100, playerX = 0f, playerY = 0f,
            aimAngleRad = 0f, shotRangePx = 200f, targets = targets, targetsCount = 1, outResult = result
        )

        assertTrue(result.isAmped)
        assertEquals(140, result.totalDamageDealt)
        assertEquals(40, result.ampDamageDealt)
        val list = result.getImpactedList()
        assertTrue(list.contains("z1"))
        assertEquals(1, result.impactedCount)

        assertEquals(60, result.newState.currentShield) // 100 - 40
        assertEquals(0, result.newState.timeSinceLastDamageMs)
    }

    @Test
    fun testResolveAmpShot_notAmped_hit() {
        val state = AmpShieldState(100, 99, 40, 2000, 10, 5000)
        val t = ZombieTarget("z1", 100f, 0f)
        val targets = arrayOf(t)

        val result = AmpShotEvaluationResult(10)
        AmpShotShieldGenerator.resolveAmpShot(
            state, baseDamage = 100, playerX = 0f, playerY = 0f,
            aimAngleRad = 0f, shotRangePx = 200f, targets = targets, targetsCount = 1, outResult = result
        )

        assertFalse(result.isAmped)
        assertEquals(100, result.totalDamageDealt)
        assertEquals(0, result.ampDamageDealt)
        val list = result.getImpactedList()
        assertTrue(list.contains("z1"))
        assertEquals(1, result.impactedCount)

        assertEquals(99, result.newState.currentShield)
        assertEquals(5000, result.newState.timeSinceLastDamageMs) // Time since damage unchanged because no drain
    }

    @Test
    fun testResolveAmpShot_miss() {
        val state = AmpShieldState(100, 100, 40, 2000, 10, 5000)
        val t = ZombieTarget("z1", 0f, 100f) // Target is at y=100
        val targets = arrayOf(t)

        val result = AmpShotEvaluationResult(10)
        AmpShotShieldGenerator.resolveAmpShot(
            state, baseDamage = 100, playerX = 0f, playerY = 0f,
            aimAngleRad = 0f, // Firing along x-axis
            shotRangePx = 200f, targets = targets, targetsCount = 1, outResult = result
        )

        assertTrue(result.isAmped) // Still drains shield even if missed!
        assertEquals(0, result.impactedCount)
        assertEquals(60, result.newState.currentShield)
    }

    @Test
    fun testResolveAmpShot_multiTargetPenetration() {
        val state = AmpShieldState(100, 100, 40, 2000, 10, 5000)
        val t1 = ZombieTarget("z1", 50f, 0f)
        val t2 = ZombieTarget("z2", 150f, 0f)
        val t3 = ZombieTarget("z3", 250f, 0f) // Out of range
        val t4 = ZombieTarget("z4", 100f, 50f) // Miss
        val targets = arrayOf(t1, t2, t3, t4)

        val result = AmpShotEvaluationResult(10)
        AmpShotShieldGenerator.resolveAmpShot(
            state, baseDamage = 100, playerX = 0f, playerY = 0f,
            aimAngleRad = 0f, shotRangePx = 200f, targets = targets, targetsCount = 4, outResult = result
        )

        assertEquals(2, result.impactedCount)
        val list = result.getImpactedList()
        assertTrue(list.contains("z1"))
        assertTrue(list.contains("z2"))
    }

    @Test
    fun testResolveAmpShot_invalidTargetsFiltered() {
        val state = AmpShieldState(100, 100, 40, 2000, 10, 5000)
        val t1 = ZombieTarget("dead", 50f, 0f, hp = 0)
        val t2 = ZombieTarget("nan", Float.NaN, 0f)
        val t3 = ZombieTarget("valid", 50f, 0f)
        val targets = arrayOf(t1, t2, t3)

        val result = AmpShotEvaluationResult(10)
        AmpShotShieldGenerator.resolveAmpShot(
            state, baseDamage = 100, playerX = 0f, playerY = 0f,
            aimAngleRad = 0f, shotRangePx = 200f, targets = targets, targetsCount = 3, outResult = result
        )

        assertEquals(1, result.impactedCount)
        val list = result.getImpactedList()
        assertTrue(list.contains("valid"))
    }

    @Test
    fun testResolveAmpShot_adversarialInputRecovery() {
        val state = AmpShieldState(100, 100, 40, 2000, 10, 5000)
        val t1 = ZombieTarget("z1", 50f, 0f)

        val result = AmpShotEvaluationResult(10)
        // Firing with NaN angle, player coords, and range.
        AmpShotShieldGenerator.resolveAmpShot(
            state, baseDamage = 100, playerX = Float.NaN, playerY = Float.NaN,
            aimAngleRad = Float.NaN, shotRangePx = Float.NaN, targets = arrayOf(t1), targetsCount = 1, outResult = result
        )

        // Math fallbacks to 0,0 angle 0 range 1000. So shot goes from 0,0 to 1000,0.
        // t1 is at 50,0 so it should be hit!
        val list = result.getImpactedList()
        assertTrue(list.contains("z1"))
        assertEquals(1, result.impactedCount)
    }

    @Test
    fun testDeterminismProof() {
        val s1 = AmpShotShieldGenerator.generateShield(1337L, 3)
        val s2 = AmpShotShieldGenerator.generateShield(1337L, 3)
        assertEquals(s1, s2)
        assertEquals(s1.computeChecksum(), s2.computeChecksum())
    }
}
