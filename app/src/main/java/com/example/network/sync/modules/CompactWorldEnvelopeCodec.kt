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

        // Pre-allocate a generous buffer, we will slice it down at the end
        val buf = ByteBuffer.allocate(config.maxPayloadBytes)
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
        buf.putInt(placedNodes.size)
        for (node in placedNodes) {
            writeShortString(buf, node.nodeId)
            writeShortString(buf, node.componentId)
            buf.putInt(node.gridX)
            buf.putInt(node.gridY)
            buf.putInt(node.rotationSteps)
            buf.put(if (node.mirrored) 1 else 0)
            buf.putInt(node.unlockCostCurrency)
            buf.put(if (node.unlocked) 1 else 0)

            buf.putInt(node.sockets.size)
            for (socket in node.sockets) {
                buf.putInt(socket.direction.ordinal)
                buf.putInt(socket.kind.ordinal)
                buf.putInt(socket.offsetTiles)
                buf.putInt(socket.unlockCostCurrency)
            }
        }

        // Frontier Sockets
        buf.putInt(receipt.frontierSockets.size)
        for (frontier in receipt.frontierSockets) {
            writeShortString(buf, frontier.frontierId)
            writeShortString(buf, frontier.parentNodeId)
            buf.putInt(frontier.targetGridX)
            buf.putInt(frontier.targetGridY)
            buf.putInt(frontier.requiredEntryDirection.ordinal)
            buf.putInt(frontier.unlockCostCurrency)
            buf.putInt(frontier.socketKind.ordinal)
        }

        // Shortcut Gates
        buf.putInt(receipt.shortcutGates.size)
        for (gate in receipt.shortcutGates) {
            writeShortString(buf, gate.gateId)
            writeShortString(buf, gate.fromNodeId)
            writeShortString(buf, gate.toNodeId)
            buf.putInt(gate.loopDistance)
            buf.putFloat(gate.backtrackSavings)
            buf.putInt(gate.unlockCostCurrency)
            buf.put(if (gate.unlocked) 1 else 0)
        }

        buf.putInt(receipt.reachableVendingCount)
        buf.put(if (receipt.solvable) 1 else 0)

        // Cache Mutations
        val mutations = cache.allMutations()
        buf.putInt(mutations.size)
        for (m in mutations) {
            buf.putInt(m.chunkX)
            buf.putInt(m.chunkY)
            buf.putInt(m.tileX)
            buf.putInt(m.tileY)
            buf.putInt(m.mutationType.ordinal)
            buf.putInt(m.intValue)
            writeShortString(buf, m.metadata)
            buf.putLong(m.updatedAtMs)
        }

        val limit = buf.position()
        require(limit <= config.maxPayloadBytes) {
            "Payload exceeded budget: \$limit > \${config.maxPayloadBytes}"
        }

        val outBytes = ByteArray(limit)
        buf.rewind()
        buf.get(outBytes)

        if (config.enableChecksum) {
            val checksum = computeFnv1a(outBytes)
            // Can be returned as part of a snapshot object or wrapper if needed
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

        val nodeCount = buf.int
        val nodes = ArrayList<PlacedSectionNode>(nodeCount)
        repeat(nodeCount) {
            val nodeId = readShortString(buf)
            val componentId = readShortString(buf)
            val gridX = buf.int
            val gridY = buf.int
            val rotationSteps = buf.int
            val mirrored = buf.get() == 1.toByte()
            val unlockCostCurrency = buf.int
            val unlocked = buf.get() == 1.toByte()

            val socketCount = buf.int
            val sockets = ArrayList<RoomSocketSpec>(socketCount)
            repeat(socketCount) {
                sockets.add(RoomSocketSpec(
                    direction = SocketDirection.entries[buf.int],
                    kind = SocketKind.entries[buf.int],
                    offsetTiles = buf.int,
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

        val frontierCount = buf.int
        val frontiers = ArrayList<FrontierDoorSocket>(frontierCount)
        repeat(frontierCount) {
            frontiers.add(FrontierDoorSocket(
                frontierId = readShortString(buf),
                parentNodeId = readShortString(buf),
                targetGridX = buf.int,
                targetGridY = buf.int,
                requiredEntryDirection = SocketDirection.entries[buf.int],
                unlockCostCurrency = buf.int,
                socketKind = SocketKind.entries[buf.int]
            ))
        }

        val gateCount = buf.int
        val gates = ArrayList<ShortcutLoopGate>(gateCount)
        repeat(gateCount) {
            gates.add(ShortcutLoopGate(
                gateId = readShortString(buf),
                fromNodeId = readShortString(buf),
                toNodeId = readShortString(buf),
                loopDistance = buf.int,
                backtrackSavings = buf.float,
                unlockCostCurrency = buf.int,
                unlocked = buf.get() == 1.toByte()
            ))
        }

        val reachableVendingCount = buf.int
        val solvable = buf.get() == 1.toByte()

        val mutationCount = buf.int
        repeat(mutationCount) {
            cacheOutput.recordMutation(
                chunkX = buf.int,
                chunkY = buf.int,
                tileX = buf.int,
                tileY = buf.int,
                mutationType = ChunkMutationType.entries[buf.int],
                intValue = buf.int,
                metadata = readShortString(buf)
            )
            // Note: updatedAtMs is not exposed by recordMutation, so we consume it
            buf.long
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
