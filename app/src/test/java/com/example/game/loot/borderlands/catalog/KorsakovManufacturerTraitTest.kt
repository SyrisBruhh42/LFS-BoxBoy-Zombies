package com.example.game.loot.borderlands.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.hypot

class KorsakovManufacturerTraitTest {

    @Test
    fun test_calculateBaseDamage_nominal() {
        val engine = KorsakovManufacturerEngine(KorsakovTraitConfig(kineticDamageMultiplier = 1.25f))
        val damage = engine.calculateBaseDamage(100f)
        assertEquals(125f, damage, 0.001f)
    }

    @Test
    fun test_calculateBaseDamage_negative_bounds() {
        val engine = KorsakovManufacturerEngine()
        assertEquals(0f, engine.calculateBaseDamage(0f), 0.001f)
        assertEquals(0f, engine.calculateBaseDamage(-50f), 0.001f)
        assertEquals(0f, engine.calculateBaseDamage(Float.NaN), 0.001f)
        assertEquals(0f, engine.calculateBaseDamage(Float.POSITIVE_INFINITY), 0.001f)
    }

    @Test
    fun test_spawnExplosiveSlug_deterministic_seeded() {
        val engine1 = KorsakovManufacturerEngine(currentSeed = 100)
        val engine2 = KorsakovManufacturerEngine(currentSeed = 100)
        
        engine1.spawnExplosiveSlug(10f, 10f, 100f)
        engine2.spawnExplosiveSlug(10f, 10f, 100f)
        
        val snap1 = engine1.getSnapshot()
        val snap2 = engine2.getSnapshot()
        
        assertEquals(snap1.activeExplosions.size, 1)
        assertEquals(snap1.activeExplosions[0].damage, snap2.activeExplosions[0].damage, 0.001f)
        assertEquals(snap1.activeExplosions[0].radiusPx, snap2.activeExplosions[0].radiusPx, 0.001f)
        assertEquals(snap1.calculateChecksum(), snap2.calculateChecksum())
    }

    @Test
    fun test_spawnExplosiveSlug_nan_infinity_recovery() {
        val engine = KorsakovManufacturerEngine()
        engine.spawnExplosiveSlug(Float.NaN, 10f, 100f)
        engine.spawnExplosiveSlug(10f, Float.POSITIVE_INFINITY, 100f)
        engine.spawnExplosiveSlug(10f, 10f, Float.NaN)
        
        val snap = engine.getSnapshot()
        assertEquals(0, snap.activeExplosions.size)
    }

    @Test
    fun test_stepExplosions_nominal_knockback() {
        val engine = KorsakovManufacturerEngine(
            config = KorsakovTraitConfig(baseExplosiveRadiusPx = 100f, baseKnockbackForce = 100f, minDamageThreshold = 0f),
            currentSeed = 1
        )
        // Ensure deterministic seed doesn't randomize too crazily for our simple test
        // Variance will be applied but it should be within 0.9 to 1.1 bounds.
        engine.spawnExplosiveSlug(0f, 0f, 100f) 
        
        val targetX = floatArrayOf(50f)
        val targetY = floatArrayOf(0f)
        val targetMasses = floatArrayOf(1f)
        
        engine.stepExplosions(0.016f, 1, targetX, targetY, targetMasses)
        
        assertTrue("Expected knockback in X direction", engine.resultVx[0] > 0f)
        assertEquals("Expected no knockback in Y direction", 0f, engine.resultVy[0], 0.001f)
        assertTrue("Expected damage", engine.resultDamage[0] > 0f)
        
        val snap = engine.getSnapshot()
        assertEquals("Explosions should be cleared after step", 0, snap.activeExplosions.size)
    }

    @Test
    fun test_stepExplosions_falloff_distance() {
        val engine = KorsakovManufacturerEngine(
            config = KorsakovTraitConfig(baseExplosiveRadiusPx = 100f, baseKnockbackForce = 100f),
            currentSeed = 42
        )
        engine.spawnExplosiveSlug(0f, 0f, 100f)
        
        val targetX = floatArrayOf(20f, 80f)
        val targetY = floatArrayOf(0f, 0f)
        val targetMasses = floatArrayOf(1f, 1f)
        
        engine.stepExplosions(0.016f, 2, targetX, targetY, targetMasses)
        
        assertTrue("Closer target should take more damage", engine.resultDamage[0] > engine.resultDamage[1])
        assertTrue("Closer target should take more knockback", engine.resultVx[0] > engine.resultVx[1])
    }

    @Test
    fun test_stepExplosions_mass_scaling() {
        val engine = KorsakovManufacturerEngine(
            config = KorsakovTraitConfig(baseExplosiveRadiusPx = 100f, baseKnockbackForce = 100f),
            currentSeed = 42
        )
        engine.spawnExplosiveSlug(0f, 0f, 100f)
        
        val targetX = floatArrayOf(50f, 50f)
        val targetY = floatArrayOf(0f, 0f)
        val targetMasses = floatArrayOf(1f, 2f) // Second is twice as heavy
        
        engine.stepExplosions(0.016f, 2, targetX, targetY, targetMasses)
        
        assertEquals("Damage should be independent of mass", engine.resultDamage[0], engine.resultDamage[1], 0.001f)
        assertTrue("Heavier target should take less knockback", engine.resultVx[0] > engine.resultVx[1])
    }

    @Test
    fun test_stepExplosions_max_zombies_limit() {
        val engine = KorsakovManufacturerEngine()
        engine.spawnExplosiveSlug(0f, 0f, 100f)
        
        // Exceed array bounds? No, engine arrays are 1024. We just pass a count of 5.
        val targetX = FloatArray(10) { 10f }
        val targetY = FloatArray(10) { 10f }
        val targetMasses = FloatArray(10) { 1f }
        
        engine.stepExplosions(0.016f, 5, targetX, targetY, targetMasses)
        
        assertTrue(engine.resultDamage[4] > 0f)
        assertEquals(0f, engine.resultDamage[5], 0.001f) // Target 5 should not be processed
    }

    @Test
    fun test_stepExplosions_dt_clamping() {
        // Technically dt doesn't affect instantaneous impulse in this exact logic right now, 
        // but testing the bounds loop and dt sanity check coverage.
        val engine = KorsakovManufacturerEngine()
        engine.spawnExplosiveSlug(0f, 0f, 100f)
        
        val targetX = floatArrayOf(10f)
        val targetY = floatArrayOf(10f)
        val targetMasses = floatArrayOf(1f)
        
        // Pass absurd dt
        engine.stepExplosions(100f, 1, targetX, targetY, targetMasses)
        
        assertTrue(engine.resultDamage[0] > 0f) // Should still process properly without blowing up
    }

    @Test
    fun test_snapshot_serialization_roundtrip() {
        val engine = KorsakovManufacturerEngine(currentSeed = 777)
        engine.spawnExplosiveSlug(5f, 5f, 50f)
        
        val originalSnapshot = engine.getSnapshot()
        val serializedMap = originalSnapshot.toMap()
        
        val restoredSnapshot = KorsakovTraitSnapshot.fromMap(serializedMap)
        
        assertEquals(originalSnapshot.internalStateSeed, restoredSnapshot.internalStateSeed)
        assertEquals(originalSnapshot.activeExplosions.size, restoredSnapshot.activeExplosions.size)
        assertEquals(originalSnapshot.config.kineticDamageMultiplier, restoredSnapshot.config.kineticDamageMultiplier, 0.001f)
        assertEquals(originalSnapshot.activeExplosions[0].damage, restoredSnapshot.activeExplosions[0].damage, 0.001f)
    }

    @Test
    fun test_snapshot_checksum_determinism() {
        val config = KorsakovTraitConfig()
        val exp1 = KorsakovSlugExplosion(1, 10f, 20f, 50f, 100f, 200f, 0)
        val exp2 = KorsakovSlugExplosion(1, 10f, 20f, 50f, 100f, 200f, 0)
        
        val snap1 = KorsakovTraitSnapshot(config, listOf(exp1), 123)
        val snap2 = KorsakovTraitSnapshot(config, listOf(exp2), 123)
        
        assertEquals(snap1.calculateChecksum(), snap2.calculateChecksum())
        
        val exp3 = KorsakovSlugExplosion(1, 10f, 20f, 50f, 100f, 201f, 0) // slightly different force
        val snap3 = KorsakovTraitSnapshot(config, listOf(exp3), 123)
        
        assertNotEquals(snap1.calculateChecksum(), snap3.calculateChecksum())
    }

    @Test
    fun test_graceful_degradation_max_capacity_stress() {
        val engine = KorsakovManufacturerEngine()
        
        // Spawn 10 explosions
        for (i in 0 until 10) {
            engine.spawnExplosiveSlug(0f, 0f, 100f)
        }
        
        val maxCapacity = 1000
        val targetX = FloatArray(maxCapacity) { 10f }
        val targetY = FloatArray(maxCapacity) { 10f }
        val targetMasses = FloatArray(maxCapacity) { 1f }
        
        // Should not throw exception and should complete quickly
        engine.stepExplosions(0.016f, maxCapacity, targetX, targetY, targetMasses)
        
        for (i in 0 until maxCapacity) {
            assertTrue(engine.resultDamage[i] > 0f)
        }
        
        val snap = engine.getSnapshot()
        assertEquals(0, snap.activeExplosions.size)
    }
}
