package com.example.game.economy.vendors

import com.example.game.ZombieEntity
import com.example.game.economy.BarricadeWindowTier
import com.example.game.economy.SingleCurrencyVendingEngine

data class CryoGridState(
    val barricadeTier: BarricadeWindowTier = BarricadeWindowTier.TIER_3_CRYO_LASER_GRID,
    var currentHp: Int = barricadeTier.maxHp
) {
    val isDestroyed: Boolean
        get() = currentHp <= 0
}

data class CryoGridTickResult(
    val damagedZombies: List<ZombieEntity>,
    val appliedSlowDebuffIds: Set<Int>
)

object WindowBarricadeTier3CryoGrid {
    fun repairBarricade(state: CryoGridState, boardsToRepair: Int): Int {
        if (state.isDestroyed || state.currentHp >= state.barricadeTier.maxHp) return 0
        val repairAmount = boardsToRepair * 50 // Example repair amount
        state.currentHp = (state.currentHp + repairAmount).coerceAtMost(state.barricadeTier.maxHp)
        return boardsToRepair * state.barricadeTier.repairRewardPerBoard
    }

    fun processClimbingZombies(
         state: CryoGridState,
         climbingZombies: List<ZombieEntity>,
         deltaTimeSec: Float
    ): CryoGridTickResult {
         val damagedZombies = mutableListOf<ZombieEntity>()
         val appliedSlowDebuffIds = mutableSetOf<Int>()
         
         if (state.isDestroyed) return CryoGridTickResult(damagedZombies, appliedSlowDebuffIds)
         
         val damage = (state.barricadeTier.counterAttackDps * deltaTimeSec).toInt().coerceAtLeast(1)
         
         for (zombie in climbingZombies) {
             zombie.hp -= damage
             damagedZombies.add(zombie)
             appliedSlowDebuffIds.add(zombie.id)
         }
         return CryoGridTickResult(damagedZombies, appliedSlowDebuffIds)
    }
}
