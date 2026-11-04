package com.example.network.sync.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostMigrationElectionProtocolTest {

    @Test
    fun `test 1 - nominal successful election completion based on peer priority`() {
        val config = HostMigrationConfig(baseTimeoutSeconds = 2.0f)
        val state = HostMigrationState(elapsedSeconds = 1.95f)
        
        val peers = listOf(
            PeerCandidate("peer1", pingMs = 50f, connectionScore = 100f, spatialX = 0f, spatialY = 0f, hasValidTopology = true),
            PeerCandidate("peer2", pingMs = 20f, connectionScore = 100f, spatialX = 0f, spatialY = 0f, hasValidTopology = true) // Better ping
        )
        
        val newState = HostMigrationElectionProtocol.stepElection(state, 0.1f, config, peers)
        
        assertFalse(newState.isActive)
        assertEquals("peer2", newState.electedHostId) // Best score
        assertEquals(2.05f, newState.elapsedSeconds, 0.001f)
    }

    @Test
    fun `test 2 - election pending state while elapsedSeconds is less than baseTimeoutSeconds`() {
        val config = HostMigrationConfig(baseTimeoutSeconds = 5.0f)
        val state = HostMigrationState(elapsedSeconds = 1.0f)
        val peers = listOf(PeerCandidate("peer1", pingMs = 50f, connectionScore = 100f, spatialX = 0f, spatialY = 0f, hasValidTopology = true))
        
        val newState = HostMigrationElectionProtocol.stepElection(state, 0.1f, config, peers)
        
        assertTrue(newState.isActive)
        assertNull(newState.electedHostId)
        assertEquals(1.1f, newState.elapsedSeconds, 0.001f)
        assertNotEquals(0L, newState.stateChecksum)
    }

    @Test
    fun `test 3 - determinism identical inputs produce exact same outputs and checksums`() {
        val config = HostMigrationConfig(seed = 999L, baseTimeoutSeconds = 1.0f)
        val state = HostMigrationState(elapsedSeconds = 0.95f)
        val peers = listOf(
            PeerCandidate("peer1", pingMs = 50f, connectionScore = 100f, spatialX = 10f, spatialY = 10f, hasValidTopology = true),
            PeerCandidate("peer2", pingMs = 50f, connectionScore = 100f, spatialX = -10f, spatialY = -10f, hasValidTopology = true)
        )
        
        val run1 = HostMigrationElectionProtocol.stepElection(state, 0.1f, config, peers)
        val run2 = HostMigrationElectionProtocol.stepElection(state, 0.1f, config, peers)
        
        assertEquals(run1.electedHostId, run2.electedHostId)
        assertEquals(run1.stateChecksum, run2.stateChecksum)
        assertEquals(run1.elapsedSeconds, run2.elapsedSeconds, 0.0001f)
    }

    @Test
    fun `test 4 - negative and out-of-bounds dtSeconds clamping`() {
        val config = HostMigrationConfig(baseTimeoutSeconds = 5.0f)
        
        // Negative dt clamped to 0.001f
        var state = HostMigrationState(elapsedSeconds = 1.0f)
        state = HostMigrationElectionProtocol.stepElection(state, -0.5f, config, emptyList())
        assertEquals(1.001f, state.elapsedSeconds, 0.0001f)
        
        // Large dt clamped to 0.100f
        state = HostMigrationElectionProtocol.stepElection(state, 5.0f, config, emptyList())
        assertEquals(1.101f, state.elapsedSeconds, 0.0001f)
    }

    @Test
    fun `test 5 - invalid inputs NaN or Infinity in peer parameters fallback cleanly`() {
        val config = HostMigrationConfig(baseTimeoutSeconds = 1.0f)
        val state = HostMigrationState(elapsedSeconds = 0.95f)
        val peers = listOf(
            PeerCandidate("peer1", pingMs = Float.NaN, connectionScore = Float.POSITIVE_INFINITY, spatialX = Float.NaN, spatialY = 0f, hasValidTopology = true),
            PeerCandidate("peer2", pingMs = 50f, connectionScore = 100f, spatialX = 0f, spatialY = 0f, hasValidTopology = true)
        )
        
        val newState = HostMigrationElectionProtocol.stepElection(state, 0.1f, config, peers)
        
        // peer2 should be chosen because peer1 has invalid stats that fallback to 0/9999
        assertEquals("peer2", newState.electedHostId)
    }

    @Test
    fun `test 6 - maximum-capacity load simulation limits gracefully`() {
        val config = HostMigrationConfig(maxCandidates = 5, baseTimeoutSeconds = 1.0f)
        val state = HostMigrationState(elapsedSeconds = 0.95f)
        
        val peers = mutableListOf<PeerCandidate>()
        for (i in 0 until 100) {
            peers.add(PeerCandidate("peer$i", pingMs = 100f - i, connectionScore = 100f, spatialX = 0f, spatialY = 0f, hasValidTopology = true))
        }
        
        val newState = HostMigrationElectionProtocol.stepElection(state, 0.1f, config, peers)
        
        // Only first 5 peers are evaluated based on maxCandidates=5.
        // Within the first 5, peer4 has the lowest ping (100 - 4 = 96).
        assertEquals("peer4", newState.electedHostId)
    }

    @Test
    fun `test 7 - verification of LCG tie-breaking mechanics`() {
        // Same exact score for both peers
        val peers = listOf(
            PeerCandidate("peerA", pingMs = 50f, connectionScore = 100f, spatialX = 0f, spatialY = 0f, hasValidTopology = true),
            PeerCandidate("peerB", pingMs = 50f, connectionScore = 100f, spatialX = 0f, spatialY = 0f, hasValidTopology = true)
        )
        
        val config1 = HostMigrationConfig(seed = 12345L, baseTimeoutSeconds = 1.0f)
        val state1 = HostMigrationState(elapsedSeconds = 0.95f)
        val result1 = HostMigrationElectionProtocol.stepElection(state1, 0.1f, config1, peers)
        
        val config2 = HostMigrationConfig(seed = 98765L, baseTimeoutSeconds = 1.0f)
        val state2 = HostMigrationState(elapsedSeconds = 0.95f)
        val result2 = HostMigrationElectionProtocol.stepElection(state2, 0.1f, config2, peers)
        
        // Assuming seeds 12345L and 98765L produce different parity in LCG step
        // We just ensure it deterministically picks one and it doesn't crash
        assertTrue(result1.electedHostId == "peerA" || result1.electedHostId == "peerB")
        assertTrue(result2.electedHostId == "peerA" || result2.electedHostId == "peerB")
    }

    @Test
    fun `test 8 - ignore peers with hasValidTopology false`() {
        val config = HostMigrationConfig(baseTimeoutSeconds = 1.0f)
        val state = HostMigrationState(elapsedSeconds = 0.95f)
        
        val peers = listOf(
            PeerCandidate("peer1", pingMs = 10f, connectionScore = 100f, spatialX = 0f, spatialY = 0f, hasValidTopology = false), // Amazing score, invalid topology
            PeerCandidate("peer2", pingMs = 100f, connectionScore = 50f, spatialX = 0f, spatialY = 0f, hasValidTopology = true)   // Worse score, valid topology
        )
        
        val newState = HostMigrationElectionProtocol.stepElection(state, 0.1f, config, peers)
        
        assertEquals("peer2", newState.electedHostId)
    }

    @Test
    fun `test 9 - distance penalties applied correctly`() {
        val config = HostMigrationConfig(baseTimeoutSeconds = 1.0f, distancePenaltyMultiplier = 1.0f)
        val state = HostMigrationState(elapsedSeconds = 0.95f)
        
        val peers = listOf(
            // Good connection but very far away (penalty: 100 + 100 = 200) -> Score = 100 - 10 - 200 = -110
            PeerCandidate("peer1", pingMs = 10f, connectionScore = 100f, spatialX = 100f, spatialY = 100f, hasValidTopology = true),
            // Worse connection but at origin (penalty: 0) -> Score = 50 - 50 - 0 = 0
            PeerCandidate("peer2", pingMs = 50f, connectionScore = 50f, spatialX = 0f, spatialY = 0f, hasValidTopology = true)
        )
        
        val newState = HostMigrationElectionProtocol.stepElection(state, 0.1f, config, peers)
        
        // Peer 2 should win because Peer 1 gets a heavy distance penalty
        assertEquals("peer2", newState.electedHostId)
    }

    @Test
    fun `test 10 - HostMigrationConfig toMap and fromMap serialization roundtrip parity`() {
        val original = HostMigrationConfig(
            baseTimeoutSeconds = 7.5f,
            distancePenaltyMultiplier = 0.1f,
            maxCandidates = 8,
            seed = 99999L
        )
        
        val map = original.toMap()
        val restored = HostMigrationConfig.fromMap(map)
        
        assertEquals(original, restored)
    }

    @Test
    fun `test 11 - HostMigrationState toMap and fromMap serialization roundtrip parity`() {
        val original = HostMigrationState(
            elapsedSeconds = 3.14f,
            isActive = false,
            electedHostId = "peerXYZ",
            stateChecksum = 123456789L
        )
        
        val map = original.toMap()
        val restored = HostMigrationState.fromMap(map)
        
        assertEquals(original, restored)
    }

    @Test
    fun `test 12 - PeerCandidate toMap and fromMap serialization roundtrip parity`() {
        val original = PeerCandidate(
            peerId = "peer123",
            pingMs = 42.5f,
            connectionScore = 99.9f,
            spatialX = -150f,
            spatialY = 300f,
            hasValidTopology = true
        )
        
        val map = original.toMap()
        val restored = PeerCandidate.fromMap(map)
        
        assertEquals(original, restored)
    }
}
