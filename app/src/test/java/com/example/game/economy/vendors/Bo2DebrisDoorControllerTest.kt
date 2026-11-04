package com.example.game.economy.vendors

import com.example.game.GameSessionState
import com.example.game.ProceduralMapEngine
import com.example.game.StationKind
import com.example.game.InteractiveStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Bo2DebrisDoorControllerTest {

    @Test
    fun `test purchase debris door successfully`() {
        val initialState = GameSessionState(points = 1500)
        
        // Add a mock door that connects room 0 to room 1
        val mockDoor = InteractiveStation(
            id = "mock_door",
            kind = StationKind.DEBRIS_DOOR,
            label = "OPEN DOOR [750 PTS]",
            x = initialState.playerX, // exactly at player
            y = initialState.playerY,
            cost = 750,
            roomIndex = 0,
            targetRoomIndex = 1
        )
        
        val modifiedMap = initialState.map.copy(stations = initialState.map.stations + mockDoor)
        val testState = initialState.copy(map = modifiedMap, unlockedRooms = setOf(0))
        
        val result = Bo2DebrisDoorController.attemptPurchaseDebrisDoor(testState, "mock_door", interactionRadius = 50f)
        
        assertTrue(result.success)
        assertEquals("Door opened!", result.message)
        assertEquals(750, result.updatedState.points)
        assertTrue(result.updatedState.unlockedRooms.contains(1))
        
        // Ensure door is removed
        assertFalse(result.updatedState.map.stations.any { it.id == "mock_door" })
    }

    @Test
    fun `test purchase debris door insufficient points`() {
        val initialState = GameSessionState(points = 500) // Need 750
        
        val mockDoor = InteractiveStation(
            id = "mock_door",
            kind = StationKind.DEBRIS_DOOR,
            label = "OPEN DOOR [750 PTS]",
            x = initialState.playerX,
            y = initialState.playerY,
            cost = 750,
            roomIndex = 0,
            targetRoomIndex = 1
        )
        
        val modifiedMap = initialState.map.copy(stations = initialState.map.stations + mockDoor)
        val testState = initialState.copy(map = modifiedMap, unlockedRooms = setOf(0))
        
        val result = Bo2DebrisDoorController.attemptPurchaseDebrisDoor(testState, "mock_door", interactionRadius = 50f)
        
        assertFalse(result.success)
        assertTrue(result.message.contains("Not enough points"))
        assertEquals(500, result.updatedState.points)
        assertFalse(result.updatedState.unlockedRooms.contains(1))
    }

    @Test
    fun `test purchase debris door too far away`() {
        val initialState = GameSessionState(points = 1500)
        
        val mockDoor = InteractiveStation(
            id = "mock_door",
            kind = StationKind.DEBRIS_DOOR,
            label = "OPEN DOOR [750 PTS]",
            x = initialState.playerX + 200f, // too far
            y = initialState.playerY + 200f,
            cost = 750,
            roomIndex = 0,
            targetRoomIndex = 1
        )
        
        val modifiedMap = initialState.map.copy(stations = initialState.map.stations + mockDoor)
        val testState = initialState.copy(map = modifiedMap, unlockedRooms = setOf(0))
        
        val result = Bo2DebrisDoorController.attemptPurchaseDebrisDoor(testState, "mock_door", interactionRadius = 50f)
        
        assertFalse(result.success)
        assertEquals("Too far to purchase.", result.message)
    }

    @Test
    fun `test purchase debris door already unlocked`() {
        val initialState = GameSessionState(points = 1500)
        
        val mockDoor = InteractiveStation(
            id = "mock_door",
            kind = StationKind.DEBRIS_DOOR,
            label = "OPEN DOOR [750 PTS]",
            x = initialState.playerX,
            y = initialState.playerY,
            cost = 750,
            roomIndex = 0,
            targetRoomIndex = 1
        )
        
        val modifiedMap = initialState.map.copy(stations = initialState.map.stations + mockDoor)
        // Set room 1 as already unlocked
        val testState = initialState.copy(map = modifiedMap, unlockedRooms = setOf(0, 1))
        
        val result = Bo2DebrisDoorController.attemptPurchaseDebrisDoor(testState, "mock_door", interactionRadius = 50f)
        
        assertFalse(result.success)
        assertEquals("Already unlocked.", result.message)
        assertEquals(1500, result.updatedState.points) // points not deducted
    }
}
