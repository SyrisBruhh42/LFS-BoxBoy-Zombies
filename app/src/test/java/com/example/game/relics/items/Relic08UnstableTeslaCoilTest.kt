package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test

class Relic08UnstableTeslaCoilTest {

    @Test
    fun test01_Invariant_canBePurchasedAtVending_isFalse() {
        assertFalse("Unstable Tesla Coil MUST NOT be purchasable at vending machines", Relic08UnstableTeslaCoilEngine.CAN_BE_PURCHASED_AT_VENDING)
    }

    @Test
    fun test02_StackScaling_calculateTargets_ZeroOrNegativeStacks() {
        assertEquals(0, Relic08UnstableTeslaCoilEngine.calculateTargets(0))
        assertEquals(0, Relic08UnstableTeslaCoilEngine.calculateTargets(-5))
    }

    @Test
    fun test03_StackScaling_calculateTargets_PositiveStacks() {
        // Base logic + 2 per stack
        assertEquals(3, Relic08UnstableTeslaCoilEngine.calculateTargets(1)) // 1 + 1*2
        assertEquals(5, Relic08UnstableTeslaCoilEngine.calculateTargets(2)) // 1 + 2*2
        assertEquals(21, Relic08UnstableTeslaCoilEngine.calculateTargets(10))
    }

    @Test
    fun test04_Tick_NoTickIfTimeNotAccumulated() {
        val engine = Relic08UnstableTeslaCoilEngine()
        val state = TeslaCoilState()
        val zombieXs = floatArrayOf(10f)
        val zombieYs = floatArrayOf(10f)
        val zombieHps = floatArrayOf(100f)

        val hits = engine.tick(
            dtSeconds = 0.1f, // Below 0.5s interval
            state = state,
            stackCount = 1,
            playerX = 0f,
            playerY = 0f,
            zombieXs = zombieXs,
            zombieYs = zombieYs,
            zombieHps = zombieHps,
            zombieCount = 1
        )

        assertEquals(0, hits)
        assertEquals(0.1f, state.accumulatedTime, 0.001f)
    }

    @Test
    fun test05_Tick_FiresWhenTimeAccumulated() {
        val engine = Relic08UnstableTeslaCoilEngine()
        val state = TeslaCoilState(accumulatedTime = 0.45f)
        val zombieXs = floatArrayOf(10f)
        val zombieYs = floatArrayOf(10f)
        val zombieHps = floatArrayOf(100f)

        val hits = engine.tick(
            dtSeconds = 0.06f, // 0.45 + 0.06 = 0.51 >= 0.5
            state = state,
            stackCount = 1,
            playerX = 0f,
            playerY = 0f,
            zombieXs = zombieXs,
            zombieYs = zombieYs,
            zombieHps = zombieHps,
            zombieCount = 1
        )

        assertEquals(1, hits)
        assertEquals(0.01f, state.accumulatedTime, 0.001f)
    }

    @Test
    fun test06_Spatial_IgnoresOutOfRangeZombies() {
        val engine = Relic08UnstableTeslaCoilEngine()
        val state = TeslaCoilState(accumulatedTime = 0.5f) // force tick
        val zombieXs = floatArrayOf(500f) // MAX_RADIUS is 300f
        val zombieYs = floatArrayOf(0f)
        val zombieHps = floatArrayOf(100f)

        val hits = engine.tick(
            dtSeconds = 0.05f,
            state = state,
            stackCount = 1,
            playerX = 0f,
            playerY = 0f,
            zombieXs = zombieXs,
            zombieYs = zombieYs,
            zombieHps = zombieHps,
            zombieCount = 1
        )

        assertEquals(0, hits) // Out of range, no hits
    }

    @Test
    fun test07_Spatial_IgnoresDeadZombies() {
        val engine = Relic08UnstableTeslaCoilEngine()
        val state = TeslaCoilState(accumulatedTime = 0.5f)
        val zombieXs = floatArrayOf(10f)
        val zombieYs = floatArrayOf(10f)
        val zombieHps = floatArrayOf(0f) // Dead

        val hits = engine.tick(
            dtSeconds = 0.05f,
            state = state,
            stackCount = 1,
            playerX = 0f,
            playerY = 0f,
            zombieXs = zombieXs,
            zombieYs = zombieYs,
            zombieHps = zombieHps,
            zombieCount = 1
        )

        assertEquals(0, hits)
    }

    @Test
    fun test08_Determinism_IdenticalSeedsYieldIdenticalTargets() {
        val engine1 = Relic08UnstableTeslaCoilEngine()
        val engine2 = Relic08UnstableTeslaCoilEngine()

        val state1 = TeslaCoilState(accumulatedTime = 0.5f, seed = 42L)
        val state2 = TeslaCoilState(accumulatedTime = 0.5f, seed = 42L)

        // 5 valid targets, picking 3
        val zombieXs = floatArrayOf(10f, 20f, 30f, 40f, 50f)
        val zombieYs = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        val zombieHps = floatArrayOf(10f, 10f, 10f, 10f, 10f)

        val hits1 = engine1.tick(0.05f, state1, 1, 0f, 0f, zombieXs, zombieYs, zombieHps, 5)
        val hits2 = engine2.tick(0.05f, state2, 1, 0f, 0f, zombieXs, zombieYs, zombieHps, 5)

        assertEquals(3, hits1)
        assertEquals(3, hits2)

        // Check identical target selection and resulting seeds
        for (i in 0 until hits1) {
            assertEquals(engine1.hitResultIndices[i], engine2.hitResultIndices[i])
        }
        assertEquals(state1.seed, state2.seed)
        assertEquals(state1.checksum, state2.checksum)
    }

    @Test
    fun test09_Boundary_NaNAndInfinityProtection() {
        val engine = Relic08UnstableTeslaCoilEngine()
        val state = TeslaCoilState(accumulatedTime = 0.5f)
        val zombieXs = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, 10f)
        val zombieYs = floatArrayOf(10f, 10f, Float.NaN)
        val zombieHps = floatArrayOf(100f, 100f, 100f)

        val hits = engine.tick(
            dtSeconds = Float.NaN, // Should clamp to 0.001
            state = state,
            stackCount = 1,
            playerX = 0f,
            playerY = 0f,
            zombieXs = zombieXs,
            zombieYs = zombieYs,
            zombieHps = zombieHps,
            zombieCount = 3
        )

        assertEquals(0, hits) // NaN/Inf coords should be rejected
        assertEquals(0.001f, state.accumulatedTime, 0.0001f) // 0.5 was reset to 0, then NaN dt clamped to 0.001
    }

    @Test
    fun test10_Serialization_ToMapFromMapParity() {
        val original = TeslaCoilState(accumulatedTime = 0.25f, seed = 999L, checksum = 12345L)
        val serialized = original.toMap()
        val deserialized = TeslaCoilState.fromMap(serialized)

        assertEquals(original.accumulatedTime, deserialized.accumulatedTime, 0.001f)
        assertEquals(original.seed, deserialized.seed)
        assertEquals(original.checksum, deserialized.checksum)
    }

    @Test
    fun test11_Stress_MaxCapacityEntities() {
        val engine = Relic08UnstableTeslaCoilEngine()
        val state = TeslaCoilState(accumulatedTime = 0.5f)

        val count = 2000
        val zombieXs = FloatArray(count) { 50f } // all in range
        val zombieYs = FloatArray(count) { 50f }
        val zombieHps = FloatArray(count) { 100f }

        val hits = engine.tick(
            dtSeconds = 0.05f,
            state = state,
            stackCount = 5, // 1 + 5*2 = 11 targets
            playerX = 0f,
            playerY = 0f,
            zombieXs = zombieXs,
            zombieYs = zombieYs,
            zombieHps = zombieHps,
            zombieCount = count
        )

        assertEquals(11, hits)
    }

    @Test
    fun test12_Boundary_HugeDtSecondsDoesNotDeathSpiral() {
        val engine = Relic08UnstableTeslaCoilEngine()
        val state = TeslaCoilState()
        val zombieXs = floatArrayOf(10f)
        val zombieYs = floatArrayOf(10f)
        val zombieHps = floatArrayOf(100f)

        engine.tick(
            dtSeconds = 50f, // extremely large dt, should clamp to 0.100f max
            state = state,
            stackCount = 1,
            playerX = 0f,
            playerY = 0f,
            zombieXs = zombieXs,
            zombieYs = zombieYs,
            zombieHps = zombieHps,
            zombieCount = 1
        )

        // 50f should be clamped to 0.1f. state started at 0.
        // so it only goes to 0.1, which is not enough to fire (needs 0.5)
        assertEquals(0.1f, state.accumulatedTime, 0.001f)
    }
}
