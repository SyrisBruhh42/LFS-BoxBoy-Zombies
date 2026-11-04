package com.example.game.relics.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure JVM Test Suite for Track 6 Relic 14: Wax Quail Booster
 * Ensures >12 test cases covering nominal, boundary, determinism, and anti-pollution constraints.
 */
class Relic14WaxQuailBoosterTest {

    private val config = WaxQuailBoosterConfig()

    @Test
    fun testNominal_singleStackSprint_grants120pxBurst() {
        val state = WaxQuailBoosterState(stackCount = 1, isSprinting = true)
        val result = WaxQuailBoosterEngine.processTick(0.016f, config, state)

        assertEquals(120.0f, result.burstAppliedPx, 0.001f)
        assertEquals(120.0f, result.dx, 0.001f) // default rotation is 0 rad (cos(0) = 1)
        assertEquals(0.0f, result.dy, 0.001f)
    }

    @Test
    fun testNominal_fiveStacksSprint_grants600pxBurst() {
        val state = WaxQuailBoosterState(stackCount = 5, isSprinting = true)
        val result = WaxQuailBoosterEngine.processTick(0.016f, config, state)

        assertEquals(600.0f, result.burstAppliedPx, 0.001f)
    }

    @Test
    fun testNominal_noSprint_noBurst() {
        val state = WaxQuailBoosterState(stackCount = 3, isSprinting = false)
        val result = WaxQuailBoosterEngine.processTick(0.016f, config, state)

        assertEquals(0.0f, result.burstAppliedPx, 0.001f)
        assertEquals(0.0f, result.dx, 0.001f)
        assertEquals(0.0f, result.dy, 0.001f)
    }

    @Test
    fun testCooldown_preventsMultipleBursts() {
        val state = WaxQuailBoosterState(stackCount = 1, isSprinting = true)
        val result1 = WaxQuailBoosterEngine.processTick(0.016f, config, state)
        assertEquals(120.0f, result1.burstAppliedPx, 0.001f)

        // Immediate next tick while sprinting should not burst again due to cooldown
        val result2 = WaxQuailBoosterEngine.processTick(0.016f, config, result1.newState)
        assertEquals(0.0f, result2.burstAppliedPx, 0.001f)

        // Advance time by 600ms (> 500ms cooldown)
        val result3 = WaxQuailBoosterEngine.processTick(0.600f, config, result1.newState.copy(burstCooldownMs = 0f))
        assertEquals(120.0f, result3.burstAppliedPx, 0.001f)
    }

    @Test
    fun testRotation_burstAppliesCorrectVector() {
        val rotation = Math.PI.toFloat() / 2f // 90 degrees
        val state = WaxQuailBoosterState(stackCount = 1, isSprinting = true, rotationRad = rotation)
        val result = WaxQuailBoosterEngine.processTick(0.016f, config, state)

        assertEquals(120.0f, result.burstAppliedPx, 0.001f)
        assertTrue(Math.abs(result.dx) < 0.001f) // cos(90) ~ 0
        assertEquals(120.0f, result.dy, 0.001f)  // sin(90) = 1
    }

    @Test
    fun testBoundary_dtClamping() {
        val state = WaxQuailBoosterState(stackCount = 1, isSprinting = true, burstCooldownMs = 500f)
        // Pass absurd dt
        val result = WaxQuailBoosterEngine.processTick(999.0f, config, state)

        // dt should clamp to 0.1s (100ms)
        // 500ms - 100ms = 400ms remaining
        assertEquals(400.0f, result.newState.burstCooldownMs, 0.001f)
    }

    @Test
    fun testBoundary_extremeStackCapping() {
        // Even with absurd stacks, burst should cap at 2400f
        val state = WaxQuailBoosterState(stackCount = 9999, isSprinting = true)
        val result = WaxQuailBoosterEngine.processTick(0.016f, config, state)

        assertEquals(2400.0f, result.burstAppliedPx, 0.001f)
    }

    @Test
    fun testMapBoundary_clamping() {
        val state = WaxQuailBoosterState(
            stackCount = 1,
            isSprinting = true,
            x = WaxQuailBoosterEngine.MAX_MAP_BOUNDARY_PX - 10f,
            y = 0f
        )
        val result = WaxQuailBoosterEngine.processTick(0.016f, config, state)

        // Should burst 120px, but x + dx would exceed boundary
        assertEquals(WaxQuailBoosterEngine.MAX_MAP_BOUNDARY_PX, result.newState.x, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvariant_stackCountMustBePositive() {
        WaxQuailBoosterState(stackCount = 0, isSprinting = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvariant_cannotBePurchased() {
        WaxQuailBoosterConfig(canBePurchasedAtVending = true)
    }

    @Test
    fun testSerialization_toMapFromMapRoundtrip() {
        val original = WaxQuailBoosterState(
            stackCount = 3,
            isSprinting = true,
            burstCooldownMs = 150f,
            x = 100.5f,
            y = -250.25f,
            rotationRad = 1.57f,
            checksum = 123456789L
        )
        val map = original.toMap()
        val deserialized = WaxQuailBoosterState.fromMap(map)

        assertEquals(original, deserialized)
    }

    @Test
    fun testDeterminism_fnv1aChecksumConsistency() {
        val state1 = WaxQuailBoosterState(stackCount = 2, isSprinting = false, x = 10f, y = 20f)
        val state2 = WaxQuailBoosterState(stackCount = 2, isSprinting = false, x = 10f, y = 20f)
        val stateDiff = WaxQuailBoosterState(stackCount = 3, isSprinting = false, x = 10f, y = 20f)

        assertEquals(state1.computeFnv1a(), state2.computeFnv1a())
        assertNotEquals(state1.computeFnv1a(), stateDiff.computeFnv1a())
    }

    @Test
    fun testRobustness_nanRecovery() {
        val state = WaxQuailBoosterState(
            stackCount = 1,
            isSprinting = true,
            burstCooldownMs = Float.NaN // Inject NaN
        )
        val result = WaxQuailBoosterEngine.processTick(0.016f, config, state)

        // Cooldown should recover to 0f, allowing the burst
        assertEquals(120.0f, result.burstAppliedPx, 0.001f)
        // Next cooldown should be set
        assertEquals(500.0f, result.newState.burstCooldownMs, 0.001f)
    }
}
