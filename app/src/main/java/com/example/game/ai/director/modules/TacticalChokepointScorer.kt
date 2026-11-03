package com.example.game.ai.director.modules

import java.util.ArrayDeque

data class TacticalScore(
    val chokepointSafety: Float,
    val kitingLoopViability: Float,
    val isSolvable: Boolean
)

object TacticalChokepointScorer {

    private val OBSTACLES = setOf('#', 'W', 'D', 'B', 'V', 'P', 'U')

    fun evaluateRoom(rows: List<String>): TacticalScore {
        if (rows.size != 12 || rows.any { it.length != 12 }) {
            return TacticalScore(0f, 0f, false)
        }

        val isSolvable = verifySolvability(rows)
        val chokepointSafety = calculateChokepointSafety(rows)
        val kitingLoopViability = calculateKitingLoopViability(rows)

        return TacticalScore(
            chokepointSafety = chokepointSafety,
            kitingLoopViability = kitingLoopViability,
            isSolvable = isSolvable
        )
    }

    private fun verifySolvability(rows: List<String>): Boolean {
        var startX = -1
        var startY = -1
        var walkableCount = 0

        for (y in 0 until 12) {
            for (x in 0 until 12) {
                if (rows[y][x] == '.') {
                    walkableCount++
                    if (startX == -1) {
                        startX = x
                        startY = y
                    }
                }
            }
        }

        if (walkableCount == 0) return true

        val visited = Array(12) { BooleanArray(12) }
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(startX to startY)
        visited[startY][startX] = true
        var visitedCount = 1

        val dx = intArrayOf(0, 1, 0, -1)
        val dy = intArrayOf(-1, 0, 1, 0)

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()

            for (i in 0 until 4) {
                val nx = x + dx[i]
                val ny = y + dy[i]

                if (nx in 0 until 12 && ny in 0 until 12 && !visited[ny][nx]) {
                    if (rows[ny][nx] == '.') {
                        visited[ny][nx] = true
                        queue.add(nx to ny)
                        visitedCount++
                    }
                }
            }
        }

        return visitedCount == walkableCount
    }

    private fun calculateChokepointSafety(rows: List<String>): Float {
        var chokepointScore = 0f
        val dx = intArrayOf(0, 1, 0, -1)
        val dy = intArrayOf(-1, 0, 1, 0)

        for (y in 1 until 11) {
            for (x in 1 until 11) {
                if (rows[y][x] == '.') {
                    var obstacleCount = 0
                    for (i in 0 until 4) {
                        val nx = x + dx[i]
                        val ny = y + dy[i]
                        if (rows[ny][nx] in OBSTACLES) {
                            obstacleCount++
                        }
                    }
                    if (obstacleCount == 3) {
                        chokepointScore += 2f
                    } else if (obstacleCount == 2) {
                        chokepointScore += 0.5f
                    }
                }
            }
        }
        return chokepointScore.coerceIn(0f, 10f)
    }

    private fun calculateKitingLoopViability(rows: List<String>): Float {
        var openSpaceCount = 0
        for (y in 1 until 11) {
            for (x in 1 until 11) {
                if (rows[y][x] == '.') {
                    var adjacentOpen = 0
                    val dx = intArrayOf(0, 1, 0, -1, 1, 1, -1, -1)
                    val dy = intArrayOf(-1, 0, 1, 0, 1, -1, 1, -1)

                    for (i in 0 until 8) {
                        val nx = x + dx[i]
                        val ny = y + dy[i]
                        if (nx in 0 until 12 && ny in 0 until 12 && rows[ny][nx] == '.') {
                            adjacentOpen++
                        }
                    }
                    if (adjacentOpen >= 5) {
                        openSpaceCount++
                    }
                }
            }
        }
        return (openSpaceCount * 0.5f).coerceIn(0f, 10f)
    }
}
