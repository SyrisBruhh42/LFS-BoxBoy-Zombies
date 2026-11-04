package com.example.game.ai.director.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseFieldTerrainSynthesizerTest {

    @Test
    fun testDeterminism() {
        val proposal1 = NoiseFieldTerrainSynthesizer.synthesizeOutdoorSector(1, "WASTELAND", 12345L)
        val proposal2 = NoiseFieldTerrainSynthesizer.synthesizeOutdoorSector(1, "WASTELAND", 12345L)

        assertEquals(proposal1.componentDef.rows, proposal2.componentDef.rows)
        assertEquals(proposal1.hazardMutator, proposal2.hazardMutator)
        assertEquals(proposal1.featuredVendorTag, proposal2.featuredVendorTag)
    }

    @Test
    fun testBoundariesAndSizes() {
        val proposal = NoiseFieldTerrainSynthesizer.synthesizeOutdoorSector(2, "SWAMP", 54321L)
        val rows = proposal.componentDef.rows

        assertEquals(12, rows.size)
        rows.forEach { row ->
            assertEquals(12, row.length)
        }

        // Check corners
        assertEquals('#', rows[0][0])
        assertEquals('#', rows[0][11])
        assertEquals('#', rows[11][0])
        assertEquals('#', rows[11][11])

        // Check doors exist
        assertTrue(rows[0].contains('D'))
        assertTrue(rows[11].contains('D'))
        assertTrue(rows.any { it[0] == 'D' })
        assertTrue(rows.any { it[11] == 'D' })

        // Check windows exist
        assertTrue(rows[0].contains('W'))
        assertTrue(rows[11].contains('W'))
        assertTrue(rows.any { it[0] == 'W' })
        assertTrue(rows.any { it[11] == 'W' })
    }

    @Test
    fun testBFSReachabilityInternal() {
        val proposal = NoiseFieldTerrainSynthesizer.synthesizeOutdoorSector(5, "TUNDRA", 99999L)
        val rows = proposal.componentDef.rows

        // Verify we can reach all walkable tiles
        val walkableTiles = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until 12) {
            for (x in 0 until 12) {
                if (rows[y][x] in listOf('.', 'V', 'P', 'U')) {
                    walkableTiles.add(Pair(x, y))
                }
            }
        }

        assertTrue(walkableTiles.isNotEmpty())

        val start = walkableTiles.first()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = java.util.ArrayDeque<Pair<Int, Int>>()

        queue.add(start)
        visited.add(start)

        val dirs = listOf(Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0))

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            for (dir in dirs) {
                val nx = curr.first + dir.first
                val ny = curr.second + dir.second
                if (nx in 0 until 12 && ny in 0 until 12) {
                    if (rows[ny][nx] in listOf('.', 'V', 'P', 'U')) {
                        val n = Pair(nx, ny)
                        if (!visited.contains(n)) {
                            visited.add(n)
                            queue.add(n)
                        }
                    }
                }
            }
        }

        assertEquals("Map should be fully connected", walkableTiles.size, visited.size)
    }
}
