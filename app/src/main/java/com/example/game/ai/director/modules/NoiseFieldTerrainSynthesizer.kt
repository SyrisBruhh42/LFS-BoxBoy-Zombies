package com.example.game.ai.director.modules

import kotlin.math.abs
import kotlin.random.Random

/**
 * Unified BoxBoys Track 2 (Local Open-Source AI Director): Seeded Simplex/Value
 * noise surface patch generator for outdoor sectors.
 */
object NoiseFieldTerrainSynthesizer {

    /**
     * Deterministic random float from coordinates and seed.
     */
    private fun hashCoordinates(x: Int, y: Int, seed: Long): Float {
        var h = seed xor (x.toLong() * 0x9E3779B97F4A7C15UL.toLong()) xor (y.toLong() * 0xC2B2AE3D27D4EB4FUL.toLong())
        h = (h xor (h ushr 32)) * -0x340d631b7bdddcdbL
        h = (h xor (h ushr 32)) * 0x100000001b3L
        h = h xor (h ushr 32)
        val rand = Random(h)
        return rand.nextFloat()
    }

    /**
     * Interpolates between a and b by t.
     */
    private fun smoothstep(t: Float): Float {
        return t * t * (3 - 2 * t)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + t * (b - a)
    }

    /**
     * Generates a 2D float grid (`Array<FloatArray>`) using basic value noise.
     */
    fun generateValueNoisePatch(width: Int, height: Int, seed: Long, scale: Float = 0.2f): Array<FloatArray> {
        val grid = Array(height) { FloatArray(width) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sx = x * scale
                val sy = y * scale

                val ix0 = sx.toInt()
                val iy0 = sy.toInt()
                val ix1 = ix0 + 1
                val iy1 = iy0 + 1

                val tx = sx - ix0
                val ty = sy - iy0

                val wx = smoothstep(tx)
                val wy = smoothstep(ty)

                val n00 = hashCoordinates(ix0, iy0, seed)
                val n10 = hashCoordinates(ix1, iy0, seed)
                val n01 = hashCoordinates(ix0, iy1, seed)
                val n11 = hashCoordinates(ix1, iy1, seed)

                val nx0 = lerp(n00, n10, wx)
                val nx1 = lerp(n01, n11, wx)

                val value = lerp(nx0, nx1, wy)
                grid[y][x] = value
            }
        }
        return grid
    }

    /**
     * Generates a 12x12 grid of ASCII characters mapping the noise values to `#` (wall)
     * and `.` (floor) with strict masonry borders. Uses a retry loop to ensure solvability.
     */
    fun synthesizeOrganicTerrainPatch(seed: Long): List<String> {
        var attempt = 0
        var currentSeed = seed

        while (attempt < 100) {
            val grid = generateTerrainGrid(currentSeed)

            // Outer masonry boundary
            for (i in 0 until 12) {
                grid[0][i] = '#'
                grid[11][i] = '#'
                grid[i][0] = '#'
                grid[i][11] = '#'
            }

            val rows = grid.map { String(it) }
            if (verifySolvability(rows)) {
                return rows
            }

            attempt++
            currentSeed++
        }

        // Fallback grid if max attempts reached (unlikely, but safe)
        val safeGrid = Array(12) { CharArray(12) { '.' } }
        for (i in 0 until 12) {
            safeGrid[0][i] = '#'
            safeGrid[11][i] = '#'
            safeGrid[i][0] = '#'
            safeGrid[i][11] = '#'
        }
        return safeGrid.map { String(it) }
    }

    private fun generateTerrainGrid(seed: Long): Array<CharArray> {
        val noisePatch = generateValueNoisePatch(12, 12, seed, scale = 0.5f)
        val grid = Array(12) { CharArray(12) }
        val threshold = 0.6f

        for (y in 0 until 12) {
            for (x in 0 until 12) {
                if (noisePatch[y][x] > threshold) {
                    grid[y][x] = '#'
                } else {
                    grid[y][x] = '.'
                }
            }
        }
        return grid
    }

    /**
     * Breadth-First Search (BFS) to guarantee that all walkable floor tiles `.`
     * form a single connected component, and are not isolated by `#`, `B`, `W`, `D`, or `V`.
     */
    fun verifySolvability(rows: List<String>): Boolean {
        if (rows.size != 12 || rows.any { it.length != 12 }) return false

        val obstacles = setOf('#', 'B', 'W', 'D', 'V')

        var startX = -1
        var startY = -1
        var totalWalkable = 0

        for (y in rows.indices) {
            for (x in rows[y].indices) {
                val ch = rows[y][x]
                if (ch == '.') {
                    totalWalkable++
                    if (startX == -1) {
                        startX = x
                        startY = y
                    }
                }
            }
        }

        if (totalWalkable == 0) return true

        val visited = Array(12) { BooleanArray(12) }
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(startX to startY)
        visited[startY][startX] = true
        var reachableCount = 1

        val dx = intArrayOf(0, 0, -1, 1)
        val dy = intArrayOf(-1, 1, 0, 0)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()

            for (i in 0 until 4) {
                val nx = cx + dx[i]
                val ny = cy + dy[i]

                if (nx in 0 until 12 && ny in 0 until 12) {
                    if (!visited[ny][nx]) {
                        val ch = rows[ny][nx]
                        if (ch !in obstacles) {
                            visited[ny][nx] = true
                            queue.add(nx to ny)
                            if (ch == '.') {
                                reachableCount++
                            }
                        }
                    }
                }
            }
        }

        return reachableCount == totalWalkable
    }
}
