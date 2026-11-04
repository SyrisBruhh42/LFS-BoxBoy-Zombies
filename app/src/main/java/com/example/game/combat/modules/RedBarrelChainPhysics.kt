package com.example.game.combat.modules

import com.example.game.combat.BarrelNode
import com.example.game.combat.ChainDetonationResult
import kotlin.math.hypot

object RedBarrelChainPhysics {
    /**
     * Resolves multi-hop Sean Cooper Boxhead barrel chain reactions deterministically
     * and computes zero-self-damage Blast-Surfing knockback impulse for the player.
     */
    fun resolveBarrelChainReaction(
        initialBarrelId: Int,
        barrels: List<BarrelNode>,
        playerX: Float,
        playerY: Float,
        blastSurfBootsStacks: Int = 0
    ): ChainDetonationResult {
        val byId = barrels.associateBy { it.id }
        val start = byId[initialBarrelId] ?: return ChainDetonationResult(emptyList(), 0, 0f, 0f, 0)

        val detonatedOrder = mutableListOf<Int>()
        val visited = HashSet<Int>()
        var frontier = listOf(start)
        visited.add(start.id)
        var depth = 0

        while (frontier.isNotEmpty()) {
            depth++
            val nextWave = mutableListOf<BarrelNode>()
            for (b in frontier) {
                detonatedOrder.add(b.id)
                for (candidate in barrels) {
                    if (candidate.id in visited || candidate.detonated) continue
                    val dist = hypot(candidate.x - b.x, candidate.y - b.y)
                    if (dist <= b.blastRadiusPx) {
                        visited.add(candidate.id)
                        nextWave.add(candidate)
                    }
                }
            }
            frontier = nextWave
        }

        // Compute Blast-Surfing impulse from the closest detonated barrel (0 self-damage!)
        var impulseX = 0f
        var impulseY = 0f
        val boostFactor = 1.0f + (blastSurfBootsStacks * 0.20f)
        for (bid in detonatedOrder) {
            val b = byId[bid] ?: continue
            val dx = playerX - b.x
            val dy = playerY - b.y
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            if (dist <= b.blastRadiusPx * 1.15f) {
                val falloff = (1f - (dist / (b.blastRadiusPx * 1.25f))).coerceIn(0.15f, 1f)
                val mag = 420f * falloff * boostFactor
                impulseX += (dx / dist) * mag
                impulseY += (dy / dist) * mag
            }
        }

        return ChainDetonationResult(
            detonatedBarrelIds = detonatedOrder,
            totalChainDepth = depth,
            blastSurfImpulseVx = impulseX,
            blastSurfImpulseVy = impulseY,
            playerSelfDamage = 0
        )
    }
}
