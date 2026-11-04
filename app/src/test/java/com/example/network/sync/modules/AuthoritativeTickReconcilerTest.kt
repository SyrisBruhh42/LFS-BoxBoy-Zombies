package com.example.network.sync.modules

import org.junit.Assert.*
import org.junit.Test

class AuthoritativeTickReconcilerTest {

    @Test
    fun testNominalPlayback() {
        val reconciler = AuthoritativeTickReconciler()

        // Add inputs
        reconciler.addLocalInput(PlayerTickInput(1L, 1f, 0f, false, 0f, 0.016f))
        reconciler.addLocalInput(PlayerTickInput(2L, 0f, 1f, false, 0f, 0.016f))

        // Apply server snapshot that acknowledges up to tick 0
        reconciler.reconcile(AuthoritativeTickSnapshot(0L, 0f, 0f, 100, 0L))

        // Ensure replay moved player
        val px1 = reconciler.currentSnapshot.playerX
        val py1 = reconciler.currentSnapshot.playerY

        assertTrue(px1 > 0f)
        assertTrue(py1 > 0f)
        assertEquals(2L, reconciler.currentSnapshot.tickId)

        // Apply server snapshot that acknowledges up to tick 1
        val newHash = reconciler.computeChecksum(1L, px1, 0f, 100)
        reconciler.reconcile(AuthoritativeTickSnapshot(1L, px1, 0f, 100, newHash))

        // Only tick 2 is replayed
        assertEquals(2L, reconciler.currentSnapshot.tickId)
        // Y moves again since tick 1 is already handled by server, and tick 2 re-applies Y movement
        assertTrue(reconciler.currentSnapshot.playerY > 0f)
    }

    @Test
    fun testBufferOverflow() {
        val config = ReconcilerConfig(maxBufferSize = 2)
        val reconciler = AuthoritativeTickReconciler(config)

        reconciler.addLocalInput(PlayerTickInput(1L, 1f, 0f, false, 0f, 0.016f))
        reconciler.addLocalInput(PlayerTickInput(2L, 1f, 0f, false, 0f, 0.016f))
        reconciler.addLocalInput(PlayerTickInput(3L, 1f, 0f, false, 0f, 0.016f)) // overwrites tick 1

        reconciler.reconcile(AuthoritativeTickSnapshot(1L, 10f, 10f, 100, 0L))

        assertEquals(3L, reconciler.currentSnapshot.tickId)
        // Should replay 2L and 3L
        assertTrue(reconciler.currentSnapshot.playerX > 10f)
    }

    @Test
    fun testOlderSnapshotSkipping() {
        val reconciler = AuthoritativeTickReconciler()
        reconciler.addLocalInput(PlayerTickInput(1L, 1f, 0f, false, 0f, 0.016f))

        reconciler.reconcile(AuthoritativeTickSnapshot(5L, 50f, 50f, 100, 0L))
        assertEquals(5L, reconciler.currentSnapshot.tickId)
        assertEquals(50f, reconciler.currentSnapshot.playerX)

        // Older snapshot (2L) should be ignored because we are at 5L
        reconciler.reconcile(AuthoritativeTickSnapshot(2L, 20f, 20f, 100, 0L))
        assertEquals(5L, reconciler.currentSnapshot.tickId)
        assertEquals(50f, reconciler.currentSnapshot.playerX)
    }

    @Test
    fun testDeterminism() {
        val r1 = AuthoritativeTickReconciler()
        val r2 = AuthoritativeTickReconciler()

        val input = PlayerTickInput(1L, 1f, -1f, true, 3.14f, 0.016f)
        r1.addLocalInput(input)
        r2.addLocalInput(input)

        val snap = AuthoritativeTickSnapshot(0L, 0f, 0f, 100, 0L)
        r1.reconcile(snap)
        r2.reconcile(snap)

        assertEquals(r1.currentSnapshot.playerX, r2.currentSnapshot.playerX)
        assertEquals(r1.currentSnapshot.playerY, r2.currentSnapshot.playerY)
        assertEquals(r1.currentSnapshot.checksum, r2.currentSnapshot.checksum)
    }

    @Test
    fun testNaNAndInfinityRecovery() {
        val r = AuthoritativeTickReconciler()
        val badInput = PlayerTickInput(
            tickId = 1L,
            moveX = Float.NaN,
            moveY = Float.POSITIVE_INFINITY,
            isFiring = false,
            aimAngleRad = Float.NEGATIVE_INFINITY,
            dtSeconds = Float.NaN
        )
        r.addLocalInput(badInput)

        val snap = AuthoritativeTickSnapshot(0L, 0f, 0f, 100, 0L)
        r.reconcile(snap)

        val curr = r.currentSnapshot
        assertTrue(curr.playerX.isFinite())
        assertTrue(curr.playerY.isFinite())
        assertEquals(0f, curr.playerX)
        assertEquals(0f, curr.playerY)
    }

    @Test
    fun testNegativeDtSecondsClamping() {
        val r = AuthoritativeTickReconciler()
        val badInput = PlayerTickInput(1L, 1f, 1f, false, 0f, -0.5f)
        r.addLocalInput(badInput)

        r.reconcile(AuthoritativeTickSnapshot(0L, 0f, 0f, 100, 0L))

        // Ensure clamped to minimum
        assertTrue(r.currentSnapshot.playerX > 0f)
        assertTrue(r.currentSnapshot.playerX < (232f * 0.01f)) // Should be around dt=0.001f
    }

    @Test
    fun testSerializationRoundtripReconcilerConfig() {
        val config = ReconcilerConfig(256, 0.005f, 0.050f)
        val map = config.toMap()
        val parsed = ReconcilerConfig.fromMap(map)

        assertEquals(config.maxBufferSize, parsed.maxBufferSize)
        assertEquals(config.dtSecondsClampedMin, parsed.dtSecondsClampedMin)
        assertEquals(config.dtSecondsClampedMax, parsed.dtSecondsClampedMax)
    }

    @Test
    fun testSerializationRoundtripPlayerTickInput() {
        val input = PlayerTickInput(12345L, -0.5f, 0.5f, true, 1.23f, 0.032f)
        val map = input.toMap()
        val parsed = PlayerTickInput.fromMap(map)

        assertEquals(input.tickId, parsed.tickId)
        assertEquals(input.moveX, parsed.moveX)
        assertEquals(input.moveY, parsed.moveY)
        assertEquals(input.isFiring, parsed.isFiring)
        assertEquals(input.aimAngleRad, parsed.aimAngleRad)
        assertEquals(input.dtSeconds, parsed.dtSeconds)
    }

    @Test
    fun testSerializationRoundtripAuthoritativeTickSnapshot() {
        val snap = AuthoritativeTickSnapshot(54321L, 100.5f, -50.25f, 85, 987654321L)
        val map = snap.toMap()
        val parsed = AuthoritativeTickSnapshot.fromMap(map)

        assertEquals(snap.tickId, parsed.tickId)
        assertEquals(snap.playerX, parsed.playerX)
        assertEquals(snap.playerY, parsed.playerY)
        assertEquals(snap.playerHp, parsed.playerHp)
        assertEquals(snap.checksum, parsed.checksum)
    }

    @Test
    fun testMaxCapacityStressUnderContinuousCorrection() {
        val config = ReconcilerConfig(maxBufferSize = 128)
        val reconciler = AuthoritativeTickReconciler(config)

        // Feed 300 inputs (overflows 128 buffer multiple times)
        for (i in 1..300) {
            reconciler.addLocalInput(PlayerTickInput(i.toLong(), 1f, 0f, true, 0f, 0.016f))
        }

        // Give it a snapshot lagging by 64 frames
        val lagTick = 300L - 64L
        val snap = AuthoritativeTickSnapshot(lagTick, 100f, 100f, 100, 0L)

        reconciler.reconcile(snap)

        // The last 64 inputs should be replayed
        assertEquals(300L, reconciler.currentSnapshot.tickId)
        // 64 ticks * 232 px/s * 0.016 s = 237.568 px
        assertTrue(reconciler.currentSnapshot.playerX > 330f) // 100 + ~237
        assertTrue(reconciler.currentSnapshot.playerX < 350f)
    }

    @Test
    fun testReplayPreservesRecoilSeededVariation() {
        val r = AuthoritativeTickReconciler()
        r.addLocalInput(PlayerTickInput(1L, 1f, 0f, true, 0f, 0.016f))
        r.reconcile(AuthoritativeTickSnapshot(0L, 0f, 0f, 100, 0L))

        val currX = r.currentSnapshot.playerX
        val currY = r.currentSnapshot.playerY

        // LCG causes recoil variance when firing. Ensure they are correctly reproduced.
        val r2 = AuthoritativeTickReconciler()
        r2.addLocalInput(PlayerTickInput(1L, 1f, 0f, true, 0f, 0.016f))
        r2.reconcile(AuthoritativeTickSnapshot(0L, 0f, 0f, 100, 0L))

        assertEquals(currX, r2.currentSnapshot.playerX)
        assertEquals(currY, r2.currentSnapshot.playerY)
    }

    @Test
    fun testEmptyBufferReconcile() {
        val r = AuthoritativeTickReconciler()
        val snap = AuthoritativeTickSnapshot(100L, 50f, 50f, 100, 1234L)
        r.reconcile(snap)

        assertEquals(100L, r.currentSnapshot.tickId)
        assertEquals(50f, r.currentSnapshot.playerX)
        assertEquals(50f, r.currentSnapshot.playerY)
        assertEquals(1234L, r.currentSnapshot.checksum)
    }
}
