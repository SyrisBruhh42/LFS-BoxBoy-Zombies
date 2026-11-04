package com.example.game.ai.director.modules

class WfcBiomeConstraintSolver(
    private val width: Int,
    private val height: Int,
    private val allowedBiomes: List<String>,
    private val adjacencyRules: Map<String, List<String>>
) {
    private val grid: Array<Array<MutableSet<String>>> = Array(height) {
        Array(width) {
            allowedBiomes.toMutableSet()
        }
    }

    private val directions = listOf(
        Pair(-1, 0), // Up
        Pair(1, 0),  // Down
        Pair(0, -1), // Left
        Pair(0, 1)   // Right
    )

    fun solveDeterministic(seed: Long): Array<Array<String>>? {
        val random = kotlin.random.Random(seed)
        while (true) {
            val cell = findMinEntropyCell()
            if (cell == null) {
                if (isFullyCollapsed()) {
                    return getResultGrid()
                } else {
                    return null
                }
            }

            val (r, c) = cell
            val possibleBiomes = grid[r][c].toList()
            if (possibleBiomes.isEmpty()) return null
            
            val chosenBiome = possibleBiomes[random.nextInt(possibleBiomes.size)]
            grid[r][c].clear()
            grid[r][c].add(chosenBiome)

            if (!propagate(r, c)) {
                return null
            }
        }
    }

    private fun findMinEntropyCell(): Pair<Int, Int>? {
        var minEntropy = Int.MAX_VALUE
        var minCell: Pair<Int, Int>? = null

        for (r in 0 until height) {
            for (c in 0 until width) {
                val entropy = grid[r][c].size
                if (entropy > 1 && entropy < minEntropy) {
                    minEntropy = entropy
                    minCell = Pair(r, c)
                }
            }
        }
        return minCell
    }

    private fun isFullyCollapsed(): Boolean {
        for (r in 0 until height) {
            for (c in 0 until width) {
                if (grid[r][c].size != 1) return false
            }
        }
        return true
    }

    private fun getResultGrid(): Array<Array<String>> {
        return Array(height) { r ->
            Array(width) { c ->
                grid[r][c].first()
            }
        }
    }

    private fun propagate(startR: Int, startC: Int): Boolean {
        val stack = mutableListOf(Pair(startR, startC))

        while (stack.isNotEmpty()) {
            val (r, c) = stack.removeLast()
            val possibleBiomes = grid[r][c]

            for ((dr, dc) in directions) {
                val nr = r + dr
                val nc = c + dc

                if (nr in 0 until height && nc in 0 until width) {
                    val neighborPossibleBiomes = grid[nr][nc]
                    
                    val allowedNeighbors = mutableSetOf<String>()
                    for (biome in possibleBiomes) {
                        adjacencyRules[biome]?.let { allowedNeighbors.addAll(it) }
                    }

                    var changed = false
                    val iterator = neighborPossibleBiomes.iterator()
                    while (iterator.hasNext()) {
                        val neighborBiome = iterator.next()
                        if (!allowedNeighbors.contains(neighborBiome)) {
                            iterator.remove()
                            changed = true
                        }
                    }

                    if (neighborPossibleBiomes.isEmpty()) {
                        return false 
                    }

                    if (changed && !stack.contains(Pair(nr, nc))) {
                        stack.add(Pair(nr, nc))
                    }
                }
            }
        }
        return true
    }
}
