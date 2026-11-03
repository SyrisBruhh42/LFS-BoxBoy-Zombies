package com.example.game.world.elona.vaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedList
import java.util.Queue

class ElonaRedwoodRailyardChokepointAlleyVaultTest {

    @Test
    fun testVaultDimensionsAndTiles() {
        val def = ElonaRedwoodRailyardChokepointAlleyVault.def

        assertEquals("Grid width must be 12", 12, def.gridWidth)
        assertEquals("Grid height must be 12", 12, def.gridHeight)
        assertEquals("Rows size must be 12", 12, def.rows.size)

        var wCount = 0
        var bCount = 0
        var vCount = 0
        var floorCount = 0

        for (row in def.rows) {
            assertEquals("Each row must be 12 characters long", 12, row.length)
            wCount += row.count { it == 'W' }
            bCount += row.count { it == 'B' }
            vCount += row.count { it == 'V' }
            floorCount += row.count { it == '.' }
        }

        assertTrue("Must contain window barricades (W)", wCount > 0)
        assertTrue("Must contain explosive barrels (B)", bCount > 0)
        assertTrue("Must contain vending machines (V)", vCount > 0)
        assertTrue("Must contain floor tiles (.)", floorCount > 0)
    }

    @Test
    fun testBfsSolvabilityVerification() {
        val def = ElonaRedwoodRailyardChokepointAlleyVault.def
        val rows = def.rows

        val height = rows.size
        val width = rows[0].length

        var startX = -1
        var startY = -1
        var totalFloors = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (rows[y][x] == '.') {
                    totalFloors++
                    if (startX == -1) {
                        startX = x
                        startY = y
                    }
                }
            }
        }

        assertTrue("Must have at least one floor tile", totalFloors > 0)

        val visited = Array(height) { BooleanArray(width) }
        val queue: Queue<Pair<Int, Int>> = LinkedList()

        queue.add(Pair(startX, startY))
        visited[startY][startX] = true
        var reachedFloors = 1

        val dx = intArrayOf(0, 0, 1, -1)
        val dy = intArrayOf(1, -1, 0, 0)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.poll()

            for (i in 0 until 4) {
                val nx = cx + dx[i]
                val ny = cy + dy[i]

                if (nx in 0 until width && ny in 0 until height) {
                    if (!visited[ny][nx] && rows[ny][nx] == '.') {
                        visited[ny][nx] = true
                        queue.add(Pair(nx, ny))
                        reachedFloors++
                    }
                }
            }
        }

        assertEquals(
            "All floor tiles (.) must be reachable from each other, forming a single connected component",
            totalFloors,
            reachedFloors
        )
    }
}
