package com.example.game.ai.director.modules

import com.example.game.world.elona.RoomSocketSpec
import com.example.game.world.elona.SectionComponentDef
import com.example.game.world.elona.SocketDirection
import com.example.game.world.elona.SocketKind
import kotlin.math.abs
import kotlin.random.Random

/**
 * Secret Vault Riddle Generator
 * Generates destructible-wall secret vault triggers and high-rarity chest rewards.
 */
object SecretVaultRiddleGenerator {
    
    fun generateSecretVault(seed: Long, biomeTag: String, wave: Int): SectionComponentDef {
        val rng = Random(seed xor (wave.toLong() * 0x7A3B91C2D4E5F6AUL.toLong()))
        
        // Build a 12x12 vault
        val grid = Array(12) { CharArray(12) { '.' } }
        
        // Outer masonry boundary
        for (i in 0 until 12) {
            grid[0][i] = '#'
            grid[11][i] = '#'
            grid[i][0] = '#'
            grid[i][11] = '#'
        }
        
        // Add a single secret barrel seam (destructible wall) to enter the vault
        val entryWall = rng.nextInt(4)
        val entryPos = rng.nextInt(2, 10)
        when (entryWall) {
            0 -> grid[0][entryPos] = 'B' // North
            1 -> grid[11][entryPos] = 'B' // South
            2 -> grid[entryPos][0] = 'B' // West
            3 -> grid[entryPos][11] = 'B' // East
        }
        
        // Add high-rarity chest rewards (Vending/Perk/PaP stations acting as chests here)
        // Ensure they don't block the walkable path entirely
        grid[5][5] = 'V'
        grid[6][6] = 'U'
        grid[5][6] = 'P'
        
        // Add a few barrels around to solve the puzzle/explode them to open the chest
        grid[3][3] = 'B'
        grid[8][8] = 'B'
        grid[3][8] = 'B'
        grid[8][3] = 'B'

        val rows = grid.map { String(it) }
        
        val unlockCost = (1500 + (wave - 1) * 200).coerceAtMost(5000)

        // The socket is on the side where we placed the barrel seam
        val sockets = mutableListOf<RoomSocketSpec>()
        
        // In Elona engine: 
        // SECRET_BARREL_SEAM allows connecting via explosives
        when (entryWall) {
            0 -> sockets.add(RoomSocketSpec(SocketDirection.NORTH, SocketKind.SECRET_BARREL_SEAM, offsetTiles = entryPos - 6, unlockCostCurrency = unlockCost))
            1 -> sockets.add(RoomSocketSpec(SocketDirection.SOUTH, SocketKind.SECRET_BARREL_SEAM, offsetTiles = entryPos - 6, unlockCostCurrency = unlockCost))
            2 -> sockets.add(RoomSocketSpec(SocketDirection.WEST, SocketKind.SECRET_BARREL_SEAM, offsetTiles = entryPos - 6, unlockCostCurrency = unlockCost))
            3 -> sockets.add(RoomSocketSpec(SocketDirection.EAST, SocketKind.SECRET_BARREL_SEAM, offsetTiles = entryPos - 6, unlockCostCurrency = unlockCost))
        }

        // Fill out the rest as walls so it can't connect freely
        val directions = listOf(SocketDirection.NORTH, SocketDirection.SOUTH, SocketDirection.WEST, SocketDirection.EAST)
        for (dir in directions) {
            if (sockets.none { it.direction == dir }) {
                sockets.add(RoomSocketSpec(dir, SocketKind.WALL))
            }
        }
        
        return SectionComponentDef(
            componentId = "boxboys.vault.${biomeTag.lowercase()}_w${wave}_${abs(seed) % 9999}",
            biomeTag = biomeTag,
            rows = rows,
            sockets = sockets
        )
    }
}
