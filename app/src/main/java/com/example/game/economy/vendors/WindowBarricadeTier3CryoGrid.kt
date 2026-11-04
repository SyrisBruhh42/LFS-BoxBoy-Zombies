package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.game.ZombieEntity
import com.example.game.economy.BarricadeWindowTier

@Keep
data class CryoGridState(
    val barricadeTier: BarricadeWindowTier = BarricadeWindowTier.TIER_3_CRYO_LASER_GRID,
    var currentHp: Int = barricadeTier.maxHp
) {
    val isDestroyed: Boolean
        get() = currentHp <= 0

    fun toMap(): Map<String, Any> = mapOf(
        "barricadeTier" to barricadeTier.name,
        "currentHp" to currentHp
    )

    companion object {
        fun fromMap(map: Map<String, Any>): CryoGridState {
            val tierName = map["barricadeTier"] as? String ?: BarricadeWindowTier.TIER_3_CRYO_LASER_GRID.name
            val hp = (map["currentHp"] as? Number)?.toInt() ?: BarricadeWindowTier.TIER_3_CRYO_LASER_GRID.maxHp

            return CryoGridState(
                barricadeTier = try { BarricadeWindowTier.valueOf(tierName) } catch (e: Exception) { BarricadeWindowTier.TIER_3_CRYO_LASER_GRID },
                currentHp = hp
            )
        }
    }
}

@Keep
data class CryoGridTickResult(
    val damagedZombies: List<ZombieEntity>,
    val appliedSlowDebuffIds: Set<Int>
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "damagedZombieIds" to damagedZombies.map { it.id },
            "appliedSlowDebuffIds" to appliedSlowDebuffIds.toList()
        )
    }
}

object WindowBarricadeTier3CryoGrid {
    fun repairBarricade(state: CryoGridState, boardsToRepair: Int): Int {
        if (state.isDestroyed || boardsToRepair <= 0 || state.currentHp >= state.barricadeTier.maxHp) return 0

        val maxHp = state.barricadeTier.maxHp
        val hpNeeded = maxHp - state.currentHp
        val boardsNeeded = (hpNeeded + 49) / 50
        val actualBoards = boardsToRepair.coerceAtMost(boardsNeeded)

        val repairAmount = actualBoards * 50
        state.currentHp = (state.currentHp + repairAmount).coerceAtMost(maxHp)

        return actualBoards * state.barricadeTier.repairRewardPerBoard
    }

    fun processClimbingZombies(
         state: CryoGridState,
         climbingZombies: List<ZombieEntity>,
         deltaTimeSec: Float
    ): CryoGridTickResult {
         if (!deltaTimeSec.isFinite() || climbingZombies.isEmpty() || state.isDestroyed) {
             return CryoGridTickResult(emptyList(), emptySet())
         }
         
         val dt = deltaTimeSec.coerceIn(0.001f, 0.100f)
         val rawDamage = state.barricadeTier.counterAttackDps * dt
         val damage = if (rawDamage < 1f) 1 else rawDamage.toInt()
         
         val damagedZombies = ArrayList<ZombieEntity>(climbingZombies.size)
         val appliedSlowDebuffIds = HashSet<Int>(climbingZombies.size)
         
         for (i in climbingZombies.indices) {
             val zombie = climbingZombies[i]
             zombie.hp -= damage
             damagedZombies.add(zombie)
             appliedSlowDebuffIds.add(zombie.id)
         }

         return CryoGridTickResult(damagedZombies, appliedSlowDebuffIds)
    }
}
