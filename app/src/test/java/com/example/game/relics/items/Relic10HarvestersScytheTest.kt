package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test

class Relic10HarvestersScytheTest {

    @Test
    fun testZeroStacks_ProvidesNoBonus() {
        val state = HarvestersScytheState(0)
        assertEquals(0.0f, state.critChanceBonus, 0.0001f)
        assertEquals(0, state.healAmountOnCrit)
        assertEquals(0, Relic10HarvestersScytheEngine.processCritHeal(state))
    }

    @Test
    fun testOneStack_ProvidesBaseBonus() {
        val state = HarvestersScytheState(1)
        assertEquals(0.05f, state.critChanceBonus, 0.0001f)
        assertEquals(8, state.healAmountOnCrit)
        assertEquals(8, Relic10HarvestersScytheEngine.processCritHeal(state))
    }

    @Test
    fun testTwoStacks_ProvidesScalingBonus() {
        val state = HarvestersScytheState(2)
        assertEquals(0.05f, state.critChanceBonus, 0.0001f) // Base crit chance doesn't scale
        assertEquals(12, state.healAmountOnCrit) // 8 + (2 - 1) * 4
        assertEquals(12, Relic10HarvestersScytheEngine.processCritHeal(state))
    }

    @Test
    fun testThreeStacks_ProvidesScalingBonus() {
        val state = HarvestersScytheState(3)
        assertEquals(0.05f, state.critChanceBonus, 0.0001f)
        assertEquals(16, state.healAmountOnCrit) // 8 + (3 - 1) * 4
        assertEquals(16, Relic10HarvestersScytheEngine.processCritHeal(state))
    }

    @Test
    fun testHighStacks_ProvidesScalingBonus() {
        val state = HarvestersScytheState(100)
        assertEquals(0.05f, state.critChanceBonus, 0.0001f)
        assertEquals(404, state.healAmountOnCrit) // 8 + (100 - 1) * 4
        assertEquals(404, Relic10HarvestersScytheEngine.processCritHeal(state))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeStacks_ThrowsException() {
        HarvestersScytheState(-1)
    }

    @Test
    fun testSerializationRoundtrip() {
        val state = HarvestersScytheState(5)
        val map = state.toMap()
        val deserialized = HarvestersScytheState.fromMap(map)
        assertEquals(state.stackCount, deserialized.stackCount)
    }

    @Test
    fun testSerializationFallback_MissingField() {
        val map = emptyMap<String, Any>()
        val deserialized = HarvestersScytheState.fromMap(map)
        assertEquals(0, deserialized.stackCount)
    }

    @Test
    fun testSerializationFallback_InvalidFieldType() {
        val map = mapOf("stackCount" to "not_a_number")
        val deserialized = HarvestersScytheState.fromMap(map)
        assertEquals(0, deserialized.stackCount)
    }

    @Test
    fun testSerializationFallback_NegativeValueIsClamped() {
        val map = mapOf("stackCount" to -5)
        val deserialized = HarvestersScytheState.fromMap(map)
        assertEquals(0, deserialized.stackCount)
    }

    @Test
    fun testChecksumDeterminism() {
        val state1 = HarvestersScytheState(10)
        val state2 = HarvestersScytheState(10)
        assertEquals(Relic10HarvestersScytheEngine.computeChecksum(state1), Relic10HarvestersScytheEngine.computeChecksum(state2))
    }

    @Test
    fun testChecksumDistinctness() {
        val state1 = HarvestersScytheState(10)
        val state2 = HarvestersScytheState(11)
        assertNotEquals(Relic10HarvestersScytheEngine.computeChecksum(state1), Relic10HarvestersScytheEngine.computeChecksum(state2))
    }

    @Test
    fun testInvariant_CannotBePurchasedAtVending() {
        assertFalse(Relic10HarvestersScytheEngine.CAN_BE_PURCHASED_AT_VENDING)
        assertFalse(Relic10HarvestersScytheEngine.RELIC_KIND.canBePurchasedAtVending)
    }
}
