package com.example.network.sync.modules

import com.example.game.world.elona.AssembledSectionReceipt
import com.example.game.world.elona.ChunkMutationType
import com.example.game.world.elona.FrontierDoorSocket
import com.example.game.world.elona.PlacedSectionNode
import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.ShortcutLoopGate
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import com.example.game.world.elona.SparseWorldStateCache
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class CompactWorldEnvelopeCodecTest {

    private fun createDummyReceipt(): AssembledSectionReceipt {
        val socket = RoomSocketSpec(SocketDirection.NORTH, SocketKind.OPEN, 0, 0)
        val node = PlacedSectionNode("node1", "comp1", 0, 0, 0, false, 0, true, listOf(socket))
        val frontier = FrontierDoorSocket("frontier1", "node1", 0, -1, SocketDirection.SOUTH, 500, SocketKind.LOCKED_DEBRIS_DOOR)
        val gate = ShortcutLoopGate("gate1", "node1", "node2", 2, 0.5f, 1000, false)
        return AssembledSectionReceipt(1, 12345L, "hash1", listOf(node), listOf(frontier), listOf(gate), 1, true)
    }

    private fun createDummyCache(): SparseWorldStateCache {
        val cache = SparseWorldStateCache()
        cache.recordMutation(0, 0, 1, 1, ChunkMutationType.DOOR_UNLOCKED, 1, "comp2")
        return cache
    }

    @Test
    fun testNominalEncodeDecode() {
        val receipt = createDummyReceipt()
        val cache = createDummyCache()
        val bytes = CompactWorldEnvelopeCodec.encodeEnvelope(receipt, cache)

        val decodedCache = SparseWorldStateCache()
        val decodedReceipt = CompactWorldEnvelopeCodec.decodeEnvelope(bytes, decodedCache)

        assertEquals(receipt.seed, decodedReceipt.seed)
        assertEquals(receipt.topologyHash, decodedReceipt.topologyHash)
        assertEquals(1, decodedReceipt.placedNodes.size)
        assertEquals(receipt.placedNodes[0].nodeId, decodedReceipt.placedNodes[0].nodeId)

        val decodedMutations = decodedCache.allMutations()
        assertEquals(1, decodedMutations.size)
        assertEquals(ChunkMutationType.DOOR_UNLOCKED, decodedMutations[0].mutationType)
        assertEquals("comp2", decodedMutations[0].metadata)
    }

    @Test
    fun testDeterminismIdenticalSeeds() {
        val receipt1 = createDummyReceipt()
        val cache1 = createDummyCache()
        val bytes1 = CompactWorldEnvelopeCodec.encodeEnvelope(receipt1, cache1)

        val receipt2 = createDummyReceipt()
        val cache2 = createDummyCache()
        val bytes2 = CompactWorldEnvelopeCodec.encodeEnvelope(receipt2, cache2)

        assertArrayEquals(bytes1, bytes2)
        assertEquals(
            CompactWorldEnvelopeCodec.computeFnv1a(bytes1),
            CompactWorldEnvelopeCodec.computeFnv1a(bytes2)
        )
    }

    @Test
    fun testEmptyReceiptAndCache() {
        val emptyReceipt = AssembledSectionReceipt(1, 0L, "", emptyList(), emptyList(), emptyList(), 0, false)
        val emptyCache = SparseWorldStateCache()
        val bytes = CompactWorldEnvelopeCodec.encodeEnvelope(emptyReceipt, emptyCache)

        val decodedCache = SparseWorldStateCache()
        val decodedReceipt = CompactWorldEnvelopeCodec.decodeEnvelope(bytes, decodedCache)

        assertTrue(decodedReceipt.placedNodes.isEmpty())
        assertTrue(decodedReceipt.frontierSockets.isEmpty())
        assertTrue(decodedReceipt.shortcutGates.isEmpty())
        assertTrue(decodedCache.allMutations().isEmpty())
    }

    @Test
    fun testMaxCapacityStressLimit() {
        val nodes = (1..150).map { i ->
            PlacedSectionNode("node\$i", "comp", i, i, 0, false, 0, true, emptyList())
        }
        val receipt = AssembledSectionReceipt(1, 0L, "hash", nodes, emptyList(), emptyList(), 0, false)
        val cache = SparseWorldStateCache()
        (1..300).forEach { i ->
            cache.recordMutation(i, i, 0, 0, ChunkMutationType.DOOR_UNLOCKED, 1, "meta")
        }

        val config = CompactWorldEnvelopeConfig(maxPayloadBytes = 15000, maxRooms = 150, maxZombies = 300)
        val bytes = CompactWorldEnvelopeCodec.encodeEnvelope(receipt, cache, config)

        assertTrue(bytes.size < 15000)

        val decodedCache = SparseWorldStateCache()
        val decodedReceipt = CompactWorldEnvelopeCodec.decodeEnvelope(bytes, decodedCache, config)

        assertEquals(150, decodedReceipt.placedNodes.size)
        assertEquals(300, decodedCache.allMutations().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPayloadExceedsBudget() {
        val nodes = (1..1000).map { i ->
            PlacedSectionNode("node\$i", "comp", i, i, 0, false, 0, true, emptyList())
        }
        val receipt = AssembledSectionReceipt(1, 0L, "hash", nodes, emptyList(), emptyList(), 0, false)
        val cache = SparseWorldStateCache()

        // Strict budget config
        val config = CompactWorldEnvelopeConfig(maxPayloadBytes = 500, maxRooms = 1000)
        CompactWorldEnvelopeCodec.encodeEnvelope(receipt, cache, config)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testStringTooLongRejection() {
        val longString = "A".repeat(256)
        val receipt = AssembledSectionReceipt(1, 0L, longString, emptyList(), emptyList(), emptyList(), 0, false)
        CompactWorldEnvelopeCodec.encodeEnvelope(receipt, SparseWorldStateCache())
    }

    @Test
    fun testConfigSerialization() {
        val config = CompactWorldEnvelopeConfig(10000, 100, 200, false)
        val map = config.toMap()
        val restored = CompactWorldEnvelopeConfig.fromMap(map)
        assertEquals(config.maxPayloadBytes, restored.maxPayloadBytes)
        assertEquals(config.maxRooms, restored.maxRooms)
        assertEquals(config.maxZombies, restored.maxZombies)
        assertEquals(config.enableChecksum, restored.enableChecksum)
    }

    @Test
    fun testSnapshotSerialization() {
        val snapshot = CompactWorldEnvelopeSnapshot(123456789L, true, 1024)
        val map = snapshot.toMap()
        val restored = CompactWorldEnvelopeSnapshot.fromMap(map)
        assertEquals(snapshot.checksum, restored.checksum)
        assertEquals(snapshot.isClamped, restored.isClamped)
        assertEquals(snapshot.payloadSize, restored.payloadSize)
    }

    @Test
    fun testZeroAndNegativeInputs() {
        val socket = RoomSocketSpec(SocketDirection.WEST, SocketKind.WALL, -5, -100)
        val node = PlacedSectionNode("n", "c", -10, -20, -3, true, -500, false, listOf(socket))
        val receipt = AssembledSectionReceipt(0, -9999L, "h", listOf(node), emptyList(), emptyList(), -5, false)
        val cache = SparseWorldStateCache()
        cache.recordMutation(-1, -2, -3, -4, ChunkMutationType.BARREL_DETONATED, -99, "")

        val bytes = CompactWorldEnvelopeCodec.encodeEnvelope(receipt, cache)
        val decodedCache = SparseWorldStateCache()
        val decodedReceipt = CompactWorldEnvelopeCodec.decodeEnvelope(bytes, decodedCache)

        assertEquals(-9999L, decodedReceipt.seed)
        assertEquals(-10, decodedReceipt.placedNodes[0].gridX)
        assertEquals(-5, decodedReceipt.placedNodes[0].sockets[0].offsetTiles)
        val mutations = decodedCache.allMutations()
        assertEquals(1, mutations.size)
        assertEquals(-1, mutations[0].chunkX)
        assertEquals(-99, mutations[0].intValue)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidMagicHeader() {
        val bytes = ByteBuffer.allocate(8).order(java.nio.ByteOrder.BIG_ENDIAN)
            .putInt(0x00000000)
            .putInt(3)
            .array()
        CompactWorldEnvelopeCodec.decodeEnvelope(bytes, SparseWorldStateCache())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidProtocolVersion() {
        val bytes = ByteBuffer.allocate(8).order(java.nio.ByteOrder.BIG_ENDIAN)
            .putInt(0x4242454E) // 'BBEN'
            .putInt(99)
            .array()
        CompactWorldEnvelopeCodec.decodeEnvelope(bytes, SparseWorldStateCache())
    }

    @Test
    fun testChecksumCalculationConsistency() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val checksum1 = CompactWorldEnvelopeCodec.computeFnv1a(bytes)
        val checksum2 = CompactWorldEnvelopeCodec.computeFnv1a(bytes)
        assertEquals(checksum1, checksum2)
    }
}
