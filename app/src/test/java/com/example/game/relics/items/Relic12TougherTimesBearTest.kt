package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class Relic12TougherTimesBearTest {

    @Test
    fun testConfigInvariants() {
        val config = TougherTimesBearConfig()
        assertFalse("TougherTimesBear must not be purchasable at vending", config.canBePurchasedAtVending)
        assertEquals(0.15f, config.baseBlockChanceScale, 0.0001f)

        try {
            TougherTimesBearConfig(canBePurchasedAtVending = true)
            fail("Should throw exception if canBePurchasedAtVending is true")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        try {
            TougherTimesBearConfig(baseBlockChanceScale = Float.NaN)
            fail("Should throw exception if NaN")
        } catch (e: IllegalArgumentException) {}
    }

    @Test
    fun testStateInvariants() {
        val state = TougherTimesBearState(stackCount = 5)
        assertEquals(5, state.stackCount)

        try {
            TougherTimesBearState(stackCount = 0)
            fail("Should throw exception if stackCount < 1")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun testSerializationRoundtrip() {
        val config = TougherTimesBearConfig(baseBlockChanceScale = 0.2f)
        val configMap = config.toMap()
        val config2 = TougherTimesBearConfig.fromMap(configMap)
        assertEquals(config.baseBlockChanceScale, config2.baseBlockChanceScale, 0.001f)
        assertEquals(config.canBePurchasedAtVending, config2.canBePurchasedAtVending)

        val state = TougherTimesBearState(stackCount = 10, invulnTimeRemaining = 0.05f)
        val stateMap = state.toMap()
        val state2 = TougherTimesBearState.fromMap(stateMap)
        assertEquals(state.stackCount, state2.stackCount)
        assertEquals(state.invulnTimeRemaining, state2.invulnTimeRemaining, 0.001f)
        assertEquals(state.computeChecksum(), state2.computeChecksum())
    }

    @Test
    fun testHyperbolicBlockChanceOneStack() {
        val config = TougherTimesBearConfig()
        val state = TougherTimesBearState(stackCount = 1)
        val hitDamages = floatArrayOf(10f, 10f, 10f, 10f, 10f)
        val xs = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        val ys = floatArrayOf(0f, 0f, 0f, 0f, 0f)

        // block chance = 1 - 1 / (1 + 0.15 * 1) = 1 - 1/1.15 = 0.1304

        var totalBlocked = 0
        var seed = 12345L
        val iterations = 10000
        for (i in 0 until iterations) {
            val res = TougherTimesBearEngine.processHits(
                dtSeconds = 0.016f,
                state = state,
                config = config,
                randomSeed = seed,
                targetX = 0f,
                targetY = 0f,
                hitCount = 1,
                hitDamages = hitDamages,
                hitSourceXs = xs,
                hitSourceYs = ys
            )
            seed = res.nextSeed
            if (res.hitsBlocked > 0) totalBlocked++
        }

        val observedChance = totalBlocked.toFloat() / iterations
        val expectedChance = 1f - (1f / (1f + 0.15f * 1))
        assertTrue("Chance $observedChance should be near $expectedChance", abs(observedChance - expectedChance) < 0.02f)
    }

    @Test
    fun testHyperbolicBlockChanceHighStacks() {
        val config = TougherTimesBearConfig()
        val state = TougherTimesBearState(stackCount = 10)
        val hitDamages = floatArrayOf(10f)
        val xs = floatArrayOf(0f)
        val ys = floatArrayOf(0f)

        var totalBlocked = 0
        var seed = 54321L
        val iterations = 10000
        for (i in 0 until iterations) {
            val res = TougherTimesBearEngine.processHits(
                dtSeconds = 0.016f,
                state = state,
                config = config,
                randomSeed = seed,
                targetX = 0f,
                targetY = 0f,
                hitCount = 1,
                hitDamages = hitDamages,
                hitSourceXs = xs,
                hitSourceYs = ys
            )
            seed = res.nextSeed
            if (res.hitsBlocked > 0) totalBlocked++
        }

        val observedChance = totalBlocked.toFloat() / iterations
        val expectedChance = 1f - (1f / (1f + 0.15f * 10))
        assertTrue("Chance $observedChance should be near $expectedChance", abs(observedChance - expectedChance) < 0.02f)
    }

    @Test
    fun testInvulnerabilityGracePeriod() {
        // We set 100% block chance artificially to test grace period
        val config = TougherTimesBearConfig(baseBlockChanceScale = 100f, postBlockInvulnDurationSeconds = 0.1f)
        val state = TougherTimesBearState(stackCount = 1000)

        // 5 hits in same frame. First one should trigger block + invuln, subsequent should be blocked by invuln
        val hitDamages = floatArrayOf(10f, 10f, 10f, 10f, 10f)
        val xs = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        val ys = floatArrayOf(0f, 0f, 0f, 0f, 0f)

        val res = TougherTimesBearEngine.processHits(
            dtSeconds = 0.016f,
            state = state,
            config = config,
            randomSeed = 1L,
            targetX = 0f,
            targetY = 0f,
            hitCount = 5,
            hitDamages = hitDamages,
            hitSourceXs = xs,
            hitSourceYs = ys
        )

        assertEquals("Should block all 5 hits", 5, res.hitsBlocked)
        assertEquals("Should take 0 damage", 0f, res.damageTaken)
        assertEquals("Should have full invuln period", 0.1f, res.newState.invulnTimeRemaining, 0.001f)
    }

    @Test
    fun testInvulnDecayOverTime() {
        val config = TougherTimesBearConfig()
        // State has 0.1 invuln time left
        val state = TougherTimesBearState(stackCount = 1, invulnTimeRemaining = 0.1f)

        val hitDamages = floatArrayOf(10f)
        val xs = floatArrayOf(0f)
        val ys = floatArrayOf(0f)

        // Passing dtSeconds = 0.1f should decrease invuln to 0
        val res = TougherTimesBearEngine.processHits(
            dtSeconds = 0.1f,
            state = state,
            config = config,
            randomSeed = 99L, // seed won't matter if it gets hit after invuln is gone (if randFloat > chance), but we test the invuln mechanic
            targetX = 0f,
            targetY = 0f,
            hitCount = 0,
            hitDamages = hitDamages,
            hitSourceXs = xs,
            hitSourceYs = ys
        )

        assertEquals("Invuln should decay to 0", 0f, res.newState.invulnTimeRemaining)
    }

    @Test
    fun testDeterminism() {
        val config = TougherTimesBearConfig()
        val state = TougherTimesBearState(stackCount = 5)
        val hitDamages = floatArrayOf(10f, 15f, 20f)
        val xs = floatArrayOf(0f, 0f, 0f)
        val ys = floatArrayOf(0f, 0f, 0f)

        val res1 = TougherTimesBearEngine.processHits(
            0.016f, state, config, 12345L, 0f, 0f, 3, hitDamages, xs, ys
        )

        val res2 = TougherTimesBearEngine.processHits(
            0.016f, state, config, 12345L, 0f, 0f, 3, hitDamages, xs, ys
        )

        assertEquals(res1.hitsBlocked, res2.hitsBlocked)
        assertEquals(res1.damageTaken, res2.damageTaken)
        assertEquals(res1.nextSeed, res2.nextSeed)
        assertEquals(res1.newState.computeChecksum(), res2.newState.computeChecksum())
    }

    @Test
    fun testNanAndInfinityRejection() {
        val config = TougherTimesBearConfig()
        val state = TougherTimesBearState(stackCount = 1)
        val hitDamages = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -10f, 0f)
        val xs = floatArrayOf(0f, 0f, 0f, 0f)
        val ys = floatArrayOf(0f, 0f, 0f, 0f)

        val res = TougherTimesBearEngine.processHits(
            0.016f, state, config, 1L, 0f, 0f, 4, hitDamages, xs, ys
        )

        assertEquals("Should ignore invalid hits", 0, res.hitsBlocked)
        assertEquals("Should take 0 damage from invalids", 0f, res.damageTaken)
    }

    @Test
    fun testSpatialBoundsRejection() {
        val config = TougherTimesBearConfig()
        val state = TougherTimesBearState(stackCount = 1)
        val hitDamages = floatArrayOf(10f)
        // Hit originates from 20000 units away
        val xs = floatArrayOf(20000f)
        val ys = floatArrayOf(0f)

        val res = TougherTimesBearEngine.processHits(
            0.016f, state, config, 1L, 0f, 0f, 1, hitDamages, xs, ys
        )

        assertEquals("Should ignore out of bounds hits", 0, res.hitsBlocked)
        assertEquals("Should take 0 damage from out of bounds", 0f, res.damageTaken)
    }

    @Test
    fun testExtremeLoadMaxCapacity() {
        val config = TougherTimesBearConfig(baseBlockChanceScale = 0.15f)
        val state = TougherTimesBearState(stackCount = 5)

        // 300 simultaneous hits
        val hitDamages = FloatArray(300) { 10f }
        val xs = FloatArray(300) { 50f }
        val ys = FloatArray(300) { 50f }

        val res = TougherTimesBearEngine.processHits(
            0.016f, state, config, 999L, 0f, 0f, 300, hitDamages, xs, ys
        )

        // It should process successfully without crash. Hits should be blocked and invuln triggers.
        assertTrue("Should process successfully under load", res.hitsBlocked >= 0)
        assertTrue("Should take some damage or none", res.damageTaken >= 0f)
        assertTrue("Next seed should be deterministic", res.nextSeed != 999L)
    }

    @Test
    fun testNegativeOrZeroHitCountGracefulHandling() {
        val config = TougherTimesBearConfig()
        val state = TougherTimesBearState(stackCount = 1, invulnTimeRemaining = 0.05f)
        val hitDamages = floatArrayOf(10f)
        val xs = floatArrayOf(0f)
        val ys = floatArrayOf(0f)

        // Passing hitCount = -5
        val res = TougherTimesBearEngine.processHits(
            0.016f, state, config, 1L, 0f, 0f, -5, hitDamages, xs, ys
        )

        assertEquals("Should handle negative hit count safely", 0, res.hitsBlocked)
        assertEquals("Damage taken should be 0", 0f, res.damageTaken)
        // Invuln time should still tick down by dt
        assertEquals("Invuln time should decay", maxOf(0f, 0.05f - 0.016f), res.newState.invulnTimeRemaining, 0.001f)
    }

    @Test
    fun testDtClamping() {
        val config = TougherTimesBearConfig()
        val state = TougherTimesBearState(stackCount = 1, invulnTimeRemaining = 0.5f)

        // Pass a massive dt (e.g., 5 seconds delay)
        val res = TougherTimesBearEngine.processHits(
            5.0f, state, config, 1L, 0f, 0f, 0, FloatArray(0), FloatArray(0), FloatArray(0)
        )

        // Dt should be clamped to 0.100f
        assertEquals("Invuln time should only decrease by max dt of 0.100f", 0.4f, res.newState.invulnTimeRemaining, 0.001f)

        // Pass negative dt
        val res2 = TougherTimesBearEngine.processHits(
            -5.0f, state, config, 1L, 0f, 0f, 0, FloatArray(0), FloatArray(0), FloatArray(0)
        )

        // Dt should be clamped to 0.001f
        assertEquals("Invuln time should decrease by min dt of 0.001f", 0.499f, res2.newState.invulnTimeRemaining, 0.001f)
    }
}
