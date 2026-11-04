package com.example.network.sync.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClientDeadReckoningPredictorTest {
    
    private val epsilon = 0.001f

    @Test
    fun testConfigSerializationRoundtrip() {
        val config = ClientDeadReckoningConfig(maxEntities = 500, maxExtrapolationTimeSeconds = 2.0f, snapThresholdPx = 150.0f)
        val map = config.toMap()
        val restored = ClientDeadReckoningConfig.fromMap(map)
        assertEquals(config, restored)
    }

    @Test
    fun testSnapshotSerializationRoundtrip() {
        val snapshot = ClientDeadReckoningSnapshot(entityId = 5, networkX = 10f, networkY = 20f, velocityX = 1f, velocityY = -1f, timestampSeconds = 1.5f)
        val map = snapshot.toMap()
        val restored = ClientDeadReckoningSnapshot.fromMap(map)
        assertEquals(snapshot, restored)
    }

    @Test
    fun testInitialUpdateSetsActiveAndOutput() {
        val engine = ClientDeadReckoningPredictor(ClientDeadReckoningConfig(maxEntities = 10))
        engine.updateNetworkState(ClientDeadReckoningSnapshot(3, 100f, 200f, 10f, 20f, 1.0f))
        
        assertTrue(engine.isActive(3))
        assertEquals(100f, engine.getX(3), epsilon)
        assertEquals(200f, engine.getY(3), epsilon)
        assertEquals(10f, engine.getVX(3), epsilon)
        assertEquals(20f, engine.getVY(3), epsilon)
    }

    @Test
    fun testInvalidInputsIgnored() {
        val engine = ClientDeadReckoningPredictor(ClientDeadReckoningConfig(maxEntities = 10))
        
        engine.updateNetworkState(ClientDeadReckoningSnapshot(1, Float.NaN, 200f, 10f, 20f, 1.0f))
        assertFalse(engine.isActive(1))

        engine.updateNetworkState(ClientDeadReckoningSnapshot(-1, 100f, 200f, 10f, 20f, 1.0f))
        
        engine.updateNetworkState(ClientDeadReckoningSnapshot(15, 100f, 200f, 10f, 20f, 1.0f))

        engine.updateNetworkState(ClientDeadReckoningSnapshot(1, 100f, 200f, 10f, 20f, Float.NaN))
        assertFalse(engine.isActive(1))
    }

    @Test
    fun testStepHandlesNaN() {
        val engine = ClientDeadReckoningPredictor(ClientDeadReckoningConfig(maxEntities = 10))
        engine.updateNetworkState(ClientDeadReckoningSnapshot(1, 100f, 200f, 10f, 0f, 1.0f))
        
        // This shouldn't crash or update to NaN
        engine.step(Float.NaN, 2.0f)
        assertEquals(100f, engine.getX(1), epsilon)
    }

    @Test
    fun testRemoveEntity() {
        val engine = ClientDeadReckoningPredictor(ClientDeadReckoningConfig(maxEntities = 10))
        engine.updateNetworkState(ClientDeadReckoningSnapshot(1, 100f, 200f, 10f, 0f, 1.0f))
        assertTrue(engine.isActive(1))
        
        engine.removeEntity(1)
        assertFalse(engine.isActive(1))
        assertTrue(engine.getX(1).isNaN())
    }

    @Test
    fun testOutOfOrderPacketsIgnored() {
        val engine = ClientDeadReckoningPredictor(ClientDeadReckoningConfig(maxEntities = 10))
        engine.updateNetworkState(ClientDeadReckoningSnapshot(1, 100f, 200f, 10f, 0f, 1.0f))
        
        // Update with older timestamp
        engine.updateNetworkState(ClientDeadReckoningSnapshot(1, 300f, 400f, 0f, 0f, 0.5f))
        
        // Step and ensure it stays near the 1.0f packet extrapolation
        engine.step(0.016f, 1.0f)
        assertEquals(100f, engine.getX(1), epsilon)
    }

    @Test
    fun testMaxExtrapolation() {
        val engine = ClientDeadReckoningPredictor(ClientDeadReckoningConfig(maxEntities = 10, maxExtrapolationTimeSeconds = 0.5f))
        engine.updateNetworkState(ClientDeadReckoningSnapshot(1, 100f, 100f, 10f, 0f, 1.0f))
        
        // Extrapolate 1.0s, but should be clamped to 0.5s
        engine.step(1.0f, 2.0f)
        
        assertEquals(105f, engine.getX(1), epsilon) // 100 + 10 * 0.5
    }

    @Test
    fun testHermiteInterpolation() {
        val engine = ClientDeadReckoningPredictor(ClientDeadReckoningConfig(maxEntities = 10))
        engine.updateNetworkState(ClientDeadReckoningSnapshot(1, 0f, 0f, 10f, 0f, 0f))
        
        // Second packet arrived at t=1.0, positioned at 10.0 with velocity 10.0
        engine.updateNetworkState(ClientDeadReckoningSnapshot(1, 10f, 0f, 10f, 0f, 1.0f))
        
        // Interpolate halfway at t=0.5
        engine.step(0.016f, 0.5f)
        
        // Linear movement from 0 to 10 at v=10 means at t=0.5 it should be at 5
        assertEquals(5f, engine.getX(1), epsilon)
        assertEquals(10f, engine.getVX(1), epsilon)
    }

    @Test
    fun testPositionSnapThreshold() {
        val engine = ClientDeadReckoningPredictor(ClientDeadReckoningConfig(maxEntities = 10, snapThresholdPx = 50f))
        engine.updateNetworkState(ClientDeadReckoningSnapshot(1, 0f, 0f, 0f, 0f, 0f))
        
        // Teleport 100px away (exceeds 50f threshold)
        engine.updateNetworkState(ClientDeadReckoningSnapshot(1, 100f, 0f, 0f, 0f, 1.0f))
        
        engine.step(0.016f, 0.5f)
        
        // Because of snap, it should be exactly at 100f, bypassing Hermite interpolation from 0
        assertEquals(100f, engine.getX(1), epsilon)
    }

    @Test
    fun testDeterminismSameSeeds() {
        val config = ClientDeadReckoningConfig(maxEntities = 5)
        
        val engine1 = ClientDeadReckoningPredictor(config)
        val engine2 = ClientDeadReckoningPredictor(config)
        
        engine1.updateNetworkState(ClientDeadReckoningSnapshot(1, 10f, 10f, 5f, 5f, 1.0f))
        engine2.updateNetworkState(ClientDeadReckoningSnapshot(1, 10f, 10f, 5f, 5f, 1.0f))
        
        engine1.step(0.016f, 1.016f)
        engine2.step(0.016f, 1.016f)
        
        val chk1 = engine1.computeChecksum()
        val chk2 = engine2.computeChecksum()
        
        assertEquals(chk1, chk2)
    }

    @Test
    fun testExtremeLoadNoOOM() {
        val maxEnts = 2000
        val engine = ClientDeadReckoningPredictor(ClientDeadReckoningConfig(maxEntities = maxEnts))
        
        for (i in 0 until maxEnts) {
            engine.updateNetworkState(ClientDeadReckoningSnapshot(i, i.toFloat(), i.toFloat(), 1f, 1f, 1.0f))
        }
        
        for (i in 0 until maxEnts) {
            engine.updateNetworkState(ClientDeadReckoningSnapshot(i, i.toFloat() + 1f, i.toFloat() + 1f, 1f, 1f, 2.0f))
        }
        
        engine.step(0.016f, 1.5f)
        
        assertTrue(engine.isActive(1999))
        assertEquals(1999.5f, engine.getX(1999), epsilon)
    }
}
