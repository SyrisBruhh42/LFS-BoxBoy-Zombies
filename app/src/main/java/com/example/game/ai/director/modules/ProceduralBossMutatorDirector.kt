package com.example.game.ai.director.modules

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import kotlin.math.abs
import kotlin.random.Random

object ProceduralBossMutatorDirector {

    fun isBossWave(wave: Int): Boolean {
        return wave > 0 && wave % 5 == 0
    }

    fun generateBossArena(wave: Int, seed: Long, biomeTag: String): SectionComponentDef {
        val rng = Random(seed xor (wave.toLong() * 0x3141592653589793L))
        val grid = Array(12) { CharArray(12) { '.' } }

        // Borders
        for (i in 0 until 12) {
            grid[0][i] = '#'
            grid[11][i] = '#'
            grid[i][0] = '#'
            grid[i][11] = '#'
        }

        // Doors on N/S/E/W
        grid[0][5] = 'D'
        grid[0][6] = 'D'
        grid[11][5] = 'D'
        grid[11][6] = 'D'
        grid[5][0] = 'D'
        grid[6][0] = 'D'
        grid[5][11] = 'D'
        grid[6][11] = 'D'

        // Windows next to doors
        grid[0][4] = 'W'
        grid[0][7] = 'W'
        grid[11][4] = 'W'
        grid[11][7] = 'W'
        grid[4][0] = 'W'
        grid[7][0] = 'W'
        grid[4][11] = 'W'
        grid[7][11] = 'W'

        // Boss arena features
        grid[2][2] = 'V'
        grid[9][9] = if (wave >= 10) 'U' else 'P'

        val barrelCount = rng.nextInt(3, 7)
        var placedBarrels = 0
        while (placedBarrels < barrelCount) {
            val bx = rng.nextInt(2, 10)
            val by = rng.nextInt(2, 10)
            if (grid[by][bx] == '.') {
                grid[by][bx] = 'B'
                placedBarrels++
            }
        }

        // We could also place some internal pillars, but let's make sure BFS solvability handles it
        if (rng.nextBoolean()) {
            grid[4][4] = '#'
            grid[4][7] = '#'
            grid[7][4] = '#'
            grid[7][7] = '#'
        }

        val rows = grid.map { String(it) }

        val unlockCost = (1000 + wave * 200).coerceAtMost(5000)

        return SectionComponentDef(
            componentId = "boxboys.boss_arena.${biomeTag.lowercase()}_w${wave}_${abs(seed) % 10000}",
            biomeTag = biomeTag,
            rows = rows,
            sockets = listOf(
                RoomSocketSpec(SocketDirection.NORTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = unlockCost),
                RoomSocketSpec(SocketDirection.EAST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = unlockCost),
                RoomSocketSpec(SocketDirection.SOUTH, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = unlockCost),
                RoomSocketSpec(SocketDirection.WEST, SocketKind.LOCKED_DEBRIS_DOOR, unlockCostCurrency = unlockCost)
            )
        )
    }

    fun generateElementalMutator(wave: Int, seed: Long): String {
        val mutators = listOf("BARREL_OVERLOAD", "CRYO_FOG", "HIGH_VOLTAGE_GRID", "BLOOD_MOON_SWARM")
        val rng = Random(seed xor (wave.toLong() * 0x8BADF00D))
        return mutators[rng.nextInt(mutators.size)]
    }

    fun verifyBfsSolvability(grid: List<String>): Boolean {
        if (grid.isEmpty()) return false
        val rows = grid.size
        val cols = grid[0].length

        var firstWalkable: Pair<Int, Int>? = null
        var totalWalkable = 0

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c] == '.') {
                    totalWalkable++
                    if (firstWalkable == null) {
                        firstWalkable = Pair(r, c)
                    }
                }
            }
        }

        if (totalWalkable == 0) return true
        if (firstWalkable == null) return false

        val visited = Array(rows) { BooleanArray(cols) { false } }
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(firstWalkable)
        visited[firstWalkable.first][firstWalkable.second] = true
        var reachableWalkable = 1

        val dirs = arrayOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))

        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()

            for ((dr, dc) in dirs) {
                val nr = r + dr
                val nc = c + dc

                if (nr in 0 until rows && nc in 0 until cols) {
                    if (!visited[nr][nc] && grid[nr][nc] == '.') {
                        visited[nr][nc] = true
                        queue.add(Pair(nr, nc))
                        reachableWalkable++
                    }
                }
            }
        }

        return reachableWalkable == totalWalkable
    }
}
