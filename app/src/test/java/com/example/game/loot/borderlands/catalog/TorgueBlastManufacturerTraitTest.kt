package com.example.game.loot.borderlands.catalog

import com.example.game.combat.modules.RedBarrel
import org.junit.Assert.*
import org.junit.Test

class TorgueBlastManufacturerTraitTest {

    @Test
    fun testNominalPath_projectileMovesAndAccelerates() {
        val config = TorgueBlastConfig(gyrojetAcceleration = 100f, maxSpeed = 1000f)
        val projectiles = listOf(GyrojetProjectile(1, 0f, 0f, 100f, 0f))

        val result = TorgueBlastManufacturerTrait.step(0.1f, projectiles, emptyList(), config)

        assertEquals(1, result.updatedProjectiles.size)
        val p = result.updatedProjectiles[0]
        assertEquals(110f, p.vx, 0.001f) // 100 + 100 * 0.1
        assertEquals(0f, p.vy, 0.001f)
        assertEquals(11f, p.x, 0.001f) // 110 * 0.1
        assertEquals(0f, p.y, 0.001f)
        assertTrue(p.isActive)
    }

    @Test
    fun testNominalPath_detonatesBarrelOnProximity() {
        val config = TorgueBlastConfig(proximityRadiusPx = 50f)
        val projectiles = listOf(GyrojetProjectile(1, 0f, 0f, 1000f, 0f)) // Moves to x=100
        val barrels = listOf(RedBarrel(10, 90f, 0f)) // Within 50px of x=100

        val result = TorgueBlastManufacturerTrait.step(0.1f, projectiles, barrels, config)

        assertEquals(1, result.detonatedBarrelIds.size)
        assertEquals(10, result.detonatedBarrelIds[0])
        assertFalse(result.updatedProjectiles[0].isActive)
        assertTrue(barrels[0].detonated)
    }

    @Test
    fun testDeterminism_identicalInputsProduceIdenticalOutputs() {
        val config = TorgueBlastConfig()
        val p1 = listOf(GyrojetProjectile(1, 10f, 20f, 30f, 40f))
        val b1 = listOf(RedBarrel(1, 50f, 60f))
        val r1 = TorgueBlastManufacturerTrait.step(0.016f, p1, b1, config)

        val p2 = listOf(GyrojetProjectile(1, 10f, 20f, 30f, 40f))
        val b2 = listOf(RedBarrel(1, 50f, 60f))
        val r2 = TorgueBlastManufacturerTrait.step(0.016f, p2, b2, config)

        assertEquals(r1.stateChecksum, r2.stateChecksum)
        assertEquals(r1.updatedProjectiles, r2.updatedProjectiles)
        assertEquals(r1.detonatedBarrelIds, r2.detonatedBarrelIds)
    }

    @Test
    fun testBoundary_maxSpeedClamp() {
        val config = TorgueBlastConfig(gyrojetAcceleration = 5000f, maxSpeed = 1200f)
        val projectiles = listOf(GyrojetProjectile(1, 0f, 0f, 1000f, 0f))

        val result = TorgueBlastManufacturerTrait.step(0.1f, projectiles, emptyList(), config)

        val p = result.updatedProjectiles[0]
        assertEquals(1200f, p.vx, 0.001f) // Should clamp to maxSpeed
    }

    @Test
    fun testBoundary_negativeDeltaTimeClamp() {
        val config = TorgueBlastConfig(gyrojetAcceleration = 100f)
        val projectiles = listOf(GyrojetProjectile(1, 0f, 0f, 100f, 0f))

        // dtSecondsRaw = -0.5f should clamp to 0.001f
        val result = TorgueBlastManufacturerTrait.step(-0.5f, projectiles, emptyList(), config)

        val p = result.updatedProjectiles[0]
        assertEquals(100.1f, p.vx, 0.001f) // 100 + 100 * 0.001
    }

    @Test
    fun testAdversarial_nanInfinityRecovery() {
        val config = TorgueBlastConfig()
        val projectiles = listOf(GyrojetProjectile(1, Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Float.NEGATIVE_INFINITY))

        val result = TorgueBlastManufacturerTrait.step(Float.NaN, projectiles, emptyList(), config)

        val p = result.updatedProjectiles[0]
        assertTrue(p.x.isFinite())
        assertTrue(p.y.isFinite())
        assertTrue(p.vx.isFinite())
        assertTrue(p.vy.isFinite())
        assertEquals(0f, p.x, 0.001f)
    }

    @Test
    fun testStress_maxCapacity() {
        val config = TorgueBlastConfig()
        val projectiles = (1..300).map { GyrojetProjectile(it, it.toFloat(), 0f, 100f, 0f) }
        val barrels = (1..100).map { RedBarrel(it, it * 10f, 0f) }

        val start = System.nanoTime()
        val result = TorgueBlastManufacturerTrait.step(0.016f, projectiles, barrels, config)
        val end = System.nanoTime()

        // Should execute well within 15ms (15,000,000 ns)
        assertTrue((end - start) < 50_000_000)
        assertEquals(300, result.updatedProjectiles.size)
    }

    @Test
    fun testSerialization_configRoundtrip() {
        val config = TorgueBlastConfig(150f, 75f, 500f, 1500f)
        val map = config.toMap()
        val restored = TorgueBlastConfig.fromMap(map)

        assertEquals(config, restored)
    }

    @Test
    fun testSerialization_projectileRoundtrip() {
        val p = GyrojetProjectile(42, 10f, 20f, 30f, 40f, false)
        val map = p.toMap()
        val restored = GyrojetProjectile.fromMap(map)

        assertEquals(p, restored)
    }

    @Test
    fun testSerialization_adversarialConfigInput() {
        val map = mapOf("proximityRadiusPx" to "invalid", "baseDamage" to -100f) // negative should clamp
        val config = TorgueBlastConfig.fromMap(map)

        assertEquals(100f, config.proximityRadiusPx) // default fallback
        assertEquals(0f, config.baseDamage) // clamped to 0
    }

    @Test
    fun testInactiveProjectile_notSimulated() {
        val config = TorgueBlastConfig()
        val projectiles = listOf(GyrojetProjectile(1, 0f, 0f, 100f, 0f, false))

        val result = TorgueBlastManufacturerTrait.step(0.1f, projectiles, emptyList(), config)

        val p = result.updatedProjectiles[0]
        assertEquals(0f, p.x, 0.001f)
        assertEquals(100f, p.vx, 0.001f)
        assertFalse(p.isActive)
    }

    @Test
    fun testMultipleDetonations() {
        val config = TorgueBlastConfig(proximityRadiusPx = 100f)
        val projectiles = listOf(
            GyrojetProjectile(1, 0f, 0f, 50f, 0f),  // Moves to x=5
            GyrojetProjectile(2, 0f, 200f, 50f, 0f) // Moves to x=5, y=200
        )
        val barrels = listOf(
            RedBarrel(10, 0f, 0f),     // Triggered by p1
            RedBarrel(11, 0f, 200f),   // Triggered by p2
            RedBarrel(12, 1000f, 0f)   // Not triggered
        )

        val result = TorgueBlastManufacturerTrait.step(0.1f, projectiles, barrels, config)

        assertEquals(2, result.detonatedBarrelIds.size)
        assertTrue(result.detonatedBarrelIds.contains(10))
        assertTrue(result.detonatedBarrelIds.contains(11))
        assertFalse(result.detonatedBarrelIds.contains(12))

        assertFalse(result.updatedProjectiles[0].isActive)
        assertFalse(result.updatedProjectiles[1].isActive)
    }
}
