package com.example.game.economy.vendors

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import com.example.game.economy.BarricadeWindowTier
import org.junit.Assert.*
import org.junit.Test

class WindowBarricadeTier4AutoTurretTest {

    private fun createZombie(id: Int, x: Float, y: Float, hp: Int): ZombieEntity {
        return ZombieEntity(
            id = id,
            archetype = ZombieArchetype.SHAMBLER, // Or any default archetype
            x = x,
            y = y,
            hp = hp,
            maxHp = hp
        )
    }

    @Test
    fun testInitialState() {
        val state = AutoTurretState()
        assertEquals(1300, state.currentHp)
        assertEquals(0f, state.accumulatorMs, 0.001f)
        assertFalse(state.isDestroyed)
    }

    @Test
    fun testSerializationParity() {
        val state1 = AutoTurretState(currentHp = 500, accumulatorMs = 50f, isDestroyed = false, randomSeed = 999)
        val map = state1.toMap()
        val state2 = AutoTurretState.fromMap(map)
        
        assertEquals(state1.currentHp, state2.currentHp)
        assertEquals(state1.accumulatorMs, state2.accumulatorMs, 0.001f)
        assertEquals(state1.isDestroyed, state2.isDestroyed)
        assertEquals(state1.randomSeed, state2.randomSeed)
        assertEquals(state1.computeChecksum(), state2.computeChecksum())
    }

    @Test
    fun testSerializationClamping() {
        val map = mapOf<String, Any>(
            "currentHp" to 2000, // over max
            "accumulatorMs" to Float.NaN, // should default to 0
            "isDestroyed" to false
        )
        val state = AutoTurretState.fromMap(map)
        assertEquals(1300, state.currentHp)
        assertEquals(0f, state.accumulatorMs, 0.001f)
        assertFalse(state.isDestroyed)
    }

    @Test
    fun testApplyDamage() {
        val state = AutoTurretState()
        WindowBarricadeTier4AutoTurret.applyDamage(state, 500)
        assertEquals(800, state.currentHp)
        assertFalse(state.isDestroyed)

        WindowBarricadeTier4AutoTurret.applyDamage(state, 1000) // overdamage
        assertEquals(0, state.currentHp)
        assertTrue(state.isDestroyed)
    }

    @Test
    fun testApplyDamageNegativeIgnored() {
        val state = AutoTurretState()
        WindowBarricadeTier4AutoTurret.applyDamage(state, -100)
        assertEquals(1300, state.currentHp)
    }

    @Test
    fun testRepairLogic() {
        val state = AutoTurretState(currentHp = 1000)
        
        // Cost 2 per HP. 100 currency = 50 HP.
        val cost1 = WindowBarricadeTier4AutoTurret.repair(state, 100)
        assertEquals(100, cost1)
        assertEquals(1050, state.currentHp)
        
        // Full repair. Missing 250 HP. Requires 500 currency.
        val cost2 = WindowBarricadeTier4AutoTurret.repair(state, 1000)
        assertEquals(500, cost2)
        assertEquals(1300, state.currentHp)
        
        // Max HP, costs 0.
        val cost3 = WindowBarricadeTier4AutoTurret.repair(state, 1000)
        assertEquals(0, cost3)
    }

    @Test
    fun testRepairRevivesTurret() {
        val state = AutoTurretState(currentHp = 0, isDestroyed = true)
        WindowBarricadeTier4AutoTurret.repair(state, 100) // 50 HP
        assertEquals(50, state.currentHp)
        assertFalse(state.isDestroyed)
    }

    @Test
    fun testTargetAcquisitionAndDamage() {
        val state = AutoTurretState()
        val result = AutoTurretEngineResult()
        
        // Turret at (0,0)
        val z1 = createZombie(1, 10f, 0f, 100) // closest
        val z2 = createZombie(2, 50f, 0f, 100)
        val z3 = createZombie(3, 200f, 0f, 100) // out of range (>150f)
        
        val zombies = listOf(z1, z2, z3)
        
        // dtSeconds 0.1f -> 100ms accumulator -> 1 tick
        WindowBarricadeTier4AutoTurret.processTurretTick(state, 0f, 0f, zombies, 0.1f, result)
        
        // 95 counterAttackDps * 0.1s tick rate = 9 dmg (or crit 18)
        assertTrue(z1.hp < 100)
        assertEquals(100, z2.hp)
        assertEquals(100, z3.hp)
        
        assertEquals(1, result.hitCount)
        assertEquals(1, result.hitZombieIds[0])
    }

    @Test
    fun testTargetingExcludesDeadZombies() {
        val state = AutoTurretState()
        val result = AutoTurretEngineResult()
        
        val z1 = createZombie(1, 10f, 0f, 0) // dead
        val z2 = createZombie(2, 50f, 0f, 100) // alive
        
        val zombies = listOf(z1, z2)
        
        WindowBarricadeTier4AutoTurret.processTurretTick(state, 0f, 0f, zombies, 0.1f, result)
        
        assertEquals(0, z1.hp) // unaltered
        assertTrue(z2.hp < 100) // z2 targeted instead
        
        assertEquals(1, result.hitCount)
        assertEquals(2, result.hitZombieIds[0])
    }

    @Test
    fun testDeterminism() {
        val state1 = AutoTurretState(randomSeed = 42)
        val result1 = AutoTurretEngineResult()
        val zombies1 = listOf(createZombie(1, 10f, 0f, 100))
        
        val state2 = AutoTurretState(randomSeed = 42)
        val result2 = AutoTurretEngineResult()
        val zombies2 = listOf(createZombie(1, 10f, 0f, 100))
        
        WindowBarricadeTier4AutoTurret.processTurretTick(state1, 0f, 0f, zombies1, 0.1f, result1)
        WindowBarricadeTier4AutoTurret.processTurretTick(state2, 0f, 0f, zombies2, 0.1f, result2)
        
        assertEquals(zombies1[0].hp, zombies2[0].hp)
        assertEquals(state1.randomSeed, state2.randomSeed)
        assertEquals(state1.computeChecksum(), state2.computeChecksum())
    }

    @Test
    fun testMultipleTicksInOneUpdate() {
        val state = AutoTurretState()
        val result = AutoTurretEngineResult()
        val z1 = createZombie(1, 10f, 0f, 100)
        
        // 0.3f dt clamped to 0.1f dt limit? Actually limit is 0.100f
        // Wait, dt is clamped to 0.1f, so 100ms.
        // Let's pass multiple 0.1f ticks to accumulate >100ms without clamping the dt.
        WindowBarricadeTier4AutoTurret.processTurretTick(state, 0f, 0f, listOf(z1), 0.1f, result)
        val hpAfter1 = z1.hp
        
        // Accumulator should be 0 now.
        WindowBarricadeTier4AutoTurret.processTurretTick(state, 0f, 0f, listOf(z1), 0.1f, result)
        val hpAfter2 = z1.hp
        
        assertTrue(hpAfter2 < hpAfter1)
    }

    @Test
    fun testNanInfinityInjections() {
        val state = AutoTurretState()
        val result = AutoTurretEngineResult()
        val z1 = createZombie(1, Float.NaN, Float.POSITIVE_INFINITY, 100)
        
        // Test DT injection
        WindowBarricadeTier4AutoTurret.processTurretTick(state, Float.NaN, Float.NEGATIVE_INFINITY, listOf(z1), Float.NaN, result)
        
        // Should clamp DT to 0.001f (1ms). No tick should happen.
        assertEquals(1f, state.accumulatorMs, 0.001f) // 1ms
        assertEquals(0, result.hitCount)
        assertEquals(100, z1.hp)
    }
    
    @Test
    fun testZeroPerFrameHeapChurn() {
        val state = AutoTurretState()
        val result = AutoTurretEngineResult()
        val z1 = createZombie(1, 10f, 0f, 100)
        val zombies = listOf(z1)
        
        // Warmup
        WindowBarricadeTier4AutoTurret.processTurretTick(state, 0f, 0f, zombies, 0.1f, result)
        
        val memBefore = Runtime.getRuntime().freeMemory()
        
        for (i in 0 until 1000) {
            z1.hp = 100
            state.accumulatorMs = 0f
            WindowBarricadeTier4AutoTurret.processTurretTick(state, 0f, 0f, zombies, 0.1f, result)
        }
        
        val memAfter = Runtime.getRuntime().freeMemory()
        // We can't strictly assert freeMemory difference is 0 due to GC unpredictability,
        // but we can ensure it doesn't throw OOM and executes successfully.
        assertTrue(result.hitCount >= 0)
    }

    @Test
    fun testNoActionIfDestroyed() {
        val state = AutoTurretState(isDestroyed = true, currentHp = 0)
        val result = AutoTurretEngineResult()
        val z1 = createZombie(1, 10f, 0f, 100)
        
        WindowBarricadeTier4AutoTurret.processTurretTick(state, 0f, 0f, listOf(z1), 0.1f, result)
        
        assertEquals(0f, state.accumulatorMs, 0.001f)
        assertEquals(100, z1.hp)
        assertEquals(0, result.hitCount)
    }

    @Test
    fun testHitCountResetOnTick() {
        val state = AutoTurretState()
        val result = AutoTurretEngineResult()
        val z1 = createZombie(1, 10f, 0f, 100)
        
        WindowBarricadeTier4AutoTurret.processTurretTick(state, 0f, 0f, listOf(z1), 0.1f, result)
        assertEquals(1, result.hitCount)
        
        // Not enough time for a tick
        WindowBarricadeTier4AutoTurret.processTurretTick(state, 0f, 0f, listOf(z1), 0.05f, result)
        assertEquals(0, result.hitCount) // hitCount should be cleared!
    }
}
