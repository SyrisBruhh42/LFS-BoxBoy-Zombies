package com.example.network

import com.example.data.AegisJsonObject
import com.example.data.CANONICAL_RMBLYN_CHANNELS
import com.example.data.RmblynChannel
import com.example.data.RmblynChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 6-Sector Tactical Ping Callouts for active survival combat (`OasisMultiplayerBar` & HUD Ping Wheel).
 */
enum class TacticalPingCallout(
    val wireId: String,
    val bannerText: String,
    val shortChipLabel: String,
    val colorHex: Long,
    val responderBotHandle: String,
    val companionAckText: String
) {
    NEED_REVIVE(
        wireId = "NEED_REVIVE",
        bannerText = "NEED REVIVE / MEDIC!",
        shortChipLabel = "MEDIC",
        colorHex = 0xFFFF2A54,
        responderBotHandle = "@willow",
        companionAckText = "Moving to your coordinates for immediate Tag-Team Rescue!"
    ),
    SPLIT_DEBRIS_DOOR(
        wireId = "SPLIT_DEBRIS_DOOR",
        bannerText = "SPLIT DEBRIS DOOR!",
        shortChipLabel = "DOOR",
        colorHex = 0xFFFFD166,
        responderBotHandle = "@cinder",
        companionAckText = "Pooling Starlight Rupees on the nearest locked sector door!"
    ),
    FOCUS_DEVIL_BOSS(
        wireId = "FOCUS_DEVIL_BOSS",
        bannerText = "FOCUS DEVIL BOSS!",
        shortChipLabel = "BOSS",
        colorHex = 0xFFFF7B00,
        responderBotHandle = "@mote",
        companionAckText = "Target locked on high-priority boss anomaly -- firing Hyper Railgun!"
    ),
    PRIME_RED_BARRELS(
        wireId = "PRIME_RED_BARRELS",
        bannerText = "PRIME RED BARRELS!",
        shortChipLabel = "BARREL",
        colorHex = 0xFF00E5FF,
        responderBotHandle = "@cinder",
        companionAckText = "Kiting horde cluster into explosive cryo/red barrel trap!"
    ),
    MYSTERY_BOX_HERE(
        wireId = "MYSTERY_BOX_HERE",
        bannerText = "MYSTERY BOX / LOOT HERE!",
        shortChipLabel = "LOOT",
        colorHex = 0xFFB388FF,
        responderBotHandle = "@mote",
        companionAckText = "Marking Mystery Box and Modular Loot Drop coordinates on radar!"
    ),
    DEFEND_SIEGE_FABRICATOR(
        wireId = "DEFEND_SIEGE_FABRICATOR",
        bannerText = "DEFEND SIEGE FABRICATOR!",
        shortChipLabel = "SIEGE",
        colorHex = 0xFF00FF66,
        responderBotHandle = "@willow",
        companionAckText = "Deploying barricades around the active Siege Fabricator sector!"
    );

    val calloutId: String get() = wireId
    val badgeCode: String get() = shortChipLabel
    val shortLabel: String get() = shortChipLabel
    val calloutText: String get() = bannerText

    companion object {
        fun fromWireId(raw: String?): TacticalPingCallout {
            val clean = raw?.trim()?.uppercase().orEmpty()
            return entries.find { it.wireId == clean || it.shortChipLabel == clean } ?: NEED_REVIVE
        }
    }
}

/**
 * Token-Bucket Rate Limiter for voice bursts & tactical quickchat pings (`burst = 4`, `refill = 1 per 1500ms`).
 */
class VoiceQuickchatRateLimiter(
    val maxBurstTokens: Int = 4,
    val refillIntervalMs: Long = 1500L,
    initialClockMs: Long = 0L
) {
    private var tokens: Int = maxBurstTokens.coerceAtLeast(1)
    private var lastRefillTimestampMs: Long = initialClockMs

    @Synchronized
    fun tryAcquire(nowMs: Long = System.currentTimeMillis()): Boolean {
        refillIfDue(nowMs)
        return if (tokens > 0) {
            tokens -= 1
            true
        } else {
            false
        }
    }

    @Synchronized
    fun availableTokens(nowMs: Long = System.currentTimeMillis()): Int {
        refillIfDue(nowMs)
        return tokens
    }

    @Synchronized
    fun cooldownRemainingMs(nowMs: Long = System.currentTimeMillis()): Long {
        refillIfDue(nowMs)
        if (tokens > 0) return 0L
        val elapsed = (nowMs - lastRefillTimestampMs).coerceAtLeast(0L)
        return (refillIntervalMs - elapsed).coerceAtLeast(0L)
    }

    @Synchronized
    fun reset(nowMs: Long = 0L) {
        tokens = maxBurstTokens.coerceAtLeast(1)
        lastRefillTimestampMs = nowMs
    }

    private fun refillIfDue(nowMs: Long) {
        if (lastRefillTimestampMs == 0L) {
            lastRefillTimestampMs = nowMs
            return
        }
        if (nowMs < lastRefillTimestampMs) {
            lastRefillTimestampMs = nowMs
            return
        }
        val elapsed = nowMs - lastRefillTimestampMs
        if (elapsed >= refillIntervalMs && refillIntervalMs > 0L) {
            val gained = (elapsed / refillIntervalMs).toInt()
            if (gained > 0) {
                tokens = (tokens + gained).coerceAtMost(maxBurstTokens)
                lastRefillTimestampMs = if (tokens == maxBurstTokens) {
                    nowMs
                } else {
                    lastRefillTimestampMs + gained * refillIntervalMs
                }
            }
        }
    }
}

/**
 * 16 kHz IMA-ADPCM / PCM Voice Frame Packet (`VOICE_PTT_FRAME`) transmitted over `:19448` WebSocket and `:48899` P2P.
 */
data class VoicePttFramePacket(
    val packetType: String = "VOICE_PTT_FRAME",
    val roomCode: String = "LFS-8K4M2P",
    val senderId: String = "peer_local",
    val senderHandle: String = "@primary",
    val channelId: String = "party",
    val sequenceNumber: Int = 1,
    val sampleRateHz: Int = SquadVoicePttAndPingEngine.SAMPLE_RATE_HZ,
    val codec: String = "IMA_ADPCM_16KHZ",
    val adpcmPayloadBase64: String,
    val waveformBars: List<Float> = listOf(0.55f, 0.82f, 0.68f, 0.44f),
    val isSynthFallback: Boolean = true,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val dbLevel: Float
        get() = ((waveformBars.maxOrNull() ?: 0.5f).coerceIn(0f, 1f) * 48f) - 48f

    fun toJsonString(): String = toWireJson()

    fun toWireJson(): String {
        val barsJson = waveformBars.take(4).joinToString(",") {
            val clamped = if (it.isFinite()) it.coerceIn(0f, 1f) else 0f
            ((clamped * 100f).roundToInt() / 100.0).toString()
        }
        return AegisJsonObject().apply {
            put("type", "VOICE_PTT_FRAME")
            put("room", roomCode)
            put("senderId", senderId)
            put("sender", senderHandle)
            put("channelId", RmblynFiveChannelRouter.normalizeChannelId(channelId))
            put("seq", sequenceNumber)
            put("rateHz", sampleRateHz)
            put("codec", codec)
            put("adpcmB64", adpcmPayloadBase64)
            put("barsCsv", barsJson)
            put("synth", isSynthFallback)
            put("ts", timestampMs)
        }.toString()
    }

    companion object {
        fun fromWireJson(rawJson: String): VoicePttFramePacket? {
            return runCatching {
                val obj = JSONObject(rawJson)
                if (obj.optString("type") != "VOICE_PTT_FRAME") return null
                val barsCsv = obj.optString("barsCsv", "0.5,0.75,0.6,0.4")
                val parsedBars = barsCsv.split(",")
                    .mapNotNull { it.trim().toFloatOrNull()?.coerceIn(0f, 1f) }
                    .let { if (it.size == 4) it else listOf(0.5f, 0.75f, 0.6f, 0.4f) }
                VoicePttFramePacket(
                    roomCode = obj.optString("room", "LFS-8K4M2P"),
                    senderId = obj.optString("senderId", "peer_remote"),
                    senderHandle = obj.optString("sender", "@peer"),
                    channelId = RmblynFiveChannelRouter.normalizeChannelId(obj.optString("channelId", "party")),
                    sequenceNumber = obj.optInt("seq", 1),
                    sampleRateHz = obj.optInt("rateHz", SquadVoicePttAndPingEngine.SAMPLE_RATE_HZ),
                    codec = obj.optString("codec", "IMA_ADPCM_16KHZ"),
                    adpcmPayloadBase64 = obj.optString("adpcmB64", ""),
                    waveformBars = parsedBars,
                    isSynthFallback = obj.optBoolean("synth", true),
                    timestampMs = obj.optLong("ts", System.currentTimeMillis())
                )
            }.getOrNull()
        }
    }
}

/**
 * Structured Tactical Ping wire packet (`TACTICAL_PING`).
 */
data class TacticalPingPacket(
    val packetType: String = "TACTICAL_PING",
    val roomCode: String = "LFS-8K4M2P",
    val callout: TacticalPingCallout,
    val senderHandle: String,
    val channelId: String = "party",
    val worldX: Float = 510f,
    val worldY: Float = 1200f,
    val wave: Int = 1,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toJsonString(): String = toWireJson()

    fun toWireJson(): String {
        val qx = if (worldX.isFinite()) (worldX * 10f).roundToInt() / 10.0 else 0.0
        val qy = if (worldY.isFinite()) (worldY * 10f).roundToInt() / 10.0 else 0.0
        return AegisJsonObject().apply {
            put("type", "TACTICAL_PING")
            put("room", roomCode)
            put("callout", callout.wireId)
            put("text", "[PING:${callout.shortChipLabel}] ${callout.bannerText}")
            put("sender", senderHandle)
            put("channelId", RmblynFiveChannelRouter.normalizeChannelId(channelId))
            put("x", qx)
            put("y", qy)
            put("wave", wave)
            put("ts", timestampMs)
        }.toString()
    }

    companion object {
        fun fromWireJson(rawJson: String): TacticalPingPacket? {
            return runCatching {
                val obj = JSONObject(rawJson)
                if (obj.optString("type") != "TACTICAL_PING") return null
                TacticalPingPacket(
                    roomCode = obj.optString("room", "LFS-8K4M2P"),
                    callout = TacticalPingCallout.fromWireId(obj.optString("callout", "NEED_REVIVE")),
                    senderHandle = obj.optString("sender", "@peer"),
                    channelId = RmblynFiveChannelRouter.normalizeChannelId(obj.optString("channelId", "party")),
                    worldX = obj.optDouble("x", 510.0).toFloat(),
                    worldY = obj.optDouble("y", 1200.0).toFloat(),
                    wave = obj.optInt("wave", 1),
                    timestampMs = obj.optLong("ts", System.currentTimeMillis())
                )
            }.getOrNull()
        }
    }
}

/**
 * Live HUD telemetry for Push-To-Talk (`[PTT: TALK]`), 4-bar dB waveform visualizer, and Tactical Ping Wheel.
 */
data class VoicePttTelemetryState(
    val isTransmitting: Boolean = false,
    val activeSpeakerHandle: String = "@primary",
    val waveformBars: List<Float> = listOf(0.18f, 0.24f, 0.20f, 0.16f),
    val isSynthCommsActive: Boolean = true,
    val framesSent: Int = 0,
    val framesReceived: Int = 0,
    val lastPingCallout: TacticalPingCallout? = null,
    val lastRadioStatus: String = "SQUAD RADIO STANDBY (16 kHz IMA-ADPCM + Synth Fallback)",
    val rateLimitTokensRemaining: Int = 4
) {
    val isPttActive: Boolean get() = isTransmitting
    val modeLabel: String get() = if (isSynthCommsActive) "16kHz Synth/ADPCM" else "16kHz IMA-ADPCM"
    val lastCalloutBadge: String get() = lastPingCallout?.shortChipLabel ?: "READY"
    val framesTransmitted: Int get() = framesSent
}

/**
 * `RATCHET-8` Canonical 5-Channel `Rmblyn` Message Router (`global`, `guild`, `party`, `trade`, `whisper`).
 */
object RmblynFiveChannelRouter {
    val CANONICAL_CHANNELS: List<String> = CANONICAL_RMBLYN_CHANNELS

    fun normalizeChannel(raw: String?): String = normalizeChannelId(raw)

    fun normalizeChannelId(raw: String?): String {
        val cleaned = raw
            ?.trim()
            ?.lowercase()
            ?.removePrefix("#")
            ?.removePrefix("@")
            ?.substringBefore(":")
            .orEmpty()
        return when {
            cleaned in CANONICAL_CHANNELS -> cleaned
            cleaned == "arcade-lounge" || cleaned == "squad" || cleaned == "coop" -> "party"
            cleaned == "clan" || cleaned == "family" -> "guild"
            cleaned == "market" || cleaned == "loot" -> "trade"
            cleaned == "dm" || cleaned == "direct" -> "whisper"
            else -> "party"
        }
    }

    fun formatChannelBadge(channelId: String): String {
        val norm = normalizeChannelId(channelId)
        return RmblynChannel.fromWireId(norm).badgeLabel
    }

    fun routeMessage(
        rawChannelId: String = "party",
        senderHandle: String = "@primary",
        messageText: String,
        isCompanionBot: Boolean = false,
        timestampMs: Long = System.currentTimeMillis()
    ): RmblynChatMessage {
        val norm = normalizeChannelId(rawChannelId)
        return RmblynChatMessage(
            id = "rmblyn_${norm}_${timestampMs}_${abs(messageText.hashCode() % 10000)}",
            channelId = norm,
            senderHandle = senderHandle,
            messageText = messageText.trim(),
            timestampMs = timestampMs,
            isCompanionBot = isCompanionBot
        )
    }

    fun routeMessage(
        existingMessages: List<RmblynChatMessage>,
        incoming: RmblynChatMessage,
        maxHistory: Int = 48
    ): List<RmblynChatMessage> {
        val normalizedMsg = incoming.copy(
            channelId = normalizeChannelId(incoming.channelId)
        )
        return (existingMessages + normalizedMsg).takeLast(maxHistory.coerceAtLeast(5))
    }

    fun filterMessagesForChannel(
        allMessages: List<RmblynChatMessage>,
        channelId: String
    ): List<RmblynChatMessage> {
        val target = normalizeChannelId(channelId)
        val matching = allMessages.filter { normalizeChannelId(it.channelId) == target }
        return if (matching.isNotEmpty()) matching else allMessages.takeLast(5)
    }
}

/**
 * Squad Voice Push-To-Talk (`VOICE_PTT_FRAME` 16 kHz IMA-ADPCM / PCM packetizer + zero-permission
 * Tactical Synth Comms fallback), 6-Sector Tactical Ping Callout engine, and 5-Channel `Rmblyn` router.
 */
class SquadVoicePttAndPingEngine(
    val rateLimiter: VoiceQuickchatRateLimiter = VoiceQuickchatRateLimiter(maxBurstTokens = 4, refillIntervalMs = 1500L)
) {
    private val _telemetry = MutableStateFlow(VoicePttTelemetryState())
    val telemetry: StateFlow<VoicePttTelemetryState> = _telemetry.asStateFlow()

    private var sequenceCounter: Int = 0

    /**
     * Toggles Push-To-Talk state and generates a 16 kHz IMA-ADPCM `VOICE_PTT_FRAME` with a live 4-bar dB waveform.
     * Uses zero-permission Tactical Synth Comms fallback when hardware microphone capture is not active.
     */
    fun toggleVoicePtt(
        roomCode: String = "LFS-8K4M2P",
        senderId: String = "peer_local",
        senderHandle: String = "@primary",
        channelId: String = "party",
        nowMs: Long = System.currentTimeMillis()
    ): VoicePttFramePacket? {
        val nextTransmitting = !_telemetry.value.isTransmitting
        return if (nextTransmitting) {
            startVoicePttBurst(
                roomCode = roomCode,
                senderId = senderId,
                senderHandle = senderHandle,
                channelId = channelId,
                nowMs = nowMs
            )
        } else {
            stopVoicePtt()
            null
        }
    }

    fun startVoicePttBurst(
        roomCode: String = "LFS-8K4M2P",
        senderId: String = "peer_local",
        senderHandle: String = "@primary",
        channelId: String = "party",
        customPcmSamples: ShortArray? = null,
        nowMs: Long = System.currentTimeMillis()
    ): VoicePttFramePacket? {
        if (!rateLimiter.tryAcquire(nowMs)) {
            val waitMs = rateLimiter.cooldownRemainingMs(nowMs)
            _telemetry.value = _telemetry.value.copy(
                isTransmitting = false,
                rateLimitTokensRemaining = rateLimiter.availableTokens(nowMs),
                lastRadioStatus = "PTT RATE LIMITED: Wait ${waitMs}ms (anti-spam guard)"
            )
            syncSharedTelemetry(_telemetry.value)
            return null
        }

        sequenceCounter += 1
        val pcm = customPcmSamples ?: synthesizeTacticalCarrierPcm(
            sequenceNumber = sequenceCounter,
            carrierFreqHz = 680f + (sequenceCounter % 4) * 55f,
            squelchTail = true
        )
        val adpcmBytes = encodeImaAdpcm(pcm)
        val adpcmB64 = Base64.getEncoder().encodeToString(adpcmBytes)
        val bars = computeFourBarWaveformLevels(pcm)
        val normChannel = RmblynFiveChannelRouter.normalizeChannelId(channelId)

        val packet = VoicePttFramePacket(
            roomCode = roomCode,
            senderId = senderId,
            senderHandle = senderHandle,
            channelId = normChannel,
            sequenceNumber = sequenceCounter,
            sampleRateHz = SAMPLE_RATE_HZ,
            codec = "IMA_ADPCM_16KHZ",
            adpcmPayloadBase64 = adpcmB64,
            waveformBars = bars,
            isSynthFallback = customPcmSamples == null,
            timestampMs = nowMs
        )

        val nextState = _telemetry.value.copy(
            isTransmitting = true,
            activeSpeakerHandle = senderHandle,
            waveformBars = bars,
            isSynthCommsActive = customPcmSamples == null,
            framesSent = _telemetry.value.framesSent + 1,
            lastRadioStatus = "PTT TX [$senderHandle -> #$normChannel] seq #$sequenceCounter (${adpcmBytes.size}B IMA-ADPCM)",
            rateLimitTokensRemaining = rateLimiter.availableTokens(nowMs)
        )
        _telemetry.value = nextState
        syncSharedTelemetry(nextState)
        return packet
    }

    fun stopVoicePtt(nowMs: Long = System.currentTimeMillis()) {
        val nextState = _telemetry.value.copy(
            isTransmitting = false,
            waveformBars = listOf(0.14f, 0.18f, 0.15f, 0.12f),
            lastRadioStatus = "SQUAD RADIO STANDBY [Squelch Closed]",
            rateLimitTokensRemaining = rateLimiter.availableTokens(nowMs)
        )
        _telemetry.value = nextState
        syncSharedTelemetry(nextState)
    }

    fun onRemoteVoiceFrameReceived(frame: VoicePttFramePacket, nowMs: Long = System.currentTimeMillis()): ShortArray {
        val rawAdpcm = runCatching { Base64.getDecoder().decode(frame.adpcmPayloadBase64) }.getOrDefault(ByteArray(0))
        val decodedPcm = if (rawAdpcm.isNotEmpty()) {
            decodeImaAdpcm(rawAdpcm)
        } else {
            synthesizeTacticalCarrierPcm(frame.sequenceNumber)
        }
        val bars = if (frame.waveformBars.size == 4) {
            frame.waveformBars
        } else {
            computeFourBarWaveformLevels(decodedPcm)
        }
        val nextState = _telemetry.value.copy(
            activeSpeakerHandle = frame.senderHandle,
            waveformBars = bars,
            framesReceived = _telemetry.value.framesReceived + 1,
            lastRadioStatus = "PTT RX [${frame.senderHandle}] seq #${frame.sequenceNumber} (${decodedPcm.size} samples @ 16kHz)",
            rateLimitTokensRemaining = rateLimiter.availableTokens(nowMs)
        )
        _telemetry.value = nextState
        syncSharedTelemetry(nextState)
        return decodedPcm
    }

    /**
     * Evaluates the token-bucket rate limiter and builds a [TacticalPingPacket] + deterministic companion reply.
     */
    fun buildRateLimitedTacticalPing(
        callout: TacticalPingCallout,
        roomCode: String = "LFS-8K4M2P",
        senderHandle: String = "@primary",
        channelId: String = "party",
        worldX: Float = 510f,
        worldY: Float = 1200f,
        wave: Int = 1,
        nowMs: Long = System.currentTimeMillis()
    ): Pair<TacticalPingPacket, RmblynChatMessage>? {
        if (!rateLimiter.tryAcquire(nowMs)) {
            val waitMs = rateLimiter.cooldownRemainingMs(nowMs)
            val nextState = _telemetry.value.copy(
                rateLimitTokensRemaining = rateLimiter.availableTokens(nowMs),
                lastRadioStatus = "PING RATE LIMITED: Wait ${waitMs}ms before next callout"
            )
            _telemetry.value = nextState
            syncSharedTelemetry(nextState)
            return null
        }

        val normChannel = RmblynFiveChannelRouter.normalizeChannelId(channelId)
        val packet = TacticalPingPacket(
            roomCode = roomCode,
            callout = callout,
            senderHandle = senderHandle,
            channelId = normChannel,
            worldX = worldX,
            worldY = worldY,
            wave = wave,
            timestampMs = nowMs
        )
        val companionReply = RmblynChatMessage(
            id = "ping_bot_${callout.wireId.lowercase()}_$nowMs",
            channelId = normChannel,
            senderHandle = callout.responderBotHandle,
            messageText = "[ACK ${callout.shortChipLabel}] ${callout.companionAckText} (Wave $wave @ ${worldX.toInt()},${worldY.toInt()})",
            timestampMs = nowMs,
            isCompanionBot = true
        )
        val nextState = _telemetry.value.copy(
            lastPingCallout = callout,
            lastRadioStatus = "PING SENT [${callout.shortChipLabel}]: ${callout.bannerText}",
            rateLimitTokensRemaining = rateLimiter.availableTokens(nowMs)
        )
        _telemetry.value = nextState
        syncSharedTelemetry(nextState)
        return packet to companionReply
    }

    fun emitVoicePttFrame(
        roomCode: String = "LFS-8K4M2P",
        senderId: String = "peer_local",
        senderHandle: String = "@primary",
        channelId: String = "party",
        nowMs: Long = System.currentTimeMillis()
    ): VoicePttFramePacket? {
        return startVoicePttBurst(
            roomCode = roomCode,
            senderId = senderId,
            senderHandle = senderHandle,
            channelId = channelId,
            nowMs = nowMs
        )
    }

    fun createTacticalPing(
        callout: TacticalPingCallout,
        roomCode: String = "LFS-8K4M2P",
        channelId: String = "party",
        senderHandle: String = "@primary",
        sectorRoomIndex: Int = 0,
        worldX: Float = 510f,
        worldY: Float = 1200f,
        wave: Int = (sectorRoomIndex + 1).coerceAtLeast(1),
        nowMs: Long = System.currentTimeMillis()
    ): TacticalPingPacket? {
        return buildRateLimitedTacticalPing(
            callout = callout,
            roomCode = roomCode,
            senderHandle = senderHandle,
            channelId = channelId,
            worldX = worldX,
            worldY = worldY,
            wave = wave,
            nowMs = nowMs
        )?.first
    }

    fun buildCompanionRadioReply(
        callout: TacticalPingCallout? = null,
        channelId: String = "party",
        wave: Int = 1,
        roomCode: String = "LFS-8K4M2P",
        nowMs: Long = System.currentTimeMillis()
    ): RmblynChatMessage {
        val normChannel = RmblynFiveChannelRouter.normalizeChannelId(channelId)
        return if (callout != null) {
            RmblynChatMessage(
                id = "ping_bot_${callout.wireId.lowercase()}_$nowMs",
                channelId = normChannel,
                senderHandle = callout.responderBotHandle,
                messageText = "[ACK ${callout.shortChipLabel}] ${callout.companionAckText} (Wave $wave, Room $roomCode)",
                timestampMs = nowMs,
                isCompanionBot = true
            )
        } else {
            buildCompanionVoiceRadioReply(
                senderHandle = "@primary",
                wave = wave,
                activeChannel = normChannel,
                sequenceNumber = sequenceCounter.coerceAtLeast(1),
                nowMs = nowMs
            )
        }
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FRAME_DURATION_MS = 20
        const val SAMPLES_PER_FRAME = 320
        const val ADPCM_BYTES_PER_FRAME = 160

        private val IMA_INDEX_TABLE = intArrayOf(
            -1, -1, -1, -1, 2, 4, 6, 8,
            -1, -1, -1, -1, 2, 4, 6, 8
        )

        private val IMA_STEP_TABLE = intArrayOf(
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
            19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
            50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
            130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
            337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
            876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
            2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
            5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
            15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
        )

        private val _sharedTelemetry = MutableStateFlow(VoicePttTelemetryState())
        val sharedTelemetry: StateFlow<VoicePttTelemetryState> = _sharedTelemetry.asStateFlow()

        private val _sharedActiveChannel = MutableStateFlow("party")
        val sharedActiveChannel: StateFlow<String> = _sharedActiveChannel.asStateFlow()

        @Volatile
        var onSharedToggleVoicePtt: (() -> Unit)? = null

        @Volatile
        var onSharedSendTacticalPing: ((TacticalPingCallout) -> Unit)? = null

        @Volatile
        var onSharedSelectRmblynChannel: ((String) -> Unit)? = null

        internal fun syncSharedTelemetry(state: VoicePttTelemetryState) {
            _sharedTelemetry.value = state
        }

        internal fun syncSharedActiveChannel(channelId: String) {
            _sharedActiveChannel.value = RmblynFiveChannelRouter.normalizeChannelId(channelId)
        }

        fun setSharedActiveChannel(channelId: String) {
            syncSharedActiveChannel(channelId)
        }

        fun triggerSharedToggleVoicePtt() {
            val cb = onSharedToggleVoicePtt
            if (cb != null) {
                cb.invoke()
            } else {
                val cur = _sharedTelemetry.value
                val nextTx = !cur.isTransmitting
                val bars = if (nextTx) {
                    computeFourBarWaveformLevels(synthesizeTacticalCarrierPcm(cur.framesSent + 1))
                } else {
                    listOf(0.14f, 0.18f, 0.15f, 0.12f)
                }
                _sharedTelemetry.value = cur.copy(
                    isTransmitting = nextTx,
                    waveformBars = bars,
                    framesSent = if (nextTx) cur.framesSent + 1 else cur.framesSent,
                    lastRadioStatus = if (nextTx) "PTT TX [@primary -> #${_sharedActiveChannel.value}]" else "SQUAD RADIO STANDBY"
                )
            }
        }

        fun triggerSharedTacticalPing(callout: TacticalPingCallout) {
            val cb = onSharedSendTacticalPing
            if (cb != null) {
                cb.invoke(callout)
            } else {
                _sharedTelemetry.value = _sharedTelemetry.value.copy(
                    lastPingCallout = callout,
                    lastRadioStatus = "PING SENT [${callout.shortChipLabel}]: ${callout.bannerText}"
                )
            }
        }

        fun triggerSharedSelectRmblynChannel(channelId: String) {
            val norm = RmblynFiveChannelRouter.normalizeChannelId(channelId)
            _sharedActiveChannel.value = norm
            onSharedSelectRmblynChannel?.invoke(norm)
        }

        /**
         * Compresses 16-bit mono PCM samples into 4-bit IMA-ADPCM (`2` samples per byte).
         */
        fun encodeImaAdpcm(pcmSamples: ShortArray): ByteArray {
            if (pcmSamples.isEmpty()) return ByteArray(0)
            val outLen = (pcmSamples.size + 1) / 2
            val out = ByteArray(outLen)
            var predictor = 0
            var stepIndex = 0

            for (i in pcmSamples.indices) {
                val sample = pcmSamples[i].toInt()
                val step = IMA_STEP_TABLE[stepIndex]
                var diff = sample - predictor
                var nibble = 0
                if (diff < 0) {
                    nibble = 8
                    diff = -diff
                }
                var vpdiff = step shr 3
                if (diff >= step) {
                    nibble = nibble or 4
                    diff -= step
                    vpdiff += step
                }
                val halfStep = step shr 1
                if (diff >= halfStep) {
                    nibble = nibble or 2
                    diff -= halfStep
                    vpdiff += halfStep
                }
                val quarterStep = step shr 2
                if (diff >= quarterStep) {
                    nibble = nibble or 1
                    vpdiff += quarterStep
                }

                if ((nibble and 8) != 0) {
                    predictor -= vpdiff
                } else {
                    predictor += vpdiff
                }
                predictor = predictor.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                stepIndex = (stepIndex + IMA_INDEX_TABLE[nibble and 0x0F]).coerceIn(0, IMA_STEP_TABLE.lastIndex)

                val byteIdx = i / 2
                if (i % 2 == 0) {
                    out[byteIdx] = (nibble and 0x0F).toByte()
                } else {
                    out[byteIdx] = ((out[byteIdx].toInt() and 0x0F) or ((nibble and 0x0F) shl 4)).toByte()
                }
            }
            return out
        }

        /**
         * Decompresses 4-bit IMA-ADPCM bytes back into 16-bit mono PCM samples.
         */
        fun decodeImaAdpcm(adpcmBytes: ByteArray, sampleCount: Int = adpcmBytes.size * 2): ShortArray {
            if (adpcmBytes.isEmpty() || sampleCount <= 0) return ShortArray(0)
            val totalSamples = sampleCount.coerceAtMost(adpcmBytes.size * 2)
            val out = ShortArray(totalSamples)
            var predictor = 0
            var stepIndex = 0

            for (i in 0 until totalSamples) {
                val b = adpcmBytes[i / 2].toInt() and 0xFF
                val nibble = if (i % 2 == 0) (b and 0x0F) else ((b ushr 4) and 0x0F)
                val step = IMA_STEP_TABLE[stepIndex]

                var vpdiff = step shr 3
                if ((nibble and 4) != 0) vpdiff += step
                if ((nibble and 2) != 0) vpdiff += step shr 1
                if ((nibble and 1) != 0) vpdiff += step shr 2

                if ((nibble and 8) != 0) {
                    predictor -= vpdiff
                } else {
                    predictor += vpdiff
                }
                predictor = predictor.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                stepIndex = (stepIndex + IMA_INDEX_TABLE[nibble]).coerceIn(0, IMA_STEP_TABLE.lastIndex)
                out[i] = predictor.toShort()
            }
            return out
        }

        /**
         * Synthesizes a 20ms (`320` samples @ 16 kHz) walkie-talkie carrier + squelch burst for
         * zero-permission Tactical Synth Comms fallback.
         */
        fun synthesizeTacticalCarrierPcm(
            sequenceNumber: Int,
            carrierFreqHz: Float = 680f,
            squelchTail: Boolean = true
        ): ShortArray {
            val samples = ShortArray(SAMPLES_PER_FRAME)
            val twoPiOverRate = (2.0 * PI) / SAMPLE_RATE_HZ.toDouble()
            val modFreqHz = 180.0 + (abs(sequenceNumber) % 5) * 35.0
            for (i in 0 until SAMPLES_PER_FRAME) {
                val env = when {
                    i < 24 -> i / 24.0
                    squelchTail && i > SAMPLES_PER_FRAME - 32 -> (SAMPLES_PER_FRAME - i) / 32.0
                    else -> 0.85
                }
                val carrier = sin(i * carrierFreqHz * twoPiOverRate)
                val overtone = 0.45 * sin(i * (carrierFreqHz * 1.5) * twoPiOverRate)
                val subMod = 0.25 * sin(i * modFreqHz * twoPiOverRate)
                val squelch = if (squelchTail && i >= SAMPLES_PER_FRAME - 24) {
                    ((i * 1103515245 + 12345 + sequenceNumber) and 0x7FFF) / 32767.0 - 0.5
                } else {
                    0.0
                }
                val combined = ((carrier * 0.55 + overtone * 0.30 + subMod + squelch * 0.35) * env * 18_500.0)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                samples[i] = combined.toShort()
            }
            return samples
        }

        /**
         * Computes 4 normalized dB waveform bar levels (`0.0f..1.0f`) from a 16-bit PCM frame.
         */
        fun computeFourBarWaveformLevels(pcmSamples: ShortArray): List<Float> {
            if (pcmSamples.isEmpty()) return listOf(0.12f, 0.12f, 0.12f, 0.12f)
            val sliceLen = (pcmSamples.size / 4).coerceAtLeast(1)
            return (0 until 4).map { barIdx ->
                val start = (barIdx * sliceLen).coerceAtMost(pcmSamples.lastIndex)
                val end = if (barIdx == 3) pcmSamples.size else ((barIdx + 1) * sliceLen).coerceAtMost(pcmSamples.size)
                var sumSq = 0.0
                var count = 0
                for (i in start until end) {
                    val norm = pcmSamples[i].toDouble() / 32768.0
                    sumSq += norm * norm
                    count++
                }
                val rms = if (count > 0) sqrt(sumSq / count) else 0.0
                val dbNormalized = if (rms <= 0.0001) {
                    0.12f
                } else {
                    val db = 20.0 * log10(rms) // -80..0 dB
                    ((db + 48.0) / 48.0).toFloat().coerceIn(0.15f, 1.0f)
                }
                dbNormalized
            }
        }

        /**
         * Deterministic `@willow`, `@cinder`, `@mote` AI companion radio reply for PTT voice comms.
         */
        fun buildCompanionVoiceRadioReply(
            senderHandle: String,
            wave: Int,
            activeChannel: String,
            sequenceNumber: Int = 1,
            nowMs: Long = System.currentTimeMillis()
        ): RmblynChatMessage {
            val normChannel = RmblynFiveChannelRouter.normalizeChannelId(activeChannel)
            val personaIdx = abs(sequenceNumber + wave) % 3
            val (botHandle, replyText) = when (personaIdx) {
                0 -> "@willow" to "[RADIO 16kHz ACK] Copy $senderHandle on #$normChannel! Medic shield & Tag-Team Rescue locked for Wave $wave."
                1 -> "@cinder" to "[RADIO 16kHz ACK] Loud and clear $senderHandle! Priming cryo/red barrels on Wave $wave perimeter."
                else -> "@mote" to "[RADIO 16kHz ACK] Signal 5x5 $senderHandle! Hyper Railgun overwatch covering Wave $wave."
            }
            return RmblynChatMessage(
                id = "ptt_bot_${personaIdx}_$nowMs",
                channelId = normChannel,
                senderHandle = botHandle,
                messageText = replyText,
                timestampMs = nowMs,
                isCompanionBot = true
            )
        }
    }
}
