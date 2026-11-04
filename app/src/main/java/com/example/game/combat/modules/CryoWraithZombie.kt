package com.example.game.combat.modules

import com.example.game.ZombieEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class CryoTrailPuddle(
    val x: Float,
    val y: Float,
    val radius: Float = 24f,
    var remainingLifeMs: Long = 4500L
)

data class CryoWraithZombieState(
    val baseEntity: ZombieEntity,
    var isGliding: Boolean = false,
    var glideTimerMs: Long = 0L,
    val frostShieldHp: Int = 150
)

object CryoWraithZombieModule {

    const val GLIDE_SPEED_MULTIPLIER = 1.85f
    const val GLIDE_DURATION_MS = 1200L
    const val GLIDE_COOLDOWN_MS = 2800L
    const val SLIP_FRICTION_REDUCTION = 0.25f
    
    fun stepWraith(
        wraithState: CryoWraithZombieState,
        targetX: Float,
        targetY: Float,
        deltaMs: Long,
        activePuddles: MutableList<CryoTrailPuddle>
    ): CryoWraithZombieState {
        val dx = targetX - wraithState.baseEntity.x
        val dy = targetY - wraithState.baseEntity.y
        val dist = hypot(dx, dy).coerceAtLeast(1f)
        
        var nextGlideTimer = wraithState.glideTimerMs - deltaMs
        var nextIsGliding = wraithState.isGliding

        if (nextGlideTimer <= 0L) {
            if (nextIsGliding) {
                // End glide, start cooldown
                nextIsGliding = false
                nextGlideTimer = GLIDE_COOLDOWN_MS
            } else if (dist in 80f..350f) {
                // Start glide
                nextIsGliding = true
                nextGlideTimer = (GLIDE_DURATION_MS - deltaMs).coerceAtLeast(0L)
            }
        }
        
        val speedMult = if (nextIsGliding) GLIDE_SPEED_MULTIPLIER else 0.7f
        val baseSpeedPxPerSec = 45f
        val moveStep = (baseSpeedPxPerSec * speedMult) * (deltaMs / 1000f)
        
        val angle = atan2(dy, dx)
        val newX = wraithState.baseEntity.x + cos(angle) * moveStep
        val newY = wraithState.baseEntity.y + sin(angle) * moveStep
        
        // Spawn cryo trail if gliding (simplified: just spawn one per step if gliding)
        if (nextIsGliding) {
             activePuddles.add(CryoTrailPuddle(newX, newY))
        }
        
        return wraithState.copy(
            baseEntity = wraithState.baseEntity.copy(x = newX, y = newY),
            isGliding = nextIsGliding,
            glideTimerMs = nextGlideTimer.coerceAtLeast(0L)
        )
    }

    fun stepPuddles(puddles: List<CryoTrailPuddle>, deltaMs: Long): List<CryoTrailPuddle> {
        return puddles.map { it.copy(remainingLifeMs = it.remainingLifeMs - deltaMs) }
            .filter { it.remainingLifeMs > 0 }
    }
    
    fun applySlipperyIcePhysics(
        playerX: Float,
        playerY: Float,
        playerVelX: Float,
        playerVelY: Float,
        puddles: List<CryoTrailPuddle>
    ): Pair<Float, Float> {
        var onIce = false
        for (p in puddles) {
            if (hypot(p.x - playerX, p.y - playerY) <= p.radius) {
                onIce = true
                break
            }
        }
        
        return if (onIce) {
            Pair(playerVelX * (1f - SLIP_FRICTION_REDUCTION), playerVelY * (1f - SLIP_FRICTION_REDUCTION))
        } else {
            Pair(playerVelX, playerVelY)
        }
    }
    
    fun generateBonusCryoAmmoDrop(weaponId: String): Map<String, Int> {
        return mapOf(weaponId to 30)
    }
}


fun evaluateCryoWraithZombieLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "CryoWraithZombie") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
