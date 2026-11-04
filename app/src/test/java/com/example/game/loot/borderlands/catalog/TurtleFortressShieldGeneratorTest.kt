package com.example.game.loot.borderlands.catalog

import org.junit.Test
import org.junit.Assert.*

class TurtleFortressShieldGeneratorTest {

    @Test
    fun testCalculateModifiersNominal() {
        val config = TurtleShieldConfig()
        val baseHp = 100f

        val result = TurtleFortressShieldGenerator.calculateModifiers(baseHp, config)

        // 100 base HP * 0.8 penalty = 80 max HP
        assertEquals(80f, result.modifiedMaxHp, 0.01f)

        // 100 base capacity * 2.2 multiplier = 220 capacity
        assertEquals(220f, result.modifiedMaxCapacity, 0.01f)
    }

    @Test
    fun testCalculateModifiersNegativeHp() {
        val config = TurtleShieldConfig()
        val result = TurtleFortressShieldGenerator.calculateModifiers(-50f, config)

        // Base HP defaults to 100 if negative, so 100 * 0.8 = 80
        assertEquals(80f, result.modifiedMaxHp, 0.01f)
    }

    @Test
    fun testCalculateModifiersNanHp() {
        val config = TurtleShieldConfig()
        val result = TurtleFortressShieldGenerator.calculateModifiers(Float.NaN, config)

        // Base HP defaults to 100 if NaN, so 100 * 0.8 = 80
        assertEquals(80f, result.modifiedMaxHp, 0.01f)
    }

    @Test
    fun testProcessDamageNominalNoOverflow() {
        val config = TurtleShieldConfig()
        val state = TurtleShieldState(currentCapacity = 220f, timeSinceLastDamage = 5f, isFullyRecharged = true)

        val overflow = TurtleFortressShieldGenerator.processDamage(50f, config, state)

        assertEquals(0f, overflow, 0.01f)
        assertEquals(170f, state.currentCapacity, 0.01f)
        assertEquals(0f, state.timeSinceLastDamage, 0.01f)
        assertFalse(state.isFullyRecharged)
    }

    @Test
    fun testProcessDamageWithOverflow() {
        val config = TurtleShieldConfig()
        val state = TurtleShieldState(currentCapacity = 100f, timeSinceLastDamage = 5f, isFullyRecharged = true)

        val overflow = TurtleFortressShieldGenerator.processDamage(150f, config, state)

        assertEquals(50f, overflow, 0.01f)
        assertEquals(0f, state.currentCapacity, 0.01f)
        assertEquals(0f, state.timeSinceLastDamage, 0.01f)
        assertFalse(state.isFullyRecharged)
    }

    @Test
    fun testProcessDamageNegativeDamage() {
        val config = TurtleShieldConfig()
        val state = TurtleShieldState(currentCapacity = 220f, timeSinceLastDamage = 5f, isFullyRecharged = true)

        val overflow = TurtleFortressShieldGenerator.processDamage(-20f, config, state)

        assertEquals(0f, overflow, 0.01f)
        assertEquals(220f, state.currentCapacity, 0.01f)
        assertEquals(5f, state.timeSinceLastDamage, 0.01f) // Not reset
        assertTrue(state.isFullyRecharged) // Not reset
    }

    @Test
    fun testStepRechargeUnderDelay() {
        val config = TurtleShieldConfig(rechargeDelaySeconds = 3.0f, rechargeRatePerSecond = 20f)
        val state = TurtleShieldState(currentCapacity = 100f, timeSinceLastDamage = 0f, isFullyRecharged = false)

        TurtleFortressShieldGenerator.stepRecharge(1.0f, config, state) // dt clamped to 0.1f

        // dtSeconds is clamped to max 0.100f
        assertEquals(0.1f, state.timeSinceLastDamage, 0.001f)
        assertEquals(100f, state.currentCapacity, 0.01f) // Did not recharge yet
        assertFalse(state.isFullyRecharged)
    }

    @Test
    fun testStepRechargePastDelay() {
        val config = TurtleShieldConfig(rechargeDelaySeconds = 3.0f, rechargeRatePerSecond = 20f, baseCapacity = 100f, capacityMultiplier = 2.2f)
        val state = TurtleShieldState(currentCapacity = 100f, timeSinceLastDamage = 2.95f, isFullyRecharged = false)

        // Steps 0.1s
        TurtleFortressShieldGenerator.stepRecharge(0.1f, config, state)

        assertEquals(3.05f, state.timeSinceLastDamage, 0.001f)
        assertEquals(102f, state.currentCapacity, 0.01f) // Recharged 20f * 0.1f = 2f
        assertFalse(state.isFullyRecharged)
    }

    @Test
    fun testStepRechargeToFull() {
        val config = TurtleShieldConfig(rechargeDelaySeconds = 3.0f, rechargeRatePerSecond = 200f, baseCapacity = 100f, capacityMultiplier = 2.2f)
        val state = TurtleShieldState(currentCapacity = 210f, timeSinceLastDamage = 3.5f, isFullyRecharged = false)

        TurtleFortressShieldGenerator.stepRecharge(0.1f, config, state)

        // 210 + 20 = 230, which is > 220, so clamped to 220
        assertEquals(220f, state.currentCapacity, 0.01f)
        assertTrue(state.isFullyRecharged)
    }

    @Test
    fun testRoundtripSerializationConfig() {
        val config = TurtleShieldConfig(
            baseCapacity = 150f,
            capacityMultiplier = 2.5f,
            hpPenaltyMultiplier = 0.7f,
            rechargeDelaySeconds = 2.0f,
            rechargeRatePerSecond = 25f
        )
        val map = config.toMap()
        val parsed = TurtleShieldConfig.fromMap(map)

        assertEquals(config.baseCapacity, parsed.baseCapacity, 0.01f)
        assertEquals(config.capacityMultiplier, parsed.capacityMultiplier, 0.01f)
        assertEquals(config.hpPenaltyMultiplier, parsed.hpPenaltyMultiplier, 0.01f)
        assertEquals(config.rechargeDelaySeconds, parsed.rechargeDelaySeconds, 0.01f)
        assertEquals(config.rechargeRatePerSecond, parsed.rechargeRatePerSecond, 0.01f)
    }

    @Test
    fun testRoundtripSerializationState() {
        val state = TurtleShieldState(
            currentCapacity = 123.4f,
            timeSinceLastDamage = 4.5f,
            isFullyRecharged = false
        )
        val map = state.toMap()
        val parsed = TurtleShieldState.fromMap(map)

        assertEquals(state.currentCapacity, parsed.currentCapacity, 0.01f)
        assertEquals(state.timeSinceLastDamage, parsed.timeSinceLastDamage, 0.01f)
        assertEquals(state.isFullyRecharged, parsed.isFullyRecharged)
    }

    @Test
    fun testDeterministicFnv1aChecksum() {
        val state1 = TurtleShieldState(currentCapacity = 150f, timeSinceLastDamage = 2.5f, isFullyRecharged = false)
        val state2 = TurtleShieldState(currentCapacity = 150f, timeSinceLastDamage = 2.5f, isFullyRecharged = false)
        val state3 = TurtleShieldState(currentCapacity = 150.1f, timeSinceLastDamage = 2.5f, isFullyRecharged = false)

        assertEquals(state1.calculateChecksum(), state2.calculateChecksum())
        assertNotEquals(state1.calculateChecksum(), state3.calculateChecksum())
    }

    @Test
    fun testLoadStressRechargeStep() {
        val config = TurtleShieldConfig()
        val states = Array(1000) {
            TurtleShieldState(currentCapacity = 50f, timeSinceLastDamage = 3.5f, isFullyRecharged = false)
        }

        for (i in 0 until 10) {
            for (state in states) {
                TurtleFortressShieldGenerator.stepRecharge(0.016f, config, state)
            }
        }

        // No allocations should happen, and all should process correctly
        // 10 steps of 0.016 = 0.16s
        // Recharge amount: 0.16s * 20f = 3.2f
        assertEquals(53.2f, states[0].currentCapacity, 0.01f)
    }
}
