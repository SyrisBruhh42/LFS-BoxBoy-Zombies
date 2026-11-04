package com.example.game.combat.modules

import com.example.game.ZombieEntity
import com.example.game.ZombieArchetype
import com.example.game.EnemyProjectile
import com.example.game.ElementalAffinity
import com.example.game.ParticleBurst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class AbyssalWeaverBlinkMageTest {

    @Test
    fun `test abyssal weaver blinks and fires orb when in range and cooldown is zero`() {
        // Arrange
        val weaver = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.ABYSSAL_WEAVER,
            x = 200f,
            y = 200f,
            hp = 100,
            maxHp = 100,
            attackCooldownMs = 100L
        )
        
        val playerX = 200f
        val playerY = 400f // Distance is 200f (in range 140..460)
        val enemyProjectiles = mutableListOf<EnemyProjectile>()
        val particles = mutableListOf<ParticleBurst>()
        var nextId = 100
        
        val originalX = weaver.x
        val originalY = weaver.y

        // Act
        AbyssalWeaverBlinkMage.processBlinkAndShoot(
            weaver = weaver,
            playerX = playerX,
            playerY = playerY,
            deltaMs = 150L, // Cooldown becomes 0
            enemyProjectiles = enemyProjectiles,
            particles = particles,
            nextEntityIdProvider = { nextId++ }
        )

        // Assert
        // Cooldown reset
        assertEquals(2900L, weaver.attackCooldownMs)
        
        // Blinked (position changed)
        val blinkDist = hypot(weaver.x - originalX, weaver.y - originalY)
        assertTrue("Blink distance $blinkDist should be between 40 and 70", blinkDist >= 39f && blinkDist <= 71f)
        
        // Particles spawned (origin and destination)
        assertEquals(16, particles.size)
        assertEquals(0xFFB388FF, particles[0].colorHex)
        
        // Orb fired
        assertEquals(1, enemyProjectiles.size)
        val orb = enemyProjectiles[0]
        assertEquals(100, orb.id)
        assertTrue(orb.spawnsPuddleOnImpact)
        assertTrue(orb.elementKind == ElementalAffinity.CRYO || orb.elementKind == ElementalAffinity.CORROSIVE)
        assertEquals(14, orb.damage)
        assertEquals(2600L, orb.ttlMs)
    }

    @Test
    fun `test abyssal weaver does not attack if out of range`() {
        val weaver = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.ABYSSAL_WEAVER,
            x = 200f,
            y = 200f,
            hp = 100,
            maxHp = 100,
            attackCooldownMs = 0L
        )
        
        val enemyProjectiles = mutableListOf<EnemyProjectile>()
        val particles = mutableListOf<ParticleBurst>()
        var nextId = 100

        // Out of range (too close)
        AbyssalWeaverBlinkMage.processBlinkAndShoot(
            weaver = weaver,
            playerX = 200f,
            playerY = 250f, // dist = 50 (< 140)
            deltaMs = 0L,
            enemyProjectiles = enemyProjectiles,
            particles = particles,
            nextEntityIdProvider = { nextId++ }
        )
        
        assertEquals(0L, weaver.attackCooldownMs)
        assertEquals(0, enemyProjectiles.size)
        assertEquals(0, particles.size)
    }
}
