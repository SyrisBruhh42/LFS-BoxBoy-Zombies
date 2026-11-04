package com.example.game.world.elona.vaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedList
import java.util.Queue

class ElonaMistharborPierDevilBossArenaVaultTest {

    @Test
    fun testVaultProperties() {
        val def = ElonaMistharborPierDevilBossArenaVault.def
        assertEquals("cryo_harbor_pier_devil_boss_arena", def.componentId)
        assertEquals("CRYO_HARBOR_PIER", def.biomeTag)
        assertEquals(12, def.rows.size)
        assertTrue(def.rows.all { it.length == 12 })
    }

    @Test
    fun testBfsSolvability() {
        val def = ElonaMistharborPierDevilBossArenaVault.def
        val rows = def.rows
        val height = rows.size
        val width = rows[0].length

        var startX = -1
        var startY = -1
        var floorCount = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (rows[y][x] == '.') {
                    floorCount++
                    if (startX == -1) {
                        startX = x
                        startY = y
                    }
                }
            }
        }

        assertTrue("Should have at least one floor tile", floorCount > 0)

        val visited = Array(height) { BooleanArray(width) }
        val queue: Queue<Pair<Int, Int>> = LinkedList()

        queue.add(Pair(startX, startY))
        visited[startY][startX] = true
        var visitedCount = 1

        val dx = intArrayOf(0, 0, 1, -1)
        val dy = intArrayOf(1, -1, 0, 0)

        while (queue.isNotEmpty()) {
            val (x, y) = queue.remove()
            for (i in 0 until 4) {
                val nx = x + dx[i]
                val ny = y + dy[i]
                if (nx in 0 until width && ny in 0 until height) {
                    if (!visited[ny][nx] && rows[ny][nx] == '.') {
                        visited[ny][nx] = true
                        queue.add(Pair(nx, ny))
                        visitedCount++
                    }
                }
            }
        }

        assertEquals("All walkable floor tiles (.) must form a single connected component", floorCount, visitedCount)
    }
}
