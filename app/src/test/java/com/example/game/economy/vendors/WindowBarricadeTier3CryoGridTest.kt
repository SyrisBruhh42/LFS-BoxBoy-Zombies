package com.example.game.economy.vendors

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import com.example.game.economy.BarricadeWindowTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowBarricadeTier3CryoGridTest {

    @Test
    fun testRepairBarricade_nominalGameplay() {
        val state = CryoGridState(currentHp = 700)
        val initialHp = state.currentHp
        val boardsToRepair = 2
        val reward = WindowBarricadeTier3CryoGrid.repairBarricade(state, boardsToRepair)

        assertEquals(initialHp + 100, state.currentHp)
        assertEquals(boardsToRepair * BarricadeWindowTier.TIER_3_CRYO_LASER_GRID.repairRewardPerBoard, reward)
    }

    @Test
    fun testRepairBarricade_fullyHealed() {
        val state = CryoGridState(currentHp = BarricadeWindowTier.TIER_3_CRYO_LASER_GRID.maxHp)
        val initialHp = state.currentHp
        val reward = WindowBarricadeTier3CryoGrid.repairBarricade(state, 5)

        assertEquals(initialHp, state.currentHp)
        assertEquals(0, reward)
    }

    @Test
    fun testRepairBarricade_destroyed() {
        val state = CryoGridState(currentHp = 0)
        val reward = WindowBarricadeTier3CryoGrid.repairBarricade(state, 5)

        assertEquals(0, state.currentHp)
        assertEquals(0, reward)
        assertTrue(state.isDestroyed)
    }

    @Test
    fun testProcessClimbingZombies_validDt() {
        val state = CryoGridState()
        val zombie = ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        val dt = 0.05f
        
        val result = WindowBarricadeTier3CryoGrid.processClimbingZombies(state, listOf(zombie), dt)

        assertEquals(1, result.damagedZombies.size)
        assertEquals(1, result.appliedSlowDebuffIds.size)
        assertTrue(result.appliedSlowDebuffIds.contains(1))
        
        val expectedDamage = (BarricadeWindowTier.TIER_3_CRYO_LASER_GRID.counterAttackDps * dt).toInt().coerceAtLeast(1)
        assertEquals(100 - expectedDamage, zombie.hp)
    }

    @Test
    fun testProcessClimbingZombies_exactMath() {
        val state = CryoGridState()
        val zombie1 = ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        val zombie2 = ZombieEntity(2, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        val dt = 0.1f
        
        val result = WindowBarricadeTier3CryoGrid.processClimbingZombies(state, listOf(zombie1, zombie2), dt)

        assertEquals(2, result.damagedZombies.size)
        assertEquals(2, result.appliedSlowDebuffIds.size)
        
        val expectedDamage = (BarricadeWindowTier.TIER_3_CRYO_LASER_GRID.counterAttackDps * dt).toInt().coerceAtLeast(1)
        assertEquals(100 - expectedDamage, zombie1.hp)
        assertEquals(100 - expectedDamage, zombie2.hp)
    }

    @Test
    fun testProcessClimbingZombies_appliedSlowDebuffIds() {
        val state = CryoGridState()
        val zombie = ZombieEntity(10, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        val dt = 0.016f // 60fps
        
        val result = WindowBarricadeTier3CryoGrid.processClimbingZombies(state, listOf(zombie), dt)

        assertTrue(result.appliedSlowDebuffIds.contains(10))
    }

    @Test
    fun testProcessClimbingZombies_zeroZombies() {
        val state = CryoGridState()
        val dt = 0.05f
        
        val result = WindowBarricadeTier3CryoGrid.processClimbingZombies(state, emptyList(), dt)

        assertTrue(result.damagedZombies.isEmpty())
        assertTrue(result.appliedSlowDebuffIds.isEmpty())
    }

    @Test
    fun testRepairBarricade_zeroNegativeInputs() {
        val state = CryoGridState(currentHp = 700)
        val initialHp = state.currentHp
        
        val rewardZero = WindowBarricadeTier3CryoGrid.repairBarricade(state, 0)
        assertEquals(0, rewardZero)
        assertEquals(initialHp, state.currentHp)
        
        val rewardNegative = WindowBarricadeTier3CryoGrid.repairBarricade(state, -5)
        assertEquals(0, rewardNegative)
        assertEquals(initialHp, state.currentHp)
    }

    @Test
    fun testProcessClimbingZombies_nanInfinityRecovery() {
        val state = CryoGridState()
        val zombie = ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        
        val resultNaN = WindowBarricadeTier3CryoGrid.processClimbingZombies(state, listOf(zombie), Float.NaN)
        assertTrue(resultNaN.damagedZombies.isEmpty())
        
        val resultPosInf = WindowBarricadeTier3CryoGrid.processClimbingZombies(state, listOf(zombie), Float.POSITIVE_INFINITY)
        assertTrue(resultPosInf.damagedZombies.isEmpty())
        
        val resultNegInf = WindowBarricadeTier3CryoGrid.processClimbingZombies(state, listOf(zombie), Float.NEGATIVE_INFINITY)
        assertTrue(resultNegInf.damagedZombies.isEmpty())
    }

    @Test
    fun testProcessClimbingZombies_maxCapacityStressTest() {
        val state = CryoGridState()
        val zombies = (1..300).map { ZombieEntity(it, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100) }
        val dt = 0.05f
        
        val result = WindowBarricadeTier3CryoGrid.processClimbingZombies(state, zombies, dt)
        
        assertEquals(300, result.damagedZombies.size)
        assertEquals(300, result.appliedSlowDebuffIds.size)
    }

    @Test
    fun testSerializationRoundtripParity() {
        val state = CryoGridState(currentHp = 500)
        val map = state.toMap()
        
        val restoredState = CryoGridState.fromMap(map)
        
        assertEquals(state.barricadeTier, restoredState.barricadeTier)
        assertEquals(state.currentHp, restoredState.currentHp)
        
        val result = CryoGridTickResult(
            damagedZombies = listOf(ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)),
            appliedSlowDebuffIds = setOf(1)
        )
        val resultMap = result.toMap()
        val damagedZombieIds = resultMap["damagedZombieIds"] as List<*>
        val appliedSlowDebuffIds = resultMap["appliedSlowDebuffIds"] as List<*>
        
        assertEquals(1, damagedZombieIds.size)
        assertEquals(1, appliedSlowDebuffIds.size)
    }

    @Test
    fun testDeterminismProof() {
        val state1 = CryoGridState()
        val zombie1 = ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        val result1 = WindowBarricadeTier3CryoGrid.processClimbingZombies(state1, listOf(zombie1), 0.016f)
        
        val state2 = CryoGridState()
        val zombie2 = ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)
        val result2 = WindowBarricadeTier3CryoGrid.processClimbingZombies(state2, listOf(zombie2), 0.016f)
        
        assertEquals(result1.damagedZombies.size, result2.damagedZombies.size)
        assertEquals(result1.appliedSlowDebuffIds, result2.appliedSlowDebuffIds)
        assertEquals(zombie1.hp, zombie2.hp)
    }
}
