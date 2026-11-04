package com.example.game.economy.vendors

import com.example.data.PerkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class PerkStaminUpMachineTest {

    private lateinit var machine: PerkStaminUpMachine
    private val epsilon = 0.0001f

    @Before
    fun setUp() {
        machine = PerkStaminUpMachine()
    }

    @Test
    fun testSerializationRoundtrip() {
        val original = StaminUpMachineState(
            machineId = 123,
            x = 100.5f,
            y = 200.75f,
            isPowered = false,
            currentCost = 1200,
            ambientRotationDeg = 45f
        )
        val map = original.toMap()
        val restored = StaminUpMachineState.fromMap(map)
        assertEquals(original, restored)
    }

    @Test
    fun testFnv1aChecksumDeterminism() {
        val state1 = StaminUpMachineState(1, 10f, 20f, true, 1500, 90f)
        val state2 = StaminUpMachineState(1, 10f, 20f, true, 1500, 90f)
        val state3 = StaminUpMachineState(1, 10.01f, 20f, true, 1500, 90f)

        assertEquals(state1.computeFnv1aChecksum(), state2.computeFnv1aChecksum())
        assertNotEquals(state1.computeFnv1aChecksum(), state3.computeFnv1aChecksum())
    }

    @Test
    fun testStepAnimationUpdate() {
        val initialState = StaminUpMachineState(1, 0f, 0f, true, 1500, 350f)
        val dt = 0.05f // 50ms

        val newState = machine.step(initialState, dt)
        val expectedRot = 350f + (PerkStaminUpMachine.ROTATION_SPEED_DEG_PER_SEC * dt)

        assertTrue("Expected $expectedRot but was ${newState.ambientRotationDeg}", abs(expectedRot - newState.ambientRotationDeg) < epsilon)
    }

    @Test
    fun testStepAnimationRotationWrap() {
        val initialState = StaminUpMachineState(1, 0f, 0f, true, 1500, 355f)
        val dt = 0.5f // artificially large to force wrap, but it gets clamped to 0.100f

        val newState = machine.step(initialState, dt)
        // Dt gets clamped to 0.100f
        val expectedRot = 355f + (PerkStaminUpMachine.ROTATION_SPEED_DEG_PER_SEC * 0.100f) // 355 + 4.5 = 359.5 (no wrap)

        // Let's test with smaller rotation to hit 360 wrap
        val wrapState = StaminUpMachineState(1, 0f, 0f, true, 1500, 359f)
        val wrapNewState = machine.step(wrapState, 0.1f)
        val wrapExpectedRot = (359f + 4.5f) - 360f // 3.5f

        assertTrue("Expected wrap $wrapExpectedRot but was ${wrapNewState.ambientRotationDeg}", abs(wrapExpectedRot - wrapNewState.ambientRotationDeg) < epsilon)
    }

    @Test
    fun testStepDtClamping() {
        val initialState = StaminUpMachineState(1, 0f, 0f, true, 1500, 0f)

        // Negative dt clamped to 0.001
        val stateNegDt = machine.step(initialState, -1f)
        assertTrue(abs(stateNegDt.ambientRotationDeg - (45f * 0.001f)) < epsilon)

        // Extremely large dt clamped to 0.100
        val stateLargeDt = machine.step(initialState, 10f)
        assertTrue(abs(stateLargeDt.ambientRotationDeg - (45f * 0.100f)) < epsilon)
    }

    @Test
    fun testStepNaNRecovery() {
        val initialState = StaminUpMachineState(1, 0f, 0f, true, 1500, 0f)
        val newState = machine.step(initialState, Float.NaN)

        // NaN should fallback to 0.001f dt
        assertTrue(abs(newState.ambientRotationDeg - (45f * 0.001f)) < epsilon)
    }

    @Test
    fun testPurchaseSuccess() {
        val state = StaminUpMachineState(1, 100f, 100f, true, 1500, 0f)
        val result = machine.attemptPurchase(state, 110f, 110f, 2000, emptySet())

        assertEquals(StaminUpPurchaseResult.SUCCESS, result.result)
        assertEquals(500, result.newCurrencyPoints)
        assertEquals(PerkType.STAMIN_UP, result.perkGranted)
    }

    @Test
    fun testPurchaseInsufficientFunds() {
        val state = StaminUpMachineState(1, 100f, 100f, true, 1500, 0f)
        val result = machine.attemptPurchase(state, 100f, 100f, 1000, emptySet())

        assertEquals(StaminUpPurchaseResult.INSUFFICIENT_FUNDS, result.result)
        assertEquals(1000, result.newCurrencyPoints)
        assertNull(result.perkGranted)
    }

    @Test
    fun testPurchaseOutOfRange() {
        val state = StaminUpMachineState(1, 100f, 100f, true, 1500, 0f)
        // distance > 72f (activation radius)
        val result = machine.attemptPurchase(state, 200f, 200f, 2000, emptySet())

        assertEquals(StaminUpPurchaseResult.OUT_OF_RANGE, result.result)
        assertEquals(2000, result.newCurrencyPoints)
        assertNull(result.perkGranted)
    }

    @Test
    fun testPurchaseAlreadyOwned() {
        val state = StaminUpMachineState(1, 100f, 100f, true, 1500, 0f)
        val result = machine.attemptPurchase(state, 100f, 100f, 2000, setOf(PerkType.STAMIN_UP, PerkType.JUGGERNOG))

        assertEquals(StaminUpPurchaseResult.ALREADY_OWNED, result.result)
        assertEquals(2000, result.newCurrencyPoints)
        assertNull(result.perkGranted)
    }

    @Test
    fun testPurchaseNotPowered() {
        val state = StaminUpMachineState(1, 100f, 100f, false, 1500, 0f)
        val result = machine.attemptPurchase(state, 100f, 100f, 2000, emptySet())

        assertEquals(StaminUpPurchaseResult.NOT_POWERED, result.result)
        assertEquals(2000, result.newCurrencyPoints)
        assertNull(result.perkGranted)
    }

    @Test
    fun testPurchasePlayerNaNRecovery() {
        val state = StaminUpMachineState(1, 100f, 100f, true, 1500, 0f)
        val result = machine.attemptPurchase(state, Float.NaN, Float.POSITIVE_INFINITY, 2000, emptySet())

        // Player position recovers to (0,0). Machine is at (100,100). Distance = 141.4 > 72 (activation radius)
        assertEquals(StaminUpPurchaseResult.OUT_OF_RANGE, result.result)
    }

    @Test
    fun testPurchaseMachineNaNRecovery() {
        val state = StaminUpMachineState(1, Float.NaN, Float.NaN, true, 1500, 0f)
        // Machine recovers to (0,0). Player at (10,10) is within radius.
        val result = machine.attemptPurchase(state, 10f, 10f, 2000, emptySet())

        assertEquals(StaminUpPurchaseResult.SUCCESS, result.result)
    }

    @Test
    fun testCalculateSpeedMultiplierWithPerk() {
        val mult = machine.calculateSpeedMultiplier(setOf(PerkType.JUGGERNOG, PerkType.STAMIN_UP))
        assertTrue(abs(1.28f - mult) < epsilon)
    }

    @Test
    fun testCalculateSpeedMultiplierWithoutPerk() {
        val mult = machine.calculateSpeedMultiplier(setOf(PerkType.JUGGERNOG))
        assertTrue(abs(1.0f - mult) < epsilon)
    }
}
