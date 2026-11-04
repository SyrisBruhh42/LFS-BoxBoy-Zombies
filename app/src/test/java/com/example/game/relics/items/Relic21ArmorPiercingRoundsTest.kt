package com.example.game.relics.items

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Relic21ArmorPiercingRoundsTest {

    @Test
    fun testInitializationAndConstraints() {
        val state = Relic21ArmorPiercingRoundsState(stackCount = 1)
        assertEquals(1, state.stackCount)
        assertFalse(state.canBePurchasedAtVending)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testZeroStackThrowsException() {
        Relic21ArmorPiercingRoundsState(stackCount = 0)
    }

    @Test
    fun testDamageScalingForBoss() {
        val state = Relic21ArmorPiercingRoundsState(stackCount = 2) // +40%
        val boss = ZombieEntity(1, ZombieArchetype.DEVIL_BOSS, 0f, 0f, 100, 100)

        val newDamage = Relic21ArmorPiercingRoundsEngine.calculateDamage(state, boss, 100)
        assertEquals(140, newDamage)
    }

    @Test
    fun testDamageScalingForEliteWeaver() {
        val state = Relic21ArmorPiercingRoundsState(stackCount = 3) // +60%
        val elite = ZombieEntity(1, ZombieArchetype.ABYSSAL_WEAVER, 0f, 0f, 100, 100)

        val newDamage = Relic21ArmorPiercingRoundsEngine.calculateDamage(state, elite, 100)
        assertEquals(160, newDamage)
    }

    @Test
    fun testDamageScalingForEliteStalker() {
        val state = Relic21ArmorPiercingRoundsState(stackCount = 1) // +20%
        val elite = ZombieEntity(1, ZombieArchetype.CALCIFIED_STALKER, 0f, 0f, 100, 100)

        val newDamage = Relic21ArmorPiercingRoundsEngine.calculateDamage(state, elite, 100)
        assertEquals(120, newDamage)
    }

    @Test
    fun testNoDamageScalingForNormalZombie() {
        val state = Relic21ArmorPiercingRoundsState(stackCount = 10) // +200%
        val normal = ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100)

        val newDamage = Relic21ArmorPiercingRoundsEngine.calculateDamage(state, normal, 100)
        assertEquals(100, newDamage) // Should remain base damage
    }

    @Test
    fun testNegativeInputHandling() {
        val state = Relic21ArmorPiercingRoundsState(stackCount = 1, Relic21ArmorPiercingRoundsConfig(-0.2f))
        val boss = ZombieEntity(1, ZombieArchetype.DEVIL_BOSS, 0f, 0f, 100, 100)

        val newDamage = Relic21ArmorPiercingRoundsEngine.calculateDamage(state, boss, 100)
        assertEquals(100, newDamage) // Negative config should just act like 0%
    }

    @Test
    fun testZeroBaseDamage() {
        val state = Relic21ArmorPiercingRoundsState(stackCount = 5)
        val boss = ZombieEntity(1, ZombieArchetype.DEVIL_BOSS, 0f, 0f, 100, 100)
        val newDamage = Relic21ArmorPiercingRoundsEngine.calculateDamage(state, boss, 0)
        assertEquals(0, newDamage)
    }

    @Test
    fun testNegativeBaseDamage() {
        val state = Relic21ArmorPiercingRoundsState(stackCount = 5)
        val boss = ZombieEntity(1, ZombieArchetype.DEVIL_BOSS, 0f, 0f, 100, 100)
        val newDamage = Relic21ArmorPiercingRoundsEngine.calculateDamage(state, boss, -50)
        assertEquals(0, newDamage)
    }

    @Test
    fun testNaNAndInfinityRecovery() {
        val state = Relic21ArmorPiercingRoundsState(stackCount = 1, Relic21ArmorPiercingRoundsConfig(Float.NaN))
        val boss = ZombieEntity(1, ZombieArchetype.DEVIL_BOSS, 0f, 0f, 100, 100)

        val newDamage = Relic21ArmorPiercingRoundsEngine.calculateDamage(state, boss, 100)
        assertEquals(100, newDamage)

        val stateInf = Relic21ArmorPiercingRoundsState(stackCount = 1, Relic21ArmorPiercingRoundsConfig(Float.POSITIVE_INFINITY))
        val newDamageInf = Relic21ArmorPiercingRoundsEngine.calculateDamage(stateInf, boss, 100)
        assertEquals(100, newDamageInf)
    }

    @Test
    fun testSerializationRoundtripParity() {
        val original = Relic21ArmorPiercingRoundsState(stackCount = 5, Relic21ArmorPiercingRoundsConfig(0.25f))
        val map = original.toMap()
        val restored = Relic21ArmorPiercingRoundsState.fromMap(map)

        assertEquals(original.stackCount, restored.stackCount)
        assertEquals(original.config.damageBonusPerStack, restored.config.damageBonusPerStack, 0.001f)
    }

    @Test
    fun testChecksumDeterminism() {
        val state1 = Relic21ArmorPiercingRoundsState(stackCount = 10, Relic21ArmorPiercingRoundsConfig(0.20f))
        val state2 = Relic21ArmorPiercingRoundsState(stackCount = 10, Relic21ArmorPiercingRoundsConfig(0.20f))
        val state3 = Relic21ArmorPiercingRoundsState(stackCount = 11, Relic21ArmorPiercingRoundsConfig(0.20f))

        assertEquals(state1.computeChecksum(), state2.computeChecksum())
        assertTrue(state1.computeChecksum() != state3.computeChecksum())
    }
}
