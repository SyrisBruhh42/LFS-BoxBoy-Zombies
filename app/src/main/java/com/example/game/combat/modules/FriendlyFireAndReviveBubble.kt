package com.example.game.combat.modules

import kotlin.math.hypot

object FriendlyFireAndReviveBubble {
    
    data class Projectile(
        val id: Int,
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
        val radius: Float = 8f,
        val damage: Int = 10,
        val isFriendly: Boolean = true
    )
    
    data class Entity(
        val id: Int,
        val x: Float,
        val y: Float,
        val isFriendly: Boolean
    )

    data class Target(
        val id: Int,
        val x: Float,
        val y: Float,
        val radius: Float,
        val isFriendly: Boolean = false
    )

    fun isFriendlyFire(projectile: Projectile, target: Target): Boolean {
        return projectile.isFriendly == target.isFriendly
    }

    fun filterFriendlyFire(projectiles: List<Projectile>, targets: List<Target>): List<Projectile> {
        return projectiles.filter { p -> 
            targets.none { t -> 
                isFriendlyFire(p, t) && hypot(p.x - t.x, p.y - t.y) <= (p.radius + t.radius)
            }
        }
    }

    fun filterValidHits(projectiles: List<Projectile>, targets: List<Target>): List<Pair<Projectile, Target>> {
        val hits = mutableListOf<Pair<Projectile, Target>>()
        
        for (p in projectiles) {
            for (t in targets) {
                if (!isFriendlyFire(p, t) && hypot(p.x - t.x, p.y - t.y) <= (p.radius + t.radius)) {
                    hits.add(Pair(p, t))
                }
            }
        }
        
        return hits
    }
    
    data class ReviveState(
        val isDowned: Boolean,
        val reviveProgress: Float = 0f,
        val bleedoutRemainingMs: Long = 30_000L
    )

    fun calculateReviveProgress(
        downedState: ReviveState,
        downedX: Float,
        downedY: Float,
        revivers: List<Entity>,
        deltaMs: Long,
        reviveDurationMs: Float = 3000f,
        reviveRadius: Float = 90f
    ): ReviveState {
        if (!downedState.isDowned) return downedState

        val isReviverNear = revivers.any {
            it.isFriendly && hypot(it.x - downedX, it.y - downedY) <= reviveRadius
        }

        val remBleed = (downedState.bleedoutRemainingMs - deltaMs).coerceAtLeast(0L)

        return if (isReviverNear) {
            val nextProg = (downedState.reviveProgress + (deltaMs.toFloat() / reviveDurationMs)).coerceAtMost(1f)
            if (nextProg >= 1f) {
                downedState.copy(isDowned = false, reviveProgress = 0f, bleedoutRemainingMs = 30_000L)
            } else {
                downedState.copy(reviveProgress = nextProg, bleedoutRemainingMs = remBleed)
            }
        } else {
            val decayed = (downedState.reviveProgress - (deltaMs.toFloat() / 6000f)).coerceAtLeast(0f)
            downedState.copy(reviveProgress = decayed, bleedoutRemainingMs = remBleed)
        }
    }
}
