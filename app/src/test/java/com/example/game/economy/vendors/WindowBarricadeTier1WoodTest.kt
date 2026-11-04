package com.example.game.economy.vendors

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowBarricadeTier1WoodTest {

    @Test
    fun testRepairOneBoard_Success() {
        val currentPlanks = 3
        val maxPlanks = 5
        val currentCurrency = 500

        val (newPlanks, newCurrency) = WindowBarricadeTier1Wood.repairOneBoard(
            currentPlanks = currentPlanks,
            maxPlanks = maxPlanks,
            currentCurrency = currentCurrency
        )

        // Tier 1 wood gives +20 per board
        assertEquals(4, newPlanks)
        assertEquals(520, newCurrency)
    }

    @Test
    fun testRepairOneBoard_AlreadyFull() {
        val currentPlanks = 5
        val maxPlanks = 5
        val currentCurrency = 500

        val (newPlanks, newCurrency) = WindowBarricadeTier1Wood.repairOneBoard(
            currentPlanks = currentPlanks,
            maxPlanks = maxPlanks,
            currentCurrency = currentCurrency
        )

        assertEquals(5, newPlanks)
        assertEquals(500, newCurrency)
    }
}
