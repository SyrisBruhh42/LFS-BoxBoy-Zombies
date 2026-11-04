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
