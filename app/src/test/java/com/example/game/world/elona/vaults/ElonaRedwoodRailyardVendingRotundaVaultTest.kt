package com.example.game.world.elona.vaults

import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedList
import java.util.Queue

class ElonaRedwoodRailyardVendingRotundaVaultTest {

    @Test
    fun testComponentDefProperties() {
        val def = ElonaRedwoodRailyardVendingRotundaVault.getComponentDef()

        assertEquals("redwood_railyard_vending_rotunda", def.componentId)
        assertEquals("INDUSTRIAL_RAILYARD", def.biomeTag)
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)
        assertEquals(12, def.rows.size)
        assertTrue(def.rows.all { it.length == 12 })
    }

    @Test
    fun testComponentDefContents() {
        val def = ElonaRedwoodRailyardVendingRotundaVault.getComponentDef()
        val allChars = def.rows.joinToString("")

        assertTrue("Should contain at least one vending machine 'V'", allChars.contains('V'))
        assertTrue("Should contain at least one explosive barrel 'B'", allChars.contains('B'))
        assertTrue("Should contain at least one barricadable window 'W'", allChars.contains('W'))
        assertTrue("Should contain at least one debris door 'D'", allChars.contains('D'))
        
        val vCount = allChars.count { it == 'V' }
        assertEquals("Should contain 3 vending machines", 3, vCount)
    }

    @Test
    fun testComponentDefSockets() {
        val def = ElonaRedwoodRailyardVendingRotundaVault.getComponentDef()
        val sockets = def.sockets

        assertEquals(4, sockets.size)

        val northSocket = sockets.find { it.direction == SocketDirection.NORTH }
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, northSocket?.kind)

        val eastSocket = sockets.find { it.direction == SocketDirection.EAST }
        assertEquals(SocketKind.BARRED_ZOMBIE_WINDOW, eastSocket?.kind)

        val southSocket = sockets.find { it.direction == SocketDirection.SOUTH }
        assertEquals(SocketKind.ARCHED, southSocket?.kind)

        val westSocket = sockets.find { it.direction == SocketDirection.WEST }
        assertEquals(SocketKind.LOCKED_DEBRIS_DOOR, westSocket?.kind)
    }

    @Test
    fun testBfsSolvabilityVerification() {
        val def = ElonaRedwoodRailyardVendingRotundaVault.getComponentDef()
        val rows = def.rows
        
        var startR = -1
        var startC = -1
        var floorCount = 0

        for (r in rows.indices) {
            for (c in rows[r].indices) {
                if (rows[r][c] == '.') {
                    floorCount++
                    if (startR == -1) {
                        startR = r
                        startC = c
                    }
                }
            }
        }

        assertTrue("Must have at least one floor tile", floorCount > 0)

        val visited = Array(def.gridHeight) { BooleanArray(def.gridWidth) }
        val q: Queue<Pair<Int, Int>> = LinkedList()

        q.add(Pair(startR, startC))
        visited[startR][startC] = true
        var visitedFloorCount = 1

        val dr = intArrayOf(-1, 1, 0, 0)
        val dc = intArrayOf(0, 0, -1, 1)

        while (q.isNotEmpty()) {
            val (r, c) = q.poll()

            for (i in 0 until 4) {
                val nr = r + dr[i]
                val nc = c + dc[i]

                if (nr in 0 until def.gridHeight && nc in 0 until def.gridWidth) {
                    if (!visited[nr][nc] && rows[nr][nc] == '.') {
                        visited[nr][nc] = true
                        visitedFloorCount++
                        q.add(Pair(nr, nc))
                    }
                }
            }
        }

        assertEquals(
            "All floor tiles must be connected and not isolated",
            floorCount,
            visitedFloorCount
        )
    }
}
