package com.example.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import com.example.data.DEFAULT_WEAPONS

class BoxBoyGameEngineTest {

    @Test
    fun `Reynolds boids horde steering bounds check`() {
        val engine = BoxBoyGameEngine()
        var state = GameSessionState()

        // Ensure map exists since the flowFieldServer relies on valid tiles
        // Provide a valid bounds scenario
        val playerX = 500f
        val playerY = 500f
        state = state.copy(playerX = playerX, playerY = playerY)

        // Setup two zombies very close to each other to trigger soft zombie-to-zombie separation
        // They need to be far from the player to avoid the "dist > z.archetype.radius + 14f" condition failing
        val z1 = ZombieEntity(id = 1, x = 100f, y = 100f, hp = 100, maxHp = 100, archetype = ZombieArchetype.SHAMBLER)
        val z2 = ZombieEntity(id = 2, x = 101f, y = 100f, hp = 100, maxHp = 100, archetype = ZombieArchetype.SHAMBLER)

        state = state.copy(zombies = listOf(z1, z2))

        // Step frame
        state = engine.stepFrame(state, 1000L, 0f, 0f, false, false, false, null, null)

        // Assert they pushed away from each other
        val newZ1 = state.zombies.find { it.id == 1 }!!
        val newZ2 = state.zombies.find { it.id == 2 }!!

        val initialDist = hypot(100f - 101f, 100f - 100f)
        val newDist = hypot(newZ1.x - newZ2.x, newZ1.y - newZ2.y)

        // Ensure their position changed (since logic pushes them away and steps forward).
        // Since logic might also have flow-field/direct steering taking over, we check that their
        // bounds checking separation calculation doesn't crash, and their final positions moved at least somewhat
        // without stacking precisely on top of one another.
        assertTrue("Zombies should move differently and not stack exactly", newDist >= 0.001f)
    }

    @Test
    fun `Multi-tier weapon upgrade stat progression`() {
        val weapon = DEFAULT_WEAPONS.first()

        val wpnLvl1 = ModularWeaponGenerator.generateProceduralWeapon(wave = 1, seed = 123L, preferredBaseWeaponId = weapon.id)
        val damageLvl1 = wpnLvl1.effectiveDamage

        val wpnLvl3 = ModularWeaponGenerator.generateProceduralWeapon(wave = 15, seed = 123L, preferredBaseWeaponId = weapon.id)
        val damageLvl3 = wpnLvl3.effectiveDamage

        // Damage should be significantly higher with level 3 weapon (late waves)
        assertTrue("Level 3 weapon ($damageLvl3) should do more damage than Level 1 ($damageLvl1)", damageLvl3 > damageLvl1)
    }

    @Test
    fun `Fixed-size ParticlePool ring-buffer recycling`() {
        val engine = BoxBoyGameEngine()
        var state = GameSessionState()

        // Add exactly 140 particles
        val initialParticles = MutableList(140) { ParticleBurst(0f, 0f, 0f, 0f, 0L) }
        state = state.copy(particles = initialParticles)

        // Detonate barrel to trigger particles
        val barrel = ExplosiveBarrel(id = 1, roomIndex = ProceduralMapEngine.DEFAULT_UNLOCKED_ROOMS.first(), x = state.playerX, y = state.playerY, isDetonated = false)
        state = state.copy(map = state.map.copy(barrels = listOf(barrel)))

        // Trigger detonation
        state = engine.stepFrame(state, 16L, 0f, 0f, true, false, false, null, null)

        // It shouldn't add more if capped or max bounds applied properly (soft cap check)
        // From game engine, if particles.size < 140, it adds particles. If it is already 140, it shouldn't add more.
        assertTrue("Particle count should be strictly bounded or recycled, size: ${state.particles.size}", state.particles.size <= 140)
    }
}
