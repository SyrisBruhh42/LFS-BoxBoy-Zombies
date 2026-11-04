package com.example.game.economy.vendors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CoopDoorSplitContributionTest {

    private fun createInitialState(
        doorId: String = "door_1",
        targetRoomIndex: Int = 1,
        totalCost: Int = 1000
    ) = CoopDoorState(
        doorId = doorId,
        targetRoomIndex = targetRoomIndex,
        totalCost = totalCost,
        currentContribution = 0,
        isUnlocked = false,
        contributionsByPeer = emptyMap()
    )

    // 1. Initialization Test: Normal
    @Test
    fun testInitializationNormal() {
        val state = createInitialState(totalCost = 500)
        assertEquals("door_1", state.doorId)
        assertEquals(500, state.totalCost)
        assertEquals(0, state.currentContribution)
        assertFalse(state.isUnlocked)
        assertTrue(state.contributionsByPeer.isEmpty())
    }

    // 2. Initialization Test: Boundary - Negative Cost
    @Test
    fun testInitializationNegativeCost() {
        try {
            createInitialState(totalCost = 0)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }

        try {
            createInitialState(totalCost = -100)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Success
        }
    }

    // 3. Normal Contribution Path
    @Test
    fun testContributeNormal() {
        val initialState = createInitialState(totalCost = 1000)
        val result = CoopDoorSplitContributionEngine.contribute(
            state = initialState,
            peerId = "peer_A",
            amount = 250,
            playerPoints = 500
        )

        assertTrue(result.success)
        assertEquals(250, result.amountContributed)
        assertEquals(250, result.updatedState.currentContribution)
        assertEquals(250, result.remainingPlayerPoints)
        assertFalse(result.isNewlyUnlocked)
        assertEquals(250, result.updatedState.contributionsByPeer["peer_A"])
    }

    // 4. Exact Door Unlock Path
    @Test
    fun testContributeUnlockExact() {
        val initialState = createInitialState(totalCost = 500)
        val result = CoopDoorSplitContributionEngine.contribute(
            state = initialState,
            peerId = "peer_A",
            amount = 500,
            playerPoints = 500
        )

        assertTrue(result.success)
        assertEquals(500, result.amountContributed)
        assertEquals(500, result.updatedState.currentContribution)
        assertEquals(0, result.remainingPlayerPoints)
        assertTrue(result.isNewlyUnlocked)
        assertTrue(result.updatedState.isUnlocked)
    }

    // 5. Excess Contribution Cap Path
    @Test
    fun testContributeExcessCapped() {
        val initialState = CoopDoorState(
            doorId = "door_1",
            targetRoomIndex = 1,
            totalCost = 1000,
            currentContribution = 900,
            isUnlocked = false,
            contributionsByPeer = mapOf("peer_A" to 900)
        )

        val result = CoopDoorSplitContributionEngine.contribute(
            state = initialState,
            peerId = "peer_B",
            amount = 500,
            playerPoints = 1000
        )

        assertTrue(result.success)
        assertEquals(100, result.amountContributed) // Capped at 100
        assertEquals(1000, result.updatedState.currentContribution)
        assertEquals(900, result.remainingPlayerPoints)
        assertTrue(result.isNewlyUnlocked)
        assertEquals(100, result.updatedState.contributionsByPeer["peer_B"])
    }

    // 6. Insufficient Funds Path
    @Test
    fun testContributeInsufficientFunds() {
        val initialState = createInitialState(totalCost = 1000)
        val result = CoopDoorSplitContributionEngine.contribute(
            state = initialState,
            peerId = "peer_A",
            amount = 500,
            playerPoints = 0
        )

        assertFalse(result.success)
        assertEquals(0, result.amountContributed)
        assertEquals(0, result.remainingPlayerPoints)
        assertEquals(0, result.updatedState.currentContribution)
    }

    // 7. Negative Input Amount Path
    @Test
    fun testContributeNegativeAmount() {
        val initialState = createInitialState(totalCost = 1000)
        val result = CoopDoorSplitContributionEngine.contribute(
            state = initialState,
            peerId = "peer_A",
            amount = -100,
            playerPoints = 500
        )

        assertFalse(result.success)
        assertEquals(0, result.amountContributed)
        assertEquals(500, result.remainingPlayerPoints)
    }

    // 8. Contribute Max Possible Function
    @Test
    fun testContributeMaxPossible() {
        val initialState = createInitialState(totalCost = 1000)
        val result = CoopDoorSplitContributionEngine.contributeMaxPossible(
            state = initialState,
            peerId = "peer_A",
            playerPoints = 750
        )

        assertTrue(result.success)
        assertEquals(750, result.amountContributed)
        assertEquals(750, result.updatedState.currentContribution)
        assertEquals(0, result.remainingPlayerPoints)
    }

    // 9. Already Unlocked Path
    @Test
    fun testContributeAlreadyUnlocked() {
        val unlockedState = CoopDoorState(
            doorId = "door_1",
            targetRoomIndex = 1,
            totalCost = 1000,
            currentContribution = 1000,
            isUnlocked = true,
            contributionsByPeer = mapOf("peer_A" to 1000)
        )

        val result = CoopDoorSplitContributionEngine.contribute(
            state = unlockedState,
            peerId = "peer_B",
            amount = 100,
            playerPoints = 500
        )

        assertFalse(result.success)
        assertEquals(0, result.amountContributed)
        assertEquals(500, result.remainingPlayerPoints)
    }

    // 10. Serialization Roundtrip Parity
    @Test
    fun testSerializationRoundtrip() {
        val originalState = CoopDoorState(
            doorId = "door_X",
            targetRoomIndex = 5,
            totalCost = 1250,
            currentContribution = 750,
            isUnlocked = false,
            contributionsByPeer = mapOf("peer_A" to 250, "peer_B" to 500)
        )

        val map = originalState.toMap()
        val deserializedState = CoopDoorState.fromMap(map)

        assertEquals(originalState, deserializedState)
    }

    // 11. Serialization Malformed Types Recovery (NaN/Infinity implicitly via map casting in fromMap)
    @Test
    fun testSerializationMalformedRecovery() {
        val badMap = mapOf<String, Any?>(
            "doorId" to null,
            "targetRoomIndex" to "invalid",
            "totalCost" to -500,
            "currentContribution" to 9999, // Exceeds totalCost after recovery
            "isUnlocked" to "not_a_boolean",
            "contributionsByPeer" to mapOf("peer_A" to "invalid_number")
        )

        val recoveredState = CoopDoorState.fromMap(badMap as Map<String, Any>)

        assertEquals("", recoveredState.doorId)
        assertEquals(0, recoveredState.targetRoomIndex)
        assertEquals(1, recoveredState.totalCost) // Coerced to at least 1
        assertEquals(1, recoveredState.currentContribution) // Coerced to fit in 0..totalCost
        assertFalse(recoveredState.isUnlocked)
        assertTrue(recoveredState.contributionsByPeer.isEmpty())
    }

    // 12. FNV-1a Checksum Determinism
    @Test
    fun testChecksumDeterminism() {
        val state1 = CoopDoorState(
            doorId = "door_1",
            targetRoomIndex = 1,
            totalCost = 1000,
            currentContribution = 500,
            isUnlocked = false,
            contributionsByPeer = mapOf("peer_A" to 200, "peer_B" to 300)
        )

        val state2 = CoopDoorState(
            doorId = "door_1",
            targetRoomIndex = 1,
            totalCost = 1000,
            currentContribution = 500,
            isUnlocked = false,
            contributionsByPeer = mapOf("peer_B" to 300, "peer_A" to 200) // Different order
        )

        val hash1 = CoopDoorSplitContributionEngine.computeChecksum(state1)
        val hash2 = CoopDoorSplitContributionEngine.computeChecksum(state2)

        assertEquals("Hashes must be identical regardless of map entry order", hash1, hash2)
    }

    // 13. Stress Test: Many Micro-Contributions
    @Test
    fun testStressMicroContributions() {
        var state = createInitialState(totalCost = 2500)

        // 250 micro-contributions of 10 points each
        for (i in 1..250) {
            val peer = "peer_${i % 4}" // 4 peers
            val result = CoopDoorSplitContributionEngine.contribute(
                state = state,
                peerId = peer,
                amount = 10,
                playerPoints = 10
            )
            assertTrue(result.success)
            state = result.updatedState
        }

        assertTrue(state.isUnlocked)
        assertEquals(2500, state.currentContribution)
        // Check peers contributions
        assertEquals(630, state.contributionsByPeer["peer_1"]) // 250/4 = 62.5 * 10 = 625 (roughly, checking if logic is sound)
        assertEquals(620, state.contributionsByPeer["peer_0"])
        assertEquals(630, state.contributionsByPeer["peer_2"])
        assertEquals(620, state.contributionsByPeer["peer_3"])
    }
}
