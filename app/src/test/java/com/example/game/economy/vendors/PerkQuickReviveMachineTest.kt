package com.example.game.economy.vendors

import com.example.data.PerkType
import com.example.game.GameSessionState
import com.example.network.CoopPeerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerkQuickReviveMachineTest {

    @Test
    fun testPurchase_SuccessSolo() {
        val state = GameSessionState(points = 1500)
        val vendorState = QuickReviveState()

        val result = PerkQuickReviveMachine.attemptPurchase(state, vendorState)

        assertTrue(result.success)
        assertEquals(500, result.updatedState.points)
        assertTrue(result.updatedState.activePerks.contains(PerkType.QUICK_REVIVE))
        assertEquals(0, result.updatedVendorState.soloRevivesUsed)
    }

    @Test
    fun testPurchase_InsufficientPoints() {
        val state = GameSessionState(points = 500)
        val vendorState = QuickReviveState()

        val result = PerkQuickReviveMachine.attemptPurchase(state, vendorState)

        assertFalse(result.success)
        assertEquals("Insufficient points.", result.message)
        assertEquals(500, result.updatedState.points) // Unchanged
    }

    @Test
    fun testPurchase_AlreadyOwned() {
        val state = GameSessionState(
            points = 2000,
            activePerks = setOf(PerkType.QUICK_REVIVE)
        )
        val vendorState = QuickReviveState(soloRevivesUsed = 0)

        val result = PerkQuickReviveMachine.attemptPurchase(state, vendorState)

        assertFalse(result.success)
        assertEquals("Already holding Quick Revive.", result.message)
        assertEquals(2000, result.updatedState.points)
    }

    @Test
    fun testPurchase_SoloMaxCapReached() {
        val state = GameSessionState(points = 1500)
        val vendorState = QuickReviveState(soloRevivesUsed = 3)

        val result = PerkQuickReviveMachine.attemptPurchase(state, vendorState)

        assertFalse(result.success)
        assertTrue(result.message.contains("Max solo revives"))
        assertFalse(result.updatedVendorState.isAvailable)
    }

    @Test
    fun testPurchase_MachineUnavailable() {
        val state = GameSessionState(points = 1500)
        val vendorState = QuickReviveState(isAvailable = false)

        val result = PerkQuickReviveMachine.attemptPurchase(state, vendorState)

        assertFalse(result.success)
        assertEquals("Machine out of stock or powered down.", result.message)
    }

    @Test
    fun testSoloRevive_Success() {
        val state = GameSessionState(
            isPlayerDowned = true,
            activePerks = setOf(PerkType.QUICK_REVIVE),
            hp = 0,
            maxHp = 100
        )
        val vendorState = QuickReviveState(soloRevivesUsed = 0)

        val result = PerkQuickReviveMachine.attemptSoloAutoRevive(state, vendorState)

        assertTrue(result.success)
        assertFalse(result.updatedState.isPlayerDowned)
        assertEquals(100, result.updatedState.hp)
        assertFalse(result.updatedState.activePerks.contains(PerkType.QUICK_REVIVE))
        assertEquals(1, result.updatedVendorState.soloRevivesUsed)
        assertTrue(result.updatedVendorState.isAvailable)
    }

    @Test
    fun testSoloRevive_ReachesMaxCap() {
        val state = GameSessionState(
            isPlayerDowned = true,
            activePerks = setOf(PerkType.QUICK_REVIVE),
            hp = 0,
            maxHp = 100
        )
        val vendorState = QuickReviveState(soloRevivesUsed = 2)

        val result = PerkQuickReviveMachine.attemptSoloAutoRevive(state, vendorState)

        assertTrue(result.success)
        assertEquals(3, result.updatedVendorState.soloRevivesUsed)
        assertFalse(result.updatedVendorState.isAvailable) // Machine should now be unavailable
    }

    @Test
    fun testSoloRevive_FailsInCoopMode() {
        val state = GameSessionState(
            isPlayerDowned = true,
            activePerks = setOf(PerkType.QUICK_REVIVE),
            coopPeers = listOf(CoopPeerState())
        )
        val vendorState = QuickReviveState()

        val result = PerkQuickReviveMachine.attemptSoloAutoRevive(state, vendorState)

        assertFalse(result.success)
        assertEquals("Auto-revive is Solo only.", result.message)
    }

    @Test
    fun testSoloRevive_NotDowned() {
        val state = GameSessionState(
            isPlayerDowned = false,
            activePerks = setOf(PerkType.QUICK_REVIVE)
        )
        val vendorState = QuickReviveState()

        val result = PerkQuickReviveMachine.attemptSoloAutoRevive(state, vendorState)

        assertFalse(result.success)
        assertEquals("Player is not downed.", result.message)
    }

    @Test
    fun testCoopReviveSpeed_WithoutPerk() {
        val peerId = "peer1"
        val state = GameSessionState(
            coopPeers = listOf(CoopPeerState(peerId = peerId, isDowned = true, reviveProgress = 0f))
        )

        // base speed = 0.333/sec, dt = 0.1sec -> increment = 0.0333
        val newState = PerkQuickReviveMachine.computeReviveProgress(state, 0.1f, peerId)
        val peer = newState.coopPeers.find { it.peerId == peerId }!!

        assertTrue(peer.reviveProgress > 0.033f && peer.reviveProgress < 0.034f)
    }

    @Test
    fun testCoopReviveSpeed_WithPerk() {
        val peerId = "peer1"
        val state = GameSessionState(
            activePerks = setOf(PerkType.QUICK_REVIVE),
            coopPeers = listOf(CoopPeerState(peerId = peerId, isDowned = true, reviveProgress = 0f))
        )

        // base speed = 0.333/sec, mult = 2.0, dt = 0.1sec -> increment = 0.0666
        val newState = PerkQuickReviveMachine.computeReviveProgress(state, 0.1f, peerId)
        val peer = newState.coopPeers.find { it.peerId == peerId }!!

        assertTrue(peer.reviveProgress > 0.066f && peer.reviveProgress < 0.067f)
    }

    @Test
    fun testCoopRevive_CompletesRevive() {
        val peerId = "peer1"
        val state = GameSessionState(
            activePerks = setOf(PerkType.QUICK_REVIVE),
            coopPeers = listOf(CoopPeerState(peerId = peerId, isDowned = true, reviveProgress = 0.95f, maxHp = 100))
        )

        val newState = PerkQuickReviveMachine.computeReviveProgress(state, 0.1f, peerId)
        val peer = newState.coopPeers.find { it.peerId == peerId }!!

        assertFalse(peer.isDowned)
        assertEquals(0f, peer.reviveProgress, 0.001f)
        assertEquals(100, peer.hp)
    }

    @Test
    fun testComputeRevive_ClampsNaNAndInfinity() {
        val peerId = "peer1"
        val state = GameSessionState(
            coopPeers = listOf(CoopPeerState(peerId = peerId, isDowned = true, reviveProgress = 0f))
        )

        // NaN should clamp to 0.001f
        var newState = PerkQuickReviveMachine.computeReviveProgress(state, Float.NaN, peerId)
        var peer = newState.coopPeers.find { it.peerId == peerId }!!
        assertTrue(peer.reviveProgress > 0f && peer.reviveProgress < 0.001f)

        // Infinity should clamp to 0.001f
        newState = PerkQuickReviveMachine.computeReviveProgress(state, Float.POSITIVE_INFINITY, peerId)
        peer = newState.coopPeers.find { it.peerId == peerId }!!
        assertTrue(peer.reviveProgress > 0f && peer.reviveProgress < 0.001f)

        // Value > 0.100f should clamp to 0.100f
        newState = PerkQuickReviveMachine.computeReviveProgress(state, 5.0f, peerId)
        peer = newState.coopPeers.find { it.peerId == peerId }!!
        assertTrue(peer.reviveProgress > 0.033f && peer.reviveProgress < 0.034f) // Corresponds to dt = 0.1f
    }

    @Test
    fun testSerializationRoundtrip() {
        val state = QuickReviveState(soloRevivesUsed = 2, isAvailable = false)
        val map = state.toJsonMap()

        val deserialized = QuickReviveState.fromJsonMap(map)

        assertEquals(state, deserialized)
        assertEquals(2, deserialized.soloRevivesUsed)
        assertFalse(deserialized.isAvailable)
    }
}