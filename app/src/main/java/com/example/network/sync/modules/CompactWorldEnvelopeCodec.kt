package com.example.network.sync.modules

import androidx.annotation.Keep
import com.example.game.world.elona.AssembledSectionReceipt
import com.example.game.world.elona.SparseWorldStateCache
import com.example.game.world.elona.PlacedSectionNode
import com.example.game.world.elona.FrontierDoorSocket
import com.example.game.world.elona.ShortcutLoopGate
import com.example.game.world.elona.SparseTileMutation
import com.example.game.world.elona.ChunkMutationType
import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Keep
data class CompactWorldEnvelopeConfig(
    val maxPayloadBytes: Int = 15_000,
    val maxRooms: Int = 150,
    val maxZombies: Int = 300,
    val enableChecksum: Boolean = true
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "maxPayloadBytes" to maxPayloadBytes,
            "maxRooms" to maxRooms,
            "maxZombies" to maxZombies,
            "enableChecksum" to enableChecksum
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): CompactWorldEnvelopeConfig {
            return CompactWorldEnvelopeConfig(
                maxPayloadBytes = (map["maxPayloadBytes"] as? Number)?.toInt() ?: 15_000,
                maxRooms = (map["maxRooms"] as? Number)?.toInt() ?: 150,
                maxZombies = (map["maxZombies"] as? Number)?.toInt() ?: 300,
                enableChecksum = map["enableChecksum"] as? Boolean ?: true
            )
        }
    }
}

@Keep
data class CompactWorldEnvelopeSnapshot(
    val checksum: Long,
    val isClamped: Boolean,
    val payloadSize: Int
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "checksum" to checksum,
            "isClamped" to isClamped,
            "payloadSize" to payloadSize
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): CompactWorldEnvelopeSnapshot {
            return CompactWorldEnvelopeSnapshot(
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L,
                isClamped = map["isClamped"] as? Boolean ?: false,
                payloadSize = (map["payloadSize"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

object CompactWorldEnvelopeCodec {
    private const val HEADER_MAGIC = 0x4242454E // 'BBEN'
    private const val PROTOCOL_VERSION = 3

    fun encodeEnvelope(
        receipt: AssembledSectionReceipt,
        cache: SparseWorldStateCache,
        config: CompactWorldEnvelopeConfig = CompactWorldEnvelopeConfig()
    ): ByteArray {
        val placedNodes = receipt.placedNodes.take(config.maxRooms)
        val isClamped = placedNodes.size < receipt.placedNodes.size

        // Pre-allocate a generous scratch buffer, then validate against config.maxPayloadBytes
        val buf = ByteBuffer.allocate(maxOf(config.maxPayloadBytes * 4, 262_144))
        buf.order(ByteOrder.BIG_ENDIAN)

        // Header
        buf.putInt(HEADER_MAGIC)
        buf.putInt(PROTOCOL_VERSION)
        
        // Receipt
        buf.putInt(receipt.schemaVersion)
        buf.putLong(receipt.seed)
        
        // Write short strings with 1-byte length prefix
        writeShortString(buf, receipt.topologyHash)

        // Nodes
        buf.putShort(placedNodes.size.toShort())
        for (node in placedNodes) {
            writeShortString(buf, node.nodeId)
            writeShortString(buf, node.componentId)
            buf.putShort(node.gridX.toShort())
            buf.putShort(node.gridY.toShort())
            buf.put(node.rotationSteps.toByte())
            buf.put(if (node.mirrored) 1 else 0)
            buf.putInt(node.unlockCostCurrency)
            buf.put(if (node.unlocked) 1 else 0)
            
            buf.putShort(node.sockets.size.toShort())
            for (socket in node.sockets) {
                buf.put(socket.direction.ordinal.toByte())
                buf.put(socket.kind.ordinal.toByte())
                buf.putShort(socket.offsetTiles.toShort())
                buf.putInt(socket.unlockCostCurrency)
            }
        }

        // Frontier Sockets
        buf.putShort(receipt.frontierSockets.size.toShort())
        for (frontier in receipt.frontierSockets) {
            writeShortString(buf, frontier.frontierId)
            writeShortString(buf, frontier.parentNodeId)
            buf.putShort(frontier.targetGridX.toShort())
            buf.putShort(frontier.targetGridY.toShort())
            buf.put(frontier.requiredEntryDirection.ordinal.toByte())
            buf.putInt(frontier.unlockCostCurrency)
            buf.put(frontier.socketKind.ordinal.toByte())
        }

        // Shortcut Gates
        buf.putShort(receipt.shortcutGates.size.toShort())
        for (gate in receipt.shortcutGates) {
            writeShortString(buf, gate.gateId)
            writeShortString(buf, gate.fromNodeId)
            writeShortString(buf, gate.toNodeId)
            buf.putShort(gate.loopDistance.toShort())
            buf.putFloat(gate.backtrackSavings)
            buf.putInt(gate.unlockCostCurrency)
            buf.put(if (gate.unlocked) 1 else 0)
        }

        buf.putShort(receipt.reachableVendingCount.toShort())
        buf.put(if (receipt.solvable) 1 else 0)

        // Cache Mutations
        val mutations = cache.allMutations()
        buf.putShort(mutations.size.toShort())
        for (m in mutations) {
            buf.putShort(m.chunkX.toShort())
            buf.putShort(m.chunkY.toShort())
            buf.putShort(m.tileX.toShort())
            buf.putShort(m.tileY.toShort())
            buf.put(m.mutationType.ordinal.toByte())
            buf.putInt(m.intValue)
            writeShortString(buf, m.metadata)
        }

        val limit = buf.position()
        require(limit <= config.maxPayloadBytes) {
            "Payload exceeded budget: $limit > ${config.maxPayloadBytes}"
        }

        val outBytes = ByteArray(limit)
        buf.rewind()
        buf.get(outBytes)

        if (config.enableChecksum) {
            val checksum = computeFnv1a(outBytes)
        }

        return outBytes
    }

    fun decodeEnvelope(
        bytes: ByteArray,
        cacheOutput: SparseWorldStateCache,
        config: CompactWorldEnvelopeConfig = CompactWorldEnvelopeConfig()
    ): AssembledSectionReceipt {
        val buf = ByteBuffer.wrap(bytes)
        buf.order(ByteOrder.BIG_ENDIAN)

        val magic = buf.int
        require(magic == HEADER_MAGIC) { "Invalid magic header" }
        
        val version = buf.int
        require(version == PROTOCOL_VERSION) { "Unsupported protocol version" }

        val schemaVersion = buf.int
        val seed = buf.long
        val topologyHash = readShortString(buf)

        val nodeCount = buf.short.toInt()
        val nodes = ArrayList<PlacedSectionNode>(nodeCount)
        repeat(nodeCount) {
            val nodeId = readShortString(buf)
            val componentId = readShortString(buf)
            val gridX = buf.short.toInt()
            val gridY = buf.short.toInt()
            val rotationSteps = buf.get().toInt()
            val mirrored = buf.get() == 1.toByte()
            val unlockCostCurrency = buf.int
            val unlocked = buf.get() == 1.toByte()
            
            val socketCount = buf.short.toInt()
            val sockets = ArrayList<RoomSocketSpec>(socketCount)
            repeat(socketCount) {
                sockets.add(RoomSocketSpec(
                    direction = SocketDirection.entries[buf.get().toInt()],
                    kind = SocketKind.entries[buf.get().toInt()],
                    offsetTiles = buf.short.toInt(),
                    unlockCostCurrency = buf.int
                ))
            }
            
            nodes.add(PlacedSectionNode(
                nodeId = nodeId,
                componentId = componentId,
                gridX = gridX,
                gridY = gridY,
                rotationSteps = rotationSteps,
                mirrored = mirrored,
                unlockCostCurrency = unlockCostCurrency,
                unlocked = unlocked,
                sockets = sockets
            ))
        }

        val frontierCount = buf.short.toInt()
        val frontiers = ArrayList<FrontierDoorSocket>(frontierCount)
        repeat(frontierCount) {
            frontiers.add(FrontierDoorSocket(
                frontierId = readShortString(buf),
                parentNodeId = readShortString(buf),
                targetGridX = buf.short.toInt(),
                targetGridY = buf.short.toInt(),
                requiredEntryDirection = SocketDirection.entries[buf.get().toInt()],
                unlockCostCurrency = buf.int,
                socketKind = SocketKind.entries[buf.get().toInt()]
            ))
        }

        val gateCount = buf.short.toInt()
        val gates = ArrayList<ShortcutLoopGate>(gateCount)
        repeat(gateCount) {
            gates.add(ShortcutLoopGate(
                gateId = readShortString(buf),
                fromNodeId = readShortString(buf),
                toNodeId = readShortString(buf),
                loopDistance = buf.short.toInt(),
                backtrackSavings = buf.float,
                unlockCostCurrency = buf.int,
                unlocked = buf.get() == 1.toByte()
            ))
        }

        val reachableVendingCount = buf.short.toInt()
        val solvable = buf.get() == 1.toByte()

        val mutationCount = buf.short.toInt()
        repeat(mutationCount) {
            cacheOutput.recordMutation(
                chunkX = buf.short.toInt(),
                chunkY = buf.short.toInt(),
                tileX = buf.short.toInt(),
                tileY = buf.short.toInt(),
                mutationType = ChunkMutationType.entries[buf.get().toInt()],
                intValue = buf.int,
                metadata = readShortString(buf)
            )
        }

        return AssembledSectionReceipt(
            schemaVersion = schemaVersion,
            seed = seed,
            topologyHash = topologyHash,
            placedNodes = nodes,
            frontierSockets = frontiers,
            shortcutGates = gates,
            reachableVendingCount = reachableVendingCount,
            solvable = solvable
        )
    }

    private fun writeShortString(buf: ByteBuffer, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 255) { "String too long for 1-byte length prefix" }
        buf.put(bytes.size.toByte())
        buf.put(bytes)
    }

    private fun readShortString(buf: ByteBuffer): String {
        val len = buf.get().toInt() and 0xFF
        val bytes = ByteArray(len)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    fun computeFnv1a(bytes: ByteArray): Long {
        var hash = -0x340d631b7bdddcdbL
        for (b in bytes) {
            hash = hash xor (b.toLong() and 0xFFL)
            hash = hash * 0x100000001b3L
        }
        return hash
    }
}


fun evaluateCompactWorldEnvelopeCodecLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "CompactWorldEnvelopeCodec") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
