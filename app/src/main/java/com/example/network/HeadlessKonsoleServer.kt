package com.example.network

import android.util.Log
import com.example.game.BoxBoyGameEngine
import com.example.game.GameSessionState
import com.example.game.StationKind
import com.example.game.ZombieArchetype
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.math.roundToInt

private const val TAG = "KonsoleServer"
const val KONSOLE_TCP_PORT = 48900
const val DEFAULT_KONSOLE_TICK_HZ = 20

/**
 * Background TCP Konsole & AI/LLM Stress-Test Server on port 48900.
 * Allows running, inspecting, stepping, and stress-testing 100% of BoxBoy Zombies
 * from a Linux Konsole (`adb forward tcp:48900 tcp:48900 && nc 127.0.0.1 48900`)
 * or from the in-app Konsole Debugger!
 */
class HeadlessKonsoleServer(
    private val scope: CoroutineScope,
    private val commandDispatcher: (String) -> String,
    private val port: Int = KONSOLE_TCP_PORT
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val _konsoleLog = MutableStateFlow(
        listOf(
            "BOXBOY KONSOLE DAEMON v2.6 READY ON LOOPBACK TCP PORT $port",
            "Linux/Terminal Connect: adb forward tcp:$port tcp:$port && nc 127.0.0.1 $port",
            "Type HELP, MAP, STATE, JSON, STRESS 150 300, SPAWN 30 DEVIL_BOSS, or BOT ON."
        )
    )
    val konsoleLog: StateFlow<List<String>> = _konsoleLog.asStateFlow()

    fun startServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val srv = ServerSocket(port, 8, InetAddress.getLoopbackAddress())
                srv.reuseAddress = true
                serverSocket = srv
                while (isActive) {
                    val client = srv.accept()
                    client.tcpNoDelay = true
                    launch(Dispatchers.IO) {
                        try {
                            val writer = PrintWriter(client.getOutputStream(), true)
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            writer.println("=== BOXBOY ZOMBIES HEADLESS KONSOLE (TCP :$port) ===")
                            writer.println("Type HELP for commands, MAP for ASCII view, JSON for LLM state, QUIT to close.")
                            writer.print("boxboy> ")
                            writer.flush()
                            var line: String? = reader.readLine()
                            while (line != null && isActive) {
                                val trimmed = line.trim()
                                if (trimmed.equals("QUIT", ignoreCase = true) || trimmed.equals("EXIT", ignoreCase = true)) {
                                    writer.println("Closing Konsole session.")
                                    break
                                }
                                if (trimmed.isNotEmpty()) {
                                    val output = executeAndRecord(trimmed)
                                    writer.println(output)
                                }
                                writer.print("boxboy> ")
                                writer.flush()
                                line = reader.readLine()
                            }
                        } catch (_: Exception) {
                        } finally {
                            try {
                                client.close()
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runCatching { Log.w(TAG, "Konsole TCP server stopped: ${e.message}") }
            }
        }
    }

    fun executeAndRecord(rawCommand: String): String {
        val clean = rawCommand.trim()
        val response = commandDispatcher(clean)
        val updated = (_konsoleLog.value + listOf("> $clean", response)).takeLast(24)
        _konsoleLog.value = updated
        return response
    }

    fun stopServer() {
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    companion object {
        fun renderAsciiBunkerMap(state: GameSessionState): String {
            val cols = 48
            val rows = 18
            val grid = Array(rows) { CharArray(cols) { ' ' } }

            val scaleX = cols / state.map.worldWidth
            val scaleY = rows / state.map.worldHeight

            // Draw rooms
            state.map.rooms.forEach { room ->
                val x1 = (room.bounds.left * scaleX).toInt().coerceIn(0, cols - 1)
                val x2 = (room.bounds.right * scaleX).toInt().coerceIn(0, cols - 1)
                val y1 = (room.bounds.top * scaleY).toInt().coerceIn(0, rows - 1)
                val y2 = (room.bounds.bottom * scaleY).toInt().coerceIn(0, rows - 1)
                val fillChar = if (room.isUnlocked) '.' else '#'
                for (y in y1..y2) {
                    for (x in x1..x2) {
                        if (y == y1 || y == y2 || x == x1 || x == x2) {
                            grid[y][x] = '+'
                        } else {
                            grid[y][x] = fillChar
                        }
                    }
                }
            }

            // Draw stations
            state.map.stations.forEach { st ->
                if (st.roomIndex in state.unlockedRooms) {
                    val sx = (st.x * scaleX).roundToInt().coerceIn(0, cols - 1)
                    val sy = (st.y * scaleY).roundToInt().coerceIn(0, rows - 1)
                    grid[sy][sx] = when (st.kind) {
                        StationKind.DEBRIS_DOOR -> '='
                        StationKind.MYSTERY_BOX -> '?'
                        StationKind.PACK_A_PUNCH -> 'U'
                        StationKind.WALL_WEAPON -> 'W'
                        StationKind.PERK_MACHINE -> 'P'
                        StationKind.BARRICADE_WINDOW -> 'X'
                        StationKind.SIEGE_FABRICATOR -> 'F'
                        StationKind.UPGRADE_ANVIL -> 'V'
                    }
                }
            }

            // Draw explosive barrels
            state.map.barrels.forEach { b ->
                if (!b.isDetonated && b.roomIndex in state.unlockedRooms) {
                    val bx = (b.x * scaleX).roundToInt().coerceIn(0, cols - 1)
                    val by = (b.y * scaleY).roundToInt().coerceIn(0, rows - 1)
                    grid[by][bx] = 'B'
                }
            }

            // Draw zombies
            state.zombies.forEach { z ->
                val zx = (z.x * scaleX).roundToInt().coerceIn(0, cols - 1)
                val zy = (z.y * scaleY).roundToInt().coerceIn(0, rows - 1)
                grid[zy][zx] = z.archetype.asciiChar
            }

            // Draw player
            val px = (state.playerX * scaleX).roundToInt().coerceIn(0, cols - 1)
            val py = (state.playerY * scaleY).roundToInt().coerceIn(0, rows - 1)
            grid[py][px] = '@'

            val sb = StringBuilder()
            sb.appendLine("WAVE ${state.wave} | HP ${state.hp}/${state.maxHp} | SHD ${state.shield}/${state.maxShield} | PTS ${state.points} | KILLS ${state.totalKills} | WPN ${state.activeModularWeaponForCurrentSlot.customTitle} (${state.currentAmmoCount}/${state.currentMaxMagSize}) | ZOMBIES ${state.zombies.size}")
            sb.appendLine("Legend: [@]=Player [z/s/c/A/W/D]=Hostiles [B]=Barrel [F]=Siege [V]=Anvil [?]=Box [U]=Pack [W]=WallGun [=]=Door")
            grid.forEach { row -> sb.appendLine(String(row)) }
            return sb.toString().trimEnd()
        }

        fun dumpStateJson(state: GameSessionState): String {
            return JSONObject().apply {
                put("wave", state.wave)
                put("hp", state.hp)
                put("maxHp", state.maxHp)
                put("shield", state.shield)
                put("maxShield", state.maxShield)
                put("points", state.points)
                put("abyssalScrap", state.depthEscrow.abyssalScrap)
                put("abyssalCrystals", state.depthEscrow.abyssalCrystals)
                put("bankedEscrowPoints", state.depthEscrow.bankedEscrowPoints)
                put("totalKills", state.totalKills)
                put("comboCount", state.comboCount)
                put("playerX", state.playerX.roundToInt())
                put("playerY", state.playerY.roundToInt())
                put("weapon", state.currentWeapon.id)
                put("modularWeaponTitle", state.activeModularWeaponForCurrentSlot.customTitle)
                put("modularWeaponRarity", state.activeModularWeaponForCurrentSlot.rarity.name)
                put("weaponLevel", state.currentWeaponLevel)
                put("ammo", state.currentAmmoCount)
                put("maxMag", state.currentMaxMagSize)
                put("reloadRemainingMs", state.reloadRemainingMs)
                put("shotsFired", state.ammoTelemetry.shotsFired)
                put("lightLockCrits", state.ammoTelemetry.lightLockCrits)
                put("accuracyPercent", state.ammoTelemetry.accuracyPercent)
                put("reloadsCompleted", state.ammoTelemetry.reloadsCompleted)
                put("cameraZoom", state.cameraTension.currentZoom)
                put("cameraFramingState", state.cameraTension.framingState.name)
                put("lightingMode", state.cameraTension.lightingMode.name)
                put("ringBufferNodeCount", state.mazeState.ringBufferNodes.size)
                put("nonEuclideanRewrites", state.mazeState.nonEuclideanRewritesCount)
                put("armorDefenseRating", state.armorRig.totalDefenseRating)
                put("armorSetActive", state.armorRig.paletteProfile.activeSetName)
                put("aiBotEnabled", state.aiBotEnabled)
                put("unlockedRooms", JSONArray(state.unlockedRooms.toList()))
                put("activeZombies", state.zombies.size)
                put("remainingWaveSpawns", state.zombiesRemainingInWave)
                put(
                    "speedConfig",
                    JSONObject().apply {
                        put("clock", state.speedConfig.clockMultiplier)
                        put("playerMove", state.speedConfig.playerMoveMultiplier)
                        put("zombieSpeed", state.speedConfig.zombieSpeedMultiplier)
                        put("fireReload", state.speedConfig.fireReloadMultiplier)
                    }
                )
            }.toString(2)
        }

        data class CliLaunchConfig(
            val port: Int = KONSOLE_TCP_PORT,
            val tickHz: Int = DEFAULT_KONSOLE_TICK_HZ,
            val asciiPreview: Boolean = false,
            val daemonMode: Boolean = false,
            val warmupTicks: Int = 4
        ) {
            val tickIntervalMs: Long
                get() = (1000L / tickHz.coerceIn(1, 240)).coerceAtLeast(4L)
        }

        @Volatile
        var lastCliLaunchConfig: CliLaunchConfig? = null
            private set

        @Volatile
        var lastAsciiPreviewOutput: String = ""
            private set

        @JvmStatic
        fun parseCliArgs(args: Array<String>): CliLaunchConfig {
            var port = KONSOLE_TCP_PORT
            var tickHz = DEFAULT_KONSOLE_TICK_HZ
            var asciiPreview = false
            var daemonMode = false
            var warmupTicks = 4

            var i = 0
            while (i < args.size) {
                val token = args[i].trim()
                when {
                    token == "--port" && i + 1 < args.size -> {
                        port = args[i + 1].toIntOrNull()?.coerceIn(1024, 65535) ?: KONSOLE_TCP_PORT
                        i += 2
                    }
                    token.startsWith("--port=") -> {
                        port = token.substringAfter("=").toIntOrNull()?.coerceIn(1024, 65535) ?: KONSOLE_TCP_PORT
                        i++
                    }
                    token == "--tick-hz" && i + 1 < args.size -> {
                        tickHz = args[i + 1].toIntOrNull()?.coerceIn(1, 240) ?: DEFAULT_KONSOLE_TICK_HZ
                        i += 2
                    }
                    token.startsWith("--tick-hz=") -> {
                        tickHz = token.substringAfter("=").toIntOrNull()?.coerceIn(1, 240) ?: DEFAULT_KONSOLE_TICK_HZ
                        i++
                    }
                    token == "--ascii-preview" -> {
                        asciiPreview = true
                        i++
                    }
                    token == "--daemon" -> {
                        daemonMode = true
                        i++
                    }
                    token == "--ticks" && i + 1 < args.size -> {
                        warmupTicks = args[i + 1].toIntOrNull()?.coerceIn(1, 1000) ?: 4
                        i += 2
                    }
                    token.toIntOrNull() != null -> {
                        port = token.toInt().coerceIn(1024, 65535)
                        i++
                    }
                    else -> i++
                }
            }
            return CliLaunchConfig(
                port = port,
                tickHz = tickHz,
                asciiPreview = asciiPreview,
                daemonMode = daemonMode,
                warmupTicks = warmupTicks
            )
        }

        @JvmStatic
        fun createStandaloneCommandDispatcher(
            engine: BoxBoyGameEngine = BoxBoyGameEngine(),
            initialState: GameSessionState = GameSessionState(aiBotEnabled = true),
            tickHz: Int = DEFAULT_KONSOLE_TICK_HZ
        ): (String) -> String {
            var currentState = initialState
            val deltaMs = (1000L / tickHz.coerceIn(1, 240)).coerceAtLeast(4L)
            return { rawCommand ->
                val parts = rawCommand.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val verb = parts.firstOrNull()?.uppercase() ?: "HELP"
                when (verb) {
                    "HELP" -> "COMMANDS: HELP | MAP | STATE | JSON | STEP [n] | WAVE <n> | SPAWN <count> [ARCHETYPE] | STRESS <count> <ticks> | BOT ON|OFF | QUIT"
                    "MAP" -> renderAsciiBunkerMap(currentState)
                    "STATE", "STATUS" -> "WAVE ${currentState.wave} | HP ${currentState.hp}/${currentState.maxHp} | PTS ${currentState.points} | ZOMBIES ${currentState.zombies.size} | TICK_HZ $tickHz"
                    "JSON" -> dumpStateJson(currentState)
                    "STEP" -> {
                        val steps = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 240) ?: 1
                        repeat(steps) {
                            currentState = engine.stepFrame(
                                state = currentState,
                                deltaMsRaw = deltaMs,
                                moveXInput = 0f,
                                moveYInput = 0f,
                                isFiringInput = false,
                                isAltBarrelDropInput = false,
                                isInteractPressedInput = false,
                                coopPeerX = null,
                                coopPeerY = null
                            )
                        }
                        renderAsciiBunkerMap(currentState)
                    }
                    "WAVE" -> {
                        val targetWave = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 999) ?: (currentState.wave + 1)
                        currentState = currentState.copy(wave = targetWave)
                        "WAVE SET TO $targetWave"
                    }
                    "SPAWN" -> {
                        val count = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 200) ?: 10
                        val archName = parts.getOrNull(2)?.uppercase() ?: "SHAMBLER"
                        val arch = ZombieArchetype.entries.find { it.name == archName } ?: ZombieArchetype.SHAMBLER
                        currentState = engine.spawnStressZombies(currentState, count, arch)
                        currentState.statusToast
                    }
                    "STRESS" -> {
                        val count = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 250) ?: 60
                        val ticks = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(10, 500) ?: 60
                        val (nextState, report) = engine.runHeadlessBenchmark(currentState, count, ticks)
                        currentState = nextState
                        "STRESS COMPLETE: ${report.ticksSimulated} ticks, peak=${report.zombieCountPeak}, maxFps=${report.estimatedMaxFps}"
                    }
                    "BOT" -> {
                        val enabled = parts.getOrNull(1)?.uppercase() != "OFF"
                        currentState = currentState.copy(aiBotEnabled = enabled)
                        "AI BOT=${if (enabled) "ON" else "OFF"}"
                    }
                    else -> "UNKNOWN COMMAND '$verb'. Type HELP."
                }
            }
        }

        /**
         * Cross-platform Linux & Windows CLI entrypoint for HeadlessKonsoleServer.
         * Supports `--port <port>`, `--tick-hz <hz>`, `--ascii-preview`, and `--daemon`.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val config = parseCliArgs(args)
            lastCliLaunchConfig = config
            val engine = BoxBoyGameEngine()
            var simState = engine.spawnStressZombies(
                GameSessionState(aiBotEnabled = true),
                count = 6,
                archetype = ZombieArchetype.SHAMBLER
            )
            repeat(config.warmupTicks) {
                simState = engine.stepFrame(
                    state = simState,
                    deltaMsRaw = config.tickIntervalMs,
                    moveXInput = 0f,
                    moveYInput = 0f,
                    isFiringInput = false,
                    isAltBarrelDropInput = false,
                    isInteractPressedInput = false,
                    coopPeerX = null,
                    coopPeerY = null
                )
            }
            val dispatcher = createStandaloneCommandDispatcher(
                engine = engine,
                initialState = simState,
                tickHz = config.tickHz
            )
            println("BOXBOY HEADLESS KONSOLE CLI // PORT=${config.port} // TICK_HZ=${config.tickHz}")
            if (config.asciiPreview) {
                val preview = dispatcher("MAP")
                lastAsciiPreviewOutput = preview
                println(preview)
            }
            if (config.daemonMode) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val server = HeadlessKonsoleServer(
                    scope = scope,
                    commandDispatcher = dispatcher,
                    port = config.port
                )
                Runtime.getRuntime().addShutdownHook(Thread { server.stopServer() })
                server.startServer()
                val reader = BufferedReader(InputStreamReader(System.`in`))
                var line: String? = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.equals("QUIT", ignoreCase = true) || trimmed.equals("EXIT", ignoreCase = true)) {
                        break
                    }
                    if (trimmed.isNotEmpty()) {
                        println(server.executeAndRecord(trimmed))
                    }
                    line = reader.readLine()
                }
                server.stopServer()
            }
        }
    }
}
