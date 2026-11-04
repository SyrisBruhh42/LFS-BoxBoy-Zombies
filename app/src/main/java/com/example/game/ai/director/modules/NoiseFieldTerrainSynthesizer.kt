package com.example.game.ai.director.modules

import com.example.game.ai.director.AiSectorBlueprintProposal
import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random
import java.util.ArrayDeque

/**
 * Seeded Simplex/Value noise surface patch generator for outdoor sectors.
 */
object NoiseFieldTerrainSynthesizer {

    // Simple 2D Value Noise
    private fun hash(x: Int, y: Int, seed: Long): Double {
        val h = (seed xor (x * 73856093L) xor (y * 19349663L))
        val rng = Random(h)
        return rng.nextDouble()
    }

    private fun noise2D(x: Double, y: Double, seed: Long): Double {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val fx = x - ix
        val fy = y - iy

        // smoothstep
        val u = fx * fx * (3.0 - 2.0 * fx)
        val v = fy * fy * (3.0 - 2.0 * fy)

        val a = hash(ix, iy, seed)
        val b = hash(ix + 1, iy, seed)
        val c = hash(ix, iy + 1, seed)
        val d = hash(ix + 1, iy + 1, seed)

        return a * (1.0 - u) * (1.0 - v) + b * u * (1.0 - v) + c * (1.0 - u) * v + d * u * v
    }

    fun synthesizeOutdoorSector(
        wave: Int,
        biomeTag: String,
        seed: Long
    ): AiSectorBlueprintProposal {
        val rng = Random(seed xor (wave.toLong() * 0x9E3779B97F4A7C15UL.toLong()))

        var validGrid: Array<CharArray>? = null
        val maxAttempts = 10
        var attemptSeed = seed

        for (attempt in 0 until maxAttempts) {
            val grid = Array(12) { CharArray(12) { '.' } }
            val noiseSeed = attemptSeed xor (attempt * 123456789L)

            // Generate terrain features based on noise
            for (y in 1 until 11) {
                for (x in 1 until 11) {
                    val nx = x * 0.5
                    val ny = y * 0.5
                    val n = noise2D(nx, ny, noiseSeed)
                    if (n > 0.65) {
                        grid[y][x] = '#'
                    } else if (n < 0.2 && rng.nextDouble() < 0.2) {
                        grid[y][x] = 'B' // occasional barrels in low areas
                    }
                }
            }

            // Outer masonry boundary with N/E/S/W Debris Doors ('D') and Barricade Windows ('W')
            for (i in 0 until 12) {
                grid[0][i] = '#'
                grid[11][i] = '#'
                grid[i][0] = '#'
                grid[i][11] = '#'
            }

            // Carve 2-tile wide Debris Doors on N/S/E/W centers
            for (d in 5..6) {
                grid[0][d] = 'D'
                grid[11][d] = 'D'
                grid[d][0] = 'D'
                grid[d][11] = 'D'
            }

            // Place Barricadable Zombie Windows ('W') flanking the doors
            for (wPos in listOf(2, 9)) {
                grid[0][wPos] = 'W'
                grid[11][wPos] = 'W'
                grid[wPos][0] = 'W'
                grid[wPos][11] = 'W'
            }

            // Ensure special items 'V', 'P', 'U' are placed on walkable floor '.'
            grid[2][2] = 'V'
            grid[9][9] = if (wave % 3 == 0) 'U' else 'P'

            // clear tiles immediately around special items so they are not boxed in
            val clears = listOf(Pair(2, 3), Pair(3, 2), Pair(3, 3), Pair(8, 9), Pair(9, 8), Pair(8, 8))
            for (c in clears) {
                if (grid[c.second][c.first] == '#') {
                    grid[c.second][c.first] = '.'
                }
            }

            // Verify BFS topological reachability
            if (verifyReachability(grid)) {
                validGrid = grid
                break
            }
            attemptSeed += 9999L
        }

        // Fallback if no valid grid found
        if (validGrid == null) {
            validGrid = fallbackGrid(wave)
        }

        val rows = validGrid.map { String(it) }
        val mutators = listOf("BARREL_OVERLOAD", "CRYO_FOG", "HIGH_VOLTAGE_GRID", "BLOOD_MOON_SWARM")
        val vendors = listOf("MARCUS_LEGENDARY_ARMS", "ZED_CRYO_SHIELDS", "TORGUE_BLAST_ORDNANCE")
        val unlockCost = (750 + (wave - 1) * 150).coerceAtMost(3500)

        val def = SectionComponentDef(
            componentId = "boxboys.noise.${biomeTag.lowercase()}_w${wave}_${abs(seed) % 9999}",
            biomeTag = biomeTag,
            rows = rows,
            sockets = listOf(
                RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = unlockCost),
                RoomSocketSpec(SocketDirection.EAST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = unlockCost),
                RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = unlockCost),
                RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = unlockCost)
            )
        )

        return AiSectorBlueprintProposal(
            componentDef = def,
            sectorCodename = "$biomeTag Noise Sector #$wave",
            hazardMutator = mutators[abs(rng.nextInt()) % mutators.size],
            featuredVendorTag = vendors[abs(rng.nextInt()) % vendors.size],
            generatedByOllamaModel = "noise-synthesis-v1"
        )
    }

    private fun verifyReachability(grid: Array<CharArray>): Boolean {
        // Find all walkable tiles ('.', 'V', 'P', 'U')
        val walkableCount = grid.sumOf { row -> row.count { it in listOf('.', 'V', 'P', 'U') } }
        if (walkableCount == 0) return true

        var start: Pair<Int, Int>? = null
        for (y in 0 until 12) {
            for (x in 0 until 12) {
                if (grid[y][x] in listOf('.', 'V', 'P', 'U')) {
                    start = Pair(x, y)
                    break
                }
            }
            if (start != null) break
        }

        if (start == null) return false

        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(start)
        visited.add(start)

        val dirs = listOf(Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0))

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            for (dir in dirs) {
                val nx = curr.first + dir.first
                val ny = curr.second + dir.second
                if (nx in 0 until 12 && ny in 0 until 12) {
                    if (grid[ny][nx] in listOf('.', 'V', 'P', 'U')) {
                        val n = Pair(nx, ny)
                        if (!visited.contains(n)) {
                            visited.add(n)
                            queue.add(n)
                        }
                    }
                }
            }
        }

        return visited.size == walkableCount
    }

    private fun fallbackGrid(wave: Int): Array<CharArray> {
        val grid = Array(12) { CharArray(12) { '.' } }
        for (i in 0 until 12) {
            grid[0][i] = '#'
            grid[11][i] = '#'
            grid[i][0] = '#'
            grid[i][11] = '#'
        }
        for (d in 5..6) {
            grid[0][d] = 'D'
            grid[11][d] = 'D'
            grid[d][0] = 'D'
            grid[d][11] = 'D'
        }
        for (wPos in listOf(2, 9)) {
            grid[0][wPos] = 'W'
            grid[11][wPos] = 'W'
            grid[wPos][0] = 'W'
            grid[wPos][11] = 'W'
        }
        grid[2][2] = 'V'
        grid[9][9] = if (wave % 3 == 0) 'U' else 'P'
        return grid
    }
}
