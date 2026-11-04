package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test

class Relic04SoldiersSyringeTest {

    @Test
    fun testInitializationAndBasicMathValidity() {
        val config = SoldiersSyringeConfig()
        val state = Relic04SoldiersSyringeEngine.createInitialState(1)

        assertEquals(1, state.stackCount)

        val multiplier = Relic04SoldiersSyringeEngine.calculateFireRateMultiplier(config, state)
        assertEquals(1.15f, multiplier, 0.001f)

        val newFireCooldownMs = Relic04SoldiersSyringeEngine.calculateNewFireCooldownMs(1000f, config, state)
        assertEquals(1000f / 1.15f, newFireCooldownMs, 0.001f)

        val newReloadMs = Relic04SoldiersSyringeEngine.calculateNewReloadTimeMs(2000f, config, state)
        assertEquals(2000f / 1.15f, newReloadMs, 0.001f)
    }

    @Test
    fun testScalingWithMultipleStacks() {
        val config = SoldiersSyringeConfig()
        val state = Relic04SoldiersSyringeEngine.createInitialState(10)

        assertEquals(10, state.stackCount)

        val multiplier = Relic04SoldiersSyringeEngine.calculateFireRateMultiplier(config, state)
        assertEquals(1.0f + (10 * 0.15f), multiplier, 0.001f)

        val newFireCooldownMs = Relic04SoldiersSyringeEngine.calculateNewFireCooldownMs(1000f, config, state)
        assertEquals(1000f / 2.5f, newFireCooldownMs, 0.001f)
    }

    @Test
    fun testDeterminismWithIdenticalInputs() {
        val config = SoldiersSyringeConfig()
        val state1 = Relic04SoldiersSyringeEngine.createInitialState(5)
        val state2 = Relic04SoldiersSyringeEngine.createInitialState(5)

        assertEquals(state1.checksum, state2.checksum)

        val m1 = Relic04SoldiersSyringeEngine.calculateFireRateMultiplier(config, state1)
        val m2 = Relic04SoldiersSyringeEngine.calculateFireRateMultiplier(config, state2)

        assertEquals(m1, m2, 0.0001f)
    }

    @Test
    fun testBoundaryLimitsZeroAndNegativeStacks() {
        val config = SoldiersSyringeConfig()
        val state0 = Relic04SoldiersSyringeEngine.createInitialState(0)
        val stateNeg = Relic04SoldiersSyringeEngine.createInitialState(-5)

        assertEquals(1, state0.stackCount)
        assertEquals(1, stateNeg.stackCount)

        val m0 = Relic04SoldiersSyringeEngine.calculateFireRateMultiplier(config, state0)
        val mNeg = Relic04SoldiersSyringeEngine.calculateFireRateMultiplier(config, stateNeg)

        assertEquals(1.15f, m0, 0.001f)
        assertEquals(1.15f, mNeg, 0.001f)
    }

    @Test
    fun testNaNAndInfinityInputRecovery() {
        val config = SoldiersSyringeConfig()
        val state = Relic04SoldiersSyringeEngine.createInitialState(1)

        val fireCooldown = Relic04SoldiersSyringeEngine.calculateNewFireCooldownMs(Float.NaN, config, state)
        assertEquals(0f, fireCooldown, 0.0f)

        val fireCooldownInf = Relic04SoldiersSyringeEngine.calculateNewFireCooldownMs(Float.POSITIVE_INFINITY, config, state)
        assertEquals(0f, fireCooldownInf, 0.0f)

        val reloadTime = Relic04SoldiersSyringeEngine.calculateNewReloadTimeMs(Float.NaN, config, state)
        assertEquals(0f, reloadTime, 0.0f)
    }

    @Test
    fun testJsonMapSerializationRoundtripParity() {
        val config = SoldiersSyringeConfig(0.2f, 0.2f)
        val state = Relic04SoldiersSyringeEngine.createInitialState(3)

        val configMap = config.toMap()
        val stateMap = state.toMap()

        val restoredConfig = SoldiersSyringeConfig.fromMap(configMap)
        val restoredState = SoldiersSyringeState.fromMap(stateMap)

        assertEquals(config.baseFireRateIncreasePerStack, restoredConfig.baseFireRateIncreasePerStack, 0.001f)
        assertEquals(state.stackCount, restoredState.stackCount)
        assertEquals(state.checksum, restoredState.checksum)
    }

    @Test
    fun testCanBePurchasedAtVendingInvariant() {
        assertFalse(Relic04SoldiersSyringeEngine.CAN_BE_PURCHASED_AT_VENDING)
    }

    @Test
    fun testExtremeLoadGracefulDegradationPaths() {
        val config = SoldiersSyringeConfig()
        val state = Relic04SoldiersSyringeEngine.createInitialState(500)

        val tickedState = Relic04SoldiersSyringeEngine.processTick(
            dtSeconds = 0.016f,
            config = config,
            state = state,
            activeZombies = 300,
            coopPeers = 4
        )

        assertEquals(500, tickedState.stackCount)
        assertEquals(state.checksum, tickedState.checksum)
    }

    @Test
    fun testInvalidChecksumSelfHealing() {
        val config = SoldiersSyringeConfig()
        val state = SoldiersSyringeState(10, 12345L) // invalid checksum

        val multiplier = Relic04SoldiersSyringeEngine.calculateFireRateMultiplier(config, state)
        assertEquals(1.15f, multiplier, 0.001f) // Should fallback to 1 stack

        val tickedState = Relic04SoldiersSyringeEngine.processTick(
            dtSeconds = 0.016f,
            config = config,
            state = state,
            activeZombies = 10,
            coopPeers = 1
        )

        assertEquals(10, tickedState.stackCount)
        assertEquals(Relic04SoldiersSyringeEngine.computeChecksum(10), tickedState.checksum)
    }

    @Test
    fun testDtClamping() {
        val config = SoldiersSyringeConfig()
        val state = Relic04SoldiersSyringeEngine.createInitialState(1)

        val tickedState = Relic04SoldiersSyringeEngine.processTick(
            dtSeconds = 5.0f, // huge dt
            config = config,
            state = state,
            activeZombies = 0,
            coopPeers = 0
        )

        assertEquals(1, tickedState.stackCount)

        val tickedStateNaN = Relic04SoldiersSyringeEngine.processTick(
            dtSeconds = Float.NaN,
            config = config,
            state = state,
            activeZombies = 0,
            coopPeers = 0
        )

        assertEquals(1, tickedStateNaN.stackCount)
    }

    @Test
    fun testCorruptedMapDeserialization() {
        val badMap: Map<String, Any> = mapOf(
            "stackCount" to "not_a_number",
            "checksum" to 0L
        )

        val state = SoldiersSyringeState.fromMap(badMap)
        assertEquals(1, state.stackCount)
        assertEquals(0L, state.checksum)

        val badConfigMap: Map<String, Any> = mapOf(
            "baseFireRateIncreasePerStack" to "NaN",
            "baseReloadSpeedIncreasePerStack" to 0f
        )

        val config = SoldiersSyringeConfig.fromMap(badConfigMap)
        assertEquals(0.15f, config.baseFireRateIncreasePerStack, 0.001f)
        assertEquals(0f, config.baseReloadSpeedIncreasePerStack, 0.001f)
    }

    @Test
    fun testMaxStackLimitsDoNotOverflow() {
        val state = Relic04SoldiersSyringeEngine.createInitialState(Int.MAX_VALUE)
        val config = SoldiersSyringeConfig()

        val multiplier = Relic04SoldiersSyringeEngine.calculateFireRateMultiplier(config, state)
        assertTrue(multiplier.isFinite())

        val newFireCooldownMs = Relic04SoldiersSyringeEngine.calculateNewFireCooldownMs(1000f, config, state)
        assertTrue(newFireCooldownMs.isFinite())
        assertTrue(newFireCooldownMs >= 0f)
    }
}
