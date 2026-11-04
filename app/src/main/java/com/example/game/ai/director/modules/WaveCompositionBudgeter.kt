package com.example.game.ai.director.modules

import com.example.game.ZombieArchetype

class WaveCompositionBudgeter {

    fun calculateWaveBudget(wave: Int): Int {
        // Base budget starts at 10 and increases significantly per wave
        return 10 + (wave * wave * 2) + (wave * 5)
    }

    fun allocateZombies(wave: Int, budget: Int): Map<ZombieArchetype, Int> {
        val allocation = mutableMapOf<ZombieArchetype, Int>()
        var remainingBudget = budget

        // Costs based on rewardPoints / 10 to simulate currency cost
        val costs = mapOf(
            ZombieArchetype.SHAMBLER to 7,
            ZombieArchetype.CRAWLER to 8,
            ZombieArchetype.SPRINTER to 9,
            ZombieArchetype.CALCIFIED_STALKER to 13,
            ZombieArchetype.ABYSSAL_WEAVER to 16,
            ZombieArchetype.DEVIL_BOSS to 45
        )

        // Ensure at least some bosses on later waves
        if (wave >= 5) {
            val bossCount = wave / 5
            val bossCost = costs[ZombieArchetype.DEVIL_BOSS]!!
            if (remainingBudget >= bossCost * bossCount) {
                allocation[ZombieArchetype.DEVIL_BOSS] = bossCount
                remainingBudget -= bossCost * bossCount
            }
        }

        // Weavers and Stalkers appear later
        if (wave >= 4 && remainingBudget > costs[ZombieArchetype.ABYSSAL_WEAVER]!!) {
            val weaverCount = (remainingBudget * 0.15 / costs[ZombieArchetype.ABYSSAL_WEAVER]!!).toInt()
            if (weaverCount > 0) {
                allocation[ZombieArchetype.ABYSSAL_WEAVER] = weaverCount
                remainingBudget -= weaverCount * costs[ZombieArchetype.ABYSSAL_WEAVER]!!
            }
        }

        if (wave >= 3 && remainingBudget > costs[ZombieArchetype.CALCIFIED_STALKER]!!) {
            val stalkerCount = (remainingBudget * 0.20 / costs[ZombieArchetype.CALCIFIED_STALKER]!!).toInt()
            if (stalkerCount > 0) {
                allocation[ZombieArchetype.CALCIFIED_STALKER] = stalkerCount
                remainingBudget -= stalkerCount * costs[ZombieArchetype.CALCIFIED_STALKER]!!
            }
        }
        
        if (wave >= 2 && remainingBudget > costs[ZombieArchetype.SPRINTER]!!) {
             val sprinterCount = (remainingBudget * 0.30 / costs[ZombieArchetype.SPRINTER]!!).toInt()
             if (sprinterCount > 0) {
                 allocation[ZombieArchetype.SPRINTER] = sprinterCount
                 remainingBudget -= sprinterCount * costs[ZombieArchetype.SPRINTER]!!
             }
        }

        if (wave >= 2 && remainingBudget > costs[ZombieArchetype.CRAWLER]!!) {
            val crawlerCount = (remainingBudget * 0.20 / costs[ZombieArchetype.CRAWLER]!!).toInt()
            if (crawlerCount > 0) {
                allocation[ZombieArchetype.CRAWLER] = crawlerCount
                remainingBudget -= crawlerCount * costs[ZombieArchetype.CRAWLER]!!
            }
        }

        // Fill remaining with Shamblers
        if (remainingBudget >= costs[ZombieArchetype.SHAMBLER]!!) {
            val shamblerCount = remainingBudget / costs[ZombieArchetype.SHAMBLER]!!
            allocation[ZombieArchetype.SHAMBLER] = shamblerCount
            remainingBudget -= shamblerCount * costs[ZombieArchetype.SHAMBLER]!!
        }

        return allocation
    }
}
