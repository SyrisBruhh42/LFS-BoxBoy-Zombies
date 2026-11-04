package com.example.game.relics.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Relic09TitaniumCrowbarTest {

    @Test
    fun `test nominal gameplay - target greater than 90 percent HP with single stack grants bonus`() {
        val targetHp = 95
        val targetMaxHp = 100
        val stackCount = 1

        val multiplier = TitaniumCrowbarEngine.computeDamageMultiplier(targetHp, targetMaxHp, stackCount)
        assertEquals(1.75f, multiplier, 0.0001f) // 1.0 + 0.75 * 1
    }

    @Test
    fun `test nominal gameplay - target greater than 90 percent HP with multiple stacks scales correctly`() {
        val targetHp = 950
        val targetMaxHp = 1000
        val stackCount = 4

        val multiplier = TitaniumCrowbarEngine.computeDamageMultiplier(targetHp, targetMaxHp, stackCount)
        assertEquals(4.0f, multiplier, 0.0001f) // 1.0 + 0.75 * 4
    }

    @Test
    fun `test nominal gameplay - target less than or equal to 90 percent HP with single stack grants NO bonus`() {
        val targetHp = 85
        val targetMaxHp = 100
        val stackCount = 1

        val multiplier = TitaniumCrowbarEngine.computeDamageMultiplier(targetHp, targetMaxHp, stackCount)
        assertEquals(1.0f, multiplier, 0.0001f)
    }

    @Test
    fun `test nominal gameplay - target less than or equal to 90 percent HP with multiple stacks grants NO bonus`() {
        val targetHp = 50
        val targetMaxHp = 100
        val stackCount = 10

        val multiplier = TitaniumCrowbarEngine.computeDamageMultiplier(targetHp, targetMaxHp, stackCount)
        assertEquals(1.0f, multiplier, 0.0001f)
    }

    @Test
    fun `test boundary condition - target exactly at 90 percent HP grants NO bonus`() {
        val targetHp = 90
        val targetMaxHp = 100
        val stackCount = 1

        val multiplier = TitaniumCrowbarEngine.computeDamageMultiplier(targetHp, targetMaxHp, stackCount)
        assertEquals(1.0f, multiplier, 0.0001f)
    }

    @Test
    fun `test boundary condition - target just above 90 percent HP grants bonus`() {
        val targetHp = 9001
        val targetMaxHp = 10000
        val stackCount = 2

        val multiplier = TitaniumCrowbarEngine.computeDamageMultiplier(targetHp, targetMaxHp, stackCount)
        assertEquals(2.5f, multiplier, 0.0001f) // 1.0 + 0.75 * 2
    }

    @Test
    fun `test adversarial condition - target max HP is zero returns base multiplier`() {
        val targetHp = 10
        val targetMaxHp = 0
        val stackCount = 1

        val multiplier = TitaniumCrowbarEngine.computeDamageMultiplier(targetHp, targetMaxHp, stackCount)
        assertEquals(1.0f, multiplier, 0.0001f)
    }

    @Test
    fun `test adversarial condition - target HP is negative returns base multiplier`() {
        val targetHp = -10
        val targetMaxHp = 100
        val stackCount = 1

        val multiplier = TitaniumCrowbarEngine.computeDamageMultiplier(targetHp, targetMaxHp, stackCount)
        assertEquals(1.0f, multiplier, 0.0001f)
    }

    @Test
    fun `test determinism proof - identical inputs produce identical outputs over multiple iterations`() {
        val targetHp = 95
        val targetMaxHp = 100
        val stackCount = 3

        val firstResult = TitaniumCrowbarEngine.computeDamageMultiplier(targetHp, targetMaxHp, stackCount)

        for (i in 0..1000) {
            val result = TitaniumCrowbarEngine.computeDamageMultiplier(targetHp, targetMaxHp, stackCount)
            assertEquals(firstResult, result, 0.0f)
        }
    }

    @Test
    fun `test serialization roundtrip - TitaniumCrowbarConfig toMap and fromMap preserve all fields`() {
        val original = TitaniumCrowbarConfig(
            hpThresholdPercent = 0.85f,
            damageBonusPerStack = 1.25f,
            canBePurchasedAtVending = false
        )

        val serialized = original.toMap()
        val deserialized = TitaniumCrowbarConfig.fromMap(serialized)

        assertEquals(original.hpThresholdPercent, deserialized.hpThresholdPercent, 0.0001f)
        assertEquals(original.damageBonusPerStack, deserialized.damageBonusPerStack, 0.0001f)
        assertFalse(deserialized.canBePurchasedAtVending)
    }

    @Test
    fun `test serialization roundtrip - TitaniumCrowbarSnapshot toMap and fromMap preserve all fields`() {
        val original = TitaniumCrowbarSnapshot(
            stackCount = 5,
            lastAppliedBonusDamage = 3.75f
        )

        val serialized = original.toMap()
        val deserialized = TitaniumCrowbarSnapshot.fromMap(serialized)

        assertEquals(original.stackCount, deserialized.stackCount)
        assertEquals(original.lastAppliedBonusDamage, deserialized.lastAppliedBonusDamage, 0.0001f)
        assertEquals(original.getFnv1aChecksum(), deserialized.getFnv1aChecksum())
    }

    @Test
    fun `test validation - canBePurchasedAtVending is strictly false`() {
        val config = TitaniumCrowbarConfig()
        assertFalse("Titanium Crowbar is a drop-only relic and must not be purchasable", config.canBePurchasedAtVending)
    }

    @Test
    fun `test batch damage apply - nominal and boundary conditions processed correctly`() {
        val baseDamageArray = floatArrayOf(100f, 200f, 150f, 300f, Float.NaN, 50f)
        val targetHpArray = intArrayOf(95, 90, 50, -10, 100, 100)
        val targetMaxHpArray = intArrayOf(100, 100, 100, 100, 100, 0)
        val outDamageAccumulator = FloatArray(6)

        TitaniumCrowbarEngine.applyCrowbarDamageBatch(
            baseDamageArray,
            targetHpArray,
            targetMaxHpArray,
            stackCount = 2, // +150% damage
            outDamageAccumulator = outDamageAccumulator
        )

        // Index 0: 95/100 -> >90%, multiplier = 2.5. 100 * 2.5 = 250
        assertEquals(250f, outDamageAccumulator[0], 0.0001f)
        // Index 1: 90/100 -> exactly 90%, multiplier = 1.0. 200 * 1.0 = 200
        assertEquals(200f, outDamageAccumulator[1], 0.0001f)
        // Index 2: 50/100 -> <90%, multiplier = 1.0. 150 * 1.0 = 150
        assertEquals(150f, outDamageAccumulator[2], 0.0001f)
        // Index 3: -10 HP -> boundary check, multiplier = 1.0. 300 * 1.0 = 300
        assertEquals(300f, outDamageAccumulator[3], 0.0001f)
        // Index 4: Base damage NaN -> sets output to 0f
        assertEquals(0f, outDamageAccumulator[4], 0.0001f)
        // Index 5: Max HP 0 -> boundary check, multiplier = 1.0. 50 * 1.0 = 50
        assertEquals(50f, outDamageAccumulator[5], 0.0001f)
    }
}
