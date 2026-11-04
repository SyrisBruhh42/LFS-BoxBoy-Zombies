package com.example.game.ai.director.modules

import org.junit.Assert.assertTrue
import org.junit.Test

class WaveCompositionBudgeterTest {

    @Test
    fun testWaveSpawnBudgetCalculation() {
        val budget1 = WaveCompositionBudgeter.calculateBudget(waveNumber = 1, players = 1, isBloodMoon = false)
        assertTrue("Wave 1 should have shamblers", budget1.shamblers > 0)
        assertTrue("Wave 1 should not have devils", budget1.devils == 0)
        assertTrue("Wave 1 should not have weavers", budget1.weavers == 0)

        val budget4 = WaveCompositionBudgeter.calculateBudget(waveNumber = 4, players = 2, isBloodMoon = false)
        assertTrue("Wave 4 should have at least 1 devil boss", budget4.devils > 0)
        assertTrue("Wave 4 should have sprinters", budget4.sprinters > 0)
        assertTrue("Wave 4 should have stalkers", budget4.stalkers > 0)
        assertTrue("Wave 4 should have weavers", budget4.weavers > 0)

        val bloodMoonBudget = WaveCompositionBudgeter.calculateBudget(waveNumber = 5, players = 4, isBloodMoon = true)
        val normalBudget = WaveCompositionBudgeter.calculateBudget(waveNumber = 5, players = 4, isBloodMoon = false)
        assertTrue("Blood moon budget should be higher", bloodMoonBudget.totalBudgetCost > normalBudget.totalBudgetCost)
    }
}
