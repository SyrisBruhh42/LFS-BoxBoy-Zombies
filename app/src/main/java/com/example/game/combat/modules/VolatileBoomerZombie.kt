package com.example.game.combat.modules

import com.example.game.ZombieEntity
import com.example.game.combat.BarrelNode
import com.example.game.combat.BoxheadComboAndPhysicsRegistry
import com.example.game.combat.ChainDetonationResult
import kotlin.math.hypot

object VolatileBoomerZombie {
    const val BOOMER_EXPLOSION_RADIUS = 185f
    const val BOOMER_EXPLOSION_DAMAGE = 350
    const val CHAIN_REACTION_BARREL_DAMAGE = 240
    
    fun triggerDeathExplosion(
        boomer: ZombieEntity,
        playerX: Float,
        playerY: Float,
        zombies: List<ZombieEntity>,
        barrels: List<BarrelNode>,
        blastSurfBootsStacks: Int = 0
    ): BoomerExplosionResult {
        var killedZombieCount = 0
        var totalDamageDealt = 0
        val detonatedBarrels = mutableListOf<BarrelNode>()
        
        // Find barrels in range of the boomer explosion
        for (b in barrels) {
            if (b.detonated) continue
            if (hypot(b.x - boomer.x, b.y - boomer.y) <= BOOMER_EXPLOSION_RADIUS) {
                detonatedBarrels.add(b)
            }
        }
        
        // Chain reactions for the detonated barrels
        val totalChainDetonatedBarrelIds = mutableSetOf<Int>()
        var maxChainDepth = 0
        var totalImpulseX = 0f
        var totalImpulseY = 0f
        
        val updatedBarrels = barrels.map { it.copy() }
        
        for (barrel in detonatedBarrels) {
            val result = BoxheadComboAndPhysicsRegistry.resolveBarrelChainReaction(
                initialBarrelId = barrel.id,
                barrels = updatedBarrels,
                playerX = playerX,
                playerY = playerY,
                blastSurfBootsStacks = blastSurfBootsStacks
            )
            
            totalChainDetonatedBarrelIds.addAll(result.detonatedBarrelIds)
            if (result.totalChainDepth > maxChainDepth) {
                maxChainDepth = result.totalChainDepth
            }
            
            totalImpulseX += result.blastSurfImpulseVx
            totalImpulseY += result.blastSurfImpulseVy
            
            for (id in result.detonatedBarrelIds) {
                updatedBarrels.find { it.id == id }?.detonated = true
            }
        }
        
        // Compute damage to zombies
        val explosionCenters = mutableListOf<Pair<Float, Float>>()
        explosionCenters.add(Pair(boomer.x, boomer.y))
        
        for (id in totalChainDetonatedBarrelIds) {
            val b = barrels.find { it.id == id }
            if (b != null) {
                explosionCenters.add(Pair(b.x, b.y))
            }
        }
        
        val updatedZombies = zombies.map { it.copy() }
        for (z in updatedZombies) {
            if (z.id == boomer.id || z.hp <= 0) continue
            
            var damageToThisZombie = 0
            
            for ((cx, cy) in explosionCenters) {
                val isBoomerCenter = cx == boomer.x && cy == boomer.y
                val r = if (isBoomerCenter) BOOMER_EXPLOSION_RADIUS else barrels.firstOrNull { it.x == cx && it.y == cy }?.blastRadiusPx ?: 145f
                if (hypot(z.x - cx, z.y - cy) <= r) {
                    val dmg = if (isBoomerCenter) BOOMER_EXPLOSION_DAMAGE else CHAIN_REACTION_BARREL_DAMAGE
                    damageToThisZombie += dmg
                }
            }
            
            if (damageToThisZombie > 0) {
                z.hp -= damageToThisZombie
                totalDamageDealt += damageToThisZombie
                if (z.hp <= 0) {
                    killedZombieCount++
                }
            }
        }
        
        var boomerSelfDamage = 0
        if (hypot(playerX - boomer.x, playerY - boomer.y) <= BOOMER_EXPLOSION_RADIUS) {
             boomerSelfDamage = BOOMER_EXPLOSION_DAMAGE
        }
        
        return BoomerExplosionResult(
            detonatedBarrelIds = totalChainDetonatedBarrelIds.toList(),
            chainDepth = maxChainDepth,
            zombiesKilled = killedZombieCount,
            totalZombieDamage = totalDamageDealt,
            playerSelfDamage = boomerSelfDamage,
            blastSurfImpulseVx = totalImpulseX,
            blastSurfImpulseVy = totalImpulseY,
            survivingZombies = updatedZombies.filter { it.hp > 0 }
        )
    }
}

data class BoomerExplosionResult(
    val detonatedBarrelIds: List<Int>,
    val chainDepth: Int,
    val zombiesKilled: Int,
    val totalZombieDamage: Int,
    val playerSelfDamage: Int,
    val blastSurfImpulseVx: Float,
    val blastSurfImpulseVy: Float,
    val survivingZombies: List<ZombieEntity>
)


fun evaluateVolatileBoomerZombieLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "VolatileBoomerZombie") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
