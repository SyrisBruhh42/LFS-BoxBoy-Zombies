package com.example.network.sync.modules

import androidx.annotation.Keep
import kotlin.math.abs

/**
 * Configuration for the Host Migration Election process.
 */
@Keep
data class HostMigrationConfig(
    val baseTimeoutSeconds: Float = 5.0f,
    val distancePenaltyMultiplier: Float = 0.05f,
    val maxCandidates: Int = 16,
    val seed: Long = 12345L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "baseTimeoutSeconds" to baseTimeoutSeconds,
        "distancePenaltyMultiplier" to distancePenaltyMultiplier,
        "maxCandidates" to maxCandidates,
        "seed" to seed
    )

    companion object {
        fun fromMap(map: Map<String, Any>): HostMigrationConfig = HostMigrationConfig(
            baseTimeoutSeconds = (map["baseTimeoutSeconds"] as? Number)?.toFloat() ?: 5.0f,
            distancePenaltyMultiplier = (map["distancePenaltyMultiplier"] as? Number)?.toFloat() ?: 0.05f,
            maxCandidates = (map["maxCandidates"] as? Number)?.toInt() ?: 16,
            seed = (map["seed"] as? Number)?.toLong() ?: 12345L
        )
    }
}

/**
 * Represents a peer candidate competing in the election.
 */
@Keep
data class PeerCandidate(
    val peerId: String,
    val pingMs: Float,
    val connectionScore: Float,
    val spatialX: Float,
    val spatialY: Float,
    val hasValidTopology: Boolean
) {
    fun toMap(): Map<String, Any> = mapOf(
        "peerId" to peerId,
        "pingMs" to pingMs,
        "connectionScore" to connectionScore,
        "spatialX" to spatialX,
        "spatialY" to spatialY,
        "hasValidTopology" to hasValidTopology
    )

    companion object {
        fun fromMap(map: Map<String, Any>): PeerCandidate = PeerCandidate(
            peerId = map["peerId"] as? String ?: "",
            pingMs = (map["pingMs"] as? Number)?.toFloat() ?: 9999f,
            connectionScore = (map["connectionScore"] as? Number)?.toFloat() ?: 0f,
            spatialX = (map["spatialX"] as? Number)?.toFloat() ?: 0f,
            spatialY = (map["spatialY"] as? Number)?.toFloat() ?: 0f,
            hasValidTopology = map["hasValidTopology"] as? Boolean ?: false
        )
    }
}

/**
 * The state of the host migration election.
 */
@Keep
data class HostMigrationState(
    val elapsedSeconds: Float = 0f,
    val isActive: Boolean = true,
    val electedHostId: String? = null,
    val stateChecksum: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "elapsedSeconds" to elapsedSeconds,
        "isActive" to isActive,
        "electedHostId" to (electedHostId ?: ""),
        "stateChecksum" to stateChecksum
    )

    companion object {
        fun fromMap(map: Map<String, Any>): HostMigrationState = HostMigrationState(
            elapsedSeconds = (map["elapsedSeconds"] as? Number)?.toFloat() ?: 0f,
            isActive = map["isActive"] as? Boolean ?: false,
            electedHostId = (map["electedHostId"] as? String)?.takeIf { it.isNotEmpty() },
            stateChecksum = (map["stateChecksum"] as? Number)?.toLong() ?: 0L
        )
    }
}

/**
 * Core engine for deterministic host migration elections.
 */
object HostMigrationElectionProtocol {

    private fun fnv1aChecksum(seed: Long, elapsed: Float, active: Boolean, elected: String?): Long {
        var hash = -3750763034362895579L

        // Include seed
        hash = hash xor seed
        hash = hash * 1099511628211L

        // Include elapsed
        val bits = elapsed.toRawBits().toLong() and 0xFFFFFFFFL
        hash = hash xor bits
        hash = hash * 1099511628211L

        // Include active
        hash = hash xor (if (active) 1L else 0L)
        hash = hash * 1099511628211L

        // Include elected string length + characters
        if (elected != null) {
            for (i in elected.indices) {
                hash = hash xor elected[i].code.toLong()
                hash = hash * 1099511628211L
            }
        } else {
            hash = hash xor 0L
            hash = hash * 1099511628211L
        }

        return hash
    }

    /**
     * Steps the election state forward by [dtSeconds].
     * Enforces zero per-frame heap churn.
     */
    fun stepElection(
        state: HostMigrationState,
        dtSeconds: Float,
        config: HostMigrationConfig,
        peers: List<PeerCandidate>
    ): HostMigrationState {
        if (!state.isActive) {
            return state
        }

        var validDt = if (dtSeconds.isFinite()) dtSeconds else 0f
        if (validDt < 0.001f) validDt = 0.001f
        if (validDt > 0.100f) validDt = 0.100f

        val newElapsed = state.elapsedSeconds + validDt

        if (newElapsed < config.baseTimeoutSeconds) {
            val newChecksum = fnv1aChecksum(config.seed, newElapsed, true, null)
            return HostMigrationState(
                elapsedSeconds = newElapsed,
                isActive = true,
                electedHostId = null,
                stateChecksum = newChecksum
            )
        }

        // Election timeout reached, determine host.
        // We evaluate up to config.maxCandidates peers without creating new arrays if possible,
        // though we must process the incoming List.

        var bestPeerId: String? = null
        var bestScore = -Float.MAX_VALUE

        // Simple LCG state for deterministic tie-breaking based on config seed
        var lcgState = config.seed

        val peerCount = if (peers.size > config.maxCandidates) config.maxCandidates else peers.size

        for (i in 0 until peerCount) {
            val peer = peers[i]
            if (!peer.hasValidTopology) continue

            val px = if (peer.spatialX.isFinite()) peer.spatialX else 0f
            val py = if (peer.spatialY.isFinite()) peer.spatialY else 0f
            val pp = if (peer.pingMs.isFinite() && peer.pingMs >= 0f) peer.pingMs else 9999f
            val pc = if (peer.connectionScore.isFinite()) peer.connectionScore else 0f

            // Simple Manhattan distance from 0,0 for penalty
            val dist = abs(px) + abs(py)
            val distPenalty = dist * config.distancePenaltyMultiplier

            // Score = Connection Score - Ping - Distance Penalty
            val score = pc - pp - distPenalty

            if (score > bestScore) {
                bestScore = score
                bestPeerId = peer.peerId
            } else if (score == bestScore && bestPeerId != null) {
                // Deterministic LCG tie-breaker
                lcgState = (lcgState * 1103515245L + 12345L) and 0x7fffffffL
                val tieBreakResult = (lcgState % 2L) == 0L
                if (tieBreakResult) {
                    bestPeerId = peer.peerId
                }
            }
        }

        // Fallback LCG if no best peer found
        if (bestPeerId == null && peerCount > 0) {
             lcgState = (lcgState * 1103515245L + 12345L) and 0x7fffffffL
             val fallbackIndex = (abs(lcgState) % peerCount).toInt()
             bestPeerId = peers[fallbackIndex].peerId
        }

        val finalChecksum = fnv1aChecksum(config.seed, newElapsed, false, bestPeerId)

        return HostMigrationState(
            elapsedSeconds = newElapsed,
            isActive = false,
            electedHostId = bestPeerId,
            stateChecksum = finalChecksum
        )
    }
}
