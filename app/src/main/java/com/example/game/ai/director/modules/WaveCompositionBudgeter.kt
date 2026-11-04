package com.example.game.ai.director.modules

import kotlin.math.max
import kotlin.math.min

object WaveCompositionBudgeter {

    data class WaveSpawnBudget(
        val shamblers: Int,
        val sprinters: Int,
        val stalkers: Int,
        val weavers: Int,
        val devils: Int,
        val totalBudgetCost: Int
    )

    fun calculateBudget(waveNumber: Int, players: Int, isBloodMoon: Boolean = false): WaveSpawnBudget {
        // Base budget scaled by wave and players
        var totalBudgetPoints = (waveNumber * 10 + waveNumber * waveNumber) * max(1, players)

        if (isBloodMoon) {
            totalBudgetPoints = (totalBudgetPoints * 1.5f).toInt()
        }

        var remainingBudget = totalBudgetPoints

        var devils = 0
        if (waveNumber % 4 == 0) {
            devils = 1 + (waveNumber / 12) * max(1, players - 1)
            val cost = devils * 50
            if (remainingBudget >= cost) {
                remainingBudget -= cost
            } else {
                devils = 0
            }
        }

        var weavers = 0
        if (waveNumber >= 3) {
            val maxWeavers = waveNumber / 3 + players
            val costPerWeaver = 20
            val targetWeaverCost = (remainingBudget * 0.4f).toInt() // Increased allocation so weavers can spawn
            weavers = min(maxWeavers, targetWeaverCost / costPerWeaver)
            if (weavers == 0 && remainingBudget >= costPerWeaver) weavers = 1
            remainingBudget -= weavers * costPerWeaver
        }

        var stalkers = 0
        if (waveNumber >= 2) {
            val maxStalkers = waveNumber / 2 + players * 2
            val costPerStalker = 15
            val targetStalkerCost = (remainingBudget * 0.35f).toInt()
            stalkers = min(maxStalkers, targetStalkerCost / costPerStalker)
            if (stalkers == 0 && remainingBudget >= costPerStalker) stalkers = 1
            remainingBudget -= stalkers * costPerStalker
        }

        var sprinters = 0
        if (waveNumber >= 2) {
            val costPerSprinter = 10
            val targetSprinterCost = (remainingBudget * 0.4f).toInt()
            sprinters = targetSprinterCost / costPerSprinter
            if (sprinters == 0 && remainingBudget >= costPerSprinter) sprinters = 1
            remainingBudget -= sprinters * costPerSprinter
        }

        val costPerShambler = 5
        val shamblers = max(0, remainingBudget / costPerShambler)
        val finalBudgetCost = totalBudgetPoints - remainingBudget + shamblers * costPerShambler

        return WaveSpawnBudget(
            shamblers = shamblers,
            sprinters = sprinters,
            stalkers = stalkers,
            weavers = weavers,
            devils = devils,
            totalBudgetCost = finalBudgetCost
        )
    }
}
