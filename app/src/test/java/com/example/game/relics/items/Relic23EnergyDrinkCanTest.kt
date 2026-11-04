package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test

class Relic23EnergyDrinkCanTest {

    @Test
    fun testNominalSpeedScalingStack1() {
        val config = EnergyDrinkCanConfig()
        val state = EnergyDrinkCanState(stackCount = 1)
        val engine = Relic23EnergyDrinkCanEngine()

        val count = 1
        val isFiring = BooleanArray(count) { false }
        val moveX = FloatArray(count) { 1f }
        val moveY = FloatArray(count) { 0f }
        val baseSpeeds = FloatArray(count) { 100f }
        val outSpeeds = FloatArray(count)

        engine.processBatch(state, config, 0.016f, isFiring, moveX, moveY, baseSpeeds, outSpeeds, count, 123L)

        // 100 * (1.0 + 1 * 0.25) = 125, minus slight micro-friction
        assertTrue("Speed should be around 125", outSpeeds[0] in 124f..125f)
    }

    @Test
    fun testMultiStackScaling() {
        val config = EnergyDrinkCanConfig()
        val state = EnergyDrinkCanState(stackCount = 4)
        val engine = Relic23EnergyDrinkCanEngine()

        val count = 1
        val isFiring = BooleanArray(count) { false }
        val moveX = FloatArray(count) { 1f }
        val moveY = FloatArray(count) { 1f }
        val baseSpeeds = FloatArray(count) { 100f }
        val outSpeeds = FloatArray(count)

        engine.processBatch(state, config, 0.016f, isFiring, moveX, moveY, baseSpeeds, outSpeeds, count, 123L)

        // 100 * (1.0 + 4 * 0.25) = 200, minus slight micro-friction
        assertTrue("Speed should be around 200", outSpeeds[0] in 199f..200f)
    }

    @Test
    fun testDisablingSpeedBoostWhenFiring() {
        val config = EnergyDrinkCanConfig()
        val state = EnergyDrinkCanState(stackCount = 4)
        val engine = Relic23EnergyDrinkCanEngine()

        val count = 1
        val isFiring = BooleanArray(count) { true }
        val moveX = FloatArray(count) { 1f }
        val moveY = FloatArray(count) { 0f }
        val baseSpeeds = FloatArray(count) { 100f }
        val outSpeeds = FloatArray(count)

        engine.processBatch(state, config, 0.016f, isFiring, moveX, moveY, baseSpeeds, outSpeeds, count, 123L)

        assertEquals("Speed should not be boosted when firing", 100f, outSpeeds[0])
    }

    @Test
    fun testDisablingSpeedBoostWhenStandingStill() {
        val config = EnergyDrinkCanConfig()
        val state = EnergyDrinkCanState(stackCount = 4)
        val engine = Relic23EnergyDrinkCanEngine()

        val count = 1
        val isFiring = BooleanArray(count) { false }
        val moveX = FloatArray(count) { 0f }
        val moveY = FloatArray(count) { 0f }
        val baseSpeeds = FloatArray(count) { 100f }
        val outSpeeds = FloatArray(count)

        engine.processBatch(state, config, 0.016f, isFiring, moveX, moveY, baseSpeeds, outSpeeds, count, 123L)

        assertEquals("Speed should not be boosted when stationary", 100f, outSpeeds[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectionOfDropOnlyViolation() {
        EnergyDrinkCanConfig(canBePurchasedAtVending = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectionOfInvalidStackCount() {
        EnergyDrinkCanState(stackCount = 0)
    }

    @Test
    fun testSpatialOutOfBoundsClamping() {
        val config = EnergyDrinkCanConfig(sprintSpeedBoostPerStack = 50.0f) // Insane boost
        val state = EnergyDrinkCanState(stackCount = 100)
        val engine = Relic23EnergyDrinkCanEngine()

        val count = 1
        val isFiring = BooleanArray(count) { false }
        val moveX = FloatArray(count) { 1f }
        val moveY = FloatArray(count) { 1f }
        val baseSpeeds = FloatArray(count) { 1000f }
        val outSpeeds = FloatArray(count)

        engine.processBatch(state, config, 0.016f, isFiring, moveX, moveY, baseSpeeds, outSpeeds, count, 123L)

        assertTrue("Output speed should be clamped to 1000f", outSpeeds[0] <= 1000f)
    }

    @Test
    fun testNaNInfinityResilience() {
        val config = EnergyDrinkCanConfig()
        val state = EnergyDrinkCanState(stackCount = 1)
        val engine = Relic23EnergyDrinkCanEngine()

        val count = 3
        val isFiring = BooleanArray(count) { false }
        val moveX = FloatArray(count) { 1f }
        val moveY = FloatArray(count) { 1f }
        val baseSpeeds = FloatArray(count)
        baseSpeeds[0] = Float.NaN
        baseSpeeds[1] = Float.POSITIVE_INFINITY
        baseSpeeds[2] = -Float.POSITIVE_INFINITY
        
        val outSpeeds = FloatArray(count)

        engine.processBatch(state, config, 0.016f, isFiring, moveX, moveY, baseSpeeds, outSpeeds, count, 123L)

        assertEquals("NaN should be handled gracefully (default 0)", 0f, outSpeeds[0])
        assertEquals("Infinity should be handled gracefully (default 0)", 0f, outSpeeds[1])
        assertEquals("-Infinity should be handled gracefully (default 0)", 0f, outSpeeds[2])
    }

    @Test
    fun testBatchArrayMaxCapacityLoadTest() {
        val config = EnergyDrinkCanConfig()
        val state = EnergyDrinkCanState(stackCount = 2)
        val engine = Relic23EnergyDrinkCanEngine()

        val count = 300
        val isFiring = BooleanArray(count) { it % 2 == 0 }
        val moveX = FloatArray(count) { if (it % 3 == 0) 1f else 0f }
        val moveY = FloatArray(count) { if (it % 3 == 0) 1f else 0f }
        val baseSpeeds = FloatArray(count) { 100f }
        val outSpeeds = FloatArray(count)

        val newState = engine.processBatch(state, config, 0.016f, isFiring, moveX, moveY, baseSpeeds, outSpeeds, count, 456L)
        
        assertNotEquals(0L, newState.checksum)
        for (i in 0 until count) {
            val isSprinting = !isFiring[i] && (moveX[i] > 0f || moveY[i] > 0f)
            if (isSprinting) {
                assertTrue("Sprinting target should be around 150", outSpeeds[i] > 145f)
            } else {
                assertEquals("Non-sprinting target should be exactly 100", 100f, outSpeeds[i])
            }
        }
    }

    @Test
    fun testDeterminismProofs() {
        val config = EnergyDrinkCanConfig()
        val state = EnergyDrinkCanState(stackCount = 3)
        val engine = Relic23EnergyDrinkCanEngine()

        val count = 50
        val isFiring = BooleanArray(count) { false }
        val moveX = FloatArray(count) { 1f }
        val moveY = FloatArray(count) { 0f }
        val baseSpeeds = FloatArray(count) { 100f }

        val outSpeeds1 = FloatArray(count)
        val outSpeeds2 = FloatArray(count)

        val state1 = engine.processBatch(state, config, 0.016f, isFiring, moveX, moveY, baseSpeeds, outSpeeds1, count, 999L)
        val state2 = engine.processBatch(state, config, 0.016f, isFiring, moveX, moveY, baseSpeeds, outSpeeds2, count, 999L)

        assertArrayEquals("Output arrays must match exactly", outSpeeds1, outSpeeds2, 0f)
        assertEquals("State checksums must match exactly", state1.checksum, state2.checksum)
    }

    @Test
    fun testRoundtripToMapFromMapConfig() {
        val original = EnergyDrinkCanConfig(sprintSpeedBoostPerStack = 0.3f, canBePurchasedAtVending = false)
        val map = original.toMap()
        val reconstructed = EnergyDrinkCanConfig.fromMap(map)

        assertEquals(original, reconstructed)
    }

    @Test
    fun testRoundtripToMapFromMapState() {
        val original = EnergyDrinkCanState(stackCount = 5, checksum = 987654321L)
        val map = original.toMap()
        val reconstructed = EnergyDrinkCanState.fromMap(map)

        assertEquals(original, reconstructed)
    }
}
