package com.example

import com.example.godot.BoxBoyGodotBridgePlugin
import com.example.network.HeadlessKonsoleServer
import com.example.thermal.TensorG3ThermalGovernor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class GodotAndPixel8ProThermalTest {

    private fun resolveProjectRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".").canonicalFile
        if (File(cwd, "godot/project.godot").exists()) {
            return cwd
        }
        val parent = cwd.parentFile
        if (parent != null && File(parent, "godot/project.godot").exists()) {
            return parent
        }
        return cwd
    }

    @Test
    fun boxBoyGodotBridgePlugin_syncsWaveStateAndClampsPixel8ProHaptics() {
        val plugin = BoxBoyGodotBridgePlugin()
        assertEquals("BoxBoyAegisBridge", plugin.getPluginName())
        assertEquals("4.7.2.stable", BoxBoyGodotBridgePlugin.GODOT_AAR_VERSION)
        assertEquals(16384, BoxBoyGodotBridgePlugin.REQUIRED_ELF_PAGE_ALIGNMENT_BYTES)

        val signals = plugin.getRegisteredSignalNames()
        assertTrue(signals.contains("on_thermal_target_fps"))
        assertTrue(signals.contains("on_coop_peer_snapshot"))
        assertTrue(signals.contains("on_load_checkpoint_json"))

        val statePayload = """
            {
              "wave": 7,
              "hp": 88,
              "maxHp": 100,
              "shield": 45,
              "points": 2450,
              "totalKills": 64,
              "weapon": "railgun",
              "activeZombies": 19,
              "playerX": 912,
              "playerY": 480,
              "targetFps": 120
            }
        """.trimIndent()
        assertTrue(plugin.syncWaveStateJson(statePayload))
        val synced = plugin.lastSyncedWaveState
        assertNotNull(synced)
        assertEquals(7, synced?.wave)
        assertEquals(88, synced?.hp)
        assertEquals("railgun", synced?.weaponId)
        assertEquals(19, synced?.activeZombies)

        val railgunHaptic = plugin.triggerPixelHaptic("WEAPON_FIRE_RAILGUN", 1.45f)
        assertEquals(1.0f, railgunHaptic.clampedIntensity, 0.0001f)
        assertEquals(
            BoxBoyGodotBridgePlugin.Pixel8ProLraPrimitive.QUICK_RISE_THUD,
            railgunHaptic.mappedPrimitive
        )
        assertEquals(255, railgunHaptic.amplitude255)

        val dashHaptic = plugin.triggerPixelHaptic("TACTICAL_DASH", 0.65f)
        assertEquals(
            BoxBoyGodotBridgePlugin.Pixel8ProLraPrimitive.HEAVY_THUD,
            dashHaptic.mappedPrimitive
        )

        val swapHaptic = plugin.triggerPixelHaptic("WEAPON_SWAP", 0.35f)
        assertEquals(
            BoxBoyGodotBridgePlugin.Pixel8ProLraPrimitive.CLICK,
            swapHaptic.mappedPrimitive
        )

        val timing120 = plugin.reportFrameTiming(119.4f, 8.33f)
        assertTrue(timing120.meets120HzBudget)
        assertTrue(timing120.meets60HzBudget)

        val timing60 = plugin.reportFrameTiming(59.8f, 16.67f)
        assertFalse(timing60.meets120HzBudget)
        assertTrue(timing60.meets60HzBudget)

        assertTrue(plugin.dispatchSquadVoicePttFrame("party:lfs-boxboy-zombies:default", "UENNMTZfT1BVUw==", 60))
        assertEquals(1, plugin.voicePttHistory.size)
        assertEquals(60, plugin.voicePttHistory.first().durationMs)
    }

    @Test
    fun boxBoyGodotBridgePlugin_dispatchesHostToGodotCallbacks() {
        val plugin = BoxBoyGodotBridgePlugin()
        val observedFps = AtomicInteger(0)
        val observedTier = AtomicReference("")
        val observedPeerJson = AtomicReference("")
        val observedCheckpointJson = AtomicReference("")

        plugin.addListener(object : BoxBoyGodotBridgePlugin.BridgeEventListener {
            override fun onThermalTargetFps(targetFps: Int, tierName: String, shaderScale: Float) {
                observedFps.set(targetFps)
                observedTier.set(tierName)
            }

            override fun onCoopPeerSnapshot(peerSnapshotJson: String) {
                observedPeerJson.set(peerSnapshotJson)
            }

            override fun onLoadCheckpointJson(checkpointJson: String) {
                observedCheckpointJson.set(checkpointJson)
            }
        })

        plugin.onThermalTargetFps(60)
        assertEquals(60, observedFps.get())
        assertEquals("TIER_60_WARM", observedTier.get())

        val peerJson = """{"peerId":"@willow","x":760,"y":420,"wave":3}"""
        plugin.onCoopPeerSnapshot(peerJson)
        assertEquals(peerJson, observedPeerJson.get())

        val checkpointJson = """{"wave":5,"hp":95,"points":1800,"totalKills":42,"weapon":"shotgun"}"""
        plugin.onLoadCheckpointJson(checkpointJson)
        assertEquals(checkpointJson, observedCheckpointJson.get())
        assertEquals(5, plugin.lastSyncedWaveState?.wave)
    }

    @Test
    fun tensorG3ThermalGovernor_stepsDownImmediatelyAndRecoversAfterDwell() {
        var fakeClockMs = 1_000L
        val governor = TensorG3ThermalGovernor(
            recoveryDwellMs = 8_000L,
            monotonicClockMs = { fakeClockMs }
        )

        // Initial nominal state: 120 FPS
        val nominal = governor.evaluateSample(
            TensorG3ThermalGovernor.ThermalTelemetrySample(
                status = TensorG3ThermalGovernor.ThermalStatusLevel.NONE,
                thermalHeadroom = 0.50f,
                skinTempCelsius = 36.0f,
                timestampMs = fakeClockMs
            )
        )
        assertEquals(TensorG3ThermalGovernor.ThermalTier.TIER_120_NOMINAL, nominal.activeTier)
        assertEquals(120, nominal.targetFps)

        // Step down 120 -> 60 FPS immediately on MODERATE thermal status
        fakeClockMs += 1_000L
        val warm = governor.evaluateSample(
            TensorG3ThermalGovernor.ThermalTelemetrySample(
                status = TensorG3ThermalGovernor.ThermalStatusLevel.MODERATE,
                thermalHeadroom = 0.86f,
                skinTempCelsius = 42.8f,
                timestampMs = fakeClockMs
            )
        )
        assertEquals(TensorG3ThermalGovernor.ThermalTier.TIER_60_WARM, warm.activeTier)
        assertEquals(60, warm.targetFps)
        assertTrue(warm.transitionedThisStep)

        // Step down 60 -> 30 FPS immediately on SEVERE thermal status
        fakeClockMs += 1_000L
        val throttled = governor.evaluateSample(
            TensorG3ThermalGovernor.ThermalTelemetrySample(
                status = TensorG3ThermalGovernor.ThermalStatusLevel.SEVERE,
                thermalHeadroom = 0.97f,
                skinTempCelsius = 45.4f,
                timestampMs = fakeClockMs
            )
        )
        assertEquals(TensorG3ThermalGovernor.ThermalTier.TIER_30_THROTTLED, throttled.activeTier)
        assertEquals(30, throttled.targetFps)
        assertTrue(throttled.transitionedThisStep)

        // Cool down to LIGHT status: before 8,000ms dwell completes, must stay at 30 FPS
        fakeClockMs += 1_000L
        val recoveryStart = governor.evaluateSample(
            TensorG3ThermalGovernor.ThermalTelemetrySample(
                status = TensorG3ThermalGovernor.ThermalStatusLevel.LIGHT,
                thermalHeadroom = 0.65f,
                skinTempCelsius = 39.0f,
                timestampMs = fakeClockMs
            )
        )
        assertEquals(TensorG3ThermalGovernor.ThermalTier.TIER_30_THROTTLED, recoveryStart.activeTier)

        fakeClockMs += 5_000L
        val midDwell = governor.evaluateSample(
            TensorG3ThermalGovernor.ThermalTelemetrySample(
                status = TensorG3ThermalGovernor.ThermalStatusLevel.LIGHT,
                thermalHeadroom = 0.65f,
                skinTempCelsius = 39.0f,
                timestampMs = fakeClockMs
            )
        )
        assertEquals(TensorG3ThermalGovernor.ThermalTier.TIER_30_THROTTLED, midDwell.activeTier)

        // Complete 8,000ms dwell -> recovers one step from 30 -> 60 FPS
        fakeClockMs += 3_500L
        val recoveredTo60 = governor.evaluateSample(
            TensorG3ThermalGovernor.ThermalTelemetrySample(
                status = TensorG3ThermalGovernor.ThermalStatusLevel.LIGHT,
                thermalHeadroom = 0.65f,
                skinTempCelsius = 39.0f,
                timestampMs = fakeClockMs
            )
        )
        assertEquals(TensorG3ThermalGovernor.ThermalTier.TIER_60_WARM, recoveredTo60.activeTier)
        assertEquals(60, recoveredTo60.targetFps)

        // Start second dwell window for 60 -> 120 FPS recovery
        fakeClockMs += 1_000L
        governor.evaluateSample(
            TensorG3ThermalGovernor.ThermalTelemetrySample(
                status = TensorG3ThermalGovernor.ThermalStatusLevel.NONE,
                thermalHeadroom = 0.55f,
                skinTempCelsius = 37.5f,
                timestampMs = fakeClockMs
            )
        )
        fakeClockMs += 8_200L
        val recoveredTo120 = governor.evaluateSample(
            TensorG3ThermalGovernor.ThermalTelemetrySample(
                status = TensorG3ThermalGovernor.ThermalStatusLevel.NONE,
                thermalHeadroom = 0.55f,
                skinTempCelsius = 37.5f,
                timestampMs = fakeClockMs
            )
        )
        assertEquals(TensorG3ThermalGovernor.ThermalTier.TIER_120_NOMINAL, recoveredTo120.activeTier)
        assertEquals(120, recoveredTo120.targetFps)
    }

    @Test
    fun headlessKonsoleServer_parsesCliArgsAndExecutesStandalonePreview() {
        val parsed = HeadlessKonsoleServer.parseCliArgs(
            arrayOf("--port", "48925", "--tick-hz", "30", "--ascii-preview", "--ticks", "2")
        )
        assertEquals(48925, parsed.port)
        assertEquals(30, parsed.tickHz)
        assertTrue(parsed.asciiPreview)
        assertFalse(parsed.daemonMode)

        HeadlessKonsoleServer.main(
            arrayOf("--port", "48925", "--tick-hz", "30", "--ascii-preview", "--ticks", "2")
        )
        assertEquals(48925, HeadlessKonsoleServer.lastCliLaunchConfig?.port)
        assertTrue(HeadlessKonsoleServer.lastAsciiPreviewOutput.contains("WAVE 1"))
        assertTrue(HeadlessKonsoleServer.lastAsciiPreviewOutput.contains("@"))

        val dispatcher = HeadlessKonsoleServer.createStandaloneCommandDispatcher(tickHz = 25)
        assertTrue(dispatcher("STATUS").contains("TICK_HZ 25"))
        assertEquals("WAVE SET TO 4", dispatcher("WAVE 4"))
        assertTrue(dispatcher("JSON").contains("\"wave\": 4"))
    }

    @Test
    fun godotProjectLaunchersAndWebCapsule_satisfyRatchetAndGate09Invariants() {
        val root = resolveProjectRoot()
        val godotProject = File(root, "godot/project.godot")
        val godotScene = File(root, "godot/scenes/BoxBoyArenaMain.tscn")
        val godotScript = File(root, "godot/scripts/BoxBoyArenaController.gd")
        val linuxLauncher = File(root, "scripts/launch_linux.sh")
        val windowsLauncher = File(root, "scripts/launch_windows.bat")
        val webIndex = File(root, "index.html")

        val allFiles = listOf(
            godotProject,
            godotScene,
            godotScript,
            linuxLauncher,
            windowsLauncher,
            webIndex
        )
        allFiles.forEach { file ->
            assertTrue("Expected file to exist: ${file.path}", file.exists())
        }

        val projectText = godotProject.readText()
        assertTrue(projectText.contains("config_version=5"))
        assertTrue(projectText.contains("res://scenes/BoxBoyArenaMain.tscn"))
        assertTrue(projectText.contains("\"4.7\""))

        val sceneText = godotScene.readText()
        assertTrue(sceneText.contains("BoxBoyArenaController.gd"))
        assertTrue(sceneText.contains("YSortWorldStage"))
        assertTrue(sceneText.contains("BoxBoyPlayer"))

        val scriptText = godotScript.readText()
        assertTrue(scriptText.contains("syncWaveStateJson"))
        assertTrue(scriptText.contains("triggerPixelHaptic"))
        assertTrue(scriptText.contains("reportFrameTiming"))
        assertTrue(scriptText.contains("dispatchSquadVoicePttFrame"))

        val indexText = webIndex.readText()
        assertTrue(indexText.contains("./lds_tokens.css"))
        assertTrue(indexText.contains("./oasis-capsule-sdk.js"))
        assertTrue(indexText.contains("<oasis-multiplayer-bar data-game-id=\"lfs-boxboy-zombies\"></oasis-multiplayer-bar>"))
        assertTrue(indexText.contains("allow-scripts allow-pointer-lock"))
        assertFalse(indexText.contains("allow-same-origin"))

        val forbiddenHomePath = "/home/" + "joe"
        val forbiddenMarkers = listOf("TO" + "DO", "FIX" + "ME", "PLACE" + "HOLDER")
        allFiles.forEach { file ->
            val content = file.readText()
            assertFalse("Forbidden path in ${file.name}", content.contains(forbiddenHomePath))
            forbiddenMarkers.forEach { marker ->
                assertFalse("Forbidden marker $marker in ${file.name}", content.contains(marker))
            }
        }
    }
}
