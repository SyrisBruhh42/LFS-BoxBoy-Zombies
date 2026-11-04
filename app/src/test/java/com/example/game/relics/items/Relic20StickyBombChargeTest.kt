package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test

class Relic20StickyBombChargeTest {

    @Test
    fun testInitialStateAndConstraints() {
        val state = StickyBombChargeState(stackCount = 1)
        assertEquals(1, state.stackCount)
        assertEquals(0, state.activeBombCount)
        assertFalse(state.canBePurchasedAtVending)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidStackCountThrows() {
        StickyBombChargeState(stackCount = 0)
    }

    @Test
    fun testSerializationRoundtrip() {
        val config = StickyBombChargeConfig(baseDamageMultiplier = 2.0f, maxTrackedBombs = 50)
        val configMap = config.toMap()
        val restoredConfig = StickyBombChargeConfig.fromMap(configMap)
        assertEquals(config.baseDamageMultiplier, restoredConfig.baseDamageMultiplier)
        assertEquals(config.maxTrackedBombs, restoredConfig.maxTrackedBombs)

        val state = StickyBombChargeState(stackCount = 2)
        state.bombEntityIds[0] = 101
        state.bombXs[0] = 10f
        state.bombYs[0] = 20f
        state.bombRemainingFuses[0] = 1.0f
        state.bombBaseDamages[0] = 50
        state.activeBombCount = 1
        state.stateChecksum = Relic20StickyBombChargeEngine.computeChecksum(state)

        val stateMap = state.toMap()
        val restoredState = StickyBombChargeState.fromMap(stateMap)

        assertEquals(state.stackCount, restoredState.stackCount)
        assertEquals(state.activeBombCount, restoredState.activeBombCount)
        assertEquals(state.bombEntityIds[0], restoredState.bombEntityIds[0])
        assertEquals(state.bombXs[0], restoredState.bombXs[0])
        assertEquals(state.bombYs[0], restoredState.bombYs[0])
        assertEquals(state.bombRemainingFuses[0], restoredState.bombRemainingFuses[0])
        assertEquals(state.bombBaseDamages[0], restoredState.bombBaseDamages[0])
        assertEquals(state.stateChecksum, restoredState.stateChecksum)
        
        assertEquals(state, restoredState)
        assertEquals(state.hashCode(), restoredState.hashCode())
    }

    @Test
    fun testHitDoesNotProcIfChanceMisses() {
        val config = StickyBombChargeConfig(baseProcChance = 0.0f, procChancePerStack = 0.0f) // 0% chance
        val state = StickyBombChargeState(stackCount = 1)
        Relic20StickyBombChargeEngine.registerHit(config, state, 1, 0f, 0f, 100, 12345L)
        assertEquals(0, state.activeBombCount)
    }

    @Test
    fun testHitProcsIfChanceHits() {
        val config = StickyBombChargeConfig(baseProcChance = 1.0f, procChancePerStack = 0.0f) // 100% chance
        val state = StickyBombChargeState(stackCount = 1)
        Relic20StickyBombChargeEngine.registerHit(config, state, 1, 5f, 10f, 100, 12345L)
        
        assertEquals(1, state.activeBombCount)
        assertEquals(1, state.bombEntityIds[0])
        assertEquals(5f, state.bombXs[0])
        assertEquals(10f, state.bombYs[0])
        assertEquals(config.fuseTimeSeconds, state.bombRemainingFuses[0])
        assertEquals((100 * config.baseDamageMultiplier).toInt(), state.bombBaseDamages[0])
    }

    @Test
    fun testStackScalingProcChance() {
        // base 5%, +5% per stack -> 20 stacks = 100% chance
        val config = StickyBombChargeConfig(baseProcChance = 0.05f, procChancePerStack = 0.05f)
        val state = StickyBombChargeState(stackCount = 20)
        Relic20StickyBombChargeEngine.registerHit(config, state, 1, 0f, 0f, 100, 12345L)
        assertEquals(1, state.activeBombCount)
    }

    @Test
    fun testTickReducesFuseTime() {
        val config = StickyBombChargeConfig(baseProcChance = 1.0f)
        val state = StickyBombChargeState(stackCount = 1)
        Relic20StickyBombChargeEngine.registerHit(config, state, 1, 0f, 0f, 100, 12345L)
        
        val initialFuse = state.bombRemainingFuses[0]
        val dt = 0.1f
        
        val outXs = FloatArray(10)
        val outYs = FloatArray(10)
        val outRs = FloatArray(10)
        val outDs = IntArray(10)
        
        val count = Relic20StickyBombChargeEngine.tick(
            config, state, dt,
            IntArray(0), FloatArray(0), FloatArray(0), 0,
            outXs, outYs, outRs, outDs
        )
        
        assertEquals(0, count)
        assertEquals(initialFuse - dt, state.bombRemainingFuses[0])
    }

    @Test
    fun testTickExplodesWhenFuseCompletes() {
        val config = StickyBombChargeConfig(baseProcChance = 1.0f, fuseTimeSeconds = 0.5f)
        val state = StickyBombChargeState(stackCount = 1)
        Relic20StickyBombChargeEngine.registerHit(config, state, 1, 10f, 20f, 100, 12345L)
        
        val outXs = FloatArray(10)
        val outYs = FloatArray(10)
        val outRs = FloatArray(10)
        val outDs = IntArray(10)
        
        var totalExplosions = 0
        // Need to tick 5 times at 0.1 dt to reach 0.5s fuse
        for (i in 0 until 5) {
            totalExplosions += Relic20StickyBombChargeEngine.tick(
                config, state, 0.1f,
                IntArray(0), FloatArray(0), FloatArray(0), 0,
                outXs, outYs, outRs, outDs
            )
        }

        assertEquals(1, totalExplosions)
        assertEquals(0, state.activeBombCount)
        
        assertEquals(10f, outXs[0])
        assertEquals(20f, outYs[0])
        assertEquals(config.blastRadiusPx, outRs[0])
        assertEquals((100 * config.baseDamageMultiplier).toInt(), outDs[0])
    }

    @Test
    fun testTickUpdatesPositionFromEntity() {
        val config = StickyBombChargeConfig(baseProcChance = 1.0f)
        val state = StickyBombChargeState(stackCount = 1)
        Relic20StickyBombChargeEngine.registerHit(config, state, 99, 0f, 0f, 100, 12345L)
        
        val outXs = FloatArray(10)
        val outYs = FloatArray(10)
        val outRs = FloatArray(10)
        val outDs = IntArray(10)
        
        Relic20StickyBombChargeEngine.tick(
            config, state, 0.05f,
            intArrayOf(99), floatArrayOf(100f), floatArrayOf(200f), 1,
            outXs, outYs, outRs, outDs
        )
        
        assertEquals(1, state.activeBombCount)
        assertEquals(100f, state.bombXs[0])
        assertEquals(200f, state.bombYs[0])
    }

    @Test
    fun testMaxTrackedBombsCap() {
        val config = StickyBombChargeConfig(baseProcChance = 1.0f, maxTrackedBombs = 2)
        val state = StickyBombChargeState(stackCount = 1)
        
        Relic20StickyBombChargeEngine.registerHit(config, state, 1, 0f, 0f, 100, 1L)
        Relic20StickyBombChargeEngine.registerHit(config, state, 2, 0f, 0f, 100, 2L)
        Relic20StickyBombChargeEngine.registerHit(config, state, 3, 0f, 0f, 100, 3L) // Should be ignored
        
        assertEquals(2, state.activeBombCount)
    }

    @Test
    fun testDeterminismSameSeedProducesSameHash() {
        val config = StickyBombChargeConfig(baseProcChance = 0.5f)
        
        val state1 = StickyBombChargeState(stackCount = 1)
        Relic20StickyBombChargeEngine.registerHit(config, state1, 1, 0f, 0f, 100, 42L)
        
        val state2 = StickyBombChargeState(stackCount = 1)
        Relic20StickyBombChargeEngine.registerHit(config, state2, 1, 0f, 0f, 100, 42L)
        
        assertEquals(state1.stateChecksum, state2.stateChecksum)
        assertEquals(state1.activeBombCount, state2.activeBombCount)
    }

    @Test
    fun testNanDtGracefulRecovery() {
        val config = StickyBombChargeConfig(baseProcChance = 1.0f)
        val state = StickyBombChargeState(stackCount = 1)
        Relic20StickyBombChargeEngine.registerHit(config, state, 1, 0f, 0f, 100, 123L)
        
        val initialFuse = state.bombRemainingFuses[0]
        
        val outXs = FloatArray(10)
        val outYs = FloatArray(10)
        val outRs = FloatArray(10)
        val outDs = IntArray(10)
        
        val count = Relic20StickyBombChargeEngine.tick(
            config, state, Float.NaN,
            IntArray(0), FloatArray(0), FloatArray(0), 0,
            outXs, outYs, outRs, outDs
        )
        
        assertEquals(0, count)
        assertEquals(initialFuse, state.bombRemainingFuses[0]) // State unaffected
    }
}
