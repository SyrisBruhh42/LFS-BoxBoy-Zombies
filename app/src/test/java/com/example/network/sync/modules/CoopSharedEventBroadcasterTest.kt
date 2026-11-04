package com.example.network.sync.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoopSharedEventBroadcasterTest {

    @Test
    fun testSerializationRoundtrip() {
        val original = CoopSharedEvent(
            type = CoopSharedEventType.BARREL_DETONATED,
            x = 120.5f,
            y = -45.0f,
            targetId = 42
        )
        
        val map = original.toMap()
        val restored = CoopSharedEvent.fromMap(map)
        
        assertEquals(original.type, restored.type)
        assertEquals(original.x, restored.x, 0.001f)
        assertEquals(original.y, restored.y, 0.001f)
        assertEquals(original.targetId, restored.targetId)
    }
    
    @Test
    fun testSerializationUnknownTypeFallback() {
        val badMap = mapOf(
            "type" to "INVALID_TYPE_STRING",
            "x" to 10f,
            "y" to 20f,
            "targetId" to 99
        )
        val restored = CoopSharedEvent.fromMap(badMap)
        assertEquals(CoopSharedEventType.UNKNOWN, restored.type)
        assertEquals(10f, restored.x, 0.001f)
        assertEquals(20f, restored.y, 0.001f)
        assertEquals(99, restored.targetId)
    }

    @Test
    fun testChecksumDeterminismIdenticalInputs() {
        val broadcaster1 = CoopSharedEventBroadcaster()
        val broadcaster2 = CoopSharedEventBroadcaster()
        
        broadcaster1.enqueueEvent(CoopSharedEventType.DOOR_UNLOCKED, 100f, 200f, 1)
        broadcaster1.enqueueEvent(CoopSharedEventType.BOSS_SPAWNED, -50f, 75f, 2)
        
        broadcaster2.enqueueEvent(CoopSharedEventType.DOOR_UNLOCKED, 100f, 200f, 1)
        broadcaster2.enqueueEvent(CoopSharedEventType.BOSS_SPAWNED, -50f, 75f, 2)
        
        val snap1 = broadcaster1.getSnapshot()
        val snap2 = broadcaster2.getSnapshot()
        
        assertEquals(2, snap1.pendingCount)
        assertEquals(2, snap2.pendingCount)
        assertEquals("Checksums must be bit-identical for same inputs", snap1.latestChecksum, snap2.latestChecksum)
    }

    @Test
    fun testChecksumDeterminismDifferentInputs() {
        val broadcaster1 = CoopSharedEventBroadcaster()
        val broadcaster2 = CoopSharedEventBroadcaster()
        
        broadcaster1.enqueueEvent(CoopSharedEventType.DOOR_UNLOCKED, 100f, 200f, 1)
        broadcaster2.enqueueEvent(CoopSharedEventType.DOOR_UNLOCKED, 100.1f, 200f, 1) // slightly different x
        
        val snap1 = broadcaster1.getSnapshot()
        val snap2 = broadcaster2.getSnapshot()
        
        assertNotEquals("Checksums must differ for different inputs", snap1.latestChecksum, snap2.latestChecksum)
    }

    @Test
    fun testDtSecondsClampingLowerBound() {
        val broadcaster = CoopSharedEventBroadcaster()
        broadcaster.enqueueEvent(CoopSharedEventType.VENDING_PURCHASE, 0f, 0f, 5)
        
        // Pass 0.0f, should be clamped to 0.001f
        val eventsZero = broadcaster.tick(0.0f, seed = 12345L)
        assertEquals(1, eventsZero.size)
        
        broadcaster.enqueueEvent(CoopSharedEventType.VENDING_PURCHASE, 0f, 0f, 5)
        // Pass 0.001f, should be exactly the same due to identical seed and clamped dt
        val eventsClamped = broadcaster.tick(0.001f, seed = 12345L)
        
        assertEquals(eventsZero[0].x, eventsClamped[0].x, 0.0001f)
        assertEquals(eventsZero[0].y, eventsClamped[0].y, 0.0001f)
    }

    @Test
    fun testDtSecondsClampingUpperBound() {
        val broadcaster = CoopSharedEventBroadcaster()
        broadcaster.enqueueEvent(CoopSharedEventType.BARREL_DETONATED, 0f, 0f, 10)
        
        // Pass 999.0f, should be clamped to 0.100f
        val eventsLarge = broadcaster.tick(999.0f, seed = 67890L)
        assertEquals(1, eventsLarge.size)
        
        broadcaster.enqueueEvent(CoopSharedEventType.BARREL_DETONATED, 0f, 0f, 10)
        // Pass 0.100f, should be exactly the same
        val eventsClamped = broadcaster.tick(0.100f, seed = 67890L)
        
        assertEquals(eventsLarge[0].x, eventsClamped[0].x, 0.0001f)
        assertEquals(eventsLarge[0].y, eventsClamped[0].y, 0.0001f)
    }

    @Test
    fun testNanInfinityGuards() {
        val broadcaster = CoopSharedEventBroadcaster()
        
        broadcaster.enqueueEvent(CoopSharedEventType.BOSS_SPAWNED, Float.NaN, Float.POSITIVE_INFINITY, 7)
        
        val snap = broadcaster.getSnapshot()
        assertEquals(1, snap.pendingCount)
        
        val events = broadcaster.tick(0.016f, seed = 1L)
        assertEquals(1, events.size)
        
        // The implementation falls back to 0f for non-finite values before applying bounds/variance
        val e = events[0]
        assertTrue("X should be finite", e.x.isFinite())
        assertTrue("Y should be finite", e.y.isFinite())
    }

    @Test
    fun testSpatialBoundsClamping() {
        val config = CoopSharedEventConfig(boundsMinX = -100f, boundsMaxX = 100f, boundsMinY = -100f, boundsMaxY = 100f)
        val broadcaster = CoopSharedEventBroadcaster(config)
        
        broadcaster.enqueueEvent(CoopSharedEventType.DOOR_UNLOCKED, -5000f, 9000f, 1)
        
        val events = broadcaster.tick(0.016f, seed = 1L)
        val e = events[0]
        
        assertTrue("X should be clamped to min bound", e.x >= -100f && e.x <= 100f)
        assertTrue("Y should be clamped to max bound", e.y >= -100f && e.y <= 100f)
    }

    @Test
    fun testNominalFlowDoorUnlocked() {
        val broadcaster = CoopSharedEventBroadcaster()
        broadcaster.enqueueEvent(CoopSharedEventType.DOOR_UNLOCKED, 10f, 20f, 100)
        
        val events = broadcaster.tick(0.016f, seed = 42L)
        assertEquals(1, events.size)
        assertEquals(CoopSharedEventType.DOOR_UNLOCKED, events[0].type)
        assertEquals(100, events[0].targetId)
    }
    
    @Test
    fun testNominalFlowBarrelDetonated() {
        val broadcaster = CoopSharedEventBroadcaster()
        broadcaster.enqueueEvent(CoopSharedEventType.BARREL_DETONATED, -10f, -20f, 101)
        
        val events = broadcaster.tick(0.016f, seed = 43L)
        assertEquals(1, events.size)
        assertEquals(CoopSharedEventType.BARREL_DETONATED, events[0].type)
        assertEquals(101, events[0].targetId)
    }

    @Test
    fun testNominalFlowVendingPurchase() {
        val broadcaster = CoopSharedEventBroadcaster()
        broadcaster.enqueueEvent(CoopSharedEventType.VENDING_PURCHASE, 50f, 50f, 102)
        
        val events = broadcaster.tick(0.016f, seed = 44L)
        assertEquals(1, events.size)
        assertEquals(CoopSharedEventType.VENDING_PURCHASE, events[0].type)
        assertEquals(102, events[0].targetId)
    }

    @Test
    fun testNominalFlowBossSpawned() {
        val broadcaster = CoopSharedEventBroadcaster()
        broadcaster.enqueueEvent(CoopSharedEventType.BOSS_SPAWNED, 0f, 0f, 103)
        
        val events = broadcaster.tick(0.016f, seed = 45L)
        assertEquals(1, events.size)
        assertEquals(CoopSharedEventType.BOSS_SPAWNED, events[0].type)
        assertEquals(103, events[0].targetId)
    }

    @Test
    fun testMaxCapacityStressTestAndWrapAround() {
        val capacity = 50
        val config = CoopSharedEventConfig(maxQueueCapacity = capacity)
        val broadcaster = CoopSharedEventBroadcaster(config)
        
        // Enqueue capacity + 10 events to force wrap-around
        for (i in 0 until capacity + 10) {
            broadcaster.enqueueEvent(CoopSharedEventType.BARREL_DETONATED, i.toFloat(), i.toFloat(), i)
        }
        
        val snap = broadcaster.getSnapshot()
        assertEquals(capacity, snap.pendingCount)
        
        val events = broadcaster.tick(0.016f, seed = 99L)
        assertEquals(capacity, events.size)
        
        // Due to wrap-around, the oldest 10 events (0 to 9) should have been overwritten.
        // So the first event read from the queue should be the 10th one added, where targetId = 10.
        // Note: targetIds[0] might actually be 50 because it overwrote index 0. 
        // But our tick function reads from 'head' which advanced to 10.
        assertEquals(10, events[0].targetId)
        
        // The last event should be the very last one added (targetId = 59)
        assertEquals(59, events[capacity - 1].targetId)
        
        // Queue should be empty after tick
        val snapAfter = broadcaster.getSnapshot()
        assertEquals(0, snapAfter.pendingCount)
    }
}
