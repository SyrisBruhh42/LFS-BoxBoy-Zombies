package com.example.game.combat.modules

import com.example.game.combat.BarrelNode
import com.example.game.combat.BoxheadComboAndPhysicsRegistry
import com.example.game.combat.ChainDetonationResult
import kotlin.math.hypot

data class VolatileBoomerZombie(
    val id: Int,
    var x: Float,
    var y: Float,
    var hp: Int,
    val maxHp: Int = 150,
    var isIgnited: Boolean = false,
    var fuseRemainingMs: Long = 2000L,
    val speed: Float = 3.5f,
    val ignitionRadius: Float = 80f,
    val blastRadius: Float = 160f,
    val blastDamage: Int = 300
)

object VolatileBoomerEngine {

    fun updateBoomer(
        boomer: VolatileBoomerZombie,
        targetX: Float,
        targetY: Float,
        deltaMs: Long,
        barrels: List<BarrelNode>
    ): BoomerUpdateResult {
        if (boomer.hp <= 0) {
            // Explode on death
            return explode(boomer, targetX, targetY, barrels)
        }

        if (boomer.isIgnited) {
            boomer.fuseRemainingMs -= deltaMs
            if (boomer.fuseRemainingMs <= 0) {
                // Explode on fuse expiry
                return explode(boomer, targetX, targetY, barrels)
            }
            // Optional: Move slower when ignited or stop moving? Let's stop moving when ignited for tension
            return BoomerUpdateResult(boomer, null)
        }

        val dx = targetX - boomer.x
        val dy = targetY - boomer.y
        val dist = hypot(dx, dy)

        if (dist <= boomer.ignitionRadius) {
            boomer.isIgnited = true
            return BoomerUpdateResult(boomer, null)
        }

        if (dist > 0) {
            boomer.x += (dx / dist) * boomer.speed
            boomer.y += (dy / dist) * boomer.speed
        }

        return BoomerUpdateResult(boomer, null)
    }

    private fun explode(
        boomer: VolatileBoomerZombie,
        playerX: Float,
        playerY: Float,
        barrels: List<BarrelNode>
    ): BoomerUpdateResult {
        // Trigger chain reaction starting from a virtual barrel at boomer's position
        val virtualBarrel = BarrelNode(
            id = -boomer.id, // Use negative id to avoid collision
            x = boomer.x,
            y = boomer.y,
            blastRadiusPx = boomer.blastRadius,
            baseDamage = boomer.blastDamage
        )

        val extendedBarrels = barrels + virtualBarrel

        val chainResult = BoxheadComboAndPhysicsRegistry.resolveBarrelChainReaction(
            initialBarrelId = virtualBarrel.id,
            barrels = extendedBarrels,
            playerX = playerX,
            playerY = playerY,
            blastSurfBootsStacks = 0
        )

        return BoomerUpdateResult(
            updatedBoomer = boomer.copy(hp = 0), // Dead
            explosionResult = chainResult
        )
    }
}

data class BoomerUpdateResult(
    val updatedBoomer: VolatileBoomerZombie,
    val explosionResult: ChainDetonationResult?
)
