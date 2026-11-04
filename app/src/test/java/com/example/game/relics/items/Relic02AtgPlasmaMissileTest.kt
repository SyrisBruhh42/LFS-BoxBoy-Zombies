package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test

class Relic02AtgPlasmaMissileTest {

    @Test
    fun testNominalProcSuccessAndDamageCalculation() {
        val config = AtgPlasmaMissileConfig()
        val state = AtgPlasmaMissileState(stackCount = 2) // +600% TOTAL damage
        
        // Random < 0.10f should proc
        val result = Relic02AtgPlasmaMissileEngine.processHit(config, state, 0.05f, 100f)
        
        assertTrue("Missile should proc", result.didProc)
        // 100 * 3.0f * 2 = 600f
        assertEquals(600f, result.missileDamage, 0.001f)
    }

    @Test
    fun testNominalProcFailure() {
        val config = AtgPlasmaMissileConfig()
        val state = AtgPlasmaMissileState(stackCount = 1)
        
        // Random >= 0.10f should fail
        val result = Relic02AtgPlasmaMissileEngine.processHit(config, state, 0.15f, 100f)
        
        assertFalse("Missile should not proc", result.didProc)
        assertEquals(0f, result.missileDamage, 0.001f)
    }

    @Test
    fun testAdversarialInputsNaNAndInfinity() {
        val config = AtgPlasmaMissileConfig()
        val state = AtgPlasmaMissileState(stackCount = 1)
        
        // Test NaN random
        var result = Relic02AtgPlasmaMissileEngine.processHit(config, state, Float.NaN, 100f)
        assertTrue("NaN random should default to 0f, which is < 0.10f, so it should proc", result.didProc)
        
        // Test Infinity random
        result = Relic02AtgPlasmaMissileEngine.processHit(config, state, Float.POSITIVE_INFINITY, 100f)
        assertTrue("Infinity random defaults to 0f, so it should proc", result.didProc)

        // Test NaN damage
        result = Relic02AtgPlasmaMissileEngine.processHit(config, state, 0.05f, Float.NaN)
        assertEquals(0f, result.missileDamage, 0.001f)
        
        // Test Negative damage
        result = Relic02AtgPlasmaMissileEngine.processHit(config, state, 0.05f, -50f)
        assertEquals(0f, result.missileDamage, 0.001f)
    }

    @Test
    fun testSerializationDeserializationRoundtrip() {
        val originalState = AtgPlasmaMissileState(stackCount = 5)
        val map = originalState.toMap()
        
        val parsedState = AtgPlasmaMissileState.fromMap(map)
        
        assertEquals(originalState.stackCount, parsedState.stackCount)
        assertEquals(originalState, parsedState)
    }

    @Test
    fun testDeserializationFallback() {
        val map = mapOf<String, Any>("stackCount" to "invalid_int")
        val parsedState = AtgPlasmaMissileState.fromMap(map)
        
        assertEquals("Should fallback to minimum stack count of 1", 1, parsedState.stackCount)
        
        val emptyMap = emptyMap<String, Any>()
        val parsedEmpty = AtgPlasmaMissileState.fromMap(emptyMap)
        assertEquals("Empty map should default to 1", 1, parsedEmpty.stackCount)
    }

    @Test
    fun testChecksumDeterminism() {
        val state1 = AtgPlasmaMissileState(stackCount = 3)
        val state2 = AtgPlasmaMissileState(stackCount = 3)
        val state3 = AtgPlasmaMissileState(stackCount = 4)
        
        val checksum1 = Relic02AtgPlasmaMissileEngine.computeChecksum(state1)
        val checksum2 = Relic02AtgPlasmaMissileEngine.computeChecksum(state2)
        val checksum3 = Relic02AtgPlasmaMissileEngine.computeChecksum(state3)
        
        assertEquals("Identical states must have same checksum", checksum1, checksum2)
        assertNotEquals("Different states must have different checksums", checksum1, checksum3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testStateConstraintNegativeStack() {
        AtgPlasmaMissileState(stackCount = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConfigInvariantPurchaseable() {
        AtgPlasmaMissileConfig(canBePurchasedAtVending = true)
    }
    
    @Test
    fun testHomingTargetNominal() {
        val xs = floatArrayOf(10f, 50f, 100f)
        val ys = floatArrayOf(10f, 50f, 100f)
        val alive = booleanArrayOf(true, true, true)
        
        val targetIndex = Relic02AtgPlasmaMissileEngine.findHomingTarget(
            missileX = 0f, missileY = 0f,
            targetXs = xs, targetYs = ys, targetAlive = alive,
            targetCount = 3, maxRange = 200f
        )
        
        assertEquals("Closest target is at index 0", 0, targetIndex)
    }

    @Test
    fun testHomingTargetIgnoresDead() {
        val xs = floatArrayOf(10f, 50f, 100f)
        val ys = floatArrayOf(10f, 50f, 100f)
        val alive = booleanArrayOf(false, true, true) // Closest is dead
        
        val targetIndex = Relic02AtgPlasmaMissileEngine.findHomingTarget(
            missileX = 0f, missileY = 0f,
            targetXs = xs, targetYs = ys, targetAlive = alive,
            targetCount = 3, maxRange = 200f
        )
        
        assertEquals("Closest ALIVE target is at index 1", 1, targetIndex)
    }

    @Test
    fun testHomingTargetMaxRange() {
        val xs = floatArrayOf(100f)
        val ys = floatArrayOf(100f)
        val alive = booleanArrayOf(true)
        
        val targetIndex = Relic02AtgPlasmaMissileEngine.findHomingTarget(
            missileX = 0f, missileY = 0f,
            targetXs = xs, targetYs = ys, targetAlive = alive,
            targetCount = 1, maxRange = 50f // Range too small (dist is ~141)
        )
        
        assertEquals("No target within range", -1, targetIndex)
    }

    @Test
    fun testHomingTargetAdversarialNaNs() {
        val xs = floatArrayOf(Float.NaN, 50f)
        val ys = floatArrayOf(Float.NaN, 50f)
        val alive = booleanArrayOf(true, true)
        
        // Target 0 has NaN, it should default to 0f, 0f
        val targetIndex = Relic02AtgPlasmaMissileEngine.findHomingTarget(
            missileX = 10f, missileY = 10f,
            targetXs = xs, targetYs = ys, targetAlive = alive,
            targetCount = 2, maxRange = 200f
        )
        
        // Dist to (0,0) is ~14.1, Dist to (50,50) is ~56.5
        assertEquals("Target 0 NaN recovers to 0f,0f making it closest", 0, targetIndex)
        
        // Test NaN in missile coords
        val targetIndexNaNMissile = Relic02AtgPlasmaMissileEngine.findHomingTarget(
            missileX = Float.NaN, missileY = Float.NaN,
            targetXs = xs, targetYs = ys, targetAlive = alive,
            targetCount = 2, maxRange = 200f
        )
        // NaN missile -> 0,0. Target 0 is 0,0. So dist is 0.
        assertEquals("NaN missile recovers to 0f,0f", 0, targetIndexNaNMissile)
    }

    @Test
    fun testHomingTargetStressLoad() {
        val stressCount = 300
        val xs = FloatArray(stressCount)
        val ys = FloatArray(stressCount)
        val alive = BooleanArray(stressCount)
        
        for (i in 0 until stressCount) {
            xs[i] = i * 10f
            ys[i] = i * 10f
            alive[i] = (i % 2 == 0) // Only evens are alive
        }
        
        // Let's place the missile near index 150 (which is alive)
        // Coords: 1500, 1500
        val targetIndex = Relic02AtgPlasmaMissileEngine.findHomingTarget(
            missileX = 1500f, missileY = 1500f,
            targetXs = xs, targetYs = ys, targetAlive = alive,
            targetCount = stressCount, maxRange = 200f
        )
        
        assertEquals("Should find the exact target at index 150", 150, targetIndex)
        
        // Let's place it near 151 (which is dead). Next closest alive are 150 or 152.
        val targetIndexOdd = Relic02AtgPlasmaMissileEngine.findHomingTarget(
            missileX = 1510f, missileY = 1510f,
            targetXs = xs, targetYs = ys, targetAlive = alive,
            targetCount = stressCount, maxRange = 200f
        )
        
        // Loop checks 0 to n. 150 will be closest first. 152 will have the same dist Sq.
        // It keeps the first closest.
        assertEquals("Should find target 150", 150, targetIndexOdd)
    }

    @Test
    fun testHomingTargetCountClamp() {
        val xs = floatArrayOf(10f)
        val ys = floatArrayOf(10f)
        val alive = booleanArrayOf(true)
        
        // Target count is larger than arrays
        val targetIndex = Relic02AtgPlasmaMissileEngine.findHomingTarget(
            missileX = 0f, missileY = 0f,
            targetXs = xs, targetYs = ys, targetAlive = alive,
            targetCount = 5, maxRange = 200f
        )
        
        assertEquals("Should correctly clamp targetCount to array sizes", 0, targetIndex)
    }
}
