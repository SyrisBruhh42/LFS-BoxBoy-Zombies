package com.example.game.loot.borderlands.catalog

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BioHazeManufacturerTraitTest {

    @Test
    fun `test config serialization roundtrip`() {
        val config = BioHazeTraitConfig(extraPierceCount = 2, splashRadiusPx = 50f, splashDurationSeconds = 5f)
        val map = config.toMap()
        val restored = BioHazeTraitConfig.fromMap(map)
        assertEquals(config, restored)
    }

    @Test
    fun `test snapshot serialization roundtrip`() {
        val snapshot = BioHazeSplashSnapshot(
            x = 100f,
            y = 200f,
            radius = 45f,
            dps = 30,
            remainingDurationSeconds = 2.5f,
            sourceChecksum = 12345L
        )
        val map = snapshot.toMap()
        val restored = BioHazeSplashSnapshot.fromMap(map)
        assertEquals(snapshot.x, restored.x, 0.001f)
        assertEquals(snapshot.y, restored.y, 0.001f)
        assertEquals(snapshot.radius, restored.radius, 0.001f)
        assertEquals(snapshot.dps, restored.dps)
        assertEquals(snapshot.remainingDurationSeconds, restored.remainingDurationSeconds, 0.001f)
        assertEquals(snapshot.sourceChecksum, restored.sourceChecksum)
    }

    @Test
    fun `test createSplashPuddle nominal`() {
        val config = BioHazeTraitConfig(splashRadiusPx = 50f, splashDurationSeconds = 3f, splashDamagePercent = 0.5f, baseSplashDps = 10)
        val state = BioHazeManufacturerEngine.createSplashPuddle(config, 100f, 100f, 100, 42L)
        assertNotNull(state)
        assertEquals(100f, state!!.x, 0.001f)
        assertEquals(100f, state.y, 0.001f)
        assertEquals(50f, state.radius, 0.001f)
        assertEquals(50, state.dps) // 100 * 0.5
        assertEquals(3f, state.remainingDurationSeconds, 0.001f)
    }

    @Test
    fun `test createSplashPuddle invalid inputs returns null`() {
        val config = BioHazeTraitConfig()
        val nanState = BioHazeManufacturerEngine.createSplashPuddle(config, Float.NaN, 100f, 100, 42L)
        assertNull(nanState)

        val infState = BioHazeManufacturerEngine.createSplashPuddle(config, 100f, Float.POSITIVE_INFINITY, 100, 42L)
        assertNull(infState)

        val zeroRadiusConfig = BioHazeTraitConfig(splashRadiusPx = 0f)
        val zeroRadiusState = BioHazeManufacturerEngine.createSplashPuddle(zeroRadiusConfig, 100f, 100f, 100, 42L)
        assertNull(zeroRadiusState)
        
        val negativeDurationConfig = BioHazeTraitConfig(splashDurationSeconds = -1f)
        val negativeDurationState = BioHazeManufacturerEngine.createSplashPuddle(negativeDurationConfig, 100f, 100f, 100, 42L)
        assertNull(negativeDurationState)
    }

    @Test
    fun `test determinism proof with identical seeds`() {
        val config = BioHazeTraitConfig()
        val state1 = BioHazeManufacturerEngine.createSplashPuddle(config, 50f, 50f, 100, 999L)!!
        val state2 = BioHazeManufacturerEngine.createSplashPuddle(config, 50f, 50f, 100, 999L)!!
        
        assertEquals(state1.getFnv1aChecksum(), state2.getFnv1aChecksum())
        assertEquals(state1.toSnapshot(), state2.toSnapshot())
    }

    @Test
    fun `test dt clamping in stepPuddles`() {
        val config = BioHazeTraitConfig(splashDurationSeconds = 3f)
        val puddle = BioHazeManufacturerEngine.createSplashPuddle(config, 0f, 0f, 100, 1L)!!
        val list = mutableListOf(puddle)

        // dt clamped to 0.100 max
        BioHazeManufacturerEngine.stepPuddles(list, 5.0f) 
        
        assertEquals(1, list.size)
        // 3.0 - 0.1 = 2.9
        assertEquals(2.9f, list[0].remainingDurationSeconds, 0.001f)

        // dt clamped to 0.001 min
        BioHazeManufacturerEngine.stepPuddles(list, -5.0f)
        assertEquals(1, list.size)
        // 2.9 - 0.001 = 2.899
        assertEquals(2.899f, list[0].remainingDurationSeconds, 0.001f)
    }

    @Test
    fun `test puddle expiration and zero allocation removal`() {
        val config = BioHazeTraitConfig(splashDurationSeconds = 0.15f)
        val p1 = BioHazeManufacturerEngine.createSplashPuddle(config, 0f, 0f, 100, 1L)!!
        val p2 = BioHazeManufacturerEngine.createSplashPuddle(config, 10f, 10f, 100, 2L)!!
        val list = mutableListOf(p1, p2)

        BioHazeManufacturerEngine.stepPuddles(list, 0.1f)
        assertEquals(2, list.size)

        BioHazeManufacturerEngine.stepPuddles(list, 0.1f) // Total dt = 0.2 (0.1 + 0.1)
        assertEquals(0, list.size)
    }
    
    @Test
    fun `test stepPuddles removes NaN puddles`() {
        val config = BioHazeTraitConfig(splashDurationSeconds = 3f)
        val p1 = BioHazeManufacturerEngine.createSplashPuddle(config, 0f, 0f, 100, 1L)!!
        p1.x = Float.NaN // corrupt puddle
        val list = mutableListOf(p1)
        
        BioHazeManufacturerEngine.stepPuddles(list, 0.05f)
        assertEquals(0, list.size)
    }

    @Test
    fun `test applySplashDamage nominal`() {
        val config = BioHazeTraitConfig(splashRadiusPx = 50f, splashDamagePercent = 1.0f)
        val p1 = BioHazeManufacturerEngine.createSplashPuddle(config, 100f, 100f, 100, 1L)!! // dps = 100
        val puddles = listOf(p1)

        val tx = floatArrayOf(100f, 140f, 200f) // 0: epicenter, 1: edge(dist 40), 2: outside
        val ty = floatArrayOf(100f, 100f, 100f)
        val thp = intArrayOf(10, 10, 10)
        val tr = floatArrayOf(10f, 10f, 10f)
        val outDmg = FloatArray(3)

        // dt = 0.1s -> dmg = 10 per tick
        BioHazeManufacturerEngine.applySplashDamage(puddles, tx, ty, thp, tr, 0.1f, outDmg)

        assertEquals(10f, outDmg[0], 0.01f) // Hit
        assertEquals(10f, outDmg[1], 0.01f) // Hit (40 < 50+10)
        assertEquals(0f, outDmg[2], 0.01f)  // Miss (100 > 60)
    }

    @Test
    fun `test applySplashDamage dead targets and NaN ignored`() {
        val config = BioHazeTraitConfig(splashRadiusPx = 50f)
        val p1 = BioHazeManufacturerEngine.createSplashPuddle(config, 100f, 100f, 100, 1L)!!
        val puddles = listOf(p1)

        val tx = floatArrayOf(100f, Float.NaN)
        val ty = floatArrayOf(100f, 100f)
        val thp = intArrayOf(0, 10) // 0: dead, 1: alive but NaN pos
        val tr = floatArrayOf(10f, 10f)
        val outDmg = FloatArray(2)

        BioHazeManufacturerEngine.applySplashDamage(puddles, tx, ty, thp, tr, 0.1f, outDmg)

        assertEquals(0f, outDmg[0], 0.01f) // Dead ignored
        assertEquals(0f, outDmg[1], 0.01f) // NaN ignored
    }

    @Test
    fun `test max capacity stress`() {
        val config = BioHazeTraitConfig(splashRadiusPx = 100f, splashDamagePercent = 1.0f)
        val puddles = MutableList(50) { 
            BioHazeManufacturerEngine.createSplashPuddle(config, 50f, 50f, 100, it.toLong())!! 
        }

        val numTargets = 300
        val tx = FloatArray(numTargets) { Random.nextFloat() * 100f }
        val ty = FloatArray(numTargets) { Random.nextFloat() * 100f }
        val thp = IntArray(numTargets) { 100 }
        val tr = FloatArray(numTargets) { 10f }
        val outDmg = FloatArray(numTargets)

        // All 300 targets inside 50 puddles
        val start = System.nanoTime()
        BioHazeManufacturerEngine.applySplashDamage(puddles, tx, ty, thp, tr, 0.1f, outDmg)
        val end = System.nanoTime()

        assertTrue((end - start) < 50_000_000) // Must finish < 50ms (well within frame budget even with unoptimized loops)
        
        // 50 puddles * 100 DPS * 0.1 dt = 500 dmg each
        for (i in 0 until numTargets) {
            assertEquals(500f, outDmg[i], 0.1f)
        }
    }
    
    @Test
    fun `test checksum change on mutation`() {
        val config = BioHazeTraitConfig(splashRadiusPx = 50f)
        val state = BioHazeManufacturerEngine.createSplashPuddle(config, 0f, 0f, 100, 42L)!!
        val hash1 = state.getFnv1aChecksum()
        
        state.remainingDurationSeconds -= 0.1f
        val hash2 = state.getFnv1aChecksum()
        
        assertNotEquals(hash1, hash2)
    }
}
