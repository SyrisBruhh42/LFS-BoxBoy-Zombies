package com.example.network.sync.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DropPodHotJoinSpawnerTest {

    @Test
    fun testDropPodConfigSerializationRoundtrip() {
        val config = DropPodConfig(
            impactShockwaveRadiusPx = 250f,
            impactShockwaveDamage = 500,
            knockbackImpulse = 600f,
            landingDurationSeconds = 2f
        )
        val map = config.toMap()
        val restored = DropPodConfig.fromMap(map)
        assertEquals(config, restored)
    }

    @Test
    fun testDropPodSnapshotSerializationRoundtrip() {
        val snapshot = DropPodSnapshot(
            dropPodId = "dp-001",
            joinerPeerId = "peer-alpha",
            x = 100f,
            y = 200f,
            timeElapsed = 1.2f,
            isLanded = true,
            hasDetonated = true,
            checksum = 9876543210L
        )
        val map = snapshot.toMap()
        val restored = DropPodSnapshot.fromMap(map)
        assertEquals(snapshot.dropPodId, restored.dropPodId)
        assertEquals(snapshot.joinerPeerId, restored.joinerPeerId)
        assertEquals(snapshot.x, restored.x, 0.0001f)
        assertEquals(snapshot.y, restored.y, 0.0001f)
        assertEquals(snapshot.timeElapsed, restored.timeElapsed, 0.0001f)
        assertEquals(snapshot.isLanded, restored.isLanded)
        assertEquals(snapshot.hasDetonated, restored.hasDetonated)
        assertEquals(snapshot.checksum, restored.checksum)
    }

    @Test
    fun testSpawnDropPodNearSquadLeader() {
        val dropPodId = "dp-002"
        val joinerId = "peer-beta"
        val leaderX = 1000f
        val leaderY = 500f

        val snapshot = DropPodHotJoinSpawner.spawnDropPod(
            dropPodId = dropPodId,
            joinerPeerId = joinerId,
            squadLeaderX = leaderX,
            squadLeaderY = leaderY,
            offsetX = 48f,
            offsetY = -32f
        )

        assertEquals(dropPodId, snapshot.dropPodId)
        assertEquals(joinerId, snapshot.joinerPeerId)
        assertEquals(1048f, snapshot.x, 0.0001f)
        assertEquals(468f, snapshot.y, 0.0001f)
        assertEquals(0f, snapshot.timeElapsed, 0.0001f)
        assertFalse(snapshot.isLanded)
        assertFalse(snapshot.hasDetonated)
        assertNotEquals(0L, snapshot.checksum)
    }

    @Test
    fun testSpawnDropPodHandlesNanOrInfinity() {
        val snapshot = DropPodHotJoinSpawner.spawnDropPod(
            dropPodId = "dp-003",
            joinerPeerId = "peer-gamma",
            squadLeaderX = Float.NaN,
            squadLeaderY = Float.POSITIVE_INFINITY
        )

        assertEquals(0f, snapshot.x, 0.0001f)
        assertEquals(0f, snapshot.y, 0.0001f)
    }

    @Test
    fun testStepDropPodPhysicsAdvancesTimerWithoutDetonating() {
        val snapshot = DropPodHotJoinSpawner.spawnDropPod("dp-004", "peer-delta", 100f, 100f)
        val config = DropPodConfig(landingDurationSeconds = 1.5f)

        val xs = FloatArray(10)
        val ys = FloatArray(10)
        val hps = IntArray(10)
        val impulses = FloatArray(10)

        val oldChecksum = snapshot.checksum

        val result = DropPodHotJoinSpawner.stepDropPodPhysics(
            snapshot, config, 0.1f, xs, ys, hps, impulses, 0
        )

        assertFalse(result)
        assertEquals(0.1f, snapshot.timeElapsed, 0.0001f)
        assertFalse(snapshot.isLanded)
        assertFalse(snapshot.hasDetonated)
        assertNotEquals(oldChecksum, snapshot.checksum)
    }

    @Test
    fun testStepDropPodPhysicsClampsDeltaTime() {
        val snapshot = DropPodHotJoinSpawner.spawnDropPod("dp-005", "peer-epsilon", 100f, 100f)
        val config = DropPodConfig(landingDurationSeconds = 1.5f)

        val xs = FloatArray(10)
        val ys = FloatArray(10)
        val hps = IntArray(10)
        val impulses = FloatArray(10)

        DropPodHotJoinSpawner.stepDropPodPhysics(snapshot, config, 5.0f, xs, ys, hps, impulses, 0)
        // Dt should be clamped to 0.100
        assertEquals(0.1f, snapshot.timeElapsed, 0.001f)

        DropPodHotJoinSpawner.stepDropPodPhysics(snapshot, config, -5.0f, xs, ys, hps, impulses, 0)
        // Dt should be clamped to 0.001
        assertEquals(0.101f, snapshot.timeElapsed, 0.001f)
    }

    @Test
    fun testStepDropPodPhysicsDetonatesAndAppliesAoEDamage() {
        val snapshot = DropPodHotJoinSpawner.spawnDropPod("dp-006", "peer-zeta", 52f, 132f) // so spawn is exactly 100, 100
        val config = DropPodConfig(
            impactShockwaveRadiusPx = 50f,
            impactShockwaveDamage = 100,
            knockbackImpulse = 200f,
            landingDurationSeconds = 1.0f
        )
        // Force timer near detonation
        snapshot.timeElapsed = 0.95f

        val xs = floatArrayOf(100f, 140f, 200f) // Distances from 100,100: 0, 40, 100
        val ys = floatArrayOf(100f, 100f, 100f)
        val hps = intArrayOf(200, 200, 200)
        val impulses = floatArrayOf(0f, 0f, 0f)

        val result = DropPodHotJoinSpawner.stepDropPodPhysics(
            snapshot, config, 0.1f, xs, ys, hps, impulses, 3
        )

        assertTrue(result)
        assertTrue(snapshot.isLanded)
        assertTrue(snapshot.hasDetonated)

        // Z0: Dist 0 <= 50 (damaged)
        assertEquals(100, hps[0])
        assertEquals(200f, impulses[0], 0.0001f)

        // Z1: Dist 40 <= 50 (damaged)
        assertEquals(100, hps[1])
        assertEquals(200f, impulses[1], 0.0001f)

        // Z2: Dist 100 > 50 (untouched)
        assertEquals(200, hps[2])
        assertEquals(0f, impulses[2], 0.0001f)
    }

    @Test
    fun testStepDropPodPhysicsDoesNotDamageDeadZombies() {
        val snapshot = DropPodHotJoinSpawner.spawnDropPod("dp-007", "peer-eta", 100f, 100f)
        val config = DropPodConfig(impactShockwaveRadiusPx = 50f, impactShockwaveDamage = 100, landingDurationSeconds = 1.0f)
        snapshot.timeElapsed = 0.95f

        val xs = floatArrayOf(100f)
        val ys = floatArrayOf(100f)
        val hps = intArrayOf(0) // Dead
        val impulses = floatArrayOf(0f)

        DropPodHotJoinSpawner.stepDropPodPhysics(snapshot, config, 0.1f, xs, ys, hps, impulses, 1)

        assertEquals(0, hps[0]) // Still 0
        assertEquals(0f, impulses[0], 0.0001f) // No knockback
    }

    @Test
    fun testStepDropPodPhysicsAlreadyDetonatedReturnsFalse() {
        val snapshot = DropPodHotJoinSpawner.spawnDropPod("dp-008", "peer-theta", 100f, 100f)
        val config = DropPodConfig()
        snapshot.hasDetonated = true
        snapshot.isLanded = true

        val result = DropPodHotJoinSpawner.stepDropPodPhysics(
            snapshot, config, 0.1f, FloatArray(0), FloatArray(0), IntArray(0), FloatArray(0), 0
        )

        assertFalse(result)
    }

    @Test
    fun testChecksumDeterminism() {
        val snapshot1 = DropPodSnapshot("dp", "p1", 10f, 20f, 1f, true, true)
        val snapshot2 = DropPodSnapshot("dp", "p1", 10f, 20f, 1f, true, true)

        assertEquals(snapshot1.computeChecksum(), snapshot2.computeChecksum())

        val snapshot3 = DropPodSnapshot("dp", "p1", 10f, 20f, 1.1f, true, true)
        assertNotEquals(snapshot1.computeChecksum(), snapshot3.computeChecksum())
    }

    @Test
    fun testMaxCapacityStressTest() {
        val snapshot = DropPodHotJoinSpawner.spawnDropPod("dp-stress", "peer-stress", 0f, 0f)
        val config = DropPodConfig(impactShockwaveRadiusPx = 100f, impactShockwaveDamage = 100, landingDurationSeconds = 1.0f)
        snapshot.timeElapsed = 0.95f

        val capacity = 300
        val xs = FloatArray(capacity) { (it % 100).toFloat() } // Most within 100px
        val ys = FloatArray(capacity) { (it % 100).toFloat() }
        val hps = IntArray(capacity) { 150 }
        val impulses = FloatArray(capacity) { 0f }

        val result = DropPodHotJoinSpawner.stepDropPodPhysics(
            snapshot, config, 0.1f, xs, ys, hps, impulses, capacity
        )

        assertTrue(result)

        for (i in 0 until capacity) {
            val dx = xs[i] - snapshot.x
            val dy = ys[i] - snapshot.y
            val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dist <= 100f) {
                assertEquals("Zombie $i HP mismatch at dist $dist", 50, hps[i])
            } else {
                assertEquals("Zombie $i HP mismatch at dist $dist", 150, hps[i])
            }
        }
    }

    @Test
    fun testBoundsCheckingOnArrays() {
        val snapshot = DropPodHotJoinSpawner.spawnDropPod("dp-bounds", "peer-bounds", 0f, 0f)
        val config = DropPodConfig(impactShockwaveRadiusPx = 50f, impactShockwaveDamage = 100, landingDurationSeconds = 1.0f)
        snapshot.timeElapsed = 0.95f

        val xs = FloatArray(5)
        val ys = FloatArray(5)
        val hps = IntArray(2) // Only 2 elements
        val impulses = FloatArray(5)

        // This shouldn't crash with ArrayIndexOutOfBoundsException due to coerceAtMost
        DropPodHotJoinSpawner.stepDropPodPhysics(
            snapshot, config, 0.1f, xs, ys, hps, impulses, 5
        )

        assertTrue(snapshot.hasDetonated)
    }
}
