package com.example.game.economy.vendors

import com.example.game.LootRarity
import com.example.game.ModularWeaponGenerator
import com.example.game.ModularWeaponInstance
import org.junit.Assert.*
import org.junit.Test

class SalvageDisplacedGearBonusTest {

    private fun createTestWeapon(
        id: String = "test_wpn_1",
        rarity: LootRarity = LootRarity.COMMON,
        itemLevel: Int = 1,
        anvilTier: Int = 0
    ): ModularWeaponInstance {
        val baseWpn = ModularWeaponGenerator.createStarterModularWeapon("pistol")
        return baseWpn.copy(
            instanceId = id,
            rarity = rarity,
            itemLevel = itemLevel,
            anvilUpgradeTier = anvilTier
        )
    }

    @Test
    fun `TC01 - Engine calculates correct rebate value for baseline weapon`() {
        val weapon = createTestWeapon(rarity = LootRarity.COMMON, itemLevel = 1, anvilTier = 0)
        // Base 150 * 1.0 (common) * 1.0 (lvl 1) * 1.0 (anvil 0) = 150
        val rebate = SalvageDisplacedGearBonusEngine.calculateRebateValue(weapon)
        assertEquals(150, rebate)
    }

    @Test
    fun `TC02 - Engine calculates correct rebate value for high tier weapon`() {
        val weapon = createTestWeapon(rarity = LootRarity.EPIC, itemLevel = 5, anvilTier = 2)
        // Base 150 * 1.82 (epic) * (1 + 4*0.1) * (1 + 2*0.25) = 150 * 1.82 * 1.4 * 1.5 = 573.3 -> 573
        val rebate = SalvageDisplacedGearBonusEngine.calculateRebateValue(weapon)
        assertEquals(573, rebate)
    }

    @Test
    fun `TC03 - Task is successfully enqueued and added to state`() {
        var state = SalvageDisplacedGearState()
        val weapon = createTestWeapon()
        
        state = SalvageDisplacedGearBonusEngine.enqueueSalvage(
            state = state,
            weapon = weapon,
            playerX = 100f,
            playerY = 100f,
            taskId = "task_1",
            delayMs = 1500L
        )

        assertEquals(1, state.pendingTasks.size)
        assertEquals("task_1", state.pendingTasks.first().taskId)
        assertEquals(150, state.pendingTasks.first().rebateValue)
    }

    @Test
    fun `TC04 - Step function decreases delay without triggering early rebate`() {
        var state = SalvageDisplacedGearState()
        val weapon = createTestWeapon()
        
        state = SalvageDisplacedGearBonusEngine.enqueueSalvage(state, weapon, 0f, 0f, "t1", 200L)
        
        // Step 100ms (0.100 sec)
        val result = SalvageDisplacedGearBonusEngine.step(state, 0.100f, 0f, 0f)
        
        assertEquals(0, result.completedRebates)
        assertEquals(0, result.tasksProcessed)
        assertEquals(1, result.newState.pendingTasks.size)
        assertEquals(100L, result.newState.pendingTasks.first().remainingDelayMs)
    }

    @Test
    fun `TC05 - Step function triggers rebate when delay completes and within range`() {
        var state = SalvageDisplacedGearState()
        val weapon = createTestWeapon()
        
        state = SalvageDisplacedGearBonusEngine.enqueueSalvage(state, weapon, 100f, 100f, "t1", 50L)
        
        // Step 60ms
        val result = SalvageDisplacedGearBonusEngine.step(state, 0.060f, 110f, 110f) // distance ~14, within 600
        
        assertEquals(150, result.completedRebates)
        assertEquals(1, result.tasksProcessed)
        assertEquals(0, result.newState.pendingTasks.size)
        assertEquals(150, result.newState.totalRebateAccrued)
    }

    @Test
    fun `TC06 - Step function drops task with no rebate if player moves out of bounds`() {
        var state = SalvageDisplacedGearState()
        val weapon = createTestWeapon()
        
        state = SalvageDisplacedGearBonusEngine.enqueueSalvage(state, weapon, 0f, 0f, "t1", 50L)
        
        // Player moves to 700, 0 which is > 600 MAX_SALVAGE_DISTANCE
        val result = SalvageDisplacedGearBonusEngine.step(state, 0.060f, 700f, 0f)
        
        assertEquals(0, result.completedRebates) // Dropped out of bounds
        assertEquals(1, result.tasksProcessed)
        assertEquals(0, result.newState.pendingTasks.size)
    }

    @Test
    fun `TC07 - Graceful degradation recovers from NaN injection in dtSeconds`() {
        var state = SalvageDisplacedGearState()
        val weapon = createTestWeapon()
        state = SalvageDisplacedGearBonusEngine.enqueueSalvage(state, weapon, 0f, 0f, "t1", 100L)
        
        val result = SalvageDisplacedGearBonusEngine.step(state, Float.NaN, 0f, 0f)
        
        // Expected fallback is 0.016s -> 16ms
        assertEquals(1, result.newState.pendingTasks.size)
        assertEquals(100L - 16L, result.newState.pendingTasks.first().remainingDelayMs)
    }

    @Test
    fun `TC08 - Graceful degradation recovers from Infinity inputs in coordinates`() {
        var state = SalvageDisplacedGearState()
        val weapon = createTestWeapon()
        
        // Enqueue with infinite origin
        state = SalvageDisplacedGearBonusEngine.enqueueSalvage(
            state, weapon, Float.POSITIVE_INFINITY, Float.NaN, "t1", 100L
        )
        // Should clamp to 0f, 0f
        assertEquals(0f, state.pendingTasks.first().originX)
        assertEquals(0f, state.pendingTasks.first().originY)
        
        // Step with infinite player pos
        val result = SalvageDisplacedGearBonusEngine.step(state, 0.200f, Float.NEGATIVE_INFINITY, Float.NaN)
        
        // Player pos clamped to 0f, 0f -> distance 0f -> within range
        assertEquals(150, result.completedRebates)
    }

    @Test
    fun `TC09 - dtSeconds is clamped to valid range`() {
        var state = SalvageDisplacedGearState()
        val weapon = createTestWeapon()
        state = SalvageDisplacedGearBonusEngine.enqueueSalvage(state, weapon, 0f, 0f, "t1", 1000L)
        
        // dtSeconds massive -> clamped to 0.100f
        val result1 = SalvageDisplacedGearBonusEngine.step(state, 50f, 0f, 0f)
        assertEquals(900L, result1.newState.pendingTasks.first().remainingDelayMs)
        
        // dtSeconds zero -> clamped to 0.001f
        val result2 = SalvageDisplacedGearBonusEngine.step(result1.newState, 0f, 0f, 0f)
        assertEquals(899L, result2.newState.pendingTasks.first().remainingDelayMs)
    }

    @Test
    fun `TC10 - Queue drops oldest task when exceeding capacity limits`() {
        var state = SalvageDisplacedGearState(maxProcessingCapacity = 3) // Extremely low capacity limit for test
        val weapon = createTestWeapon()
        
        // Enqueue 7 tasks. Max list size is maxProcessingCapacity * 2 = 6. 
        // 7th should push out the oldest.
        for (i in 1..7) {
            state = SalvageDisplacedGearBonusEngine.enqueueSalvage(state, weapon, 0f, 0f, "task_$i", 100L)
        }
        
        assertEquals(6, state.pendingTasks.size)
        // task_1 should have been dropped, oldest should now be task_2
        assertEquals("task_2", state.pendingTasks.first().taskId)
        assertEquals("task_7", state.pendingTasks.last().taskId)
    }

    @Test
    fun `TC11 - Step function processes maximum capacity limit of tasks per frame`() {
        var state = SalvageDisplacedGearState(maxProcessingCapacity = 2) // Limit 2 processed per tick
        val weapon = createTestWeapon()
        
        // Add 4 tasks all ready to be processed
        for (i in 1..4) {
            state = SalvageDisplacedGearBonusEngine.enqueueSalvage(state, weapon, 0f, 0f, "task_$i", 50L)
        }
        
        val result = SalvageDisplacedGearBonusEngine.step(state, 0.100f, 0f, 0f)
        
        // Only 2 should be processed despite all 4 being ready
        assertEquals(2, result.tasksProcessed)
        assertEquals(300, result.completedRebates) // 150 * 2
        assertEquals(2, result.newState.pendingTasks.size)
        // Unprocessed tasks should remain the ones at the end of the queue
        assertEquals("task_3", result.newState.pendingTasks[0].taskId)
        assertEquals("task_4", result.newState.pendingTasks[1].taskId)
    }

    @Test
    fun `TC12 - State serialization roundtrip parity and checksum verification`() {
        var state = SalvageDisplacedGearState()
        val weapon = createTestWeapon(id = "wpn_cache_1", rarity = LootRarity.RARE)
        
        state = SalvageDisplacedGearBonusEngine.enqueueSalvage(state, weapon, 10f, 20f, "t1", 500L)
        
        val checksumBefore = state.calculateChecksum()
        val mapData = state.toMap()
        
        val weaponCache = mapOf("wpn_cache_1" to weapon)
        val stateAfter = SalvageDisplacedGearState.fromMap(mapData, weaponCache)
        
        assertNotNull(stateAfter)
        assertEquals(state.pendingTasks.size, stateAfter.pendingTasks.size)
        assertEquals(state.pendingTasks.first().taskId, stateAfter.pendingTasks.first().taskId)
        assertEquals(state.pendingTasks.first().originX, stateAfter.pendingTasks.first().originX)
        assertEquals(state.totalRebateAccrued, stateAfter.totalRebateAccrued)
        assertEquals(checksumBefore, stateAfter.calculateChecksum())
    }
}
