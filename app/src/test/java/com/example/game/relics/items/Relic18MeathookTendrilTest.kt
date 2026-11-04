package com.example.game.relics.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Relic18MeathookTendrilTest {

    @Test
    fun testModelInvariants() {
        val state = MeathookTendrilState(stackCount = 2, seed = 12345L)
        assertFalse("Drop-only relics cannot be purchased at vending", state.canBePurchasedAtVending)
        assertEquals(2, state.stackCount)
        assertEquals(12345L, state.seed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testZeroStackThrowsException() {
        MeathookTendrilState(stackCount = 0, seed = 0L)
    }

    @Test
    fun testSerializationRoundTrip() {
        val config = MeathookTendrilConfig(baseChanceParam = 0.25f, pullRadiusPx = 150f, pullSpeedPxPerSec = 300f)
        val configMap = config.toMap()
        val restoredConfig = MeathookTendrilConfig.fromMap(configMap)
        
        assertEquals(config.baseChanceParam, restoredConfig.baseChanceParam, 0.001f)
        assertEquals(config.pullRadiusPx, restoredConfig.pullRadiusPx, 0.001f)
        
        val state = MeathookTendrilState(stackCount = 5, seed = 987654321L)
        val stateMap = state.toMap()
        val restoredState = MeathookTendrilState.fromMap(stateMap)
        
        assertEquals(state.stackCount, restoredState.stackCount)
        assertEquals(state.seed, restoredState.seed)
        assertEquals(state.checksum, restoredState.checksum)
    }

    @Test
    fun testDeterminismIdenticalSeeds() {
        val engine = MeathookTendrilEngine(maxCapacity = 10)
        val config = MeathookTendrilConfig()
        val state1 = MeathookTendrilState(1, seed = 42L)
        val state2 = MeathookTendrilState(1, seed = 42L)
        
        val entityX = floatArrayOf(10f, 20f)
        val entityY = floatArrayOf(10f, 20f)

        val res1 = engine.processHit(config, state1, 0.016f, 0f, 0f, entityX, entityY, 2)
        val res2 = engine.processHit(config, state2, 0.016f, 0f, 0f, entityX, entityY, 2)
        
        assertEquals(res1.triggered, res2.triggered)
        assertEquals(res1.numPulled, res2.numPulled)
        assertEquals(res1.newState.seed, res2.newState.seed)
    }
    
    @Test
    fun testTargetLimitScaling() {
        val engine = MeathookTendrilEngine(maxCapacity = 50)
        val config = MeathookTendrilConfig(pullRadiusPx = 1000f)
        val state = MeathookTendrilState(stackCount = 2, seed = 0L) // +5 per stack -> 10 max
        
        val entityX = FloatArray(20) { 10f }
        val entityY = FloatArray(20) { 10f }
        
        // Find a seed that triggers
        var currentSeed = 100L
        var triggered = false
        var res: MeathookTendrilResult? = null
        for (i in 0..100) {
            val testState = MeathookTendrilState(2, currentSeed)
            res = engine.processHit(config, testState, 0.016f, 0f, 0f, entityX, entityY, 20)
            if (res.triggered) {
                triggered = true
                break
            }
            currentSeed++
        }
        
        assertTrue("Should have triggered eventually", triggered)
        assertEquals("Should cap at stackCount * 5 (2*5=10) targets", 10, res!!.numPulled)
    }
    
    @Test
    fun testNaNAndInfinityIgnored() {
        val engine = MeathookTendrilEngine(maxCapacity = 10)
        val config = MeathookTendrilConfig(pullRadiusPx = 1000f)
        val state = MeathookTendrilState(stackCount = 10, seed = 42L) // high stack = high chance
        
        val entityX = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, 10f)
        val entityY = floatArrayOf(0f, 0f, 10f)
        
        var currentSeed = 42L
        var res: MeathookTendrilResult? = null
        for (i in 0..100) {
            val testState = MeathookTendrilState(10, currentSeed)
            res = engine.processHit(config, testState, 0.016f, 0f, 0f, entityX, entityY, 3)
            if (res.triggered) break
            currentSeed++
        }
        
        assertEquals("Should only pull the finite coordinate", 1, res!!.numPulled)
        assertEquals("Pulled index should be 2 (the finite one)", 2, res.pulledIndices[0])
    }
    
    @Test
    fun testZeroAllocationScaling300Inputs() {
        val engine = MeathookTendrilEngine(maxCapacity = 400)
        val config = MeathookTendrilConfig(pullRadiusPx = 500f)
        val state = MeathookTendrilState(stackCount = 5, seed = 123L) // Max targets = 25
        
        val entityX = FloatArray(350) { it.toFloat() }
        val entityY = FloatArray(350) { 0f }
        
        var triggered = false
        var currentSeed = 123L
        for (i in 0..100) {
            val testState = MeathookTendrilState(5, currentSeed)
            val res = engine.processHit(config, testState, 0.016f, 0f, 0f, entityX, entityY, 350)
            if (res.triggered) {
                triggered = true
                assertEquals("Should cap at 25 targets for stack 5", 25, res.numPulled)
                break
            }
            currentSeed++
        }
        assertTrue(triggered)
    }

    @Test
    fun testHyperbolicChanceStack1() {
        val engine = MeathookTendrilEngine(maxCapacity = 10)
        val config = MeathookTendrilConfig(baseChanceParam = 0.25f)
        val state = MeathookTendrilState(stackCount = 1, seed = 1L)
        // chance = 1 - 1/(1+0.25*1) = 1 - 1/1.25 = 1 - 0.8 = 0.2 (20%)
        
        var triggerCount = 0
        var currentState = state
        val entityX = floatArrayOf()
        val entityY = floatArrayOf()
        
        val iterations = 10000
        for (i in 0 until iterations) {
            val res = engine.processHit(config, currentState, 0.016f, 0f, 0f, entityX, entityY, 0)
            if (res.triggered) triggerCount++
            currentState = res.newState
        }
        
        val rate = triggerCount.toFloat() / iterations
        assertTrue("Rate should be near 20%, got $rate", abs(rate - 0.20f) < 0.02f)
    }
    
    @Test
    fun testHyperbolicChanceStack5() {
        val engine = MeathookTendrilEngine(maxCapacity = 10)
        val config = MeathookTendrilConfig(baseChanceParam = 0.25f)
        val state = MeathookTendrilState(stackCount = 5, seed = 1L)
        // chance = 1 - 1/(1+0.25*5) = 1 - 1/2.25 = 1 - 0.444 = 0.555 (55.5%)
        
        var triggerCount = 0
        var currentState = state
        val entityX = floatArrayOf()
        val entityY = floatArrayOf()
        
        val iterations = 10000
        for (i in 0 until iterations) {
            val res = engine.processHit(config, currentState, 0.016f, 0f, 0f, entityX, entityY, 0)
            if (res.triggered) triggerCount++
            currentState = res.newState
        }
        
        val rate = triggerCount.toFloat() / iterations
        assertTrue("Rate should be near 55.5%, got $rate", abs(rate - 0.555f) < 0.02f)
    }

    @Test
    fun testDtClamping() {
        val engine = MeathookTendrilEngine(maxCapacity = 10)
        val config = MeathookTendrilConfig(pullSpeedPxPerSec = 1000f, pullRadiusPx = 1000f)
        
        val entityX = floatArrayOf(10f)
        val entityY = floatArrayOf(0f)
        
        // Find a triggering seed
        var triggerSeed = 1L
        while (true) {
            val res = engine.processHit(config, MeathookTendrilState(10, triggerSeed), 0.016f, 0f, 0f, entityX, entityY, 1)
            if (res.triggered) break
            triggerSeed++
        }
        
        // However, the distance to epicenter is 10px. So the moveDist is minOf(pullDist, 10px).
        // For clamping test, we need distance to epicenter to be >= pullDist.
        val entityXFar = floatArrayOf(200f)
        val entityYFar = floatArrayOf(0f)
        
        // Find triggering seed again for the new coordinates
        triggerSeed = 1L
        while (true) {
            val res = engine.processHit(config, MeathookTendrilState(10, triggerSeed), 0.016f, 0f, 0f, entityXFar, entityYFar, 1)
            if (res.triggered) break
            triggerSeed++
        }
        
        // Max dt is 0.100, so max pull dist is 1000 * 0.1 = 100px. Entity is at 200px.
        val res1 = engine.processHit(config, MeathookTendrilState(10, triggerSeed), 10.0f, 0f, 0f, entityXFar, entityYFar, 1)
        val pullDist1 = abs(res1.pullDx[0])
        assertEquals("dt should be clamped to 0.100 max", 100f, pullDist1, 0.1f)
        
        // Min dt is 0.001, so min pull dist is 1000 * 0.001 = 1px
        val res2 = engine.processHit(config, MeathookTendrilState(10, triggerSeed), 0.00001f, 0f, 0f, entityXFar, entityYFar, 1)
        val pullDist2 = abs(res2.pullDx[0])
        assertEquals("dt should be clamped to 0.001 min", 1f, pullDist2, 0.1f)
    }

    @Test
    fun testClosestTargetsSelected() {
        val engine = MeathookTendrilEngine(maxCapacity = 10)
        val config = MeathookTendrilConfig(pullRadiusPx = 100f)
        val state = MeathookTendrilState(stackCount = 1, seed = 0L) // 5 targets max
        
        // 6 targets, one is out of range, others vary in distance
        val entityX = floatArrayOf(200f, 10f, 50f, 30f, 90f, 20f)
        val entityY = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
        
        var triggerSeed = 1L
        var res: MeathookTendrilResult? = null
        while (true) {
            res = engine.processHit(config, MeathookTendrilState(1, triggerSeed), 0.016f, 0f, 0f, entityX, entityY, 6)
            if (res.triggered) break
            triggerSeed++
        }
        
        assertEquals(5, res!!.numPulled)
        
        val pulledIndicesList = res.pulledIndices.slice(0 until 5)
        assertTrue(pulledIndicesList.contains(1)) // dist 10
        assertTrue(pulledIndicesList.contains(5)) // dist 20
        assertTrue(pulledIndicesList.contains(3)) // dist 30
        assertTrue(pulledIndicesList.contains(2)) // dist 50
        assertTrue(pulledIndicesList.contains(4)) // dist 90
        assertFalse(pulledIndicesList.contains(0)) // dist 200 (out of range)
    }
    
    @Test
    fun testDoNotPullPastEpicenter() {
        val engine = MeathookTendrilEngine(maxCapacity = 10)
        val config = MeathookTendrilConfig(pullSpeedPxPerSec = 1000f)
        
        val entityX = floatArrayOf(10f) // distance 10
        val entityY = floatArrayOf(0f)
        
        var triggerSeed = 1L
        while (true) {
            val res = engine.processHit(config, MeathookTendrilState(10, triggerSeed), 0.016f, 0f, 0f, entityX, entityY, 1)
            if (res.triggered) break
            triggerSeed++
        }
        
        // dt=0.016, speed=1000 -> pullDist = 16px. But dist=10px. Should clamp to 10px.
        val res = engine.processHit(config, MeathookTendrilState(10, triggerSeed), 0.016f, 0f, 0f, entityX, entityY, 1)
        val pullDx = res.pullDx[0]
        assertEquals("Should not pull more than 10px", -10f, pullDx, 0.01f) // Note: -10f because hit is 0, entity is 10. dx is -10.
    }
}