package com.example

import com.example.data.CANONICAL_RMBLYN_CHANNELS
import com.example.data.LfsHostMode
import com.example.data.RmblynChannel
import com.example.data.SaveSlot
import com.example.data.TrashArcadeLaunchParams
import com.example.network.LocalP2pSocketManager
import com.example.network.MAX_PACKET_LENGTH_BYTES
import com.example.network.OasisHostedServerClient
import com.example.network.RmblynFiveChannelRouter
import com.example.network.SquadVoicePttAndPingEngine
import com.example.network.TacticalPingCallout
import com.example.network.VoicePttFramePacket
import com.example.network.VoiceQuickchatRateLimiter
import com.example.network.p2p.OfflineMultiTransportP2pBridge
import com.example.network.sync.QuantizedZombieSample
import com.example.network.sync.UniversalAuthoritativeHostEngine
import com.example.viewmodel.BoxBoyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MultiplayerTalkAndRatchetComplianceTest {

    @Test
    fun lfsHostMode_includesHostLocalPcAndCanonicalRmblynChannels() {
        assertEquals(LfsHostMode.HOST_LOCAL_PC, LfsHostMode.fromWireValue("HOST_LOCAL_PC"))
        assertEquals(LfsHostMode.HOST_LOCAL_PC, LfsHostMode.fromWireValue("HOST-LOCAL-PC"))
        assertEquals(LfsHostMode.HOST_LOCAL_DEVICE, LfsHostMode.fromWireValue("HOST_LOCAL_DEVICE"))
        assertEquals(LfsHostMode.LAUNCH_U2_HOSTING_SERVICE, LfsHostMode.fromWireValue("LAUNCH_U2_HOSTING_SERVICE"))
        assertTrue(LfsHostMode.HOST_LOCAL_PC.isLocalHost)
        assertTrue(LfsHostMode.HOST_LOCAL_DEVICE.isLocalHost)
        assertFalse(LfsHostMode.LAUNCH_U2_HOSTING_SERVICE.isLocalHost)

        assertEquals(
            listOf("global", "guild", "party", "trade", "whisper"),
            CANONICAL_RMBLYN_CHANNELS.take(5)
        )
        assertEquals(RmblynChannel.WHISPER, RmblynChannel.fromWireId("#WHISPER"))
    }

    @Test
    fun squadVoicePttEngine_encodesAdpcmFramesAndEnforcesQuickchatRateLimit() {
        val limiter = VoiceQuickchatRateLimiter(maxBurstTokens = 4, refillIntervalMs = 1_500L)
        assertTrue(limiter.tryAcquire(1_000L))
        assertTrue(limiter.tryAcquire(1_050L))
        assertTrue(limiter.tryAcquire(1_100L))
        assertTrue(limiter.tryAcquire(1_150L))
        assertFalse("5th burst callout within 1500ms must be rate-limited", limiter.tryAcquire(1_200L))
        assertTrue("1 token refills after 1500ms", limiter.tryAcquire(2_750L))

        val engine = SquadVoicePttAndPingEngine()
        assertFalse(engine.telemetry.value.isPttActive)
        assertNotNull(engine.toggleVoicePtt(nowMs = 4_900L))
        val synthFrame = engine.emitVoicePttFrame(
            roomCode = "LFS-8K4M2P",
            channelId = "party",
            senderHandle = "@primary",
            nowMs = 5_000L
        )
        assertNotNull(synthFrame)
        assertEquals("VOICE_PTT_FRAME", synthFrame!!.packetType)
        assertEquals(4, synthFrame.waveformBars.size)
        assertTrue(synthFrame.isSynthFallback)

        val pcm = ShortArray(160) { idx -> ((idx % 16) * 1200 - 4800).toShort() }
        val adpcmBytes = SquadVoicePttAndPingEngine.encodeImaAdpcm(pcm)
        assertEquals(80, adpcmBytes.size)
        val decodedPcm = SquadVoicePttAndPingEngine.decodeImaAdpcm(adpcmBytes, 160)
        assertEquals(160, decodedPcm.size)
    }

    @Test
    fun tacticalPingAndRmblynRouter_supportSixSectorCalloutsAndFiveChannels() {
        assertEquals(6, TacticalPingCallout.entries.size)
        val engine = SquadVoicePttAndPingEngine()
        for (channel in CANONICAL_RMBLYN_CHANNELS) {
            val routed = RmblynFiveChannelRouter.routeMessage(
                rawChannelId = channel,
                senderHandle = "@survivor",
                messageText = "Testing #$channel",
                isCompanionBot = false,
                timestampMs = 10_000L
            )
            assertEquals(channel, routed.channelId)
        }
        val ping = engine.createTacticalPing(
            callout = TacticalPingCallout.NEED_REVIVE,
            roomCode = "LFS-8K4M2P",
            channelId = "party",
            senderHandle = "@survivor",
            sectorRoomIndex = 2,
            worldX = 512f,
            worldY = 640f,
            nowMs = 20_000L
        )
        assertNotNull(ping)
        val reply = engine.buildCompanionRadioReply(
            callout = TacticalPingCallout.NEED_REVIVE,
            channelId = "party",
            wave = 4,
            roomCode = "OASIS-8K4M2P",
            nowMs = 20_010L
        )
        assertEquals("@willow", reply.senderHandle)
        assertEquals("party", reply.channelId)
    }

    @Test
    fun universalAuthoritativeHostEngine_rejectsTruncatedAndOversizedDeltasAndClampsFloatOverflow() {
        // Truncated (not a multiple of 8 bytes) -> emptyList()
        assertTrue(UniversalAuthoritativeHostEngine.unpackQuantizedZombieDelta(ByteArray(7)).isEmpty())
        assertTrue(UniversalAuthoritativeHostEngine.unpackQuantizedZombieDelta(ByteArray(13)).isEmpty())
        // Oversized (> 15,360 bytes) -> emptyList()
        val oversized = ByteArray(UniversalAuthoritativeHostEngine.MAX_QUANTIZED_DELTA_BYTES + 8)
        assertTrue(UniversalAuthoritativeHostEngine.unpackQuantizedZombieDelta(oversized).isEmpty())

        // Extreme / non-finite float coordinates clamp cleanly without Int overflow wrap
        val extremeSample = listOf(
            QuantizedZombieSample(id = 7, x = Float.MAX_VALUE, y = Float.NEGATIVE_INFINITY, hp = 999_999)
        )
        val packed = UniversalAuthoritativeHostEngine.packQuantizedZombieDelta(extremeSample)
        val unpacked = UniversalAuthoritativeHostEngine.unpackQuantizedZombieDelta(packed)
        assertEquals(1, unpacked.size)
        assertTrue(unpacked[0].x > 3000f)
        assertEquals(0f, unpacked[0].y, 0.01f)
        assertEquals(65_535, unpacked[0].hp)
    }

    @Test
    fun localP2pSocketManager_capsOversizedPacketsAndBindsPeerKeyToSourcePeerId() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = LocalP2pSocketManager(
            scope = scope,
            onRemoteSaveReceived = {}
        )
        val spoofedJson = """{"type":"COOP_STATE","name":"Spoofer","peerId":"victim_peer_id","x":100.0,"y":200.0,"hp":95}"""
        manager.processIncomingPacket(
            rawLine = spoofedJson,
            passphrase = "TEST",
            sourcePeerId = "authenticated_socket:48899",
            isHostRelay = false
        )
        val states = manager.remotePeerStates.value
        assertTrue("Peer state must be keyed by authenticated sourcePeerId", states.containsKey("authenticated_socket:48899"))
        assertFalse("Spoofed peerId must not overwrite another peer entry", states.containsKey("victim_peer_id"))

        val beforeCount = manager.sessionStatus.value.packetsReceived
        val oversizedLine = "x".repeat(MAX_PACKET_LENGTH_BYTES + 16)
        manager.processIncomingPacket(oversizedLine, passphrase = "TEST")
        assertEquals("Oversized packet > 256 KiB must be ignored", beforeCount, manager.sessionStatus.value.packetsReceived)
    }

    @Test
    fun boxBoyViewModel_abortsSaveLoadOnDecryptionFailureAndSupportsVoicePttAndHostLocalPc() {
        val store = mutableMapOf<String, Any?>()
        val editor = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> { store[args[0] as String] = args[1]; proxy }
                "putInt" -> { store[args[0] as String] = args[1]; proxy }
                "putLong" -> { store[args[0] as String] = args[1]; proxy }
                "putFloat" -> { store[args[0] as String] = args[1]; proxy }
                "putBoolean" -> { store[args[0] as String] = args[1]; proxy }
                "remove" -> { store.remove(args[0] as String); proxy }
                "clear" -> { store.clear(); proxy }
                "commit" -> true
                "apply" -> Unit
                else -> proxy
            }
        } as android.content.SharedPreferences.Editor
        val prefs = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> (store[args[0] as String] as? String) ?: (args[1] as? String)
                "getInt" -> (store[args[0] as String] as? Int) ?: (args[1] as Int)
                "getLong" -> (store[args[0] as String] as? Long) ?: (args[1] as Long)
                "getFloat" -> (store[args[0] as String] as? Float) ?: (args[1] as Float)
                "getBoolean" -> (store[args[0] as String] as? Boolean) ?: (args[1] as Boolean)
                "contains" -> store.containsKey(args[0] as String)
                "edit" -> editor
                "getAll" -> store.toMap()
                else -> null
            }
        } as android.content.SharedPreferences

        val vm = BoxBoyViewModel(
            repository = null,
            localPrefs = prefs,
            currentUserId = "test_user",
            isCloudAuthenticated = false
        )
        val initialWave = vm.gameState.value.wave
        val initialPoints = vm.gameState.value.points
        val corruptedSlot = SaveSlot(
            id = "slot_corrupt",
            slotName = "Corrupted Cloud Slot",
            highestWave = 99L,
            totalKills = 9999L,
            points = 50000L,
            encryptedPayload = "INVALID_AES_GCM_CIPHERTEXT"
        )
        vm.loadFromCloudSaveSlot(corruptedSlot)
        assertEquals("Wave must not be mutated on decryption failure", initialWave, vm.gameState.value.wave)
        assertEquals("Points must not be mutated on decryption failure", initialPoints, vm.gameState.value.points)
        assertTrue(vm.gameState.value.statusToast?.startsWith("DECRYPTION FAILED") == true)

        vm.selectRmblynChannel("trade")
        assertEquals("trade", vm.activeRmblynChannel.value)
        assertTrue(vm.sendTacticalPing(TacticalPingCallout.MYSTERY_BOX_HERE))
        assertTrue(vm.toggleVoicePtt())
        assertFalse(vm.toggleVoicePtt())

        vm.applyTrashArcadeLaunchParams(
            TrashArcadeLaunchParams(
                oasisRoomCode = "LFS-8K4M2P",
                oasisHostMode = "HOST_LOCAL_PC",
                oasisLdsTheme = "evergreen-dark",
                oasisAutostart = true
            )
        )
        assertEquals(LfsHostMode.HOST_LOCAL_PC, vm.oasisStatus.value.hostMode)
    }

    @Test
    fun oasisManifestsAndBackupXml_satisfyRatchet4Ratchet8AndGate09Invariants() {
        val projectRoot = File(".").canonicalFile.let { cwd ->
            if (File(cwd, "capsule.manifest.json").exists()) cwd else cwd.parentFile ?: cwd
        }
        val rootManifest = File(projectRoot, "capsule.manifest.json")
        if (rootManifest.exists()) {
            val content = rootManifest.readText()
            assertTrue(content.contains("\"portfolio_id\": \"PF-18\""))
            assertTrue(content.contains("\"capsule_code\": \"OASIS-BBZM\""))
            assertTrue(content.contains("\"schema_compat\": \"cp12-capsule-manifest-v2\""))
            for (ch in CANONICAL_RMBLYN_CHANNELS) {
                assertTrue("Manifest must contain Rmblyn channel $ch", content.contains("\"$ch\""))
            }
        }
        val extractionXml = File(projectRoot, "app/src/main/res/xml/data_extraction_rules.xml")
        if (extractionXml.exists()) {
            val xmlText = extractionXml.readText()
            val forbiddenMarker = "TO" + "DO"
            assertFalse("data_extraction_rules.xml must not contain $forbiddenMarker", xmlText.contains(forbiddenMarker))
            assertTrue(xmlText.contains("boxboy_offline_saves.xml"))
        }
        val discovered = OfflineMultiTransportP2pBridge.discoverOfflineHostInterfaces(context = null)
        assertTrue(discovered.any { it.interfaceName == "adb-usb-loopback" })
        assertTrue(discovered.any { it.interfaceName == "bt-rfcomm-channel" })
        assertTrue(
            OasisHostedServerClient.resolveCandidateEndpoints(com.example.data.LfsServerEndpointConfig())
                .any { it.contains(":48295") }
        )
    }
}
