package com.example.game.economy.vendors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaceableBarricadeWallBlockTest {

    // GROUP 1: Nominal Placement, Damage Application, and Step (DT) Regeneration Tests

    @Test
    fun testInitializationAndDamage() {
        val config = PlaceableBarricadeConfig("w1", BarricadeBlockType.CONCRETE, 50f, 50f)
        val state = PlaceableBarricadeState(10f, 10f, config.type.maxHp)

        PlaceableBarricadeWallBlockEngine.applyDamage(config, state, 500)
        assertEquals(1000, state.currentHp)
        assertEquals(0f, state.timeSinceLastDamageSec, 0.001f)
    }

    @Test
    fun testDamageClampsAtZero() {
        val config = PlaceableBarricadeConfig("w2", BarricadeBlockType.TITANIUM, 50f, 50f)
        val state = PlaceableBarricadeState(10f, 10f, config.type.maxHp)

        PlaceableBarricadeWallBlockEngine.applyDamage(config, state, 5000)
        assertEquals(0, state.currentHp)
    }

    @Test
    fun testEnergyRegeneration() {
        val config = PlaceableBarricadeConfig("e1", BarricadeBlockType.HARD_LIGHT_ENERGY, 50f, 50f)
        val state = PlaceableBarricadeState(10f, 10f, config.type.maxHp)

        PlaceableBarricadeWallBlockEngine.applyDamage(config, state, 400)
        assertEquals(400, state.currentHp)

        // Step up to delay
        PlaceableBarricadeWallBlockEngine.step(config, state, 3.0f)
        assertEquals(3.0f, state.timeSinceLastDamageSec, 0.001f)
        assertEquals(400, state.currentHp) // Hasn't regenerated yet this frame

        // Next step should regenerate
        PlaceableBarricadeWallBlockEngine.step(config, state, 0.1f)
        assertEquals(410, state.currentHp) // 100 * 0.1
    }

    @Test
    fun testNonEnergyDoesNotRegenerate() {
        val config = PlaceableBarricadeConfig("c1", BarricadeBlockType.CONCRETE, 50f, 50f)
        val state = PlaceableBarricadeState(10f, 10f, config.type.maxHp)

        PlaceableBarricadeWallBlockEngine.applyDamage(config, state, 500)
        PlaceableBarricadeWallBlockEngine.step(config, state, 5.0f)
        assertEquals(1000, state.currentHp) // Concrete doesn't regen
    }

    // GROUP 2: Determinism and Serialization

    @Test
    fun testConfigSerializationParity() {
        val config = PlaceableBarricadeConfig("w1", BarricadeBlockType.TITANIUM, 42.5f, 77.1f)
        val map = config.toMap()
        val restored = PlaceableBarricadeConfig.fromMap(map)
        assertEquals(config, restored)
    }

    @Test
    fun testStateSerializationParity() {
        val state = PlaceableBarricadeState(15.5f, -22.3f, 400, 1.25f)
        val map = state.toMap()
        val restored = PlaceableBarricadeState.fromMap(map)
        assertEquals(state, restored)
    }

    @Test
    fun testChecksumDeterminism() {
        val config = PlaceableBarricadeConfig("b1", BarricadeBlockType.HARD_LIGHT_ENERGY, 100f, 100f)
        val state = PlaceableBarricadeState(50f, 50f, 800)

        val hash1 = PlaceableBarricadeWallBlockEngine.calculateChecksum(config, state)
        val hash2 = PlaceableBarricadeWallBlockEngine.calculateChecksum(config, state)
        assertEquals(hash1, hash2)
        
        PlaceableBarricadeWallBlockEngine.applyDamage(config, state, 100)
        val hash3 = PlaceableBarricadeWallBlockEngine.calculateChecksum(config, state)
        assertNotEquals(hash1, hash3)
    }
    
    @Test
    fun testChecksumCrossPlatformParity() {
        val config = PlaceableBarricadeConfig("b1", BarricadeBlockType.HARD_LIGHT_ENERGY, 100f, 100f)
        val state = PlaceableBarricadeState(50f, 50f, 800)
        val hash = PlaceableBarricadeWallBlockEngine.calculateChecksum(config, state)
        
        val mapState = PlaceableBarricadeState.fromMap(state.toMap())
        val mapConfig = PlaceableBarricadeConfig.fromMap(config.toMap())
        
        val hashFromMap = PlaceableBarricadeWallBlockEngine.calculateChecksum(mapConfig, mapState)
        assertEquals(hash, hashFromMap)
    }

    // GROUP 3: Boundary, Adversarial, and Max-Capacity Stress Tests

    @Test
    fun testDtClampingAndAdversarialValues() {
        val config = PlaceableBarricadeConfig("e2", BarricadeBlockType.HARD_LIGHT_ENERGY, 50f, 50f)
        val state = PlaceableBarricadeState(10f, 10f, config.type.maxHp)

        // NaN should be ignored
        PlaceableBarricadeWallBlockEngine.step(config, state, Float.NaN)
        assertEquals(0f, state.timeSinceLastDamageSec, 0.001f)

        // Infinity should be ignored
        PlaceableBarricadeWallBlockEngine.step(config, state, Float.POSITIVE_INFINITY)
        assertEquals(0f, state.timeSinceLastDamageSec, 0.001f)

        // Negative should be ignored
        PlaceableBarricadeWallBlockEngine.step(config, state, -1.0f)
        assertEquals(0f, state.timeSinceLastDamageSec, 0.001f)

        // Huge DT should be clamped to 0.1f max per step
        PlaceableBarricadeWallBlockEngine.step(config, state, 1000f)
        assertEquals(0.1f, state.timeSinceLastDamageSec, 0.001f)
    }

    @Test
    fun testBatchAabbRoutingNoCollision() {
        val config = PlaceableBarricadeConfig("w1", BarricadeBlockType.CONCRETE, 50f, 50f)
        val state = PlaceableBarricadeState(100f, 100f, 1500)

        val entityBounds = floatArrayOf(
            10f, 10f, 20f, 20f, // No overlap
            200f, 200f, 220f, 220f // No overlap
        )
        val outMtv = FloatArray(4)

        PlaceableBarricadeWallBlockEngine.resolveBatchCollisions(config, state, entityBounds, outMtv, 2)

        assertEquals(0f, outMtv[0], 0.001f)
        assertEquals(0f, outMtv[1], 0.001f)
        assertEquals(0f, outMtv[2], 0.001f)
        assertEquals(0f, outMtv[3], 0.001f)
    }

    @Test
    fun testBatchAabbRoutingWithCollisions() {
        val config = PlaceableBarricadeConfig("w1", BarricadeBlockType.CONCRETE, 50f, 50f)
        val state = PlaceableBarricadeState(100f, 100f, 1500)

        val entityBounds = floatArrayOf(
            90f, 110f, 110f, 130f, // Left overlap by 10
            110f, 90f, 130f, 110f  // Top overlap by 10
        )
        val outMtv = FloatArray(4)

        PlaceableBarricadeWallBlockEngine.resolveBatchCollisions(config, state, entityBounds, outMtv, 2)

        assertEquals(-10f, outMtv[0], 0.001f)
        assertEquals(0f, outMtv[1], 0.001f)

        assertEquals(0f, outMtv[2], 0.001f)
        assertEquals(-10f, outMtv[3], 0.001f)
    }

    @Test
    fun testMaxCapacityStressTest300Entities() {
        val config = PlaceableBarricadeConfig("w1", BarricadeBlockType.CONCRETE, 50f, 50f)
        val state = PlaceableBarricadeState(100f, 100f, 1500)

        val count = 300
        val entityBounds = FloatArray(count * 4)
        val outMtv = FloatArray(count * 2)

        val random = Random(42)
        for (i in 0 until count) {
            val idx = i * 4
            // Scatter entities around and some inside the wall
            val rx = 50f + random.nextFloat() * 150f
            val ry = 50f + random.nextFloat() * 150f
            entityBounds[idx] = rx
            entityBounds[idx+1] = ry
            entityBounds[idx+2] = rx + 20f
            entityBounds[idx+3] = ry + 20f
        }

        // Add some NaN poison
        entityBounds[10 * 4] = Float.NaN

        PlaceableBarricadeWallBlockEngine.resolveBatchCollisions(config, state, entityBounds, outMtv, count)

        // Verify NaN handled
        assertEquals(0f, outMtv[10 * 2], 0.001f)
        assertEquals(0f, outMtv[10 * 2 + 1], 0.001f)

        // Verify we completed without exceptions and at least some collisions were resolved
        var collisionCount = 0
        for (i in 0 until count) {
            val idx = i * 2
            if (outMtv[idx] != 0f || outMtv[idx+1] != 0f) {
                collisionCount++
            }
        }
        assertTrue("Expected some collisions in 300 random entities", collisionCount > 0)
    }
}
