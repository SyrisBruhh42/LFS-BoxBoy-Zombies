package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.hypot

@Keep
data class BlastSurfConfig(
    val baseImpulseBoost: Float = 0.20f,
    val shockwaveDamagePerStack: Int = 80,
    val reloadWeaponOnSurf: Boolean = true,
    val maxShockwaveRadius: Float = 200f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "baseImpulseBoost" to baseImpulseBoost,
        "shockwaveDamagePerStack" to shockwaveDamagePerStack,
        "reloadWeaponOnSurf" to reloadWeaponOnSurf,
        "maxShockwaveRadius" to maxShockwaveRadius
    )

    companion object {
        fun fromMap(map: Map<String, Any>): BlastSurfConfig {
            return BlastSurfConfig(
                baseImpulseBoost = (map["baseImpulseBoost"] as? Number)?.toFloat() ?: 0.20f,
                shockwaveDamagePerStack = (map["shockwaveDamagePerStack"] as? Number)?.toInt() ?: 80,
                reloadWeaponOnSurf = map["reloadWeaponOnSurf"] as? Boolean ?: true,
                maxShockwaveRadius = (map["maxShockwaveRadius"] as? Number)?.toFloat() ?: 200f
            )
        }
    }
}

@Keep
data class BlastSurfStateSnapshot(
    val stackCount: Int,
    val totalSurfsPerformed: Int,
    val checksum: Long
) {
    fun toMap(): Map<String, Any> = mapOf(
        "stackCount" to stackCount,
        "totalSurfsPerformed" to totalSurfsPerformed,
        "checksum" to checksum
    )

    companion object {
        fun fromMap(map: Map<String, Any>): BlastSurfStateSnapshot {
            return BlastSurfStateSnapshot(
                stackCount = (map["stackCount"] as? Number)?.toInt() ?: 1,
                totalSurfsPerformed = (map["totalSurfsPerformed"] as? Number)?.toInt() ?: 0,
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

@Keep
data class BlastSurfResult(
    val playerDamageTaken: Int,
    val impulseMultiplier: Float,
    val shouldReloadWeapon: Boolean,
    val zombiesDamagedCount: Int,
    val totalShockwaveDamageDealt: Int,
    val newState: BlastSurfStateSnapshot
)

object Relic07BlastSurfThrusters {
    const val ID = "blast_surf_boots"
    const val CAN_BE_PURCHASED_AT_VENDING = false

    fun computeChecksum(stackCount: Int, totalSurfsPerformed: Int): Long {
        var hash = -2128831035146727653L // FNV-1a offset basis
        hash = (hash xor stackCount.toLong()) * 1099511628211L
        hash = (hash xor totalSurfsPerformed.toLong()) * 1099511628211L
        return hash
    }

    /**
     * Executes the RoR2 Passive Relic logic for Blast-Surf Thrusters.
     * Evaluates barrel blast proximity to player, applies 0 self-damage, triggers reload,
     * boosts knockback impulse, and applies shockwave damage to nearby zombies deterministically
     * with zero allocation in the hot loop.
     */
    fun processBarrelSurf(
        config: BlastSurfConfig,
        state: BlastSurfStateSnapshot,
        barrelX: Float,
        barrelY: Float,
        playerX: Float,
        playerY: Float,
        dtSeconds: Float,
        zombieX: FloatArray,
        zombieY: FloatArray,
        zombieHp: IntArray,
        zombieCount: Int
    ): BlastSurfResult {
        // Enforce invariants and clamping
        val stacks = maxOf(1, state.stackCount)
        val dt = if (dtSeconds.isNaN() || dtSeconds.isInfinite()) 0.016f else dtSeconds.coerceIn(0.001f, 0.100f)
        
        val pX = if (playerX.isNaN() || playerX.isInfinite()) 0f else playerX
        val pY = if (playerY.isNaN() || playerY.isInfinite()) 0f else playerY
        val bX = if (barrelX.isNaN() || barrelX.isInfinite()) 0f else barrelX
        val bY = if (barrelY.isNaN() || barrelY.isInfinite()) 0f else barrelY
        
        val dx = pX - bX
        val dy = pY - bY
        val distToPlayer = hypot(dx, dy)
        
        // If player is out of blast range, it's not a surf event for this barrel.
        // Assuming max shockwave radius is also the player trigger radius for simplicity.
        if (distToPlayer > config.maxShockwaveRadius) {
            return BlastSurfResult(
                playerDamageTaken = 0,
                impulseMultiplier = 1.0f,
                shouldReloadWeapon = false,
                zombiesDamagedCount = 0,
                totalShockwaveDamageDealt = 0,
                newState = state // Unchanged
            )
        }
        
        // Player successfully surfed!
        val newSurfs = state.totalSurfsPerformed + 1
        val newChecksum = computeChecksum(stacks, newSurfs)
        val impulse = 1.0f + stacks * config.baseImpulseBoost
        val shockDamage = stacks * config.shockwaveDamagePerStack
        
        // Zero-allocation spatial check and damage application for shockwave
        var zombiesHit = 0
        var totalDmg = 0
        val zCount = minOf(zombieCount, zombieX.size, zombieY.size, zombieHp.size)
        
        for (i in 0 until zCount) {
            val zx = if (zombieX[i].isNaN() || zombieX[i].isInfinite()) 0f else zombieX[i]
            val zy = if (zombieY[i].isNaN() || zombieY[i].isInfinite()) 0f else zombieY[i]
            
            val zdx = zx - bX
            val zdy = zy - bY
            val zDist = hypot(zdx, zdy)
            
            if (zDist <= config.maxShockwaveRadius && zombieHp[i] > 0) {
                zombieHp[i] = maxOf(0, zombieHp[i] - shockDamage)
                zombiesHit++
                totalDmg += shockDamage
            }
        }
        
        return BlastSurfResult(
            playerDamageTaken = 0, // Zero barrel self-damage
            impulseMultiplier = impulse,
            shouldReloadWeapon = config.reloadWeaponOnSurf,
            zombiesDamagedCount = zombiesHit,
            totalShockwaveDamageDealt = totalDmg,
            newState = BlastSurfStateSnapshot(stacks, newSurfs, newChecksum)
        )
    }
}


fun evaluateRelic07BlastSurfThrustersLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic07BlastSurfThrusters") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
