package com.example.game.world.elona.vaults

import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque

class ElonaRedwoodRailyardSpawnFortressVaultTest {

    @Test
    fun verifyGridDimensions() {
        val def = ElonaRedwoodRailyardSpawnFortressVault.DEFINITION
        assertEquals(12, def.gridWidth)
        assertEquals(12, def.gridHeight)
        assertEquals(12, def.rows.size)
        for (row in def.rows) {
            assertEquals(12, row.length)
        }
    }

    @Test
    fun verifyBiomeTag() {
        val def = ElonaRedwoodRailyardSpawnFortressVault.DEFINITION
        assertEquals("INDUSTRIAL_RAILYARD", def.biomeTag)
    }

    @Test
    fun verifySocketTypes() {
        val def = ElonaRedwoodRailyardSpawnFortressVault.DEFINITION
        assertEquals(4, def.sockets.size)

        val north = def.sockets.find { it.direction == SocketDirection.NORTH }
        assertNotNull("North socket missing", north)

        val east = def.sockets.find { it.direction == SocketDirection.EAST }
        assertNotNull("East socket missing", east)

        val south = def.sockets.find { it.direction == SocketDirection.SOUTH }
        assertNotNull("South socket missing", south)

        val west = def.sockets.find { it.direction == SocketDirection.WEST }
        assertNotNull("West socket missing", west)
    }

    @Test
    fun verifyFeaturePresence() {
        val def = ElonaRedwoodRailyardSpawnFortressVault.DEFINITION
        val allText = def.rows.joinToString("")
        assertTrue("Expected to find walls (#)", allText.contains('#'))
        assertTrue("Expected to find floor tiles (.)", allText.contains('.'))
        assertTrue("Expected to find debris doors (D)", allText.contains('D'))
        assertTrue("Expected to find window barricades (W)", allText.contains('W'))
        assertTrue("Expected to find barrels (B)", allText.contains('B'))
        assertTrue("Expected to find vending machines (V)", allText.contains('V'))
    }

    @Test
    fun verifyBfsSolvability() {
        val def = ElonaRedwoodRailyardSpawnFortressVault.DEFINITION

        // Find all walkable floor tiles ('.')
        val floorTiles = mutableListOf<Pair<Int, Int>>()
        for (y in def.rows.indices) {
            val row = def.rows[y]
            for (x in row.indices) {
                if (row[x] == '.') {
                    floorTiles.add(Pair(x, y))
                }
            }
        }

        assertTrue("Should have floor tiles", floorTiles.isNotEmpty())

        val start = floorTiles.first()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(start)
        visited.add(start)

        val dx = intArrayOf(0, 1, 0, -1)
        val dy = intArrayOf(-1, 0, 1, 0)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            for (i in 0 until 4) {
                val nx = curr.first + dx[i]
                val ny = curr.second + dy[i]
                val neighbor = Pair(nx, ny)

                if (nx >= 0 && nx < def.gridWidth && ny >= 0 && ny < def.gridHeight) {
                    if (def.rows[ny][nx] == '.' && !visited.contains(neighbor)) {
                        visited.add(neighbor)
                        queue.add(neighbor)
                    }
                }
            }
        }

        assertEquals(
            "All floor tiles must be reachable",
            floorTiles.size,
            visited.size
        )
    }
}
