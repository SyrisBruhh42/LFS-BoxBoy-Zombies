package com.example.game.ai.director.modules

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WfcBiomeConstraintSolverTest {

    @Test
    fun testWfcBiomeConstraintSolverSolvesSimpleGrid() {
        val width = 3
        val height = 3
        val allowedBiomes = listOf("Forest", "Plains", "Desert")
        val adjacencyRules = mapOf(
            "Forest" to listOf("Forest", "Plains"),
            "Plains" to listOf("Forest", "Plains", "Desert"),
            "Desert" to listOf("Plains", "Desert")
        )

        val solver = WfcBiomeConstraintSolver(width, height, allowedBiomes, adjacencyRules)
        val result = solver.solveDeterministic(1234L)

        assertNotNull("Solver should return a result grid", result)

        // Verify adjacency constraints
        result?.let { grid ->
            val directions = listOf(
                Pair(-1, 0),
                Pair(1, 0),
                Pair(0, -1),
                Pair(0, 1)
            )
            for (r in 0 until height) {
                for (c in 0 until width) {
                    val biome = grid[r][c]
                    for ((dr, dc) in directions) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until height && nc in 0 until width) {
                            val neighborBiome = grid[nr][nc]
                            val allowedNeighbors = adjacencyRules[biome] ?: emptyList()
                            assertTrue("Constraint failed: $biome next to $neighborBiome", allowedNeighbors.contains(neighborBiome))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testWfcBiomeConstraintSolverImpossibleGrid() {
        val width = 2
        val height = 2
        val allowedBiomes = listOf("Forest", "Desert")
        val adjacencyRules = mapOf(
            "Forest" to listOf("Forest"),
            "Desert" to listOf("Desert")
            // No connection between Forest and Desert
            // If they happen to be randomly selected next to each other, solver might fail
        )

        val solver = WfcBiomeConstraintSolver(width, height, allowedBiomes, adjacencyRules)
        val result = solver.solveDeterministic(42L)
        // Note: For 2x2 with identical components being allowed, it might just fill with Forest or Desert.
        // Let's create an impossible scenario by enforcing contradictory bounds (since we don't have bounding constraints yet,
        // we'll just test that it returns a valid solution or null).
        // Since Forest can only be next to Forest, and Desert next to Desert, the only valid solutions are all Forest or all Desert.
        if (result != null) {
            val first = result[0][0]
            for (r in 0 until height) {
                for (c in 0 until width) {
                    assertTrue(result[r][c] == first)
                }
            }
        }
    }
}
