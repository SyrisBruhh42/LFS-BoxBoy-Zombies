package com.example.game.loot.borderlands.catalog

import com.example.game.ZombieEntity
import org.junit.Assert.*
import org.junit.Test

class SpikeReflectShieldGeneratorTest {

    @Test
    fun testShieldInitializationAndValidBounds() {
        val config = SpikeReflectShieldConfig(maxCapacity = 150f)
        val state = SpikeReflectShieldState(currentCapacity = 150f)
        
        assertEquals(150f, config.maxCapacity, 0.001f)
        assertEquals(150f, state.currentCapacity, 0.001f)
        assertTrue(state.isFullyRecharged)
    }

    @Test
    fun testRechargeDelayAndRateBehavior() {
        val config = SpikeReflectShieldConfig(rechargeDelaySeconds = 2.0f, rechargeRatePerSecond = 10f, maxCapacity = 100f)
        val state = SpikeReflectShieldState(currentCapacity = 50f, timeSinceLastDamage = 0f, isFullyRecharged = false)
        
        // Wait for delay but not enough
        SpikeReflectShieldGenerator.stepRecharge(1.9f, config, state)
        assertEquals(50f, state.currentCapacity, 0.001f)
        
        // Exceed delay, should start charging
        SpikeReflectShieldGenerator.stepRecharge(0.2f, config, state)
        // Note: dt is clamped to 0.1s max per step
        assertEquals(51f, state.currentCapacity, 0.001f)
    }

    @Test
    fun testDtSteppingLimits() {
        val config = SpikeReflectShieldConfig(rechargeDelaySeconds = 0f, rechargeRatePerSecond = 100f, maxCapacity = 100f)
        val state = SpikeReflectShieldState(currentCapacity = 0f, timeSinceLastDamage = 0f, isFullyRecharged = false)
        
        // A huge dt like 10.0 should be clamped to 0.100
        SpikeReflectShieldGenerator.stepRecharge(10.0f, config, state)
        assertEquals(10f, state.currentCapacity, 0.001f)
        
        // A tiny dt like 0.0001 should be clamped to 0.001
        SpikeReflectShieldGenerator.stepRecharge(0.0001f, config, state)
        assertEquals(10.1f, state.currentCapacity, 0.001f)
    }

    @Test
    fun testSuccessfulMeleeDamageReflection() {
        val config = SpikeReflectShieldConfig(spikeDamage = 40, triggerRadiusPx = 50f)
        val state = SpikeReflectShieldState(currentCapacity = 100f)
        val attacker = ZombieEntity(id = 1, archetype = com.example.game.ZombieArchetype.SHAMBLER, x = 10f, y = 10f, hp = 100, maxHp = 100)
        val result = SpikeReflectResult()
        
        // Player at 0,0, attacker at 10,10. Distance is approx 14, which is <= 50.
        SpikeReflectShieldGenerator.processMeleeDamage(20f, config, state, 0f, 0f, attacker, result)
        
        assertEquals(80f, state.currentCapacity, 0.001f)
        assertEquals(60, attacker.hp)
        assertTrue(result.targetDamaged)
        assertEquals(40, result.reflectedDamage)
    }

    @Test
    fun testOutOfBoundsNonReflection() {
        val config = SpikeReflectShieldConfig(spikeDamage = 40, triggerRadiusPx = 50f)
        val state = SpikeReflectShieldState(currentCapacity = 100f)
        // Distance > 50
        val attacker = ZombieEntity(id = 1, archetype = com.example.game.ZombieArchetype.SHAMBLER, x = 100f, y = 100f, hp = 100, maxHp = 100)
        val result = SpikeReflectResult()
        
        SpikeReflectShieldGenerator.processMeleeDamage(20f, config, state, 0f, 0f, attacker, result)
        
        assertEquals(80f, state.currentCapacity, 0.001f)
        assertEquals(100, attacker.hp)
        assertFalse(result.targetDamaged)
        assertEquals(0, result.reflectedDamage)
    }

    @Test
    fun testOutOfShieldNonReflection() {
        val config = SpikeReflectShieldConfig(spikeDamage = 40, triggerRadiusPx = 50f)
        val state = SpikeReflectShieldState(currentCapacity = 0f)
        val attacker = ZombieEntity(id = 1, archetype = com.example.game.ZombieArchetype.SHAMBLER, x = 10f, y = 10f, hp = 100, maxHp = 100)
        val result = SpikeReflectResult()
        
        SpikeReflectShieldGenerator.processMeleeDamage(20f, config, state, 0f, 0f, attacker, result)
        
        assertEquals(0f, state.currentCapacity, 0.001f)
        assertEquals(100, attacker.hp)
        assertFalse(result.targetDamaged)
        assertEquals(0, result.reflectedDamage)
    }

    @Test
    fun testNullAttackerHandling() {
        val config = SpikeReflectShieldConfig(spikeDamage = 40)
        val state = SpikeReflectShieldState(currentCapacity = 100f)
        val result = SpikeReflectResult()
        
        SpikeReflectShieldGenerator.processMeleeDamage(20f, config, state, 0f, 0f, null, result)
        
        assertEquals(80f, state.currentCapacity, 0.001f)
        assertFalse(result.targetDamaged)
        assertEquals(0, result.reflectedDamage)
    }

    @Test
    fun testNanInfinityRecovery() {
        val config = SpikeReflectShieldConfig()
        val state = SpikeReflectShieldState(currentCapacity = 100f)
        val attacker = ZombieEntity(id = 1, archetype = com.example.game.ZombieArchetype.SHAMBLER, x = 10f, y = 10f, hp = 100, maxHp = 100)
        val result = SpikeReflectResult()
        
        // Damage is NaN
        SpikeReflectShieldGenerator.processMeleeDamage(Float.NaN, config, state, 0f, 0f, attacker, result)
        assertEquals(100f, state.currentCapacity, 0.001f)
        
        // Damage is Negative
        SpikeReflectShieldGenerator.processMeleeDamage(-10f, config, state, 0f, 0f, attacker, result)
        assertEquals(100f, state.currentCapacity, 0.001f)
        
        // dt is NaN
        SpikeReflectShieldGenerator.stepRecharge(Float.NaN, config, state)
        assertEquals(100f, state.currentCapacity, 0.001f)
    }

    @Test
    fun testSerializationParity() {
        val originalConfig = SpikeReflectShieldConfig(
            spikeDamage = 75,
            isCryoSpike = true,
            triggerRadiusPx = 70f,
            rechargeDelaySeconds = 2.5f,
            rechargeRatePerSecond = 20f,
            maxCapacity = 120f
        )
        val map = originalConfig.toMap()
        val deserializedConfig = SpikeReflectShieldConfig.fromMap(map)
        
        assertEquals(originalConfig, deserializedConfig)
        
        val originalState = SpikeReflectShieldState(
            currentCapacity = 60f,
            timeSinceLastDamage = 1.5f,
            isFullyRecharged = false
        )
        val stateMap = originalState.toMap()
        val deserializedState = SpikeReflectShieldState.fromMap(stateMap)
        
        assertEquals(originalState, deserializedState)
    }

    @Test
    fun testFnvChecksumDeterminism() {
        val state1 = SpikeReflectShieldState(
            currentCapacity = 55.5f,
            timeSinceLastDamage = 1.1f,
            isFullyRecharged = false
        )
        val state2 = SpikeReflectShieldState(
            currentCapacity = 55.5f,
            timeSinceLastDamage = 1.1f,
            isFullyRecharged = false
        )
        
        assertEquals(state1.calculateChecksum(), state2.calculateChecksum())
        
        state2.currentCapacity = 55.6f
        assertNotEquals(state1.calculateChecksum(), state2.calculateChecksum())
    }

    @Test
    fun testFullDepletionAndRechargeCycle() {
        val config = SpikeReflectShieldConfig(maxCapacity = 100f, rechargeDelaySeconds = 1.0f, rechargeRatePerSecond = 50f)
        val state = SpikeReflectShieldState(currentCapacity = 100f)
        val attacker = ZombieEntity(id = 1, archetype = com.example.game.ZombieArchetype.SHAMBLER, x = 10f, y = 10f, hp = 100, maxHp = 100)
        val result = SpikeReflectResult()
        
        // Take 150 damage, should deplete shield to 0
        SpikeReflectShieldGenerator.processMeleeDamage(150f, config, state, 0f, 0f, attacker, result)
        assertEquals(0f, state.currentCapacity, 0.001f)
        assertFalse(state.isFullyRecharged)
        assertEquals(0f, state.timeSinceLastDamage, 0.001f)
        
        // Wait 1.0 seconds (10 steps of 0.1s)
        for (i in 1..10) {
            SpikeReflectShieldGenerator.stepRecharge(0.1f, config, state)
        }
        assertEquals(1.0f, state.timeSinceLastDamage, 0.001f)
        assertEquals(0f, state.currentCapacity, 0.001f)
        
        // Step to charge
        SpikeReflectShieldGenerator.stepRecharge(0.1f, config, state)
        assertEquals(5f, state.currentCapacity, 0.001f)
        
        // Step multiple times to full charge
        for (i in 1..20) {
            SpikeReflectShieldGenerator.stepRecharge(0.1f, config, state)
        }
        assertEquals(100f, state.currentCapacity, 0.001f)
        assertTrue(state.isFullyRecharged)
    }

    @Test
    fun testCryoSpikeReflectedProperty() {
        val config = SpikeReflectShieldConfig(spikeDamage = 40, isCryoSpike = true)
        val state = SpikeReflectShieldState(currentCapacity = 100f)
        val attacker = ZombieEntity(id = 1, archetype = com.example.game.ZombieArchetype.SHAMBLER, x = 10f, y = 10f, hp = 100, maxHp = 100)
        val result = SpikeReflectResult()
        
        SpikeReflectShieldGenerator.processMeleeDamage(10f, config, state, 0f, 0f, attacker, result)
        
        assertTrue(result.targetDamaged)
        assertTrue(result.isCryoReflected)
    }
}
