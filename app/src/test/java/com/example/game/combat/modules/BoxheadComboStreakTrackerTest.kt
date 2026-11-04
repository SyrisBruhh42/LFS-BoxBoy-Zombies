package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BoxheadComboStreakTrackerTest {

    private lateinit var tracker: BoxheadComboStreakTracker

    @Before
    fun setup() {
        tracker = BoxheadComboStreakTracker()
    }

    @Test
    fun testInitialState() {
        assertEquals(0, tracker.currentCombo)
        assertEquals(0L, tracker.timeRemainingMs)
        assertEquals(1.0f, tracker.getCurrencyMultiplier(), 0.001f)
    }

    @Test
    fun testEnemyKilledIncrementsCombo() {
        val relic = tracker.onEnemyKilled()
        assertEquals(1, tracker.currentCombo)
        assertEquals(3000L, tracker.timeRemainingMs)
        assertEquals(RelicTier.NONE, relic)
    }

    @Test
    fun testTimerDecayCurve() {
        // Combo 1 -> 3000ms
        tracker.onEnemyKilled()
        assertEquals(3000L, tracker.timeRemainingMs)

        // Combo 11 -> 3000 - 10 * 50 = 2500ms
        tracker.reset()
        for (i in 1..11) {
            tracker.onEnemyKilled()
        }
        assertEquals(2500L, tracker.timeRemainingMs)

        // Combo 60 -> 3000 - 59 * 50 = 50ms, but should be clamped to 500ms
        tracker.reset()
        for (i in 1..60) {
            tracker.onEnemyKilled()
        }
        assertEquals(500L, tracker.timeRemainingMs)
    }

    @Test
    fun testUpdateDecaysTimerAndResetsCombo() {
        tracker.onEnemyKilled() // Combo 1, 3000ms

        tracker.update(1000L)
        assertEquals(1, tracker.currentCombo)
        assertEquals(2000L, tracker.timeRemainingMs)

        tracker.update(2500L) // Exceeds remaining time
        assertEquals(0, tracker.currentCombo)
        assertEquals(0L, tracker.timeRemainingMs)
    }

    @Test
    fun testCurrencyMultiplierLimits() {
        // Initial
        assertEquals(1.0f, tracker.getCurrencyMultiplier(), 0.001f)

        // 10 combo -> 1.0 + 10 * 0.05 = 1.5
        for (i in 1..10) {
            tracker.onEnemyKilled()
        }
        assertEquals(1.5f, tracker.getCurrencyMultiplier(), 0.001f)

        // 40 combo -> 1.0 + 40 * 0.05 = 3.0
        tracker.reset()
        for (i in 1..40) {
            tracker.onEnemyKilled()
        }
        assertEquals(3.0f, tracker.getCurrencyMultiplier(), 0.001f)

        // 100 combo -> should cap at 3.0
        tracker.reset()
        for (i in 1..100) {
            tracker.onEnemyKilled()
        }
        assertEquals(3.0f, tracker.getCurrencyMultiplier(), 0.001f)
    }

    @Test
    fun testRelicTriggers() {
        for (i in 1..24) {
            assertEquals(RelicTier.NONE, tracker.onEnemyKilled())
        }

        // 25th kill
        assertEquals(RelicTier.TIER_25, tracker.onEnemyKilled())

        for (i in 26..49) {
            assertEquals(RelicTier.NONE, tracker.onEnemyKilled())
        }

        // 50th kill
        assertEquals(RelicTier.TIER_50, tracker.onEnemyKilled())

        for (i in 51..99) {
            assertEquals(RelicTier.NONE, tracker.onEnemyKilled())
        }

        // 100th kill
        assertEquals(RelicTier.TIER_100, tracker.onEnemyKilled())

        // 101st kill
        assertEquals(RelicTier.NONE, tracker.onEnemyKilled())
    }
}
