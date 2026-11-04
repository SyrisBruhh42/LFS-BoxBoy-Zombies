package com.example.game.economy.vendors

import com.example.game.ZombieEntity
import com.example.game.ZombieArchetype
import com.example.game.economy.BarricadeWindowTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowBarricadeTier3CryoGridTest {

    @Test
    fun testRepairBarricade() {
        val state = CryoGridState(currentHp = 100)
        val initialHp = state.currentHp
        val currencyEarned = WindowBarricadeTier3CryoGrid.repairBarricade(state, 2)

        assertEquals(BarricadeWindowTier.TIER_3_CRYO_LASER_GRID.repairRewardPerBoard * 2, currencyEarned)
        assertTrue(state.currentHp > initialHp)
    }

    @Test
    fun testProcessClimbingZombies() {
        val state = CryoGridState()
        val zombie1 = ZombieEntity(id = 1, archetype = ZombieArchetype.SHAMBLER, x = 0f, y = 0f, hp = 100, maxHp = 100)
        val zombie2 = ZombieEntity(id = 2, archetype = ZombieArchetype.SHAMBLER, x = 0f, y = 0f, hp = 100, maxHp = 100)

        val climbingZombies = listOf(zombie1, zombie2)
        val deltaTimeSec = 1.0f

        val expectedDamage = (BarricadeWindowTier.TIER_3_CRYO_LASER_GRID.counterAttackDps * deltaTimeSec).toInt().coerceAtLeast(1)

        val result = WindowBarricadeTier3CryoGrid.processClimbingZombies(state, climbingZombies, deltaTimeSec)

        assertEquals(2, result.damagedZombies.size)
        assertEquals(2, result.appliedSlowDebuffIds.size)
        assertTrue(result.appliedSlowDebuffIds.contains(1))
        assertTrue(result.appliedSlowDebuffIds.contains(2))

        assertEquals(100 - expectedDamage, zombie1.hp)
        assertEquals(100 - expectedDamage, zombie2.hp)
    }
}
