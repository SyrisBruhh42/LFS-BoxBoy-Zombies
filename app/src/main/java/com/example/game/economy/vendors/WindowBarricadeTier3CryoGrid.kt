package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.game.ZombieEntity
import com.example.game.contracts.Fnv1a64Hasher
import com.example.game.economy.BarricadeWindowTier

@Keep
data class CryoGridState(
    val barricadeTier: BarricadeWindowTier = BarricadeWindowTier.TIER_3_CRYO_LASER_GRID,
    var currentHp: Int = barricadeTier.maxHp
) {
    val isDestroyed: Boolean
        get() = currentHp <= 0

    val integrityRatio: Float
        get() = (currentHp.toFloat() / barricadeTier.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    fun toMap(): Map<String, Any> = mapOf(
        "barricadeTier" to barricadeTier.name,
        "currentHp" to currentHp
    )

    fun computeChecksum(): Long {
        var h = Fnv1a64Hasher.initial()
        h = Fnv1a64Hasher.mixString(h, barricadeTier.name)
        h = Fnv1a64Hasher.mixInt(h, currentHp)
        return h
    }

    companion object {
        fun fromMap(map: Map<String, Any>): CryoGridState {
            val tierName = map["barricadeTier"] as? String ?: BarricadeWindowTier.TIER_3_CRYO_LASER_GRID.name
            val hp = (map["currentHp"] as? Number)?.toInt() ?: BarricadeWindowTier.TIER_3_CRYO_LASER_GRID.maxHp

            return CryoGridState(
                barricadeTier = try {
                    BarricadeWindowTier.valueOf(tierName)
                } catch (_: Exception) {
                    BarricadeWindowTier.TIER_3_CRYO_LASER_GRID
                },
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
    const val HP_PER_BOARD = 50
    const val CRYO_SLOW_MULTIPLIER = 0.45f

    fun repairBarricade(state: CryoGridState, boardsToRepair: Int): Int {
        if (state.isDestroyed || boardsToRepair <= 0 || state.currentHp >= state.barricadeTier.maxHp) return 0

        val maxHp = state.barricadeTier.maxHp
        val hpNeeded = maxHp - state.currentHp
        val boardsNeeded = (hpNeeded + (HP_PER_BOARD - 1)) / HP_PER_BOARD
        val actualBoards = boardsToRepair.coerceAtMost(boardsNeeded)

        val repairAmount = actualBoards * HP_PER_BOARD
        state.currentHp = (state.currentHp + repairAmount).coerceAtMost(maxHp)

        return actualBoards * state.barricadeTier.repairRewardPerBoard
    }

    fun applyZombieBreachDamage(state: CryoGridState, attackingZombies: Int, deltaTimeSec: Float): Int {
        if (attackingZombies <= 0 || state.isDestroyed || !deltaTimeSec.isFinite()) return state.currentHp
        val dt = deltaTimeSec.coerceIn(0.001f, 0.100f)
        val damage = (attackingZombies * 24f * dt).toInt().coerceAtLeast(1)
        state.currentHp = (state.currentHp - damage).coerceAtLeast(0)
        return state.currentHp
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


fun evaluateWindowBarricadeTier3CryoGridLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "WindowBarricadeTier3CryoGrid") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
