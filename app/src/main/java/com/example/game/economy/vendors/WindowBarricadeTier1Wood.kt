package com.example.game.economy.vendors

import com.example.game.economy.BarricadeWindowTier

object WindowBarricadeTier1Wood {

    /**
     * Attempts to repair one board on a Tier 1 Timber Plank barricade.
     * Rewards the player with +20 currency if a board was successfully added.
     * 
     * @param currentPlanks The current number of intact planks.
     * @param maxPlanks The maximum capacity for planks on this window.
     * @param currentCurrency The player's current currency balance.
     * @return A Pair containing the updated plank count and the updated currency balance.
     */
    fun repairOneBoard(currentPlanks: Int, maxPlanks: Int, currentCurrency: Int): Pair<Int, Int> {
        val tier = BarricadeWindowTier.TIER_1_WOOD_BOARDS
        if (currentPlanks < maxPlanks) {
            return Pair(currentPlanks + 1, currentCurrency + tier.repairRewardPerBoard)
        }
        return Pair(currentPlanks, currentCurrency)
    }
}
