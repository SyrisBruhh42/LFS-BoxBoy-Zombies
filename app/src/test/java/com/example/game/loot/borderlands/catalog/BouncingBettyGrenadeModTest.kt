package com.example.game.loot.borderlands.catalog

import org.junit.Assert.*
import org.junit.Test

class BouncingBettyGrenadeModTest {

    @Test
    fun testConfigSerializationRoundtrip() {
        val config = BouncingBettyConfig(
            fuseTimeSeconds = 2.5f,
            leapApexZ = 120f,
            pelletsPerInterval = 8
        )
        val map = config.toMap()
        val restored = BouncingBettyConfig.fromMap(map)

        assertEquals(config.fuseTimeSeconds, restored.fuseTimeSeconds, 0.0001f)
        assertEquals(config.leapApexZ, restored.leapApexZ, 0.0001f)
        assertEquals(config.pelletsPerInterval, restored.pelletsPerInterval)
    }

    @Test
    fun testStateSerializationRoundtrip() {
        val state = BouncingBettyState(
            id = 42,
            phase = BettyPhase.LEAPING,
            x = 10f,
            y = 20f,
            z = 30f,
            vx = -5f,
            timerSeconds = 1.2f,
            sprayAngle = 0.5f
        )
        val map = state.toMap()
        val restored = BouncingBettyState.fromMap(map)

        assertEquals(42, restored.id)
        assertEquals(BettyPhase.LEAPING, restored.phase)
        assertEquals(10f, restored.x, 0.0001f)
        assertEquals(30f, restored.z, 0.0001f)
        assertEquals(1.2f, restored.timerSeconds, 0.0001f)
    }

    @Test
    fun testPelletSerializationRoundtrip() {
        val pellet = BettyPellet(1f, 2f, 3f, 4f, 5f, 60f)
        val map = pellet.toMap()
        val restored = BettyPellet.fromMap(map)

        assertEquals(1f, restored.x, 0.0001f)
        assertEquals(5f, restored.vy, 0.0001f)
        assertEquals(60f, restored.damage, 0.0001f)
    }

    @Test
    fun testThrownPhaseGravityAndBounce() {
        val config = BouncingBettyConfig(gravity = -1000f, bounciness = 0.5f)
        val state = BouncingBettyState(id = 1, phase = BettyPhase.THROWN, z = 100f, vz = 0f)

        // Drop for 0.1s
        BouncingBettyGrenadeEngine.tick(config, state, 0.1f)
        assertTrue("Z should decrease", state.z < 100f)
        assertEquals(-100f, state.vz, 0.001f)

        // Force ground hit at high speed to trigger bounce
        state.z = 1f
        state.vz = -100f
        BouncingBettyGrenadeEngine.tick(config, state, 0.1f)

        assertEquals(0f, state.z, 0.001f)
        assertTrue("Should bounce up", state.vz > 0f)
        assertEquals(BettyPhase.THROWN, state.phase)
    }

    @Test
    fun testThrownToFuseTransition() {
        val config = BouncingBettyConfig(fuseTimeSeconds = 1.5f)
        // Set low velocity to avoid bouncing and settle to rest
        val state = BouncingBettyState(id = 1, phase = BettyPhase.THROWN, z = 1f, vz = -10f)

        BouncingBettyGrenadeEngine.tick(config, state, 0.1f)

        assertEquals(BettyPhase.FUSE, state.phase)
        assertEquals(0f, state.z, 0.001f)
        assertEquals(0f, state.vz, 0.001f)
        assertEquals(1.5f, state.timerSeconds, 0.001f)
    }

    @Test
    fun testFuseToLeapingTransition() {
        val config = BouncingBettyConfig(leapVelocityZ = 300f)
        val state = BouncingBettyState(id = 1, phase = BettyPhase.FUSE, timerSeconds = 0.05f)

        // Tick past fuse time
        BouncingBettyGrenadeEngine.tick(config, state, 0.1f)

        assertEquals(BettyPhase.LEAPING, state.phase)
        assertEquals(300f, state.vz, 0.001f)
    }

    @Test
    fun testLeapingToSprayingTransition() {
        val config = BouncingBettyConfig(leapApexZ = 50f)
        val state = BouncingBettyState(id = 1, phase = BettyPhase.LEAPING, z = 45f, vz = 100f)

        // Tick enough to push past apex
        BouncingBettyGrenadeEngine.tick(config, state, 0.1f)

        assertEquals(BettyPhase.SPRAYING, state.phase)
        assertEquals(50f, state.z, 0.001f) // clamped to apex
        assertEquals(0f, state.vz, 0.001f)
    }

    @Test
    fun testSprayingPelletGeneration() {
        val config = BouncingBettyConfig(sprayIntervalSeconds = 0.1f, pelletsPerInterval = 4, baseDamage = 100f)
        val state = BouncingBettyState(id = 1, phase = BettyPhase.SPRAYING, timerSeconds = 0f, z = 50f)

        // Tick 0.15s, should trigger exactly one burst
        val result = BouncingBettyGrenadeEngine.tick(config, state, 0.15f)

        assertEquals(BettyPhase.SPRAYING, state.phase)
        assertEquals(4, result.spawnedPellets.size)
        assertEquals(50f, result.spawnedPellets[0].z, 0.001f)
        assertEquals(100f, result.spawnedPellets[0].damage, 0.001f)
        assertTrue("Spray angle should rotate", state.sprayAngle > 0f)
    }

    @Test
    fun testSprayingToDetonatedTransition() {
        val config = BouncingBettyConfig(sprayDurationSeconds = 1.0f)
        val state = BouncingBettyState(id = 1, phase = BettyPhase.SPRAYING, timerSeconds = 0.95f)

        BouncingBettyGrenadeEngine.tick(config, state, 0.1f)

        assertEquals(BettyPhase.DETONATED, state.phase)
    }

    @Test
    fun testDeterminismAndChecksum() {
        val config = BouncingBettyConfig()
        val state1 = BouncingBettyState(id = 1, phase = BettyPhase.THROWN, z = 50f, vz = 10f)
        val state2 = BouncingBettyState(id = 1, phase = BettyPhase.THROWN, z = 50f, vz = 10f)

        val result1 = BouncingBettyGrenadeEngine.tick(config, state1, 0.05f)
        val result2 = BouncingBettyGrenadeEngine.tick(config, state2, 0.05f)

        assertEquals(result1.checksum, result2.checksum)
        assertEquals(state1.z, state2.z, 0.0f)
    }

    @Test
    fun testNanInfinityProtectionAndDtClamping() {
        val config = BouncingBettyConfig()
        val state = BouncingBettyState(id = 1, phase = BettyPhase.THROWN, z = Float.NaN, vx = Float.POSITIVE_INFINITY)

        // Pass massive dt, should be clamped to 0.100f
        BouncingBettyGrenadeEngine.tick(config, state, 500f)

        // NaN z should have been clamped to 0 before applying velocity
        // Infinity vx clamped to 0
        assertTrue("State should be cleaned of NaN/Infinity", state.z.isFinite())
        assertTrue("State should be cleaned of NaN/Infinity", state.vx.isFinite())
        // Since it dropped to 0 and started at 0 cleanly, it might be at ground
        assertEquals(0f, state.vx, 0.0f)
    }

    @Test
    fun testExtremeLoadSpraying() {
        // High pellet count and fast intervals for max capacity stress
        val config = BouncingBettyConfig(sprayIntervalSeconds = 0.01f, pelletsPerInterval = 32)
        val state = BouncingBettyState(id = 1, phase = BettyPhase.SPRAYING, timerSeconds = 0f)

        // Tick exactly 0.1 seconds, which is 10 intervals, 10 * 32 = 320 pellets
        val result = BouncingBettyGrenadeEngine.tick(config, state, 0.10f)

        assertEquals(320, result.spawnedPellets.size)
    }
}
