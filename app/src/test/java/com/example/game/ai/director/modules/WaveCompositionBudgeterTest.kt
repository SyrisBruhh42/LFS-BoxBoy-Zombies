package com.example.game.ai.director.modules

import com.example.game.ZombieArchetype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveCompositionBudgeterTest {

    private val budgeter = WaveCompositionBudgeter()

    @Test
    fun testWave1BudgetAndAllocation() {
        val budget = budgeter.calculateWaveBudget(1)
        assertEquals(17, budget) // 10 + 2 + 5 = 17

        val allocation = budgeter.allocateZombies(1, budget)
        // Wave 1 should only have Shamblers because Sprinters/Crawlers unlock at wave 2
        assertEquals(2, allocation[ZombieArchetype.SHAMBLER]) // 17 / 7 = 2
        assertEquals(1, allocation.size)
    }

    @Test
    fun testWave2BudgetAndAllocation() {
        val budget = budgeter.calculateWaveBudget(2)
        assertEquals(28, budget) // 10 + 8 + 10 = 28
        
        val allocation = budgeter.allocateZombies(2, budget)
        assertTrue(allocation.containsKey(ZombieArchetype.SHAMBLER))
        // Depending on exact rounding, should have some other types or mostly shamblers
    }

    @Test
    fun testWave5BossAllocation() {
        val budget = budgeter.calculateWaveBudget(5)
        val allocation = budgeter.allocateZombies(5, budget)
        
        assertTrue(allocation.containsKey(ZombieArchetype.DEVIL_BOSS))
        assertEquals(1, allocation[ZombieArchetype.DEVIL_BOSS])
    }
    
    @Test
    fun testWave10BossAllocation() {
        val budget = budgeter.calculateWaveBudget(10)
        val allocation = budgeter.allocateZombies(10, budget)
        
        assertTrue(allocation.containsKey(ZombieArchetype.DEVIL_BOSS))
        assertEquals(2, allocation[ZombieArchetype.DEVIL_BOSS])
        assertTrue(allocation.containsKey(ZombieArchetype.ABYSSAL_WEAVER))
        assertTrue(allocation.containsKey(ZombieArchetype.CALCIFIED_STALKER))
    }
}
