package com.example.game.combat.modules

import kotlin.math.hypot

data class RedBarrel(
    val id: Int,
    val x: Float,
    val y: Float,
    val blastRadiusPx: Float = 145f,
    var detonated: Boolean = false
)

data class ChainDetonationResult(
    val detonatedBarrelIds: List<Int>,
    val totalChainDepth: Int,
    val shockwaveStaggerVx: Float,
    val shockwaveStaggerVy: Float
)

object RedBarrelChainPhysics {
    /**
     * Resolves multi-hop Sean Cooper Boxhead barrel chain reactions.
     * Computes shockwave stagger knockback impulse for a target entity (e.g. player or enemy).
     */
    fun resolveChainReaction(
        initialBarrelId: Int,
        barrels: List<RedBarrel>,
        targetX: Float,
        targetY: Float
    ): ChainDetonationResult {
        val byId = barrels.associateBy { it.id }
        val start = byId[initialBarrelId] ?: return ChainDetonationResult(emptyList(), 0, 0f, 0f)

        val detonatedOrder = mutableListOf<Int>()
        val visited = HashSet<Int>()
        var frontier = listOf(start)
        visited.add(start.id)
        var depth = 0

        while (frontier.isNotEmpty()) {
            depth++
            val nextWave = mutableListOf<RedBarrel>()
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

        // Compute shockwave stagger impulse from the closest detonated barrel
        var impulseX = 0f
        var impulseY = 0f
        
        for (bid in detonatedOrder) {
            val b = byId[bid] ?: continue
            val dx = targetX - b.x
            val dy = targetY - b.y
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            if (dist <= b.blastRadiusPx * 1.15f) {
                val falloff = (1f - (dist / (b.blastRadiusPx * 1.25f))).coerceIn(0.15f, 1f)
                val mag = 420f * falloff
                impulseX += (dx / dist) * mag
                impulseY += (dy / dist) * mag
            }
        }

        return ChainDetonationResult(
            detonatedBarrelIds = detonatedOrder,
            totalChainDepth = depth,
            shockwaveStaggerVx = impulseX,
            shockwaveStaggerVy = impulseY
        )
    }
}
