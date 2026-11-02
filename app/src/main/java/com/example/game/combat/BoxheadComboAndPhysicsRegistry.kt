package com.example.game.combat

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Track 3: Sean Cooper Boxhead 2Play Combat Feel, Chain-Reaction Barrel Physics,
 * Combo Streak Multipliers, and Zero Self-Damage Blast-Surfing Registry.
 */
data class BarrelNode(
    val id: Int,
    val x: Float,
    val y: Float,
    val isCryo: Boolean = false,
    val blastRadiusPx: Float = 145f,
    val baseDamage: Int = 240,
    var detonated: Boolean = false
)

data class ChainDetonationResult(
    val detonatedBarrelIds: List<Int>,
    val totalChainDepth: Int,
    val blastSurfImpulseVx: Float,
    val blastSurfImpulseVy: Float,
    val playerSelfDamage: Int // Always 0 when PhD Flopper / Blast-Surfing is active!
)

data class BoxheadComboState(
    val currentStreak: Int = 0,
    val highestStreak: Int = 0,
    val comboDecayRemainingMs: Long = 0L,
    val unlockedMilestones: Set<Int> = emptySet()
) {
    val currencyMultiplier: Float
        get() = when {
            currentStreak >= 100 -> 3.0f
            currentStreak >= 50 -> 2.25f
            currentStreak >= 25 -> 1.75f
            currentStreak >= 10 -> 1.35f
            currentStreak >= 5 -> 1.15f
            else -> 1.0f
        }
}

object BoxheadComboAndPhysicsRegistry {
    const val COMBO_WINDOW_MS = 3_600L
    val RELIC_DROP_COMBO_MILESTONES = setOf(25, 50, 100)

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

    /**
     * Advances the Boxhead kill streak combo and triggers rare RoR2 relic drops on 25x/50x/100x milestones.
     */
    fun registerKillStreak(
        state: BoxheadComboState,
        killsThisTick: Int
    ): Pair<BoxheadComboState, Int> {
        if (killsThisTick <= 0) return state to 0
        val nextStreak = state.currentStreak + killsThisTick
        val newlyReachedMilestones = RELIC_DROP_COMBO_MILESTONES.filter {
            it <= nextStreak && it !in state.unlockedMilestones
        }
        val updated = state.copy(
            currentStreak = nextStreak,
            highestStreak = max(state.highestStreak, nextStreak),
            comboDecayRemainingMs = COMBO_WINDOW_MS,
            unlockedMilestones = state.unlockedMilestones + newlyReachedMilestones
        )
        return updated to newlyReachedMilestones.size
    }

    fun stepComboTimer(state: BoxheadComboState, deltaMs: Long): BoxheadComboState {
        if (state.currentStreak == 0) return state
        val remaining = (state.comboDecayRemainingMs - deltaMs).coerceAtLeast(0L)
        return if (remaining == 0L) {
            state.copy(currentStreak = 0, comboDecayRemainingMs = 0L)
        } else {
            state.copy(comboDecayRemainingMs = remaining)
        }
    }
}
