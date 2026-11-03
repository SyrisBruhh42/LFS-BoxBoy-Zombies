package com.example.game.world.elona.vaults

import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedList
import java.util.Queue

class ElonaSunvaleBazaarChokepointAlleyVaultTest {

    @Test
    fun `test vault properties and socket entries`() {
        val def = ElonaSunvaleBazaarChokepointAlleyVault.componentDef

        assertEquals("vault.bazaar.chokepoint_alley", def.componentId)
        assertEquals("COLLAPSED_MALL_BAZAAR", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)
        assertEquals(12, def.rows.size)

        val northSocket = def.sockets.find { it.direction == SocketDirection.NORTH }
        val eastSocket = def.sockets.find { it.direction == SocketDirection.EAST }
        val southSocket = def.sockets.find { it.direction == SocketDirection.SOUTH }
        val westSocket = def.sockets.find { it.direction == SocketDirection.WEST }

        assertTrue(northSocket != null && northSocket.kind == SocketKind.LOCKED_DEBRIS_DOOR)
        assertTrue(eastSocket != null && eastSocket.kind == SocketKind.ARCHED)
        assertTrue(southSocket != null && southSocket.kind == SocketKind.LOCKED_DEBRIS_DOOR)
        assertTrue(westSocket != null && westSocket.kind == SocketKind.BARRED_ZOMBIE_WINDOW)
    }

    @Test
    fun `test vault contains necessary features`() {
        val def = ElonaSunvaleBazaarChokepointAlleyVault.componentDef
        val allTiles = def.rows.joinToString("")

        assertTrue("Should contain barricadable window (W)", allTiles.contains('W'))
        assertTrue("Should contain explosive barrel (B)", allTiles.contains('B'))
        assertTrue("Should contain vending machine (V)", allTiles.contains('V'))
        assertTrue("Should contain debris door (D)", allTiles.contains('D'))
    }

    @Test
    fun `test BFS solvability of walkable floor tiles`() {
        val def = ElonaSunvaleBazaarChokepointAlleyVault.componentDef

        var startX = -1
        var startY = -1
        var totalFloorTiles = 0

        for (y in 0 until def.gridHeight) {
            for (x in 0 until def.gridWidth) {
                if (def.rows[y][x] == '.') {
                    totalFloorTiles++
                    if (startX == -1) {
                        startX = x
                        startY = y
                    }
                }
            }
        }

        assertTrue("Must have at least one walkable tile", totalFloorTiles > 0)

        val visited = Array(def.gridHeight) { BooleanArray(def.gridWidth) }
        val queue: Queue<Pair<Int, Int>> = LinkedList()

        queue.add(Pair(startX, startY))
        visited[startY][startX] = true
        var visitedCount = 1

        val dx = intArrayOf(0, 0, 1, -1)
        val dy = intArrayOf(1, -1, 0, 0)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.poll()

            for (i in 0 until 4) {
                val nx = cx + dx[i]
                val ny = cy + dy[i]

                if (nx in 0 until def.gridWidth && ny in 0 until def.gridHeight) {
                    if (!visited[ny][nx] && def.rows[ny][nx] == '.') {
                        visited[ny][nx] = true
                        queue.add(Pair(nx, ny))
                        visitedCount++
                    }
                }
            }
        }

        assertEquals("All walkable floor tiles should be connected", totalFloorTiles, visitedCount)
    }
}
