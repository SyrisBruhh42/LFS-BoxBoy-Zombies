package com.example.game.relics.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Relic17ShatteringJusticeTest {
    @Test
    fun testDropOnlyInvariant() {
        val config = Relic17ShatteringJusticeConfig()
        assertFalse("Relic must not be purchasable at vending", config.canBePurchasedAtVending)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testZeroStacksThrowsException() {
        Relic17ShatteringJusticeState(stackCount = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeStacksThrowsException() {
        Relic17ShatteringJusticeState(stackCount = -1)
    }

    @Test
    fun testHitTrackingUnderThreshold() {
        val config = Relic17ShatteringJusticeConfig()
        val engine = Relic17ShatteringJusticeEngine(config)
        val state = Relic17ShatteringJusticeState(stackCount = 1)

        val hits = intArrayOf(100, 100, 100, 100)
        engine.processHits(state, hits)

        assertEquals(1, state.activeTrackingCount)
        assertEquals(100, state.trackedEnemyIds[0])
        assertEquals(4, state.enemyHitCounts[0])
        assertEquals(0f, state.activeArmorPulverizedDurations[0])
    }

    @Test
    fun testHitTrackingTriggerThreshold() {
        val config = Relic17ShatteringJusticeConfig()
        val engine = Relic17ShatteringJusticeEngine(config)
        val state = Relic17ShatteringJusticeState(stackCount = 1)

        val hits = intArrayOf(100, 100, 100, 100, 100)
        engine.processHits(state, hits)

        assertEquals(1, state.activeTrackingCount)
        assertEquals(100, state.trackedEnemyIds[0])
        assertEquals(0, state.enemyHitCounts[0]) // Resets while active
        assertEquals(8.0f, state.activeArmorPulverizedDurations[0])
    }

    @Test
    fun testDurationStackScaling() {
        val config = Relic17ShatteringJusticeConfig(baseDurationSeconds = 8.0f, durationPerStackSeconds = 8.0f)
        val engine = Relic17ShatteringJusticeEngine(config)
        val state = Relic17ShatteringJusticeState(stackCount = 3) // 8 + (3 - 1) * 8 = 24.0s

        val hits = intArrayOf(200, 200, 200, 200, 200)
        engine.processHits(state, hits)

        assertEquals(1, state.activeTrackingCount)
        assertEquals(24.0f, state.activeArmorPulverizedDurations[0])
    }

    @Test
    fun testTickDecrementsDurationAndRemoves() {
        val config = Relic17ShatteringJusticeConfig()
        val engine = Relic17ShatteringJusticeEngine(config)
        val state = Relic17ShatteringJusticeState(stackCount = 1)
        state.trackedEnemyIds[0] = 100
        state.activeArmorPulverizedDurations[0] = 0.05f
        state.enemyHitCounts[0] = 0
        state.activeTrackingCount = 1

        engine.tick(state, 0.04f)
        assertEquals(1, state.activeTrackingCount)
        assertTrue(abs(state.activeArmorPulverizedDurations[0] - 0.01f) < 0.001f)

        engine.tick(state, 0.02f)
        assertEquals(0, state.activeTrackingCount)
    }

    @Test
    fun testDtClamping() {
        val config = Relic17ShatteringJusticeConfig()
        val engine = Relic17ShatteringJusticeEngine(config)
        val state = Relic17ShatteringJusticeState(stackCount = 1)
        state.trackedEnemyIds[0] = 100
        state.activeArmorPulverizedDurations[0] = 8.0f
        state.activeTrackingCount = 1

        engine.tick(state, 10.0f)
        assertEquals(1, state.activeTrackingCount)
        assertTrue(abs(state.activeArmorPulverizedDurations[0] - (8.0f - 0.100f)) < 0.001f)
    }

    @Test
    fun testSerializationRoundtripConfig() {
        val config = Relic17ShatteringJusticeConfig(requiredHits = 6, baseDurationSeconds = 10.0f)
        val map = config.toMap()
        val restored = Relic17ShatteringJusticeConfig.fromMap(map)
        assertEquals(config, restored)
    }

    @Test
    fun testSerializationRoundtripState() {
        val state = Relic17ShatteringJusticeState(stackCount = 2)
        state.trackedEnemyIds[0] = 100
        state.enemyHitCounts[0] = 3
        state.activeArmorPulverizedDurations[0] = 0f

        state.trackedEnemyIds[1] = 300
        state.enemyHitCounts[1] = 0
        state.activeArmorPulverizedDurations[1] = 12.0f
        state.activeTrackingCount = 2
        state.computeChecksum()

        val map = state.toMap()
        val restored = Relic17ShatteringJusticeState.fromMap(map)

        assertEquals(state.stackCount, restored.stackCount)
        assertEquals(state.activeTrackingCount, restored.activeTrackingCount)
        assertEquals(state.checksum, restored.checksum)
        assertEquals(100, restored.trackedEnemyIds[0])
        assertEquals(3, restored.enemyHitCounts[0])
        assertEquals(12.0f, restored.activeArmorPulverizedDurations[1])
    }

    @Test
    fun testStressCapacityAndZeroAllocation() {
        val config = Relic17ShatteringJusticeConfig()
        val engine = Relic17ShatteringJusticeEngine(config)
        val state = Relic17ShatteringJusticeState(stackCount = 1)

        val hits = IntArray(300) { it }

        val runtime = Runtime.getRuntime()
        runtime.gc()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        engine.processHits(state, hits)

        val memAfter = runtime.totalMemory() - runtime.freeMemory()

        assertEquals(300, state.activeTrackingCount)
        // Memory should roughly be same (allow some noise)
        assertTrue(abs(memAfter - memBefore) < 500_000)
    }

    @Test
    fun testNaNAndInfinityGuards() {
        val config = Relic17ShatteringJusticeConfig()
        val engine = Relic17ShatteringJusticeEngine(config)
        val state = Relic17ShatteringJusticeState(stackCount = 1)
        state.trackedEnemyIds[0] = 100
        state.activeArmorPulverizedDurations[0] = 8.0f
        state.activeTrackingCount = 1

        // Pass NaN
        engine.tick(state, Float.NaN)
        assertEquals(8.0f - 0.001f, state.activeArmorPulverizedDurations[0], 0.001f) // clamped to 0.001

        // Pass Infinity
        engine.tick(state, Float.POSITIVE_INFINITY)
        assertEquals(8.0f - 0.002f, state.activeArmorPulverizedDurations[0], 0.001f) // clamped to 0.001
    }

    @Test
    fun testDeterministicExecutionAndChecksum() {
        val config = Relic17ShatteringJusticeConfig()
        val engine = Relic17ShatteringJusticeEngine(config)
        val state1 = Relic17ShatteringJusticeState(stackCount = 1)
        val state2 = Relic17ShatteringJusticeState(stackCount = 1)

        val hits = intArrayOf(1, 2, 3, 1, 2, 1, 1, 1) // 5 hits for 1

        engine.processHits(state1, hits)
        engine.processHits(state2, hits)

        assertEquals(state1.checksum, state2.checksum)
        assertTrue(state1.checksum != 0L)

        engine.tick(state1, 0.016f)
        engine.tick(state2, 0.016f)

        assertEquals(state1.checksum, state2.checksum)
    }
}
