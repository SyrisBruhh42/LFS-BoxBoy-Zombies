package com.example.game.world.elona.vaults

import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedList
import java.util.Queue

class ElonaMistharborPierVendingRotundaVaultTest {

    @Test
    fun testLayoutDimensions() {
        val def = ElonaMistharborPierVendingRotundaVaultDef
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)
        assertEquals(12, def.rows.size)
        def.rows.forEach { row ->
            assertEquals(12, row.length)
        }
    }

    @Test
    fun testSocketConfiguration() {
        val def = ElonaMistharborPierVendingRotundaVaultDef
        assertEquals(4, def.sockets.size)

        val northSocket = def.sockets.find { it.direction == SocketDirection.NORTH }
        val eastSocket = def.sockets.find { it.direction == SocketDirection.EAST }
        val southSocket = def.sockets.find { it.direction == SocketDirection.SOUTH }
        val westSocket = def.sockets.find { it.direction == SocketDirection.WEST }

        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, northSocket?.kind)
        assertEquals(SocketKind.BARRED_ZOMBIE_WINDOW, eastSocket?.kind)
        assertEquals(SocketKind.ARCHED, southSocket?.kind)
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, westSocket?.kind)
    }

    @Test
    fun testBfsSolvability() {
        val def = ElonaMistharborPierVendingRotundaVaultDef
        val walkablePoints = mutableListOf<Pair<Int, Int>>()

        // Find all walkable floors '.'
        for (y in 0 until def.gridHeight) {
            for (x in 0 until def.gridWidth) {
                if (def.rows[y][x] == '.') {
                    walkablePoints.add(Pair(x, y))
                }
            }
        }

        assertTrue("Should have walkable points", walkablePoints.isNotEmpty())

        val start = walkablePoints.first()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue: Queue<Pair<Int, Int>> = LinkedList()

        queue.add(start)
        visited.add(start)

        val directions = listOf(
            Pair(0, -1), // North
            Pair(1, 0),  // East
            Pair(0, 1),  // South
            Pair(-1, 0)  // West
        )

        while (queue.isNotEmpty()) {
            val current = queue.poll()!!

            for (dir in directions) {
                val nx = current.first + dir.first
                val ny = current.second + dir.second

                if (nx in 0 until def.gridWidth && ny in 0 until def.gridHeight) {
                    val nChar = def.rows[ny][nx]
                    if (nChar == '.' && !visited.contains(Pair(nx, ny))) {
                        visited.add(Pair(nx, ny))
                        queue.add(Pair(nx, ny))
                    }
                }
            }
        }

        // Assert all walkable floors '.' form a single connected component
        for (point in walkablePoints) {
            assertTrue("Floor point $point should be reachable", visited.contains(point))
        }
        
        // Also check if every 'V' is adjacent to a '.'
        for (y in 0 until def.gridHeight) {
            for (x in 0 until def.gridWidth) {
                val char = def.rows[y][x]
                if (char == 'V') {
                    val isAdjacentToFloor = directions.any { dir ->
                        val nx = x + dir.first
                        val ny = y + dir.second
                        nx in 0 until def.gridWidth && ny in 0 until def.gridHeight && def.rows[ny][nx] == '.'
                    }
                    assertTrue("Vending machine at ($x, $y) should be adjacent to a walkable floor", isAdjacentToFloor)
                }
            }
        }
    }
}
