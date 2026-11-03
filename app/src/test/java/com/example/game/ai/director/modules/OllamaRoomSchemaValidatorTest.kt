package com.example.game.ai.director.modules

import org.junit.Test
import org.junit.Assert.*

class OllamaRoomSchemaValidatorTest {

    @Test
    fun `test valid ollama json schema`() {
        val json = """
            {
                "componentId": "test_room",
                "sectorCodename": "Test Sector",
                "hazardMutator": "BARREL_OVERLOAD",
                "featuredVendorTag": "MARCUS_LEGENDARY_ARMS",
                "rows": [
                    "############",
                    "#..........#",
                    "#...B......#",
                    "#......V...#",
                    "#...#......#",
                    "#..........#",
                    "D..........D",
                    "#..........#",
                    "#...P......#",
                    "#......W...#",
                    "#..........#",
                    "############"
                ]
            }
        """.trimIndent()

        assertTrue(OllamaRoomSchemaValidator.validateOllamaResponse(json))
    }

    @Test
    fun `test invalid hazard mutator`() {
        val json = """
            {
                "componentId": "test_room",
                "sectorCodename": "Test Sector",
                "hazardMutator": "INVALID_HAZARD",
                "featuredVendorTag": "MARCUS_LEGENDARY_ARMS",
                "rows": [
                    "############",
                    "#..........#",
                    "#..........#",
                    "#..........#",
                    "#..........#",
                    "#..........#",
                    "D..........D",
                    "#..........#",
                    "#..........#",
                    "#..........#",
                    "#..........#",
                    "############"
                ]
            }
        """.trimIndent()

        assertFalse(OllamaRoomSchemaValidator.validateOllamaResponse(json))
    }

    @Test
    fun `test invalid grid characters`() {
        val json = """
            {
                "componentId": "test_room",
                "sectorCodename": "Test Sector",
                "hazardMutator": "BARREL_OVERLOAD",
                "featuredVendorTag": "MARCUS_LEGENDARY_ARMS",
                "rows": [
                    "############",
                    "#...X......#",
                    "#..........#",
                    "#..........#",
                    "#..........#",
                    "#..........#",
                    "D..........D",
                    "#..........#",
                    "#..........#",
                    "#..........#",
                    "#..........#",
                    "############"
                ]
            }
        """.trimIndent()

        assertFalse(OllamaRoomSchemaValidator.validateOllamaResponse(json))
    }

    @Test
    fun `test disconnected floor tiles`() {
        val json = """
            {
                "componentId": "test_room",
                "sectorCodename": "Test Sector",
                "hazardMutator": "BARREL_OVERLOAD",
                "featuredVendorTag": "MARCUS_LEGENDARY_ARMS",
                "rows": [
                    "############",
                    "#..........#",
                    "#...####...#",
                    "#...#..#...#",
                    "#...#..#...#",
                    "#...####...#",
                    "D..........D",
                    "#..........#",
                    "#..........#",
                    "#..........#",
                    "#..........#",
                    "############"
                ]
            }
        """.trimIndent()

        assertFalse(OllamaRoomSchemaValidator.validateOllamaResponse(json))
    }

    @Test
    fun `test no walkable tiles is invalid`() {
        val json = """
            {
                "componentId": "test_room",
                "sectorCodename": "Test Sector",
                "hazardMutator": "BARREL_OVERLOAD",
                "featuredVendorTag": "MARCUS_LEGENDARY_ARMS",
                "rows": [
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############"
                ]
            }
        """.trimIndent()

        assertFalse(OllamaRoomSchemaValidator.validateOllamaResponse(json))
    }
}
