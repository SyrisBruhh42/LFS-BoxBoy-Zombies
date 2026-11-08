package com.example.network.sync

import com.example.game.relics.PassiveRelicStackState
import com.example.game.relics.Ror2PassiveRelicSystem
import com.example.game.world.elona.AssembledSectionReceipt
import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.SparseWorldStateCache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.math.roundToInt

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

/**
 * 8-Byte Quantized Zombie Delta entry (`uint16 id, int16 xDeciPx, int16 yDeciPx, uint16 hp`)
 * compressing 120 zombies from `11,040 B` verbose JSON down to `960 B` (`<1 KB` single MTU frame, `FP-N06`).
 */
data class QuantizedZombieSample(
    val id: Int,
    val x: Float,
    val y: Float,
    val hp: Int
)

object UniversalAuthoritativeHostEngine {
    const val PROTOCOL_VERSION = "BoxBoysNetProtocol-v3"
    const val BASE_STARTING_CURRENCY = 500
    const val CURRENCY_STIPEND_PER_WAVE = 650
    const val BYTES_PER_QUANTIZED_ZOMBIE = 8
    const val MAX_QUANTIZED_DELTA_BYTES = 15_360
    const val MAX_QUANTIZED_ZOMBIES = MAX_QUANTIZED_DELTA_BYTES / BYTES_PER_QUANTIZED_ZOMBIE

    private fun quantizeDeciCoordinate(coord: Float): Short {
        if (!coord.isFinite()) return 0
        val scaled = (coord * 10f).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
        return scaled.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    /**
     * Packs a list of [QuantizedZombieSample] into a compact binary delta (`8` bytes per zombie:
     * `uint16 id, int16 xDeciPx, int16 yDeciPx, uint16 hp`).
     * 120 zombies = `960` bytes (`< 1 KB`).
     */
    fun packQuantizedZombieDelta(zombies: List<QuantizedZombieSample>): ByteArray {
        val boundedZombies = if (zombies.size > MAX_QUANTIZED_ZOMBIES) {
            zombies.take(MAX_QUANTIZED_ZOMBIES)
        } else {
            zombies
        }
        val buf = ByteBuffer.allocate(boundedZombies.size * BYTES_PER_QUANTIZED_ZOMBIE)
            .order(ByteOrder.BIG_ENDIAN)
        for (z in boundedZombies) {
            val clampedId = z.id.coerceIn(0, 65_535)
            val xDeci = quantizeDeciCoordinate(z.x)
            val yDeci = quantizeDeciCoordinate(z.y)
            val clampedHp = z.hp.coerceIn(0, 65_535)
            buf.putShort(clampedId.toShort())
            buf.putShort(xDeci)
            buf.putShort(yDeci)
            buf.putShort(clampedHp.toShort())
        }
        return buf.array()
    }

    /**
     * Unpacks a binary delta produced by [packQuantizedZombieDelta] back into [QuantizedZombieSample] records.
     * Rejects truncated (`bytes.size % 8 != 0`) or oversized (`> 15,360 B`) payloads (`BB-V4-D00-04bafc85`).
     */
    fun unpackQuantizedZombieDelta(bytes: ByteArray): List<QuantizedZombieSample> {
        if (bytes.isEmpty() || bytes.size > MAX_QUANTIZED_DELTA_BYTES || bytes.size % BYTES_PER_QUANTIZED_ZOMBIE != 0) {
            return emptyList()
        }
        val count = bytes.size / BYTES_PER_QUANTIZED_ZOMBIE
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val out = ArrayList<QuantizedZombieSample>(count)
        repeat(count) {
            val id = buf.short.toInt() and 0xFFFF
            val xDeci = buf.short.toInt()
            val yDeci = buf.short.toInt()
            val hp = buf.short.toInt() and 0xFFFF
            out.add(
                QuantizedZombieSample(
                    id = id,
                    x = xDeci / 10f,
                    y = yDeci / 10f,
                    hp = hp
                )
            )
        }
        return out
    }

    /**
     * Encodes [zombies] as a Base64 wire frame (`1,280` chars for 120 zombies, fitting inside a single 1,500 B MTU frame).
     */
    fun packQuantizedZombieDeltaBase64(zombies: List<QuantizedZombieSample>): String {
        return Base64.getEncoder().encodeToString(packQuantizedZombieDelta(zombies))
    }

    fun unpackQuantizedZombieDeltaBase64(encodedB64: String): List<QuantizedZombieSample> {
        val trimmed = encodedB64.trim()
        if (trimmed.isEmpty() || trimmed.length > 20_480) return emptyList()
        val decoded = runCatching { Base64.getDecoder().decode(trimmed) }.getOrNull() ?: return emptyList()
        return unpackQuantizedZombieDelta(decoded)
    }

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

