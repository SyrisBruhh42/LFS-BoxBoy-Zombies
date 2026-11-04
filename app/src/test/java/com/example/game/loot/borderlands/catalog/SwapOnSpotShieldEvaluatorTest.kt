package com.example.game.loot.borderlands.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SwapOnSpotShieldEvaluatorTest {

    private val baseEquipped = ShieldStats(
        capacity = 1000,
        rechargeRate = 100,
        rechargeDelay = 2.5f,
        gimmickValue = 50
    )

    @Test
    fun `Test 1 Nominal gameplay - upgrade scenario`() {
        val candidate = ShieldStats(
            capacity = 1200, // +200
            rechargeRate = 120, // +20
            rechargeDelay = 2.0f, // -0.5 (better)
            gimmickValue = 60 // +10
        )
        
        val result = SwapOnSpotShieldEvaluator.computeDeltas(baseEquipped, candidate)
        assertEquals(200, result.capacityDelta)
        assertEquals(20, result.rechargeRateDelta)
        assertEquals(-0.5f, result.rechargeDelayDelta, 0.001f)
        assertEquals(10, result.gimmickDelta)
        assertTrue(result.isUpgrade)
    }

    @Test
    fun `Test 2 Nominal gameplay - downgrade scenario`() {
        val candidate = ShieldStats(
            capacity = 800, // -200
            rechargeRate = 80, // -20
            rechargeDelay = 3.0f, // +0.5 (worse)
            gimmickValue = 40 // -10
        )
        
        val result = SwapOnSpotShieldEvaluator.computeDeltas(baseEquipped, candidate)
        assertEquals(-200, result.capacityDelta)
        assertEquals(-20, result.rechargeRateDelta)
        assertEquals(0.5f, result.rechargeDelayDelta, 0.001f)
        assertEquals(-10, result.gimmickDelta)
        assertFalse(result.isUpgrade)
    }

    @Test
    fun `Test 3 Nominal gameplay - identical gimmick comparison vs different gimmick comparison`() {
        val candidateSameGimmick = ShieldStats(
            capacity = 1000,
            rechargeRate = 100,
            rechargeDelay = 2.5f,
            gimmickValue = 50
        )
        val resultSame = SwapOnSpotShieldEvaluator.computeDeltas(baseEquipped, candidateSameGimmick, seed = 12345L)
        assertEquals(0, resultSame.gimmickDelta) // No jitter if base delta is 0
        
        val candidateDiffGimmick = ShieldStats(
            capacity = 1000,
            rechargeRate = 100,
            rechargeDelay = 2.5f,
            gimmickValue = 51 // Delta = 1
        )
        val resultDiff = SwapOnSpotShieldEvaluator.computeDeltas(baseEquipped, candidateDiffGimmick, seed = 12345L)
        assertTrue(resultDiff.gimmickDelta != 1) // Expected to be jittered due to seed
    }

    @Test
    fun `Test 4 Determinism proofs - identical seeds and inputs generate identical results`() {
        val candidate = ShieldStats(1100, 110, 2.0f, 60)
        val seed = 987654321L
        
        val result1 = SwapOnSpotShieldEvaluator.computeDeltas(baseEquipped, candidate, seed)
        val result2 = SwapOnSpotShieldEvaluator.computeDeltas(baseEquipped, candidate, seed)
        
        assertEquals(result1.gimmickDelta, result2.gimmickDelta)
        assertEquals(result1.computeChecksum(), result2.computeChecksum())
    }

    @Test
    fun `Test 5 Boundary adversarial - zero inputs`() {
        val zeroEquipped = ShieldStats(0, 0, 0f, 0)
        val zeroCandidate = ShieldStats(0, 0, 0f, 0)
        
        val result = SwapOnSpotShieldEvaluator.computeDeltas(zeroEquipped, zeroCandidate)
        assertEquals(0, result.capacityDelta)
        assertEquals(0, result.rechargeRateDelta)
        assertEquals(0f, result.rechargeDelayDelta, 0.001f)
        assertEquals(0, result.gimmickDelta)
        assertFalse(result.isUpgrade)
    }

    @Test
    fun `Test 6 Boundary adversarial - negative inputs`() {
        val negEquipped = ShieldStats(-100, -10, -1.0f, -5)
        val negCandidate = ShieldStats(-50, -5, -2.0f, -2) // "better" negatives
        
        val result = SwapOnSpotShieldEvaluator.computeDeltas(negEquipped, negCandidate)
        assertEquals(50, result.capacityDelta)
        assertEquals(5, result.rechargeRateDelta)
        assertEquals(-1.0f, result.rechargeDelayDelta, 0.001f)
        assertEquals(3, result.gimmickDelta)
        assertTrue(result.isUpgrade)
    }

    @Test
    fun `Test 7 Boundary adversarial - NaN Infinity inputs recovery`() {
        // Step presentation dtSeconds
        val result = ShieldSwapComparisonResult(10, 10, 1.0f, 10, true, 0f)
        
        SwapOnSpotShieldEvaluator.stepPresentation(result, Float.NaN)
        assertTrue(result.presentationPulse > 0f) // Coerced to 0.001f
        
        SwapOnSpotShieldEvaluator.stepPresentation(result, Float.POSITIVE_INFINITY)
        // Should also coerce to 0.001f
        
        val prevPulse = result.presentationPulse
        SwapOnSpotShieldEvaluator.stepPresentation(result, Float.NEGATIVE_INFINITY)
        assertTrue(result.presentationPulse > prevPulse) 
        
        // Evaluate Swap with NaN position
        val swapResult = SwapOnSpotShieldEvaluator.evaluateSwap(
            baseEquipped, baseEquipped, 
            playerX = Float.NaN, playerY = 0f, 
            candidateX = 0f, candidateY = 0f
        )
        assertNotNull(swapResult) // Treated as 0f, 0f so distance is 0, which is < MAX
    }

    @Test
    fun `Test 8 Maximum capacity stress testing - batch evaluation`() {
        val count = 300
        val candidates = Array(count) { baseEquipped.copy(capacity = baseEquipped.capacity + it) }
        val cx = FloatArray(count) { 0f }
        val cy = FloatArray(count) { 0f }
        val seeds = LongArray(count) { it.toLong() }
        val outResults = Array<ShieldSwapComparisonResult?>(count) { null }
        
        val px = 0f
        val py = 0f
        
        val validCount = SwapOnSpotShieldEvaluator.evaluateBatch(
            baseEquipped, px, py, candidates, cx, cy, seeds, count, outResults
        )
        
        assertEquals(count, validCount)
        assertEquals(count, outResults.filterNotNull().size)
        
        // Last one should have 299 cap delta
        assertEquals(299, outResults[count - 1]?.capacityDelta)
    }

    @Test
    fun `Test 9 Network max packet cap limits - bounds iteration limit`() {
        // Mismatched array sizes, should limit to the minimum
        val count = 500
        val candidates = Array(300) { baseEquipped }
        val cx = FloatArray(200) { 0f }
        val cy = FloatArray(400) { 0f }
        val seeds = LongArray(500) { 0L }
        val outResults = Array<ShieldSwapComparisonResult?>(600) { null }
        
        val validCount = SwapOnSpotShieldEvaluator.evaluateBatch(
            baseEquipped, 0f, 0f, candidates, cx, cy, seeds, count, outResults
        )
        
        // Should be limited by cx size (200)
        assertEquals(200, validCount)
    }

    @Test
    fun `Test 10 Serialization parity tests - ShieldStats toMap fromMap`() {
        val stats = ShieldStats(1234, 56, 7.8f, 90)
        val map = stats.toMap()
        val restored = ShieldStats.fromMap(map)
        
        assertEquals(stats.capacity, restored.capacity)
        assertEquals(stats.rechargeRate, restored.rechargeRate)
        assertEquals(stats.rechargeDelay, restored.rechargeDelay, 0.001f)
        assertEquals(stats.gimmickValue, restored.gimmickValue)
        assertEquals(stats.computeChecksum(), restored.computeChecksum())
    }

    @Test
    fun `Test 11 Serialization parity tests - ShieldSwapComparisonResult toMap fromMap`() {
        val result = ShieldSwapComparisonResult(100, -20, 0.5f, 10, true, 1.2f)
        val map = result.toMap()
        val restored = ShieldSwapComparisonResult.fromMap(map)
        
        assertEquals(result.capacityDelta, restored.capacityDelta)
        assertEquals(result.rechargeRateDelta, restored.rechargeRateDelta)
        assertEquals(result.rechargeDelayDelta, restored.rechargeDelayDelta, 0.001f)
        assertEquals(result.gimmickDelta, restored.gimmickDelta)
        assertEquals(result.isUpgrade, restored.isUpgrade)
        assertEquals(result.presentationPulse, restored.presentationPulse, 0.001f)
        assertEquals(result.computeChecksum(), restored.computeChecksum())
    }

    @Test
    fun `Test 12 Spatial distance validation`() {
        val maxDist = SwapOnSpotShieldEvaluator.MAX_INTERACTION_RADIUS
        
        // Just within range
        val resultIn = SwapOnSpotShieldEvaluator.evaluateSwap(
            baseEquipped, baseEquipped,
            playerX = 0f, playerY = 0f,
            candidateX = maxDist - 0.1f, candidateY = 0f
        )
        assertNotNull(resultIn)
        
        // Just out of range
        val resultOut = SwapOnSpotShieldEvaluator.evaluateSwap(
            baseEquipped, baseEquipped,
            playerX = 0f, playerY = 0f,
            candidateX = maxDist + 0.1f, candidateY = 0f
        )
        assertNull(resultOut)
    }
}
