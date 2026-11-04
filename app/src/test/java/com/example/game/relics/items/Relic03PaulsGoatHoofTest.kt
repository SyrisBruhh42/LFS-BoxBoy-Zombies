package com.example.game.relics.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure JVM Unit Test Suite for Relic03PaulsGoatHoof
 * Tests execution paths without Robolectric to ensure <15s Gradle times.
 */
class Relic03PaulsGoatHoofTest {

    private val TOLERANCE = 0.0001f

    @Test
    fun testConfigDefaultsAndInvariant() {
        val config = PaulsGoatHoofConfig()
        assertEquals(0.14f, config.speedMultiplierPerStack, TOLERANCE)
        assertEquals(false, config.canBePurchasedAtVending)
        assertEquals(2400f, config.sectorWorldSizePx, TOLERANCE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConfigFailsWhenVendingIsTrue() {
        PaulsGoatHoofConfig(canBePurchasedAtVending = true)
    }

    @Test
    fun testStateDefaultAndConstraints() {
        val state1 = PaulsGoatHoofState(stackCount = -5)
        assertEquals(1, state1.stackCount) // Coerced to 1

        val state2 = PaulsGoatHoofState(stackCount = 0)
        assertEquals(1, state2.stackCount) // Coerced to 1

        val state3 = PaulsGoatHoofState(stackCount = 5)
        assertEquals(5, state3.stackCount)
    }

    @Test
    fun testMapSerializationRoundTrip() {
        val originalConfig = PaulsGoatHoofConfig(speedMultiplierPerStack = 0.20f, sectorWorldSizePx = 4000f)
        val originalState = PaulsGoatHoofState(stackCount = 3, lcgSeed = 9999L)

        val configMap = originalConfig.toMap()
        val stateMap = originalState.toMap()

        val restoredConfig = PaulsGoatHoofConfig.fromMap(configMap)
        val restoredState = PaulsGoatHoofState.fromMap(stateMap)

        assertEquals(originalConfig.speedMultiplierPerStack, restoredConfig.speedMultiplierPerStack, TOLERANCE)
        assertEquals(originalConfig.sectorWorldSizePx, restoredConfig.sectorWorldSizePx, TOLERANCE)
        assertEquals(originalState.stackCount, restoredState.stackCount)
        assertEquals(originalState.lcgSeed, restoredState.lcgSeed)
    }

    @Test
    fun testMapSerializationWithMissingOrInvalidData() {
        val configMap = mapOf<String, Any>("speedMultiplierPerStack" to "invalid")
        val restoredConfig = PaulsGoatHoofConfig.fromMap(configMap)
        assertEquals(0.14f, restoredConfig.speedMultiplierPerStack, TOLERANCE)

        val stateMap = mapOf<String, Any>("stackCount" to -10)
        val restoredState = PaulsGoatHoofState.fromMap(stateMap)
        assertEquals(1, restoredState.stackCount)
    }

    @Test
    fun testChecksumDeterminism() {
        val state1 = PaulsGoatHoofState(stackCount = 2, lcgSeed = 100L)
        val state2 = PaulsGoatHoofState(stackCount = 2, lcgSeed = 100L)
        
        assertEquals(state1.calculateChecksum(), state2.calculateChecksum())
        
        val state3 = PaulsGoatHoofState(stackCount = 3, lcgSeed = 100L)
        assertNotEquals(state1.calculateChecksum(), state3.calculateChecksum())
    }

    @Test
    fun testComputeMovementNominalPath() {
        val config = PaulsGoatHoofConfig()
        val state = PaulsGoatHoofState(stackCount = 1) // +14% speed
        // Multiplier = 1.14

        val result = Relic03PaulsGoatHoofEngine.computeMovement(
            dtSeconds = 0.1f, // Standard step
            config = config,
            state = state,
            currentX = 100f,
            currentY = 100f,
            inputDx = 1f, // Moving right
            inputDy = 0f,
            baseSpeedPxPerSec = 100f
        )

        // final speed = 100 * 1.14 = 114
        // move dist = 114 * 0.1 = 11.4
        // newX = 100 + 11.4 = 111.4
        assertEquals(1.14f, result.actualSpeedMultiplier, TOLERANCE)
        assertEquals(111.4f, result.newX, TOLERANCE)
        assertEquals(100f, result.newY, TOLERANCE)
    }

    @Test
    fun testComputeMovementStackScaling() {
        val config = PaulsGoatHoofConfig()
        val state = PaulsGoatHoofState(stackCount = 5) // +70% speed (5 * 14%)
        // Multiplier = 1.70

        val result = Relic03PaulsGoatHoofEngine.computeMovement(
            dtSeconds = 0.1f,
            config = config,
            state = state,
            currentX = 100f,
            currentY = 100f,
            inputDx = 0f,
            inputDy = 1f, // Moving down
            baseSpeedPxPerSec = 100f
        )

        // final speed = 100 * 1.70 = 170
        // move dist = 170 * 0.1 = 17.0
        // newY = 100 + 17 = 117
        assertEquals(1.70f, result.actualSpeedMultiplier, TOLERANCE)
        assertEquals(100f, result.newX, TOLERANCE)
        assertEquals(117f, result.newY, TOLERANCE)
    }

    @Test
    fun testComputeMovementClampingBounds() {
        val config = PaulsGoatHoofConfig(sectorWorldSizePx = 2400f)
        val state = PaulsGoatHoofState(stackCount = 10)

        // Moving out of upper bounds
        val resultOutMax = Relic03PaulsGoatHoofEngine.computeMovement(
            dtSeconds = 0.1f,
            config = config,
            state = state,
            currentX = 2395f,
            currentY = 2395f,
            inputDx = 1f,
            inputDy = 1f,
            baseSpeedPxPerSec = 1000f // Fast to ensure it goes out
        )
        assertTrue(resultOutMax.newX <= 2400f)
        assertTrue(resultOutMax.newY <= 2400f)

        // Moving out of lower bounds
        val resultOutMin = Relic03PaulsGoatHoofEngine.computeMovement(
            dtSeconds = 0.1f,
            config = config,
            state = state,
            currentX = 5f,
            currentY = 5f,
            inputDx = -1f,
            inputDy = -1f,
            baseSpeedPxPerSec = 1000f
        )
        assertTrue(resultOutMin.newX >= 0f)
        assertTrue(resultOutMin.newY >= 0f)
    }

    @Test
    fun testComputeMovementAdversarialInputs() {
        val config = PaulsGoatHoofConfig()
        val state = PaulsGoatHoofState()

        val resultNaN = Relic03PaulsGoatHoofEngine.computeMovement(
            dtSeconds = Float.NaN,
            config = config,
            state = state,
            currentX = 10f,
            currentY = 10f,
            inputDx = 1f,
            inputDy = 1f,
            baseSpeedPxPerSec = 10f
        )
        
        // Should gracefully degrade and not update position
        assertEquals(10f, resultNaN.newX, TOLERANCE)
        assertEquals(10f, resultNaN.newY, TOLERANCE)
        assertEquals(1.0f, resultNaN.actualSpeedMultiplier, TOLERANCE)

        val resultInf = Relic03PaulsGoatHoofEngine.computeMovement(
            dtSeconds = 0.1f,
            config = config,
            state = state,
            currentX = Float.POSITIVE_INFINITY,
            currentY = 10f,
            inputDx = 1f,
            inputDy = 1f,
            baseSpeedPxPerSec = 10f
        )
        assertEquals(0f, resultInf.newX, TOLERANCE) // Handled by fallback to 0f
    }

    @Test
    fun testAddStacksUtility() {
        val state = PaulsGoatHoofState(stackCount = 2)
        Relic03PaulsGoatHoofEngine.addStacks(state, 3)
        assertEquals(5, state.stackCount)
        
        Relic03PaulsGoatHoofEngine.addStacks(state, -10)
        assertEquals(5, state.stackCount) // Cannot add negative stacks
    }

    @Test
    fun testBatchMovementExtremeLoad() {
        val count = 300 // Max capacity stress test
        val config = PaulsGoatHoofConfig()
        val states = Array(count) { PaulsGoatHoofState(stackCount = 2, lcgSeed = it.toLong()) }
        val currentXs = FloatArray(count) { 100f }
        val currentYs = FloatArray(count) { 100f }
        val inputDxs = FloatArray(count) { 1f }
        val inputDys = FloatArray(count) { 0f }
        val baseSpeeds = FloatArray(count) { 100f }
        
        val outNewXs = FloatArray(count)
        val outNewYs = FloatArray(count)
        val outMultipliers = FloatArray(count)
        val outDust = IntArray(count)

        Relic03PaulsGoatHoofEngine.processBatchMovement(
            dtSeconds = 0.1f,
            config = config,
            states = states,
            currentXs = currentXs,
            currentYs = currentYs,
            inputDxs = inputDxs,
            inputDys = inputDys,
            baseSpeedPxPerSecs = baseSpeeds,
            outNewXs = outNewXs,
            outNewYs = outNewYs,
            outActualSpeedMultipliers = outMultipliers,
            outGeneratedDustParticles = outDust,
            count = count
        )

        // Verify output for all 300 entities
        for (i in 0 until count) {
            // Stack = 2 -> +28% -> multiplier 1.28
            assertEquals(1.28f, outMultipliers[i], TOLERANCE)
            // Speed = 100 * 1.28 = 128. Dist = 128 * 0.1 = 12.8
            // 100 + 12.8 = 112.8
            assertEquals(112.8f, outNewXs[i], TOLERANCE)
            assertEquals(100f, outNewYs[i], TOLERANCE)
            // Dust generated is pseudo-random but should run without throwing
            assertTrue(outDust[i] >= 0)
        }
    }
}
