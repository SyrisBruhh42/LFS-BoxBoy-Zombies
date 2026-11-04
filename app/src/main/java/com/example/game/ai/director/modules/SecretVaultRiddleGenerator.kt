package com.example.game.ai.director.modules

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.random.Random

object SecretVaultRiddleGenerator {

    /**
     * Generates a Secret Vault SectionComponentDef.
     * These vaults are hidden behind destructible barrel seams and contain high-rarity rewards.
     */
    fun generateSecretVault(
        wave: Int,
        biomeTag: String,
        seed: Long
    ): SectionComponentDef {
        val rng = Random(seed xor (wave.toLong() * 0x8273645L))
        val grid = Array(12) { CharArray(12) { '.' } }

        // Construct outer boundary walls
        for (i in 0 until 12) {
            grid[0][i] = '#'
            grid[11][i] = '#'
            grid[i][0] = '#'
            grid[i][11] = '#'
        }

        // Add the SECRET_BARREL_SEAM on the SOUTH side (so it can be accessed from the NORTH of another room)
        grid[11][5] = 'B'
        grid[11][6] = 'B'
        
        // Add random obstacles
        val numObstacles = 4 + rng.nextInt(6)
        repeat(numObstacles) {
            val ox = rng.nextInt(2, 10)
            val oy = rng.nextInt(2, 10)
            if (grid[oy][ox] == '.') {
                grid[oy][ox] = '#'
            }
        }

        // Place high-rarity rewards (U: Pack-a-Punch, P: Perk-a-Cola, V: Vending Machine)
        val rewards = listOf('U', 'P', 'V')
        for (reward in rewards) {
            var placed = false
            while (!placed) {
                val rx = rng.nextInt(2, 10)
                val ry = rng.nextInt(2, 10)
                if (grid[ry][rx] == '.') {
                    grid[ry][rx] = reward
                    placed = true
                }
            }
        }
        
        // Ensure BFS solvability; if isolated tiles exist, clear them (make them walkable)
        val rows = grid.map { String(it) }
        val finalRows = if (verifyBfsSolvability(rows)) {
            rows
        } else {
            // Very simple fallback: clear the internal obstacles if not solvable
            for (y in 1 until 11) {
                for (x in 1 until 11) {
                    if (grid[y][x] == '#') {
                        grid[y][x] = '.'
                    }
                }
            }
            grid.map { String(it) }
        }

        val componentId = "boxboys.vault.${biomeTag.lowercase()}_w${wave}_${abs(seed) % 9999}"

        return SectionComponentDef(
            componentId = componentId,
            biomeTag = biomeTag,
            rows = finalRows,
            sockets = listOf(
                RoomSocketSpec(SocketDirection.SOUTH, SocketKind.SECRET_BARREL_SEAM, unlockCostCurrency = 0)
            )
        )
    }

    /**
     * Verifies that all walkable floor tiles ('.') form a single connected component
     * and are not isolated by obstacle tiles like walls ('#'), barrels ('B'),
     * windows ('W'), doors ('D'), or vending machines ('V').
     */
    fun verifyBfsSolvability(rows: List<String>): Boolean {
        if (rows.size != 12 || rows.any { it.length != 12 }) return false
        
        var startX = -1
        var startY = -1
        var floorCount = 0

        for (y in 0 until 12) {
            for (x in 0 until 12) {
                if (rows[y][x] == '.') {
                    if (startX == -1) {
                        startX = x
                        startY = y
                    }
                    floorCount++
                }
            }
        }

        // If there are no floor tiles, technically they are all connected (or it's trivially false)
        // A valid room should have some floor.
        if (floorCount == 0) return true
        if (startX == -1) return true

        val visited = Array(12) { BooleanArray(12) }
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startX, startY))
        visited[startY][startX] = true
        var visitedCount = 1

        val dx = intArrayOf(0, 1, 0, -1)
        val dy = intArrayOf(-1, 0, 1, 0)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()

            for (i in 0 until 4) {
                val nx = cx + dx[i]
                val ny = cy + dy[i]

                if (nx in 0 until 12 && ny in 0 until 12) {
                    if (!visited[ny][nx] && rows[ny][nx] == '.') {
                        visited[ny][nx] = true
                        queue.add(Pair(nx, ny))
                        visitedCount++
                    }
                }
            }
        }

        return visitedCount == floorCount
    }
}


fun evaluateSecretVaultRiddleGeneratorLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "SecretVaultRiddleGenerator") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
