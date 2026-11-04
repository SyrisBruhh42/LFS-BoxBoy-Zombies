package com.example.game.ai.director.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NoiseFieldTerrainSynthesizerTest {

    @Test
    fun testValueNoiseDeterminism() {
        val patch1 = NoiseFieldTerrainSynthesizer.generateValueNoisePatch(5, 5, 42L)
        val patch2 = NoiseFieldTerrainSynthesizer.generateValueNoisePatch(5, 5, 42L)
        val patch3 = NoiseFieldTerrainSynthesizer.generateValueNoisePatch(5, 5, 43L)
        
        var differences = 0
        for (y in 0 until 5) {
            for (x in 0 until 5) {
                assertEquals("Same seed should yield identical values", patch1[y][x], patch2[y][x], 0.0001f)
                if (abs(patch1[y][x] - patch3[y][x]) > 0.001f) {
                    differences++
                }
            }
        }
        
        assertTrue("Different seeds should yield different grids", differences > 0)
    }

    @Test
    fun testSynthesizeOrganicTerrainPatch_returnsSolvableGrid() {
        val rows = NoiseFieldTerrainSynthesizer.synthesizeOrganicTerrainPatch(12345L)
        
        assertEquals(12, rows.size)
        rows.forEach { assertEquals(12, it.length) }
        
        // Assert borders are #
        assertEquals("############", rows.first())
        assertEquals("############", rows.last())
        rows.forEach {
            assertTrue(it.startsWith('#'))
            assertTrue(it.endsWith('#'))
        }
        
        assertTrue("The generated grid should be solvable", NoiseFieldTerrainSynthesizer.verifySolvability(rows))
    }

    @Test
    fun testVerifySolvability_disconnected() {
        val grid = listOf(
            "############",
            "#..........#",
            "#.########.#",
            "#.#......#.#",
            "#.#.####.#.#",
            "#.#.#..#.#.#",
            "#.#.#..#.#.#",
            "#.#.####.#.#",
            "#.#......#.#",
            "#.########.#",
            "#..........#",
            "############"
        )
        
        assertFalse(
            "Isolated areas should fail solvability verification",
            NoiseFieldTerrainSynthesizer.verifySolvability(grid)
        )
    }

    @Test
    fun testVerifySolvability_isolatedByObstacles() {
        val grid = listOf(
            "############",
            "#..........#",
            "#....B.....#",
            "#....D.....#",
            "#....W.....#",
            "#....V.....#",
            "######.#####", // Gap here but blocked by obstacles above? No, the gap is in row 6
            "##.........#", // The room is divided if we place obstacles across
            "#B.........#",
            "#..........#",
            "#..........#",
            "############"
        )
        // Let's create a properly isolated grid using V, W, D, B
        val isolatedGrid = listOf(
            "############",
            "#.....#....#",
            "#..B..#....#",
            "###V###....#",
            "#..W..#....#",
            "#.....#....#",
            "#######....#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "############"
        )
        
        assertFalse(
            "Obstacles should block solvability",
            NoiseFieldTerrainSynthesizer.verifySolvability(isolatedGrid)
        )
    }

    @Test
    fun testVerifySolvability_solvable() {
        // Explicitly very simple solvable grid.
        val solvableGrid = listOf(
            "############",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "############"
        )
        
        assertTrue(
            "Valid connected path should pass",
            NoiseFieldTerrainSynthesizer.verifySolvability(solvableGrid)
        )
    }
}
