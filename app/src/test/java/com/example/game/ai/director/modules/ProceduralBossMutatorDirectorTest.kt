package com.example.game.ai.director.modules

import org.junit.Assert.*
import org.junit.Test

class ProceduralBossMutatorDirectorTest {

    @Test
    fun testIsBossWave() {
        assertFalse(ProceduralBossMutatorDirector.isBossWave(1))
        assertFalse(ProceduralBossMutatorDirector.isBossWave(4))
        assertTrue(ProceduralBossMutatorDirector.isBossWave(5))
        assertFalse(ProceduralBossMutatorDirector.isBossWave(9))
        assertTrue(ProceduralBossMutatorDirector.isBossWave(10))
        assertTrue(ProceduralBossMutatorDirector.isBossWave(15))
        assertTrue(ProceduralBossMutatorDirector.isBossWave(20))
        assertFalse(ProceduralBossMutatorDirector.isBossWave(21))
    }

    @Test
    fun testGenerateElementalMutator() {
        val mutator1 = ProceduralBossMutatorDirector.generateElementalMutator(5, 999L)
        val mutator2 = ProceduralBossMutatorDirector.generateElementalMutator(5, 999L)
        val mutator3 = ProceduralBossMutatorDirector.generateElementalMutator(10, 12345L)

        // Ensure deterministic output
        assertEquals(mutator1, mutator2)
        // Ensure valid mutator
        assertTrue(listOf("BARREL_OVERLOAD", "CRYO_FOG", "HIGH_VOLTAGE_GRID", "BLOOD_MOON_SWARM").contains(mutator1))
        assertTrue(listOf("BARREL_OVERLOAD", "CRYO_FOG", "HIGH_VOLTAGE_GRID", "BLOOD_MOON_SWARM").contains(mutator3))
    }

    @Test
    fun testGenerateBossArena() {
        val wave = 10
        val seed = 12345L
        val biomeTag = "UNDER_CITY"

        val arena = ProceduralBossMutatorDirector.generateBossArena(wave, seed, biomeTag)

        assertEquals("UNDER_CITY", arena.biomeTag)
        assertTrue(arena.componentId.contains("boxboys.boss_arena.under_city_w10_"))
        assertEquals(12, arena.rows.size)
        arena.rows.forEach { row ->
            assertEquals(12, row.length)
        }
        assertEquals(4, arena.sockets.size)

        // It should be solvable
        assertTrue(ProceduralBossMutatorDirector.verifyBfsSolvability(arena.rows))
    }

    @Test
    fun testVerifyBfsSolvability_Solvable() {
        val solvableGrid = listOf(
            "############",
            "#..........#",
            "#.###.####.#",
            "#.#.#.#..#.#",
            "#.#.#.##.#.#",
            "#.#......#.#",
            "#.########.#",
            "#..........#",
            "############"
        )
        assertTrue(ProceduralBossMutatorDirector.verifyBfsSolvability(solvableGrid))
    }

    @Test
    fun testVerifyBfsSolvability_Unsolvable() {
        val unsolvableGrid = listOf(
            "############",
            "#.....#....#",
            "#.###.#.##.#",
            "#.#.#.#..#.#",
            "#######.##.#",
            "#.#......#.#",
            "#.########.#",
            "#..........#",
            "############"
        )
        assertFalse(ProceduralBossMutatorDirector.verifyBfsSolvability(unsolvableGrid))
    }
}
