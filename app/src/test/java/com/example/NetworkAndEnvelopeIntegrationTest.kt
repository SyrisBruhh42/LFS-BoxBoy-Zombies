package com.example

import com.example.data.CryptoSyncEngine
import com.example.data.LfsHostMode
import com.example.data.LfsLifecyclePolicy
import com.example.data.LfsServerEndpointConfig
import com.example.network.LocalP2pSocketManager
import com.example.network.MAX_REMOTE_PEERS
import com.example.network.OasisHostedServerClient
import com.example.network.p2p.OfflineMultiTransportP2pBridge
import com.example.network.sync.QuantizedZombieSample
import com.example.network.sync.UniversalAuthoritativeHostEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 4 Integration Test Suite (`FP-N01..N06`):
 * - 4-Way `aegisEnvelopeV1` Unification (`:19448`, `U2 :3000`, `U4`, `BoxBoys`)
 * - 4-Player Star-Mesh P2P Hub (`MAX_REMOTE_PEERS = 3`) & Multi-Subnet Discovery
 * - 8-Byte Quantized Zombie Delta Compression (`120` zombies = `960 B` < `1 KB`)
 * - `OasisHostedServerClient` `HOST_LOCAL_DEVICE` & `10.0.2.2:19448` candidate resolution
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkAndEnvelopeIntegrationTest {

    @Test
    fun fpN04_fourWayAegisEnvelopeV1_unifiesSnakeAndCamelCaseAcrossAesGcmAndCanonicalBase64() {
        val snapshot = CryptoSyncEngine.GameSaveSnapshot(
            slotName = "Slot 01 - CoOp Wave 18",
            highestWave = 18,
            totalKills = 640,
            points = 18500,
            unlockedWeaponIds = listOf("pistol", "shotgun", "raybox"),
            weaponLevels = mapOf("pistol" to 3, "shotgun" to 4, "raybox" to 5),
            activePerks = listOf("JUGGERNOG", "SPEED_COLA", "DOUBLE_TAP"),
            mapSeed = 19960227L,
            unlockedRoomsCount = 7,
            deviceTag = "Laputa-Host",
            timestampEpochMs = 1762035000000L
        )

        // 1. Default AES_256_GCM envelope emits both snake_case and camelCase keys
        val gcmEnv = CryptoSyncEngine.sealAegisEnvelopeV1(
            slotKey = "slot_01",
            snapshot = snapshot,
            generation = 4,
            passphrase = "OASIS-UNIFIED-KEY",
            roomId = "LFS-8K4M2P",
            cipher = "AES_256_GCM"
        )
        assertEquals("aegisEnvelopeV1", gcmEnv.optString("schema"))
        assertEquals(1, gcmEnv.optInt("version"))
        assertEquals("AES_256_GCM", gcmEnv.optString("cipher"))
        assertEquals("AES-256-GCM", gcmEnv.optString("algorithm"))
        assertEquals("slot_01", gcmEnv.optString("slot_key"))
        assertEquals("slot_01", gcmEnv.optString("slotKey"))
        assertEquals("slot_01", gcmEnv.optString("slotId"))
        assertEquals("lfs-boxboy-zombies", gcmEnv.optString("capsule_id"))
        assertEquals("lfs-boxboy-zombies", gcmEnv.optString("capsuleId"))
        assertEquals(gcmEnv.optString("ivB64"), gcmEnv.optString("ivBase64"))
        assertEquals(gcmEnv.optString("ciphertext_b64"), gcmEnv.optString("ciphertextB64"))
        assertEquals(gcmEnv.optString("ciphertextB64"), gcmEnv.optString("ciphertextBase64"))
        assertEquals(gcmEnv.optString("payload_sha256"), gcmEnv.optString("plaintextSha256"))
        assertTrue(gcmEnv.optString("authTagBase64").isNotBlank())
        assertTrue(gcmEnv.optString("hmacSignature").length == 64)

        val unsealedGcm = CryptoSyncEngine.verifyAegisEnvelopeV1(
            gcmEnv.toString(),
            "OASIS-UNIFIED-KEY"
        ).getOrThrow()
        assertEquals(18, unsealedGcm.highestWave)
        assertEquals(640, unsealedGcm.totalKills)
        assertEquals(18500, unsealedGcm.points)

        // 2. CANONICAL_BASE64_JSON mode (used by :19448 standby / U4 fallback) round-trips cleanly
        val b64Env = CryptoSyncEngine.sealAegisEnvelopeV1(
            slotKey = "slot_02",
            snapshot = snapshot,
            generation = 5,
            passphrase = "OASIS-UNIFIED-KEY",
            roomId = "LFS-8K4M2P",
            cipher = "CANONICAL_BASE64_JSON"
        )
        assertEquals("CANONICAL_BASE64_JSON", b64Env.optString("cipher"))
        val unsealedB64 = CryptoSyncEngine.verifyAegisEnvelopeV1(
            b64Env.toString(),
            "OASIS-UNIFIED-KEY"
        ).getOrThrow()
        assertEquals("Slot 01 - CoOp Wave 18", unsealedB64.slotName)
        assertEquals(18, unsealedB64.highestWave)

        // 3. Raw AES_256_GCM_RAW mode (matching :19448 and U2 fourTierSaveCustody.ts) round-trips cleanly
        val rawGcmEnv = CryptoSyncEngine.sealAegisEnvelopeV1(
            slotKey = "slot_03",
            snapshot = snapshot,
            generation = 6,
            passphrase = "OASIS-UNIFIED-KEY",
            roomId = "LFS-8K4M2P",
            cipher = "AES_256_GCM_RAW"
        )
        val unsealedRawGcm = CryptoSyncEngine.verifyAegisEnvelopeV1(
            rawGcmEnv.toString(),
            "OASIS-UNIFIED-KEY"
        ).getOrThrow()
        assertEquals(18, unsealedRawGcm.highestWave)
        assertEquals(listOf("pistol", "shotgun", "raybox"), unsealedRawGcm.unlockedWeaponIds)
    }

    @Test
    fun fpN06_quantizedZombieDelta_compresses120ZombiesUnder1Kilobyte() {
        val horde = (1..120).map { idx ->
            QuantizedZombieSample(
                id = idx,
                x = 120.0f + idx * 4.5f,
                y = 240.0f + idx * 3.2f,
                hp = 100 + idx * 5
            )
        }

        val binaryDelta = UniversalAuthoritativeHostEngine.packQuantizedZombieDelta(horde)
        assertEquals(120 * UniversalAuthoritativeHostEngine.BYTES_PER_QUANTIZED_ZOMBIE, binaryDelta.size)
        assertEquals(960, binaryDelta.size)
        assertTrue("Binary delta must be < 1024 bytes", binaryDelta.size < 1024)

        val base64Delta = UniversalAuthoritativeHostEngine.packQuantizedZombieDeltaBase64(horde)
        assertEquals(1280, base64Delta.length)
        assertTrue("Base64 delta must fit inside single 1500B MTU packet", base64Delta.length < 1500)

        val unpacked = UniversalAuthoritativeHostEngine.unpackQuantizedZombieDeltaBase64(base64Delta)
        assertEquals(120, unpacked.size)
        assertEquals(1, unpacked.first().id)
        assertEquals(124.5f, unpacked.first().x, 0.11f)
        assertEquals(243.2f, unpacked.first().y, 0.11f)
        assertEquals(105, unpacked.first().hp)
        assertEquals(120, unpacked.last().id)
        assertEquals(700, unpacked.last().hp)
    }

    @Test
    fun fpN01AndN05_hosterPayloadAndMultiTransportDiscovery_preserveWireContracts() {
        assertEquals(3, MAX_REMOTE_PEERS)
        val lanIp = LocalP2pSocketManager.resolveLocalLanIp()
        assertNotNull(lanIp)
        assertTrue(lanIp.isNotBlank())

        val bcastAddrs = LocalP2pSocketManager.resolveSubnetBroadcastAddresses()
        assertTrue(bcastAddrs.isNotEmpty())

        val u3Payload = OasisHostedServerClient.buildU3HosterCreateRoomPayload(
            cleanCode = "LFS-8K4M2P",
            roomTitle = "Sanctuary 4P Squad",
            hostMode = LfsHostMode.HOST_LOCAL_DEVICE,
            lifecyclePolicy = LfsLifecyclePolicy.ON_DEMAND_WAKE
        )
        assertTrue(u3Payload.contains("\"host_mode\":\"HOST_LOCAL_DEVICE\""))
        assertTrue(u3Payload.contains("\"hosting_mode\":\"HOST_LOCAL_DEVICE\""))

        val endpoints = OasisHostedServerClient.resolveCandidateEndpoints(LfsServerEndpointConfig())
        assertTrue(endpoints.contains("http://127.0.0.1:19448"))
        assertTrue(endpoints.contains("http://10.0.2.2:19448"))

        val offlineInterfaces = OfflineMultiTransportP2pBridge.discoverOfflineHostInterfaces()
        assertTrue(offlineInterfaces.any { it.interfaceName == "adb-usb-loopback" })
        assertTrue(offlineInterfaces.any { it.interfaceName == "bt-rfcomm-channel" })
    }
}
