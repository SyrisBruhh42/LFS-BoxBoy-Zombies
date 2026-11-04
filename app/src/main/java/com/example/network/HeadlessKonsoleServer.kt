package com.example.network

import android.util.Log
import com.example.game.GameSessionState
import com.example.game.StationKind
import com.example.game.ZombieArchetype
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
            "BOXBOY KONSOLE DAEMON v2.6 READY ON LOOPBACK TCP PORT $KONSOLE_TCP_PORT",
            "Linux/Terminal Connect: adb forward tcp:$KONSOLE_TCP_PORT tcp:$KONSOLE_TCP_PORT && nc 127.0.0.1 $KONSOLE_TCP_PORT",
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
                            writer.println("=== BOXBOY ZOMBIES HEADLESS KONSOLE (TCP :$KONSOLE_TCP_PORT) ===")
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
                Log.w(TAG, "Konsole TCP server stopped: ${e.message}")
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
    }
}
