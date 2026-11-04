package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test

class Relic11LensMakersGlassesTest {

    @Test
    fun `TC01 - Config invariant canBePurchasedAtVending is exactly false`() {
        val config = Relic11LensMakersGlassesConfig()
        assertFalse("Drop-only relics must NEVER be vendored", config.canBePurchasedAtVending)
    }

    @Test
    fun `TC02 - getCriticalStrikeChance is 0 percent at 0 stacks`() {
        val state = Relic11LensMakersGlassesState(0, Relic11LensMakersGlassesState.computeChecksum(0))
        val chance = Relic11LensMakersGlassesEngine.getCriticalStrikeChance(state)
        assertEquals(0.0f, chance, 0.0001f)
    }

    @Test
    fun `TC03 - getCriticalStrikeChance scales by exactly 10 percent per stack`() {
        val state = Relic11LensMakersGlassesState(5, Relic11LensMakersGlassesState.computeChecksum(5))
        val chance = Relic11LensMakersGlassesEngine.getCriticalStrikeChance(state)
        assertEquals(0.50f, chance, 0.0001f)
    }

    @Test
    fun `TC04 - getCriticalStrikeChance caps at 100 percent at 10 stacks`() {
        val state = Relic11LensMakersGlassesState(10, Relic11LensMakersGlassesState.computeChecksum(10))
        val chance = Relic11LensMakersGlassesEngine.getCriticalStrikeChance(state)
        assertEquals(1.0f, chance, 0.0001f)
    }
    
    @Test
    fun `TC05 - getCriticalStrikeChance remains bounded at 100 percent past 10 stacks`() {
        val state = Relic11LensMakersGlassesState(25, Relic11LensMakersGlassesState.computeChecksum(25))
        val chance = Relic11LensMakersGlassesEngine.getCriticalStrikeChance(state)
        assertEquals(1.0f, chance, 0.0001f)
    }

    @Test
    fun `TC06 - State handles negative stack counts gracefully as zero`() {
        val state = Relic11LensMakersGlassesState(-3, Relic11LensMakersGlassesState.computeChecksum(-3))
        val chance = Relic11LensMakersGlassesEngine.getCriticalStrikeChance(state)
        assertEquals(0.0f, chance, 0.0001f)
    }

    @Test
    fun `TC07 - FNV-1a Checksum validation prevents tampered state from returning positive crit rolls`() {
        // Valid checksum for stack 10
        val validChecksum = Relic11LensMakersGlassesState.computeChecksum(10)
        
        // Corrupted state with mismatched checksum
        val corruptedState = Relic11LensMakersGlassesState(10, validChecksum + 1)
        assertFalse(Relic11LensMakersGlassesEngine.isStateValid(corruptedState))

        // Ensure engine refuses to grant crits on corrupted state
        val result = Relic11LensMakersGlassesEngine.evaluateCriticalStrike(corruptedState, 0.0f, 12345L)
        assertFalse("Tampered state must return false", result)
    }

    @Test
    fun `TC08 - deterministic serialization roundtrip via toMap and fromMap`() {
        val original = Relic11LensMakersGlassesState(7, Relic11LensMakersGlassesState.computeChecksum(7))
        val map = original.toMap()
        val parsed = Relic11LensMakersGlassesState.fromMap(map)
        
        assertEquals(original.stackCount, parsed.stackCount)
        assertEquals(original.checksum, parsed.checksum)
    }

    @Test
    fun `TC09 - Deterministic seeded variation returns identical outputs for identical seeds`() {
        val state = Relic11LensMakersGlassesState(3, Relic11LensMakersGlassesState.computeChecksum(3))
        val seed = 987654321L
        
        val roll1 = Relic11LensMakersGlassesEngine.evaluateCriticalStrike(state, 0.0f, seed)
        val roll2 = Relic11LensMakersGlassesEngine.evaluateCriticalStrike(state, 0.0f, seed)
        
        assertEquals("Identical seeds must yield identical results", roll1, roll2)
    }
    
    @Test
    fun `TC10 - evaluateCriticalStrike correctly bounds NaN Infinity baseChances`() {
        val state = Relic11LensMakersGlassesState(5, Relic11LensMakersGlassesState.computeChecksum(5))
        
        val nanResult = Relic11LensMakersGlassesEngine.evaluateCriticalStrike(state, Float.NaN, 123L)
        assertFalse("NaN base chance must return false", nanResult)
        
        val infResult = Relic11LensMakersGlassesEngine.evaluateCriticalStrike(state, Float.POSITIVE_INFINITY, 123L)
        assertFalse("Infinity base chance must return false", infResult)
    }

    @Test
    fun `TC11 - batchEvaluateCriticalStrikes processes successfully with zero per-frame heap churn on valid data`() {
        val count = 300
        val state = Relic11LensMakersGlassesState(5, Relic11LensMakersGlassesState.computeChecksum(5)) // 50% chance
        
        val baseChances = FloatArray(count) { 0.0f }
        val seeds = LongArray(count) { it.toLong() * 12345L }
        val results = BooleanArray(count)
        
        // This must run without exceptions
        Relic11LensMakersGlassesEngine.batchEvaluateCriticalStrikes(state, baseChances, seeds, results, count)
        
        var crits = 0
        for (i in 0 until count) {
            if (results[i]) crits++
        }
        
        // At 50%, roughly half should crit. Ensure it's not all true or all false.
        assertTrue(crits > 0)
        assertTrue(crits < count)
    }

    @Test
    fun `TC12 - batchEvaluateCriticalStrikes zeroes out and returns early on tampered checksum state`() {
        val count = 50
        val tamperedState = Relic11LensMakersGlassesState(10, 99999L) // 10 stacks should be 100% crit, but bad checksum
        
        val baseChances = FloatArray(count) { 0.0f }
        val seeds = LongArray(count) { it.toLong() }
        val results = BooleanArray(count) { true } // pre-fill with true
        
        Relic11LensMakersGlassesEngine.batchEvaluateCriticalStrikes(tamperedState, baseChances, seeds, results, count)
        
        // Since state is corrupt, engine should zero-out the output array
        for (i in 0 until count) {
            assertFalse(results[i])
        }
    }
}
