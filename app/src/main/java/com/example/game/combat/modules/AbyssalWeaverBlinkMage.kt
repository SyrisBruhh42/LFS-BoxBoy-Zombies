package com.example.game.combat.modules

import com.example.game.BoxBoyGameEngine
import com.example.game.ZombieEntity
import com.example.game.ZombieArchetype
import com.example.game.EnemyProjectile
import com.example.game.ElementalAffinity
import com.example.game.ParticleBurst
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

object AbyssalWeaverBlinkMage {
    
    /**
     * Executes the Abyssal Weaver's short-range teleport (blink) and slow-orb projectile attack.
     */
    fun processBlinkAndShoot(
        weaver: ZombieEntity,
        playerX: Float,
        playerY: Float,
        deltaMs: Long,
        enemyProjectiles: MutableList<EnemyProjectile>,
        particles: MutableList<ParticleBurst>,
        nextEntityIdProvider: () -> Int
    ) {
        if (weaver.archetype != ZombieArchetype.ABYSSAL_WEAVER) return

        weaver.attackCooldownMs = (weaver.attackCooldownMs - deltaMs).coerceAtLeast(0L)
        val distToPlayer = hypot(playerX - weaver.x, playerY - weaver.y)

        if (weaver.attackCooldownMs == 0L && distToPlayer in 140f..460f) {
            weaver.attackCooldownMs = 2900L
            
            // 1. Blink: Teleport a short distance (40f-70f) along a random angle
            val blinkAngle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val blinkDist = Random.nextFloat() * 30f + 40f
            
            val newX = weaver.x + cos(blinkAngle) * blinkDist
            val newY = weaver.y + sin(blinkAngle) * blinkDist
            
            // Add particles at origin
            repeat(8) {
                val vx = Random.nextFloat() * 80f - 40f
                val vy = Random.nextFloat() * 80f - 40f
                particles.add(
                    ParticleBurst(
                        x = weaver.x,
                        y = weaver.y,
                        vx = vx,
                        vy = vy,
                        colorHex = 0xFFB388FF, // Volatic/Abyssal purple
                        ttlMs = 320L
                    )
                )
            }
            
            weaver.x = newX
            weaver.y = newY
            
            // Add particles at destination
            repeat(8) {
                val vx = Random.nextFloat() * 80f - 40f
                val vy = Random.nextFloat() * 80f - 40f
                particles.add(
                    ParticleBurst(
                        x = weaver.x,
                        y = weaver.y,
                        vx = vx,
                        vy = vy,
                        colorHex = 0xFFB388FF,
                        ttlMs = 320L
                    )
                )
            }

            // 2. Shoot slow-orb projectile
            val ang = atan2(playerY - weaver.y, playerX - weaver.x)
            val elem = if (Random.nextBoolean()) ElementalAffinity.CRYO else ElementalAffinity.CORROSIVE
            val speed = 185f
            enemyProjectiles.add(
                EnemyProjectile(
                    id = nextEntityIdProvider(),
                    x = weaver.x,
                    y = weaver.y,
                    vx = cos(ang) * speed,
                    vy = sin(ang) * speed,
                    damage = 14,
                    radius = 11f,
                    colorHex = elem.colorHex,
                    spawnsPuddleOnImpact = true,
                    elementKind = elem,
                    ttlMs = 2600L
                )
            )
        }
    }
}


fun evaluateAbyssalWeaverBlinkMageLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "AbyssalWeaverBlinkMage") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
