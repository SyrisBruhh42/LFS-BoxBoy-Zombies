package com.example.game.economy.vendors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VendingItemOfTheDayTimerTest {

    @Test
    fun testNominalExecution() {
        val timer = VendingItemOfTheDayTimer(12345)
        timer.step(0.05f)
        val snapshot = timer.getSnapshot()
        assertEquals(12345, snapshot.currentSeed)
        assertEquals(179.95f, snapshot.timeRemainingSeconds, 0.001f)
    }

    @Test
    fun testSeedRotation() {
        val timer = VendingItemOfTheDayTimer(12345)
        timer.step(180f) // Should clamp dt to 0.100f
        val snapshot1 = timer.getSnapshot()
        assertEquals(12345, snapshot1.currentSeed)

        // Fast forward
        for (i in 0 until 1800) {
            timer.step(0.100f)
        }
        val snapshot2 = timer.getSnapshot()
        assertNotEquals(12345, snapshot2.currentSeed)
    }

    @Test
    fun testDeterminism() {
        val timer1 = VendingItemOfTheDayTimer(100)
        val timer2 = VendingItemOfTheDayTimer(100)
        for (i in 0 until 500) {
            timer1.step(0.016f)
            timer2.step(0.016f)
        }
        assertEquals(timer1.getSnapshot().currentSeed, timer2.getSnapshot().currentSeed)
        assertEquals(timer1.getSnapshot().timeRemainingSeconds, timer2.getSnapshot().timeRemainingSeconds, 0.0001f)
    }

    @Test
    fun testBoundaryZeroDt() {
        val timer = VendingItemOfTheDayTimer(555)
        timer.step(0f)
        assertEquals(180f - 0.001f, timer.getSnapshot().timeRemainingSeconds, 0.0001f)
    }

    @Test
    fun testBoundaryNegativeDt() {
        val timer = VendingItemOfTheDayTimer(555)
        timer.step(-1f)
        assertEquals(180f - 0.001f, timer.getSnapshot().timeRemainingSeconds, 0.0001f)
    }

    @Test
    fun testBoundaryNaNDt() {
        val timer = VendingItemOfTheDayTimer(555)
        timer.step(Float.NaN)
        assertEquals(180f - 0.001f, timer.getSnapshot().timeRemainingSeconds, 0.0001f)
    }

    @Test
    fun testBoundaryInfinityDt() {
        val timer = VendingItemOfTheDayTimer(555)
        timer.step(Float.POSITIVE_INFINITY)
        assertEquals(180f - 0.001f, timer.getSnapshot().timeRemainingSeconds, 0.0001f)
    }

    @Test
    fun testMaxLoadStress() {
        val timer = VendingItemOfTheDayTimer(999)
        for (i in 0 until 100_000) {
            timer.step(0.016f)
        }
        val snapshot = timer.getSnapshot()
        assertNotEquals(999, snapshot.currentSeed)
    }

    @Test
    fun testSerializationRoundtrip() {
        val state = VendingTimerState(42, 120.5f)
        val map = state.toMap()
        val restored = VendingTimerState.fromMap(map)
        assertEquals(state.currentSeed, restored.currentSeed)
        assertEquals(state.timeRemainingSeconds, restored.timeRemainingSeconds, 0.0001f)
    }

    @Test
    fun testRestoreFromSnapshot() {
        val timer = VendingItemOfTheDayTimer(1)
        timer.restoreFromSnapshot(VendingTimerState(99, 50f))
        val snapshot = timer.getSnapshot()
        assertEquals(99, snapshot.currentSeed)
        assertEquals(50f, snapshot.timeRemainingSeconds, 0.0001f)
    }

    @Test
    fun testRestoreFromSnapshotNaN() {
        val timer = VendingItemOfTheDayTimer(1)
        timer.restoreFromSnapshot(VendingTimerState(99, Float.NaN))
        val snapshot = timer.getSnapshot()
        assertEquals(99, snapshot.currentSeed)
        assertEquals(0f, snapshot.timeRemainingSeconds, 0.0001f)
    }

    @Test
    fun testExactZeroTimeRotation() {
        val timer = VendingItemOfTheDayTimer(123)
        for (i in 0 until 1800) {
            timer.step(0.100f)
        }
        val nextSeed = timer.getSnapshot().currentSeed
        assertNotEquals(123, nextSeed)
        assertEquals(180f, timer.getSnapshot().timeRemainingSeconds, 0.0001f)
    }
}
