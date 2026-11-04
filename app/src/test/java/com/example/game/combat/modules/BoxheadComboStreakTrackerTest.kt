package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxheadComboStreakTrackerTest {

    @Test
    fun testInitialState() {
        val tracker = BoxheadComboStreakTracker()
        assertEquals(0, tracker.currentStreak)
        assertEquals(1.0f, tracker.currencyMultiplier, 0.001f)
    }

    @Test
    fun testRegisterKillsIncreasesStreakAndMultiplier() {
        val tracker = BoxheadComboStreakTracker()

        tracker.registerKills(5)
        assertEquals(5, tracker.currentStreak)
        assertEquals(1.15f, tracker.currencyMultiplier, 0.001f)

        tracker.registerKills(5)
        assertEquals(10, tracker.currentStreak)
        assertEquals(1.35f, tracker.currencyMultiplier, 0.001f)

        tracker.registerKills(15)
        assertEquals(25, tracker.currentStreak)
        assertEquals(1.75f, tracker.currencyMultiplier, 0.001f)

        tracker.registerKills(25)
        assertEquals(50, tracker.currentStreak)
        assertEquals(2.25f, tracker.currencyMultiplier, 0.001f)

        tracker.registerKills(50)
        assertEquals(100, tracker.currentStreak)
        assertEquals(3.0f, tracker.currencyMultiplier, 0.001f)
    }

    @Test
    fun testRelicDropsOnMilestones() {
        val tracker = BoxheadComboStreakTracker()

        var relics = tracker.registerKills(24)
        assertEquals(0, relics)

        relics = tracker.registerKills(1)
        assertEquals(1, relics) // Hit 25 milestone

        relics = tracker.registerKills(24)
        assertEquals(0, relics)

        relics = tracker.registerKills(2)
        assertEquals(1, relics) // Hit 50 milestone

        relics = tracker.registerKills(49)
        assertEquals(1, relics) // Hit 100 milestone
    }

    @Test
    fun testRelicDropsMultipleAtOnce() {
        val tracker = BoxheadComboStreakTracker()

        val relics = tracker.registerKills(100)
        assertEquals(3, relics) // Hit 25, 50, and 100 milestones all at once
    }

    @Test
    fun testComboDecayResetsStreak() {
        val tracker = BoxheadComboStreakTracker()

        tracker.registerKills(10)
        assertEquals(10, tracker.currentStreak)

        val decayMs = tracker.comboDecayRemainingMs
        tracker.tick(decayMs - 1)
        assertEquals(10, tracker.currentStreak)

        tracker.tick(1)
        assertEquals(0, tracker.currentStreak)
        assertEquals(1.0f, tracker.currencyMultiplier, 0.001f)
    }
}
