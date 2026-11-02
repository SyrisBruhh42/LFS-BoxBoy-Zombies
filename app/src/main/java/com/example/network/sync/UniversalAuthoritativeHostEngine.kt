package com.example.network.sync

import com.example.game.relics.PassiveRelicStackState
import com.example.game.relics.Ror2PassiveRelicSystem
import com.example.game.world.elona.AssembledSectionReceipt
import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.SparseWorldStateCache
import org.json.JSONArray
import org.json.JSONObject

/**
 * Track 7: Universal Authoritative Host Engine & Risk of Rain 2 Mid-Session Drop-In Synchronizer.
 * Runs identically on the Dedicated Backend Server (`U3 :19448` / Web Server) OR embedded inside
 * any mobile device acting as an Offline Local Host over USB, Wi-Fi LAN, or Bluetooth.
 */
data class HotJoinDropPodPackage(
    val joinerPeerId: String,
    val joinedAtWave: Int,
    val dropPodSpawnX: Float,
    val dropPodSpawnY: Float,
    val impactShockwaveRadiusPx: Float = 180f,
    val impactShockwaveDamage: Int = 320,
    val waveScaledStartingCurrency: Int,
    val catchUpRelics: PassiveRelicStackState,
    val worldReceiptTopologyHash: String,
    val compactDeltaPayloadBytes: Int
)

object UniversalAuthoritativeHostEngine {
    const val PROTOCOL_VERSION = "BoxBoysNetProtocol-v3"
    const val BASE_STARTING_CURRENCY = 500
    const val CURRENCY_STIPEND_PER_WAVE = 650

    /**
     * Computes the Risk of Rain 2 style Mid-Session Drop-In package so a player joining at Wave W
     * lands near the squad leader in an Orbital Drop-Pod with enough single-currency points to
     * immediately hit the nearest Borderlands/BO2 Vending Machine + 1 random RoR2 Passive Relic
     * per 5 waves elapsed.
     */
    fun buildMidSessionHotJoinPackage(
        joinerPeerId: String,
        currentWave: Int,
        squadLeaderX: Float,
        squadLeaderY: Float,
        worldReceipt: AssembledSectionReceipt,
        stateCache: SparseWorldStateCache
    ): HotJoinDropPodPackage {
        val safeWave = currentWave.coerceAtLeast(1)
        val stipend = BASE_STARTING_CURRENCY + (safeWave - 1) * CURRENCY_STIPEND_PER_WAVE
        val catchUpRelics = Ror2PassiveRelicSystem.rollMidSessionCatchUpRelics(
            wave = safeWave,
            seed = worldReceipt.seed xor joinerPeerId.hashCode().toLong()
        )
        val deltaJson = stateCache.serializeCompactJson()
        val payloadBytes = deltaJson.toByteArray(Charsets.UTF_8).size

        return HotJoinDropPodPackage(
            joinerPeerId = joinerPeerId,
            joinedAtWave = safeWave,
            dropPodSpawnX = squadLeaderX + 48f,
            dropPodSpawnY = squadLeaderY - 32f,
            waveScaledStartingCurrency = stipend,
            catchUpRelics = catchUpRelics,
            worldReceiptTopologyHash = worldReceipt.topologyHash,
            compactDeltaPayloadBytes = payloadBytes
        )
    }

    /**
     * Serializes the full hot-join world sync envelope (`AssembledSectionReceipt` + `SparseWorldStateCache`)
     * guaranteed to fit under the 15 KB (`MAX_SYNC_PAYLOAD_BYTES`) budget for instant Bluetooth/USB/Wi-Fi transfer.
     */
    fun serializeHotJoinWorldEnvelope(
        worldReceipt: AssembledSectionReceipt,
        stateCache: SparseWorldStateCache,
        currentWave: Int
    ): String {
        val encoded = buildString {
            append("{\"proto\":\"").append(PROTOCOL_VERSION).append('"')
            append(",\"wave\":").append(currentWave)
            append(",\"seed\":").append(worldReceipt.seed)
            append(",\"hash\":\"").append(worldReceipt.topologyHash).append('"')
            append(",\"nodes\":[")
            for (i in worldReceipt.placedNodes.indices) {
                if (i > 0) append(',')
                val n = worldReceipt.placedNodes[i]
                append("{\"id\":\"").append(n.nodeId).append('"')
                append(",\"cid\":\"").append(n.componentId).append('"')
                append(",\"gx\":").append(n.gridX)
                append(",\"gy\":").append(n.gridY)
                append(",\"rot\":").append(n.rotationSteps)
                append(",\"u\":").append(n.unlocked)
                append('}')
            }
            append("],\"stateDelta\":").append(stateCache.serializeCompactJson()).append('}')
        }
        require(encoded.toByteArray(Charsets.UTF_8).size <= ElonaSocketFrontierEngine.MAX_SYNC_PAYLOAD_BYTES) {
            "Hot-join envelope exceeded 15 KB budget"
        }
        return encoded
    }
}
