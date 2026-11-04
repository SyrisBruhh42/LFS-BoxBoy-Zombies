package com.example.game.loot.borderlands.catalog

import com.example.game.ElementalAffinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AdaptiveResistShieldGeneratorTest {

    @Test
    fun testNominalRecharge() {
        val config = AdaptiveShieldConfig(
            maxCapacity = 100f,
            rechargeDelaySeconds = 2.0f,
            rechargeRatePerSecond = 10f
        )
        val state = AdaptiveShieldState(
            currentCapacity = 50f,
            timeSinceLastDamage = 0f,
            isFullyRecharged = false
        )

        // Step within delay, should not recharge
        AdaptiveResistShieldGenerator.stepRecharge(0.1f, config, state)
        assertEquals(50f, state.currentCapacity, 0.01f)
        assertEquals(0.1f, state.timeSinceLastDamage, 0.01f)

        // Step past delay
        state.timeSinceLastDamage = 2.0f
        AdaptiveResistShieldGenerator.stepRecharge(0.1f, config, state)
        assertEquals(51f, state.currentCapacity, 0.01f)
    }

    @Test
    fun testMaxHpBonus() {
        val config = AdaptiveShieldConfig(maxHpBonusPct = 0.25f)
        val maxHp = AdaptiveResistShieldGenerator.getEffectiveMaxHp(100, config)
        assertEquals(125, maxHp)
    }

    @Test
    fun testDamageNoResistance() {
        val config = AdaptiveShieldConfig(maxCapacity = 100f)
        val state = AdaptiveShieldState(currentCapacity = 100f)

        val result = AdaptiveResistShieldGenerator.processDamage(
            rawDamage = 20f,
            element = ElementalAffinity.KINETIC,
            config = config,
            state = state
        )

        assertEquals(20f, result.shieldDamageTaken, 0.01f)
        assertEquals(0, result.hpSpilloverDamage)
        assertFalse(result.resistanceApplied)
        assertEquals(ElementalAffinity.KINETIC, result.adaptedElement)
        assertEquals(80f, state.currentCapacity, 0.01f)
        assertFalse(state.isFullyRecharged)
    }

    @Test
    fun testDamageWithResistance() {
        val config = AdaptiveShieldConfig(maxCapacity = 100f, elementalResistanceBonusPct = 0.5f)
        val state = AdaptiveShieldState(currentCapacity = 100f, lastHitElement = ElementalAffinity.INCENDIARY)

        val result = AdaptiveResistShieldGenerator.processDamage(
            rawDamage = 40f,
            element = ElementalAffinity.INCENDIARY,
            config = config,
            state = state
        )

        // 40 damage with 50% resistance = 20 damage
        assertEquals(20f, result.shieldDamageTaken, 0.01f)
        assertEquals(0, result.hpSpilloverDamage)
        assertTrue(result.resistanceApplied)
        assertEquals(ElementalAffinity.INCENDIARY, result.adaptedElement)
        assertEquals(80f, state.currentCapacity, 0.01f)
    }

    @Test
    fun testResistanceAdaptsToNewElement() {
        val config = AdaptiveShieldConfig(maxCapacity = 100f, elementalResistanceBonusPct = 0.5f)
        val state = AdaptiveShieldState(currentCapacity = 100f, lastHitElement = ElementalAffinity.KINETIC)

        val result1 = AdaptiveResistShieldGenerator.processDamage(
            rawDamage = 20f,
            element = ElementalAffinity.CRYO,
            config = config,
            state = state
        )

        assertFalse(result1.resistanceApplied)
        assertEquals(ElementalAffinity.CRYO, result1.adaptedElement)
        assertEquals(ElementalAffinity.CRYO, state.lastHitElement)

        val result2 = AdaptiveResistShieldGenerator.processDamage(
            rawDamage = 20f,
            element = ElementalAffinity.CRYO,
            config = config,
            state = state
        )

        assertTrue(result2.resistanceApplied)
    }

    @Test
    fun testDamageSpilloverToHp() {
        val config = AdaptiveShieldConfig(maxCapacity = 100f)
        val state = AdaptiveShieldState(currentCapacity = 20f)

        val result = AdaptiveResistShieldGenerator.processDamage(
            rawDamage = 50f,
            element = ElementalAffinity.KINETIC,
            config = config,
            state = state
        )

        assertEquals(20f, result.shieldDamageTaken, 0.01f)
        assertEquals(30, result.hpSpilloverDamage)
        assertEquals(0f, state.currentCapacity, 0.01f)
    }

    @Test
    fun testNegativeOrZeroDamageIsIgnored() {
        val config = AdaptiveShieldConfig()
        val state = AdaptiveShieldState(currentCapacity = 100f, lastHitElement = ElementalAffinity.CORROSIVE)

        val result = AdaptiveResistShieldGenerator.processDamage(-10f, ElementalAffinity.KINETIC, config, state)
        assertEquals(0f, result.shieldDamageTaken, 0.01f)
        assertEquals(0, result.hpSpilloverDamage)
        assertEquals(ElementalAffinity.CORROSIVE, state.lastHitElement)
    }

    @Test
    fun testDtSecondsIsClampedAndGuards() {
        val config = AdaptiveShieldConfig(maxCapacity = 100f, rechargeDelaySeconds = 0f, rechargeRatePerSecond = 10f)
        val state = AdaptiveShieldState(currentCapacity = 50f)

        // Try to step with huge dt
        AdaptiveResistShieldGenerator.stepRecharge(10f, config, state)
        // Clamp bounds [0.001, 0.100], so it should step by 0.100
        // Capacity increase = 10 * 0.100 = 1.0
        assertEquals(51f, state.currentCapacity, 0.01f)

        // Test NaN
        AdaptiveResistShieldGenerator.stepRecharge(Float.NaN, config, state)
        assertEquals(51f, state.currentCapacity, 0.01f)
    }

    @Test
    fun testMapSerializationAndDeserialization() {
        val config = AdaptiveShieldConfig(maxCapacity = 150f, maxHpBonusPct = 0.3f)
        val map = config.toMap()
        val configRestored = AdaptiveShieldConfig.fromMap(map)
        assertEquals(config.maxCapacity, configRestored.maxCapacity, 0.01f)
        assertEquals(config.maxHpBonusPct, configRestored.maxHpBonusPct, 0.01f)

        val state = AdaptiveShieldState(currentCapacity = 45f, lastHitElement = ElementalAffinity.VOLTAIC)
        val stateMap = state.toMap()
        val stateRestored = AdaptiveShieldState.fromMap(stateMap)
        assertEquals(state.currentCapacity, stateRestored.currentCapacity, 0.01f)
        assertEquals(ElementalAffinity.VOLTAIC, stateRestored.lastHitElement)
    }

    @Test
    fun testStateChecksumIsDeterministic() {
        val state1 = AdaptiveShieldState(currentCapacity = 100f, lastHitElement = ElementalAffinity.INCENDIARY)
        val state2 = AdaptiveShieldState(currentCapacity = 100f, lastHitElement = ElementalAffinity.INCENDIARY)
        val state3 = AdaptiveShieldState(currentCapacity = 100f, lastHitElement = ElementalAffinity.CRYO)

        assertEquals(state1.calculateChecksum(), state2.calculateChecksum())
        assertTrue(state1.calculateChecksum() != state3.calculateChecksum())
    }

    @Test
    fun testDamageWithNanIsIgnored() {
        val config = AdaptiveShieldConfig(maxCapacity = 100f)
        val state = AdaptiveShieldState(currentCapacity = 100f)

        val result = AdaptiveResistShieldGenerator.processDamage(Float.NaN, ElementalAffinity.KINETIC, config, state)
        assertEquals(0f, result.shieldDamageTaken, 0.01f)
        assertEquals(100f, state.currentCapacity, 0.01f)
    }

    @Test
    fun testMaxHpWithNanIsBaseHp() {
        val config = AdaptiveShieldConfig(maxHpBonusPct = Float.NaN)
        val hp = AdaptiveResistShieldGenerator.getEffectiveMaxHp(100, config)
        assertEquals(100, hp)
    }
}
