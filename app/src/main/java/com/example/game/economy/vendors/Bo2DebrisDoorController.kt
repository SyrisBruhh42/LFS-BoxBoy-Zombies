package com.example.game.economy.vendors

import com.example.game.GameSessionState
import com.example.game.ProceduralMapEngine
import com.example.game.StationKind
import com.example.game.InteractiveStation
import kotlin.math.hypot

object Bo2DebrisDoorController {
    
    data class DoorPurchaseResult(
        val success: Boolean,
        val updatedState: GameSessionState,
        val message: String
    )

    fun attemptPurchaseDebrisDoor(
        state: GameSessionState,
        doorId: String,
        interactionRadius: Float = 100f
    ): DoorPurchaseResult {
        // Find the door station in the current map
        val doorStation = state.map.stations.find { it.id == doorId && it.kind == StationKind.DEBRIS_DOOR }
        
        if (doorStation == null) {
            return DoorPurchaseResult(false, state, "Door not found.")
        }
        
        // Check distance
        val dist = hypot(state.playerX - doorStation.x, state.playerY - doorStation.y)
        if (dist > interactionRadius) {
            return DoorPurchaseResult(false, state, "Too far to purchase.")
        }
        
        // Target room must not be unlocked yet
        if (state.unlockedRooms.contains(doorStation.targetRoomIndex)) {
            return DoorPurchaseResult(false, state, "Already unlocked.")
        }
        
        // Check cost
        if (state.points < doorStation.cost) {
            return DoorPurchaseResult(false, state, "Not enough points. Need ${doorStation.cost}.")
        }
        
        // Perform purchase: Deduct points, unlock target room
        val newPoints = state.points - doorStation.cost
        val newUnlockedRooms = state.unlockedRooms + doorStation.targetRoomIndex
        
        // The door station itself should be removed from the map or left alone depending on design.
        // We will remove the door from the stations list because it's now unlocked.
        val newStations = state.map.stations.filterNot { it.id == doorId }
        val newMap = state.map.copy(
            rooms = state.map.rooms.map { room ->
                if (room.index in newUnlockedRooms) {
                    room.copy(isUnlocked = true)
                } else {
                    room
                }
            },
            stations = newStations
        )
        
        val newState = state.copy(
            points = newPoints,
            unlockedRooms = newUnlockedRooms,
            map = newMap
        )
        
        return DoorPurchaseResult(true, newState, "Door opened!")
    }
}


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [Bo2DebrisDoorController].
 */
data class Bo2DebrisDoorControllerTelemetryReceipt(
    val moduleName: String = "Bo2DebrisDoorController",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeBo2DebrisDoorControllerTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): Bo2DebrisDoorControllerTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "Bo2DebrisDoorController") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return Bo2DebrisDoorControllerTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateBo2DebrisDoorControllerLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Bo2DebrisDoorController") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
