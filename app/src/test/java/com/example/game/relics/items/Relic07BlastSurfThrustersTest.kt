package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class Relic07BlastSurfThrustersTest {

    @Test
    fun testInitializationAndConstants() {
        assertEquals("blast_surf_boots", Relic07BlastSurfThrusters.ID)
        assertFalse("Blast Surf Thrusters MUST NOT be purchasable at vending machines", Relic07BlastSurfThrusters.CAN_BE_PURCHASED_AT_VENDING)
    }

    @Test
    fun testConfigSerializationRoundtrip() {
        val original = BlastSurfConfig(baseImpulseBoost = 0.25f, shockwaveDamagePerStack = 100, reloadWeaponOnSurf = true, maxShockwaveRadius = 250f)
        val map = original.toMap()
        val restored = BlastSurfConfig.fromMap(map)
        assertEquals(original, restored)
    }

    @Test
    fun testStateSerializationRoundtrip() {
        val original = BlastSurfStateSnapshot(stackCount = 5, totalSurfsPerformed = 12, checksum = 12345L)
        val map = original.toMap()
        val restored = BlastSurfStateSnapshot.fromMap(map)
        assertEquals(original, restored)
    }

    @Test
    fun testChecksumDeterminism() {
        val chk1 = Relic07BlastSurfThrusters.computeChecksum(1, 0)
        val chk2 = Relic07BlastSurfThrusters.computeChecksum(1, 0)
        assertEquals(chk1, chk2)
        val chk3 = Relic07BlastSurfThrusters.computeChecksum(2, 0)
        assertNotEquals(chk1, chk3)
    }

    @Test
    fun testProcessBarrelSurf_PlayerOutOfRange() {
        val config = BlastSurfConfig(maxShockwaveRadius = 100f)
        val state = BlastSurfStateSnapshot(1, 0, Relic07BlastSurfThrusters.computeChecksum(1, 0))
        val zX = FloatArray(1) { 10f }
        val zY = FloatArray(1) { 10f }
        val zHp = IntArray(1) { 100 }

        val result = Relic07BlastSurfThrusters.processBarrelSurf(
            config, state,
            barrelX = 0f, barrelY = 0f,
            playerX = 200f, playerY = 200f, // > 100f distance
            dtSeconds = 0.016f,
            zombieX = zX, zombieY = zY, zombieHp = zHp, zombieCount = 1
        )

        assertEquals(0, result.totalShockwaveDamageDealt)
        assertEquals(1.0f, result.impulseMultiplier, 0.001f)
        assertFalse(result.shouldReloadWeapon)
        assertEquals(state, result.newState) // Unchanged
    }

    @Test
    fun testProcessBarrelSurf_NominalSurf() {
        val config = BlastSurfConfig(maxShockwaveRadius = 100f, shockwaveDamagePerStack = 80, baseImpulseBoost = 0.20f)
        val stacks = 2
        val state = BlastSurfStateSnapshot(stacks, 0, Relic07BlastSurfThrusters.computeChecksum(stacks, 0))

        val zX = FloatArray(2) { i -> if (i == 0) 50f else 150f } // 1 in range, 1 out of range
        val zY = FloatArray(2) { 0f }
        val zHp = IntArray(2) { 200 }

        val result = Relic07BlastSurfThrusters.processBarrelSurf(
            config, state,
            barrelX = 0f, barrelY = 0f,
            playerX = 10f, playerY = 10f, // In range
            dtSeconds = 0.016f,
            zombieX = zX, zombieY = zY, zombieHp = zHp, zombieCount = 2
        )

        assertEquals(0, result.playerDamageTaken)
        assertTrue(result.shouldReloadWeapon)
        assertEquals(1.0f + (2 * 0.20f), result.impulseMultiplier, 0.001f)
        assertEquals(1, result.zombiesDamagedCount)
        assertEquals(160, result.totalShockwaveDamageDealt) // 2 stacks * 80
        assertEquals(40, zHp[0]) // 200 - 160
        assertEquals(200, zHp[1]) // Unchanged
        assertEquals(1, result.newState.totalSurfsPerformed)
    }

    @Test
    fun testProcessBarrelSurf_MaxCapacityStress() {
        val config = BlastSurfConfig(maxShockwaveRadius = 300f, shockwaveDamagePerStack = 50)
        val state = BlastSurfStateSnapshot(10, 5, Relic07BlastSurfThrusters.computeChecksum(10, 5))

        val count = 300
        val zX = FloatArray(count) { 10f } // All same spot, inside radius
        val zY = FloatArray(count) { 10f }
        val zHp = IntArray(count) { 1000 }

        val result = Relic07BlastSurfThrusters.processBarrelSurf(
            config, state,
            barrelX = 0f, barrelY = 0f,
            playerX = 0f, playerY = 0f,
            dtSeconds = 0.016f,
            zombieX = zX, zombieY = zY, zombieHp = zHp, zombieCount = count
        )

        assertEquals(count, result.zombiesDamagedCount)
        assertEquals(500 * count, result.totalShockwaveDamageDealt) // 10 stacks * 50 = 500 dmg per zombie
        assertEquals(6, result.newState.totalSurfsPerformed)
    }

    @Test
    fun testProcessBarrelSurf_NanInfinityGuards() {
        val config = BlastSurfConfig(maxShockwaveRadius = 100f)
        val state = BlastSurfStateSnapshot(1, 0, Relic07BlastSurfThrusters.computeChecksum(1, 0))
        val zX = FloatArray(1) { Float.NaN }
        val zY = FloatArray(1) { Float.POSITIVE_INFINITY }
        val zHp = IntArray(1) { 100 }

        val result = Relic07BlastSurfThrusters.processBarrelSurf(
            config, state,
            barrelX = Float.NaN, barrelY = Float.NaN,
            playerX = Float.POSITIVE_INFINITY, playerY = Float.NEGATIVE_INFINITY,
            dtSeconds = Float.NaN,
            zombieX = zX, zombieY = zY, zombieHp = zHp, zombieCount = 1
        )

        assertEquals(0, result.playerDamageTaken)
        // Handled gracefully, coordinates treated as 0f, so player is at 0,0, barrel at 0,0, zombie at 0,0
        // Distances will be 0f, which is <= 100f
        assertEquals(1, result.zombiesDamagedCount)
    }

    @Test
    fun testProcessBarrelSurf_ClampsNegativeOrZeroStacks() {
        val config = BlastSurfConfig(maxShockwaveRadius = 100f, shockwaveDamagePerStack = 80, baseImpulseBoost = 0.2f)
        val state = BlastSurfStateSnapshot(-5, 0, 0L)
        val zX = FloatArray(1) { 0f }
        val zY = FloatArray(1) { 0f }
        val zHp = IntArray(1) { 100 }

        val result = Relic07BlastSurfThrusters.processBarrelSurf(
            config, state, 0f, 0f, 0f, 0f, 0.016f, zX, zY, zHp, 1
        )

        // Stacks should be clamped to 1
        assertEquals(1.2f, result.impulseMultiplier, 0.001f)
        assertEquals(80, result.totalShockwaveDamageDealt)
        assertEquals(1, result.newState.stackCount)
    }

    @Test
    fun testProcessBarrelSurf_ZombiesAlreadyDead() {
        val config = BlastSurfConfig(maxShockwaveRadius = 100f, shockwaveDamagePerStack = 80)
        val state = BlastSurfStateSnapshot(1, 0, 0L)
        val zX = FloatArray(1) { 0f }
        val zY = FloatArray(1) { 0f }
        val zHp = IntArray(1) { 0 } // Already dead

        val result = Relic07BlastSurfThrusters.processBarrelSurf(
            config, state, 0f, 0f, 0f, 0f, 0.016f, zX, zY, zHp, 1
        )

        assertEquals(0, result.zombiesDamagedCount)
        assertEquals(0, result.totalShockwaveDamageDealt)
        assertEquals(0, zHp[0])
    }

    @Test
    fun testProcessBarrelSurf_MismatchedArrays() {
        val config = BlastSurfConfig(maxShockwaveRadius = 100f, shockwaveDamagePerStack = 80)
        val state = BlastSurfStateSnapshot(1, 0, 0L)
        val zX = FloatArray(10) { 0f }
        val zY = FloatArray(5) { 0f }
        val zHp = IntArray(2) { 100 }

        val result = Relic07BlastSurfThrusters.processBarrelSurf(
            config, state, 0f, 0f, 0f, 0f, 0.016f, zX, zY, zHp, 100 // claims 100
        )

        // Should only process min(100, 10, 5, 2) = 2
        assertEquals(2, result.zombiesDamagedCount)
        assertEquals(160, result.totalShockwaveDamageDealt)
    }

    @Test
    fun testConfigDefaults() {
        val config = BlastSurfConfig()
        assertEquals(0.20f, config.baseImpulseBoost, 0.001f)
        assertEquals(80, config.shockwaveDamagePerStack)
        assertTrue(config.reloadWeaponOnSurf)
        assertEquals(200f, config.maxShockwaveRadius, 0.001f)
    }
}
