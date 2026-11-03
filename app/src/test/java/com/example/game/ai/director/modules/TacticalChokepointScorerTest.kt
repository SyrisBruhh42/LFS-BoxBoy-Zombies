package com.example.game.ai.director.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticalChokepointScorerTest {

    @Test
    fun testValidSolvableRoom() {
        val rows = listOf(
            "############",
            "#..........#",
            "#.B......B.#",
            "#..........#",
            "#...W..W...#",
            "#..........#",
            "#..........#",
            "D..........D",
            "#..........#",
            "#.V......V.#",
            "#..........#",
            "############"
        )

        val score = TacticalChokepointScorer.evaluateRoom(rows)
        assertTrue(score.isSolvable)
    }

    @Test
    fun testUnsolvableRoom() {
        // Disconnected walkable area due to wall block
        val rows = listOf(
            "############",
            "D..#.......#",
            "#..#.....B.#",
            "####.......#",
            "#...W..W...#",
            "#..........#",
            "#..........#",
            "D..........D",
            "#..........#",
            "#.V......V.#",
            "#..........#",
            "############"
        )

        val score = TacticalChokepointScorer.evaluateRoom(rows)
        assertFalse(score.isSolvable)
    }

    @Test
    fun testUnsolvableDueToOtherObstacles() {
        // Disconnected walkable area due to barrels and vending machines
        val rows = listOf(
            "############",
            "D.V........#",
            "#B.........#",
            "W..........#",
            "#...W..W...#",
            "#..........#",
            "#..........#",
            "D..........D",
            "#..........#",
            "#.V......V.#",
            "#..........#",
            "############"
        )

        val score = TacticalChokepointScorer.evaluateRoom(rows)
        assertFalse(score.isSolvable) // D at (0, 1), V at (2, 1), B at (1, 2), W at (0, 3). The single . at (1, 1) is surrounded by obstacles D, V, B, W and walls.
    }

    @Test
    fun testInvalidSize() {
        val rows = listOf(
            "############",
            "#..........#"
        )

        val score = TacticalChokepointScorer.evaluateRoom(rows)
        assertFalse(score.isSolvable)
        assertEquals(0f, score.chokepointSafety, 0.01f)
        assertEquals(0f, score.kitingLoopViability, 0.01f)
    }
}
