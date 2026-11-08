package com.example.godot

import androidx.annotation.Keep
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

/**
 * Runtime-retained marker annotation matching Godot 4.7.2 `@UsedByGodot` JNI reflection contract
 * without requiring a hard compile-time dependency on `org.godotengine:godot` AAR classes.
 */
@Keep
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class UsedByGodot

/**
 * Hybrid Kotlin <-> Godot 4.7.2 Bridge Plugin (`BoxBoyAegisBridge`).
 *
 * Designed for Google Pixel 8 Pro (`Tensor G3`, `16 KB` ELF page-aligned `godot-4.7.2.stable.aar`)
 * and cross-platform desktop/capsule execution without hard compile dependencies on external
 * `org.godotengine.*` classes.
 */
@Keep
@UsedByGodot
class BoxBoyGodotBridgePlugin(
    private val onStateSnapshotCallback: ((WaveStateBridgeSnapshot) -> Unit)? = null,
    private val onHapticCallback: ((HapticDispatchReceipt) -> Unit)? = null,
    private val onFrameTimingCallback: ((FrameTimingSample) -> Unit)? = null,
    private val onSquadVoicePttCallback: ((SquadVoicePttFrame) -> Unit)? = null
) {

    @Keep
    interface BridgeEventListener {
        fun onThermalTargetFps(targetFps: Int, tierName: String, shaderScale: Float) {}
        fun onCoopPeerSnapshot(peerSnapshotJson: String) {}
        fun onLoadCheckpointJson(checkpointJson: String) {}
        fun onWaveStateSynced(snapshot: WaveStateBridgeSnapshot) {}
        fun onPixelHapticTriggered(receipt: HapticDispatchReceipt) {}
        fun onFrameTimingReported(sample: FrameTimingSample) {}
        fun onSquadVoicePttDispatched(frame: SquadVoicePttFrame) {}
    }

    @Keep
    enum class Pixel8ProLraPrimitive(
        val primitiveCode: Int,
        val defaultScale: Float,
        val fallbackDurationMs: Long
    ) {
        TICK(primitiveCode = 1, defaultScale = 0.45f, fallbackDurationMs = 8L),
        CLICK(primitiveCode = 2, defaultScale = 0.65f, fallbackDurationMs = 12L),
        HEAVY_THUD(primitiveCode = 3, defaultScale = 0.90f, fallbackDurationMs = 24L),
        QUICK_RISE_THUD(primitiveCode = 4, defaultScale = 1.00f, fallbackDurationMs = 36L)
    }

    @Keep
    data class WaveStateBridgeSnapshot(
        val rawJson: String,
        val wave: Int,
        val hp: Int,
        val maxHp: Int,
        val shield: Int,
        val points: Int,
        val totalKills: Int,
        val weaponId: String,
        val activeZombies: Int,
        val playerX: Int,
        val playerY: Int,
        val targetFps: Int
    )

    @Keep
    data class HapticDispatchReceipt(
        val effectId: String,
        val clampedIntensity: Float,
        val mappedPrimitive: Pixel8ProLraPrimitive,
        val amplitude255: Int,
        val durationMs: Long
    )

    @Keep
    data class FrameTimingSample(
        val fps: Float,
        val frameTimeMs: Float,
        val meets120HzBudget: Boolean,
        val meets60HzBudget: Boolean
    )

    @Keep
    data class SquadVoicePttFrame(
        val channelId: String,
        val pcmBase64: String,
        val durationMs: Int
    )

    private val listeners = CopyOnWriteArrayList<BridgeEventListener>()
    private val _hapticHistory = CopyOnWriteArrayList<HapticDispatchReceipt>()
    private val _voicePttHistory = CopyOnWriteArrayList<SquadVoicePttFrame>()

    @Volatile
    var lastSyncedWaveState: WaveStateBridgeSnapshot? = null
        private set

    @Volatile
    var lastFrameTimingSample: FrameTimingSample? = null
        private set

    @Volatile
    var lastThermalTargetFps: Int = 120
        private set

    @Volatile
    var lastCoopPeerSnapshotJson: String = ""
        private set

    @Volatile
    var lastLoadedCheckpointJson: String = ""
        private set

    val hapticHistory: List<HapticDispatchReceipt>
        get() = _hapticHistory.toList()

    val voicePttHistory: List<SquadVoicePttFrame>
        get() = _voicePttHistory.toList()

    @Keep
    fun getPluginName(): String = PLUGIN_NAME

    @Keep
    fun getRegisteredSignalNames(): Set<String> = setOf(
        SIGNAL_THERMAL_TARGET_FPS,
        SIGNAL_COOP_PEER_SNAPSHOT,
        SIGNAL_LOAD_CHECKPOINT_JSON,
        SIGNAL_COMFORT_CONFIG_UPDATED
    )

    fun addListener(listener: BridgeEventListener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: BridgeEventListener) {
        listeners.remove(listener)
    }

    /**
     * Invoked from Godot 4.7.2 (`BoxBoyArenaController.gd`) to synchronize live wave combat state
     * with the Kotlin save-slot ring and tactical overlay.
     */
    @Keep
    @UsedByGodot
    fun syncWaveStateJson(stateJson: String): Boolean {
        val trimmed = stateJson.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val json = JSONObject(trimmed)
            val snapshot = WaveStateBridgeSnapshot(
                rawJson = trimmed,
                wave = json.optInt("wave", 1).coerceAtLeast(1),
                hp = json.optInt("hp", 100).coerceAtLeast(0),
                maxHp = json.optInt("maxHp", 100).coerceAtLeast(1),
                shield = json.optInt("shield", 0).coerceAtLeast(0),
                points = json.optInt("points", 0).coerceAtLeast(0),
                totalKills = json.optInt("totalKills", 0).coerceAtLeast(0),
                weaponId = json.optString("weapon", "pistol").ifBlank { "pistol" },
                activeZombies = json.optInt("activeZombies", 0).coerceAtLeast(0),
                playerX = json.optInt("playerX", 800),
                playerY = json.optInt("playerY", 450),
                targetFps = json.optInt("targetFps", lastThermalTargetFps).coerceIn(30, 120)
            )
            lastSyncedWaveState = snapshot
            onStateSnapshotCallback?.invoke(snapshot)
            listeners.forEach { listener ->
                runCatching { listener.onWaveStateSynced(snapshot) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Invoked from Godot 4.7.2 (`BoxBoyArenaController.gd`) to trigger Google Pixel 8 Pro
     * X-axis LRA haptics with automatic primitive mapping and `[0.0f, 1.0f]` clamping.
     */
    @Keep
    @UsedByGodot
    fun triggerPixelHaptic(effectId: String, intensity: Float): HapticDispatchReceipt {
        val normalizedId = effectId.trim().uppercase().ifEmpty { "GENERIC_PULSE" }
        val clamped = if (intensity.isNaN()) 0.5f else intensity.coerceIn(0.0f, 1.0f)
        val primitive = when {
            normalizedId.contains("SHOTGUN") ||
                normalizedId.contains("RAILGUN") ||
                normalizedId.contains("BOSS") ||
                normalizedId.contains("KABOOM") -> Pixel8ProLraPrimitive.QUICK_RISE_THUD
            normalizedId.contains("DASH") ||
                normalizedId.contains("GRENADE") ||
                normalizedId.contains("DAMAGE") -> Pixel8ProLraPrimitive.HEAVY_THUD
            normalizedId.contains("SWAP") ||
                normalizedId.contains("INTERACT") ||
                normalizedId.contains("RELOAD") -> Pixel8ProLraPrimitive.CLICK
            else -> Pixel8ProLraPrimitive.TICK
        }
        val amp255 = (clamped * 255f).roundToInt().coerceIn(1, 255)
        val receipt = HapticDispatchReceipt(
            effectId = normalizedId,
            clampedIntensity = clamped,
            mappedPrimitive = primitive,
            amplitude255 = amp255,
            durationMs = primitive.fallbackDurationMs
        )
        _hapticHistory.add(receipt)
        while (_hapticHistory.size > 64) {
            _hapticHistory.removeAt(0)
        }
        onHapticCallback?.invoke(receipt)
        listeners.forEach { listener ->
            runCatching { listener.onPixelHapticTriggered(receipt) }
        }
        return receipt
    }

    /**
     * Invoked from Godot 4.7.2 (`BoxBoyArenaController.gd`) to report live render FPS and frame times.
     */
    @Keep
    @UsedByGodot
    fun reportFrameTiming(fps: Float, frameTimeMs: Float): FrameTimingSample {
        val safeFps = if (fps.isFinite()) fps.coerceIn(0f, 240f) else 0f
        val safeFrameMs = if (frameTimeMs.isFinite()) frameTimeMs.coerceIn(0.1f, 250f) else 16.67f
        val sample = FrameTimingSample(
            fps = safeFps,
            frameTimeMs = safeFrameMs,
            meets120HzBudget = safeFrameMs <= 8.85f && safeFps >= 110f,
            meets60HzBudget = safeFrameMs <= 17.2f && safeFps >= 56f
        )
        lastFrameTimingSample = sample
        onFrameTimingCallback?.invoke(sample)
        listeners.forEach { listener ->
            runCatching { listener.onFrameTimingReported(sample) }
        }
        return sample
    }

    /**
     * Invoked from Godot 4.7.2 (`BoxBoyArenaController.gd`) when the player transmits a squad PTT voice frame.
     */
    @Keep
    @UsedByGodot
    fun dispatchSquadVoicePttFrame(channelId: String, pcmBase64: String, durationMs: Int): Boolean {
        val cleanChannel = channelId.trim()
        val cleanPayload = pcmBase64.trim()
        if (cleanChannel.isEmpty() || cleanPayload.isEmpty() || durationMs <= 0) {
            return false
        }
        val frame = SquadVoicePttFrame(
            channelId = cleanChannel,
            pcmBase64 = cleanPayload,
            durationMs = durationMs.coerceIn(10, 5_000)
        )
        _voicePttHistory.add(frame)
        while (_voicePttHistory.size > 32) {
            _voicePttHistory.removeAt(0)
        }
        onSquadVoicePttCallback?.invoke(frame)
        listeners.forEach { listener ->
            runCatching { listener.onSquadVoicePttDispatched(frame) }
        }
        return true
    }

    /**
     * Host -> Godot signal callback dispatcher for Tensor G3 thermal target FPS updates (`120 -> 60 -> 30`).
     */
    @Keep
    fun onThermalTargetFps(
        targetFps: Int,
        tierName: String = resolveTierName(targetFps),
        shaderScale: Float = resolveShaderScale(targetFps)
    ) {
        val clampedFps = when {
            targetFps >= 100 -> 120
            targetFps >= 45 -> 60
            else -> 30
        }
        lastThermalTargetFps = clampedFps
        listeners.forEach { listener ->
            runCatching { listener.onThermalTargetFps(clampedFps, tierName, shaderScale) }
        }
    }

    /**
     * Host -> Godot signal callback dispatcher for LAN/Konsole Co-Op peer state snapshots.
     */
    @Keep
    fun onCoopPeerSnapshot(peerSnapshotJson: String) {
        val trimmed = peerSnapshotJson.trim()
        if (trimmed.isEmpty()) return
        lastCoopPeerSnapshotJson = trimmed
        listeners.forEach { listener ->
            runCatching { listener.onCoopPeerSnapshot(trimmed) }
        }
    }

    /**
     * Host -> Godot signal callback dispatcher for 20-slot Save-Ring checkpoint restoration.
     */
    @Keep
    fun onLoadCheckpointJson(checkpointJson: String) {
        val trimmed = checkpointJson.trim()
        if (trimmed.isEmpty()) return
        lastLoadedCheckpointJson = trimmed
        syncWaveStateJson(trimmed)
        listeners.forEach { listener ->
            runCatching { listener.onLoadCheckpointJson(trimmed) }
        }
    }

    companion object {
        const val PLUGIN_NAME = "BoxBoyAegisBridge"
        const val GODOT_AAR_VERSION = "4.7.2.stable"
        const val REQUIRED_ELF_PAGE_ALIGNMENT_BYTES = 16384

        const val SIGNAL_THERMAL_TARGET_FPS = "on_thermal_target_fps"
        const val SIGNAL_COOP_PEER_SNAPSHOT = "on_coop_peer_snapshot"
        const val SIGNAL_LOAD_CHECKPOINT_JSON = "on_load_checkpoint_json"
        const val SIGNAL_COMFORT_CONFIG_UPDATED = "on_comfort_config_updated"

        private fun resolveTierName(targetFps: Int): String = when {
            targetFps >= 100 -> "TIER_120_NOMINAL"
            targetFps >= 45 -> "TIER_60_WARM"
            else -> "TIER_30_THROTTLED"
        }

        private fun resolveShaderScale(targetFps: Int): Float = when {
            targetFps >= 100 -> 1.0f
            targetFps >= 45 -> 0.75f
            else -> 0.50f
        }
    }
}
