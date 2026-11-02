package com.example

import com.example.data.DEFAULT_WEAPONS
import com.example.game.BoxBoyGameEngine
import com.example.game.DeterministicParticlePool
import com.example.game.HordeSteeringEngine
import com.example.game.WeaponUpgradeProgression
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Automated smoke tests validating BoxBoy Zombies horde AI steering dynamics,
 * weapon Pack-a-Punch upgrade progression scaling, and deterministic particle pool behavior.
 */
class HordeWeaponParticleSmokeTest {

    @Test
    fun testHordeSteeringSeparationAndFlockingDynamics() {
        val zombies = listOf(
            ZombieEntity(id = 1, x = 100f, y = 100f, hp = 100, maxHp = 100, archetype = ZombieArchetype.SHAMBLER),
            ZombieEntity(id = 2, x = 102f, y = 101f, hp = 100, maxHp = 100, archetype = ZombieArchetype.SHAMBLER),
            ZombieEntity(id = 3, x = 150f, y = 150f, hp = 600, maxHp = 600, archetype = ZombieArchetype.DEVIL_BOSS)
        )

        val targetX = 300f
        val targetY = 300f
        val flowVx = 0.707f
        val flowVy = 0.707f

        val steeringResult0 = HordeSteeringEngine.computeHordeSteering(
            zombieIndex = 0,
            zombies = zombies,
            targetX = targetX,
            targetY = targetY,
            flowVx = flowVx,
            flowVy = flowVy
        )

        // Separation vector should be non-zero due to adjacent zombie #1
        val (sepX, sepY) = steeringResult0.separationVector
        val sepMag = hypot(sepX, sepY)
        assertTrue("Separation should push away when neighbors are close", sepMag > 0.01f)

        // Steer vector must be normalized
        val steerMag = hypot(steeringResult0.steerX, steeringResult0.steerY)
        assertEquals("Steer vector should be normalized to unit length", 1.0f, steerMag, 0.05f)

        // Leader vector should be detected because DEVIL_BOSS is in flock radius
        val (ldrX, ldrY) = steeringResult0.leaderVector
        val ldrMag = hypot(ldrX, ldrY)
        assertTrue("Leader vector should identify nearby Devil Boss", ldrMag > 0.01f)
    }

    @Test
    fun testWeaponUpgradeProgressionTiersAndScaling() {
        val pistol = DEFAULT_WEAPONS.first { it.id == "pistol" }
        assertEquals(750, pistol.upgradeBaseCost)

        // Tier 1 (Factory)
        val t1 = WeaponUpgradeProgression.getTierStats(1)
        assertEquals(1.0f, t1.damageMultiplier, 0.001f)
        assertEquals(0, t1.bonusMagCapacity)
        assertEquals(0, t1.bonusPierceCount)
        assertEquals(1.0f, t1.reloadTimeMultiplier, 0.001f)

        // Tier 3 (Hyper Velocity Core)
        val t3 = WeaponUpgradeProgression.getTierStats(3)
        assertEquals(1.84f, t3.damageMultiplier, 0.001f)
        assertEquals(8, t3.bonusMagCapacity)
        assertEquals(1, t3.bonusPierceCount)
        assertTrue("Reload time should decrease with tier", t3.reloadTimeMultiplier < 1.0f)
        assertTrue("Fire cooldown should decrease with tier", t3.fireCooldownMultiplier < 1.0f)

        // Tier 5 (Apex)
        val t5 = WeaponUpgradeProgression.getTierStats(5)
        assertEquals(2.68f, t5.damageMultiplier, 0.001f)
        assertEquals(16, t5.bonusMagCapacity)
        assertEquals(2, t5.bonusPierceCount)
        assertTrue("Milestone perk should describe Apex Core", t5.milestonePerk.contains("Apex Core"))

        // Upgrade costs
        val costT1 = WeaponUpgradeProgression.calculateUpgradeCost(pistol.upgradeBaseCost, 1)
        val costT4 = WeaponUpgradeProgression.calculateUpgradeCost(pistol.upgradeBaseCost, 4)
        assertEquals(750, costT1)
        assertEquals(3000, costT4)

        // Effective damage and pierce calculations
        val effDmgT1 = WeaponUpgradeProgression.calculateEffectiveDamage(pistol.baseDamage, 1)
        val effDmgT5 = WeaponUpgradeProgression.calculateEffectiveDamage(pistol.baseDamage, 5)
        assertTrue("Tier 5 damage must be substantially higher than Tier 1", effDmgT5 > effDmgT1 * 2)

        val effPierceT1 = WeaponUpgradeProgression.calculateEffectivePierce(pistol.pierceCount, 1)
        val effPierceT5 = WeaponUpgradeProgression.calculateEffectivePierce(pistol.pierceCount, 5)
        assertEquals(1, effPierceT1)
        assertEquals(3, effPierceT5)
    }

    @Test
    fun testDeterministicParticlePoolRepeatabilityAndRecycling() {
        val poolA = DeterministicParticlePool(capacity = 64, initialSeed = 424242L)
        val poolB = DeterministicParticlePool(capacity = 64, initialSeed = 424242L)

        // Spawn bursts on both pools with identical parameters
        poolA.spawnBurst(x = 100f, y = 200f, count = 10, speedMin = -150, speedMax = 150, colorHex = 0xFFFF2A54)
        poolB.spawnBurst(x = 100f, y = 200f, count = 10, speedMin = -150, speedMax = 150, colorHex = 0xFFFF2A54)

        assertEquals("Both pools must have 10 active particles", 10, poolA.activeCount())
        assertEquals(poolA.activeCount(), poolB.activeCount())

        val listA = poolA.toActiveList()
        val listB = poolB.toActiveList()

        for (i in listA.indices) {
            assertEquals("Particle vx must match deterministically", listA[i].vx, listB[i].vx, 0.0001f)
            assertEquals("Particle vy must match deterministically", listA[i].vy, listB[i].vy, 0.0001f)
            assertEquals("Particle color must match", listA[i].colorHex, listB[i].colorHex)
        }

        // Advance simulation step
        poolA.step(100L)
        poolB.step(100L)

        val listA2 = poolA.toActiveList()
        val listB2 = poolB.toActiveList()
        for (i in listA2.indices) {
            assertEquals("Particle x after step must match", listA2[i].x, listB2[i].x, 0.0001f)
            assertEquals("Particle y after step must match", listA2[i].y, listB2[i].y, 0.0001f)
        }

        // Advance past TTL (ttlMs = 360L, advance 300L more -> total 400L)
        poolA.step(300L)
        assertEquals("Particles should expire once ttlMs elapses", 0, poolA.activeCount())

        // Recycle all
        poolB.recycleAll()
        assertEquals("RecycleAll should clear active particles", 0, poolB.activeCount())
    }

    @Test
    fun testEngineHasDeterministicParticlePoolInitialized() {
        val engine = BoxBoyGameEngine()
        assertNotNull(engine.deterministicParticlePool)
        assertEquals(256, engine.deterministicParticlePool.capacity)
    }
}
