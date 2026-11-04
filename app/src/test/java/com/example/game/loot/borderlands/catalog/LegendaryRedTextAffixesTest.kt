package com.example.game.loot.borderlands.catalog

import org.junit.Assert.*
import org.junit.Test

class LegendaryRedTextAffixesTest {

    @Test
    fun testNominalExecutionInfinity() {
        val config = GearConfig().apply {
            baseDamage = 100f
            baseFireRate = 10f
            baseSpread = 2f
            baseMagazineSize = 30
        }
        val state = GearStateSnapshot().apply {
            isBroken = false
            timeSinceBrokenMs = 0f
        }
        val result = OverriddenGearStats()
        LegendaryRedTextEngine.applyAffixInPlace(config, state, LegendaryRedTextAffix.INFINITY, result)

        assertTrue(result.isInfiniteAmmo)
        assertEquals(1, result.actualMagazineSize)
        assertEquals(1f, result.actualSpread, 0.001f)
    }

    @Test
    fun testNominalExecutionBeeBroken() {
        val config = GearConfig().apply {
            baseDamage = 100f
            baseFireRate = 10f
            baseSpread = 2f
            baseMagazineSize = 30
        }
        val stateBroken = GearStateSnapshot().apply {
            isBroken = true
            timeSinceBrokenMs = 0f
        }
        val resultBroken = OverriddenGearStats()
        LegendaryRedTextEngine.applyAffixInPlace(config, stateBroken, LegendaryRedTextAffix.BEE, resultBroken)
        
        assertEquals(100f, resultBroken.actualDamage, 0.001f) // Amp not applied
        assertEquals(0.3f, resultBroken.shieldCapacityMult, 0.001f)
        assertEquals(0.2f, resultBroken.shieldRechargeRateMult, 0.001f)
    }

    @Test
    fun testNominalExecutionBeeUnbroken() {
        val config = GearConfig().apply {
            baseDamage = 100f
            baseFireRate = 10f
            baseSpread = 2f
            baseMagazineSize = 30
        }
        val stateUnbroken = GearStateSnapshot().apply {
            isBroken = false
            timeSinceBrokenMs = 0f
        }
        val resultUnbroken = OverriddenGearStats()
        LegendaryRedTextEngine.applyAffixInPlace(config, stateUnbroken, LegendaryRedTextAffix.BEE, resultUnbroken)
        
        assertEquals(600f, resultUnbroken.actualDamage, 0.001f) // Amp applied (100 + 500)
    }

    @Test
    fun testDeterminismIdenticalInputs() {
        val config1 = GearConfig().apply {
            baseDamage = 100f
            baseFireRate = 10f
            baseSpread = 2f
            baseMagazineSize = 30
        }
        val state1 = GearStateSnapshot().apply {
            isBroken = false
            timeSinceBrokenMs = 0f
        }
        val result1 = OverriddenGearStats()
        LegendaryRedTextEngine.applyAffixInPlace(config1, state1, LegendaryRedTextAffix.CONFERENCE_CALL, result1)
        val checksum1 = LegendaryRedTextEngine.computeChecksum(state1, config1, LegendaryRedTextAffix.CONFERENCE_CALL)

        val config2 = GearConfig().apply {
            baseDamage = 100f
            baseFireRate = 10f
            baseSpread = 2f
            baseMagazineSize = 30
        }
        val state2 = GearStateSnapshot().apply {
            isBroken = false
            timeSinceBrokenMs = 0f
        }
        val result2 = OverriddenGearStats()
        LegendaryRedTextEngine.applyAffixInPlace(config2, state2, LegendaryRedTextAffix.CONFERENCE_CALL, result2)
        val checksum2 = LegendaryRedTextEngine.computeChecksum(state2, config2, LegendaryRedTextAffix.CONFERENCE_CALL)

        assertEquals(result1.actualDamage, result2.actualDamage, 0.001f)
        assertEquals(checksum1, checksum2)
    }

    @Test
    fun testBoundaryConditionsNegativeInputs() {
        val config = GearConfig().apply {
            baseDamage = -100f
            baseFireRate = -10f
            baseSpread = -2f
            baseMagazineSize = -30
        }
        val state = GearStateSnapshot().apply {
            isBroken = false
            timeSinceBrokenMs = 0f
        }
        val result = OverriddenGearStats()
        LegendaryRedTextEngine.applyAffixInPlace(config, state, LegendaryRedTextAffix.INFINITY, result)

        assertTrue(result.isInfiniteAmmo)
        assertEquals(1, result.actualMagazineSize)
        assertEquals(0f, result.actualSpread, 0.001f) // -1 coerced in 0, 1000
    }

    @Test
    fun testAdversarialNaNInjection() {
        val config = GearConfig().apply {
            baseDamage = Float.NaN
            baseFireRate = Float.NaN
            baseSpread = Float.NaN
            baseMagazineSize = 30
        }
        val state = GearStateSnapshot().apply {
            isBroken = false
            timeSinceBrokenMs = Float.NaN
        }
        val result = OverriddenGearStats()
        LegendaryRedTextEngine.applyAffixInPlace(config, state, LegendaryRedTextAffix.HERALD, result)

        // NaN should fallback to safe zero equivalents
        assertEquals(0f, result.actualDamage, 0.001f) // NaN * 1.5 -> safeBase 0 * 1.5 = 0
        assertEquals(0.1f, result.actualFireRate, 0.001f)
        assertEquals(0f, result.actualSpread, 0.001f)
        assertEquals(6, result.additionalProjectiles)
        
        LegendaryRedTextEngine.stepInPlace(state, Float.NaN)
        assertEquals(0f, state.timeSinceBrokenMs, 0.001f)
    }

    @Test
    fun testAdversarialInfinityInjection() {
        val config = GearConfig().apply {
            baseDamage = Float.POSITIVE_INFINITY
            baseFireRate = Float.NEGATIVE_INFINITY
            baseSpread = Float.POSITIVE_INFINITY
            baseMagazineSize = 30
        }
        val state = GearStateSnapshot().apply {
            isBroken = true
            timeSinceBrokenMs = Float.POSITIVE_INFINITY
        }
        val result = OverriddenGearStats()
        LegendaryRedTextEngine.applyAffixInPlace(config, state, LegendaryRedTextAffix.HERALD, result)

        assertEquals(0f, result.actualDamage, 0.001f)
        assertEquals(0.1f, result.actualFireRate, 0.001f)
        assertEquals(0f, result.actualSpread, 0.001f)

        LegendaryRedTextEngine.stepInPlace(state, Float.POSITIVE_INFINITY)
        // dt fallback to 0.016, timeSinceBrokenMs was inf so it becomes 0 from safe defaults
        assertEquals(16f, state.timeSinceBrokenMs, 0.001f)
    }
    
    @Test
    fun testDtClamping() {
        val state = GearStateSnapshot().apply {
            isBroken = true
            timeSinceBrokenMs = 0f
        }
        
        // dt too small
        LegendaryRedTextEngine.stepInPlace(state, 0.0001f)
        assertEquals(1f, state.timeSinceBrokenMs, 0.001f) // 0.001 * 1000 = 1
        
        // dt too big
        state.timeSinceBrokenMs = 0f
        LegendaryRedTextEngine.stepInPlace(state, 10f)
        assertEquals(100f, state.timeSinceBrokenMs, 0.001f) // 0.100 * 1000 = 100
    }

    @Test
    fun testSerializationRoundtripGearConfig() {
        val original = GearConfig().apply {
            baseDamage = 123.4f
            baseFireRate = 5.6f
            baseSpread = 7.8f
            baseMagazineSize = 42
        }
        val map = original.toMap()
        val restored = GearConfig().apply { fromMap(map) }
        assertEquals(original.baseDamage, restored.baseDamage, 0.001f)
    }

    @Test
    fun testSerializationRoundtripGearState() {
        val original = GearStateSnapshot().apply {
            isBroken = true
            timeSinceBrokenMs = 1500f
            shotsFiredContinuous = 15
            bonusAccumulator = 0.5f
        }
        val map = original.toMap()
        val restored = GearStateSnapshot().apply { fromMap(map) }
        assertEquals(original.timeSinceBrokenMs, restored.timeSinceBrokenMs, 0.001f)
    }
    
    @Test
    fun testSerializationRoundtripOverriddenStats() {
        val original = OverriddenGearStats().apply {
            actualDamage = 100f
            actualFireRate = 5f
            actualSpread = 1f
            actualMagazineSize = 20
            additionalProjectiles = 3
            isInfiniteAmmo = true
            shieldCapacityMult = 0.5f
            shieldRechargeRateMult = 1.2f
            ricochetCount = 2
        }
        val map = original.toMap()
        val restored = OverriddenGearStats().apply { fromMap(map) }
        assertEquals(original.actualDamage, restored.actualDamage, 0.001f)
    }

    @Test
    fun testStressLoadMaxCapacity() {
        val config = GearConfig().apply {
            baseDamage = 100f
            baseFireRate = 10f
            baseSpread = 2f
            baseMagazineSize = 30
        }
        val state = GearStateSnapshot().apply {
            isBroken = false
            timeSinceBrokenMs = 0f
        }
        val result = OverriddenGearStats()
        
        val start = System.currentTimeMillis()
        for (i in 0 until 100000) {
            LegendaryRedTextEngine.applyAffixInPlace(config, state, LegendaryRedTextAffix.CONFERENCE_CALL, result)
            LegendaryRedTextEngine.stepInPlace(state, 0.016f)
            val hash = LegendaryRedTextEngine.computeChecksum(state, config, LegendaryRedTextAffix.CONFERENCE_CALL)
            assertNotNull(result)
            assertNotEquals(0, hash)
        }
        val duration = System.currentTimeMillis() - start
        assertTrue("Stress test took too long: ${duration}ms", duration < 1000)
    }
    
    @Test
    fun testMultipleAffixes() {
        val config = GearConfig().apply {
            baseDamage = 100f
            baseFireRate = 10f
            baseSpread = 2f
            baseMagazineSize = 30
        }
        val state = GearStateSnapshot().apply {
            isBroken = false
            timeSinceBrokenMs = 0f
        }
        val resultNorfleet = OverriddenGearStats()
        LegendaryRedTextEngine.applyAffixInPlace(config, state, LegendaryRedTextAffix.NORFLEET, resultNorfleet)
        assertEquals(300f, resultNorfleet.actualDamage, 0.001f)
        assertEquals(3, resultNorfleet.actualMagazineSize)
        assertEquals(2, resultNorfleet.additionalProjectiles)

        val resultSham = OverriddenGearStats()
        LegendaryRedTextEngine.applyAffixInPlace(config, state, LegendaryRedTextAffix.SHAM, resultSham)
        assertEquals(0.2f, resultSham.shieldCapacityMult, 0.001f)
        
        val resultFibber = OverriddenGearStats()
        LegendaryRedTextEngine.applyAffixInPlace(config, state, LegendaryRedTextAffix.FIBBER, resultFibber)
        assertEquals(400f, resultFibber.actualDamage, 0.001f)
        assertEquals(1, resultFibber.ricochetCount)
    }
    
    @Test
    fun testNaNSerializationRecovery() {
        val badMap = mapOf<String, Any>(
            "baseDamage" to Float.NaN,
            "baseFireRate" to Float.POSITIVE_INFINITY,
            "baseSpread" to 0f,
            "baseMagazineSize" to "invalid"
        )
        
        val config = GearConfig().apply { fromMap(badMap) }
        assertEquals(0f, config.baseDamage, 0.001f)
        assertEquals(0f, config.baseFireRate, 0.001f)
        assertEquals(0f, config.baseSpread, 0.001f)
        assertEquals(0, config.baseMagazineSize)
    }
}
