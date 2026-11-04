package com.example.game.loot.borderlands.catalog

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class MirvClusterGrenadeModTest {

    @Test
    fun test1_serializationRoundTrip_config() {
        val original = MirvClusterConfig(
            initialExplosionRadiusPx = 200f,
            childExplosionRadiusPx = 100f,
            initialDamage = 250f,
            childDamage = 90f,
            childCount = 8,
            childSpeed = 400f,
            childFuseTimeMs = 1200f
        )
        val map = original.toMap()
        val restored = MirvClusterConfig.fromMap(map)
        assertEquals(original, restored)
    }

    @Test
    fun test2_serializationRoundTrip_grenade() {
        val original = MirvGrenade(
            id = 42,
            x = 100f,
            y = -50f,
            vx = 10f,
            vy = -20f,
            fuseTimerMs = 500f,
            isChild = true,
            isActive = true
        )
        val map = original.toMap()
        val restored = MirvGrenade.fromMap(map)
        assertEquals(original, restored)
    }

    @Test
    fun test3_configBoundsClamping() {
        val badMap = mapOf(
            "initialExplosionRadiusPx" to -500f, // clamped to 10f
            "childCount" to 100, // clamped to 20
            "childDamage" to -100f // clamped to 0f
        )
        val config = MirvClusterConfig.fromMap(badMap)
        assertEquals(10f, config.initialExplosionRadiusPx)
        assertEquals(20, config.childCount)
        assertEquals(0f, config.childDamage)
        // Others should be default
        assertEquals(300f, config.childSpeed)
    }

    @Test
    fun test4_grenadeNaNProtection() {
        val badMap = mapOf<String, Any>(
            "x" to Float.NaN,
            "y" to Float.POSITIVE_INFINITY,
            "fuseTimerMs" to Float.NEGATIVE_INFINITY
        )
        val grenade = MirvGrenade.fromMap(badMap)
        assertEquals(0f, grenade.x)
        assertEquals(0f, grenade.y)
        assertEquals(0f, grenade.fuseTimerMs)
    }

    @Test
    fun test5_movementIntegration_noDetonation() {
        val grenade = MirvGrenade(1, 0f, 0f, 100f, 200f, 1000f, false, true)
        val config = MirvClusterConfig()
        
        val result = MirvClusterGrenadeMod.step(0.100f, listOf(grenade), config, 2) // exactly 0.1s
        
        assertEquals(1, result.updatedGrenades.size)
        val g = result.updatedGrenades[0]
        assertEquals(10f, g.x)
        assertEquals(20f, g.y)
        assertEquals(900f, g.fuseTimerMs) // 1000ms - 100ms
        assertEquals(0, result.explosions.size)
        assertTrue(g.isActive)
    }

    @Test
    fun test6_parentDetonation_spawnsChildren() {
        // Parent with just enough fuse to blow up this frame
        val parent = MirvGrenade(1, 50f, 50f, 0f, 0f, 10f, false, true)
        val config = MirvClusterConfig(childCount = 4, childSpeed = 100f)
        
        val result = MirvClusterGrenadeMod.step(0.016f, listOf(parent), config, 10)
        
        // Parent goes inactive, 4 children spawned -> 5 total grenades
        assertEquals(5, result.updatedGrenades.size)
        
        val parentOut = result.updatedGrenades.find { it.id == 1 }
        assertNotNull(parentOut)
        assertFalse(parentOut!!.isActive)
        
        val children = result.updatedGrenades.filter { it.isChild && it.isActive }
        assertEquals(4, children.size)
        
        // Verify child IDs started at 10
        assertTrue(children.any { it.id == 10 })
        assertTrue(children.any { it.id == 13 })
        
        // Initial explosion created
        assertEquals(1, result.explosions.size)
        val exp = result.explosions[0]
        assertFalse(exp.isChild)
        assertEquals(50f, exp.x)
        assertEquals(50f, exp.y)
    }

    @Test
    fun test7_childDetonation_noRecursiveSpawn() {
        val child = MirvGrenade(20, 100f, 100f, 0f, 0f, 10f, true, true)
        val config = MirvClusterConfig()
        
        val result = MirvClusterGrenadeMod.step(0.016f, listOf(child), config, 100)
        
        // Should only have the one grenade (now inactive)
        assertEquals(1, result.updatedGrenades.size)
        assertFalse(result.updatedGrenades[0].isActive)
        
        // Should create a child explosion
        assertEquals(1, result.explosions.size)
        val exp = result.explosions[0]
        assertTrue(exp.isChild)
        assertEquals(100f, exp.x)
        assertEquals(100f, exp.y)
    }

    @Test
    fun test8_inactiveGrenadesPassThrough() {
        val inactive = MirvGrenade(1, 0f, 0f, 0f, 0f, 0f, false, false)
        val config = MirvClusterConfig()
        
        val result = MirvClusterGrenadeMod.step(0.100f, listOf(inactive), config, 2)
        
        assertEquals(1, result.updatedGrenades.size)
        assertFalse(result.updatedGrenades[0].isActive)
        assertEquals(0, result.explosions.size)
    }

    @Test
    fun test9_dtClampingAndNaNProtection() {
        val g = MirvGrenade(1, 10f, 10f, 100f, 0f, 1000f, false, true)
        val config = MirvClusterConfig()
        
        // NaN dt should fall back to 0.016f
        val result1 = MirvClusterGrenadeMod.step(Float.NaN, listOf(g), config, 2)
        assertEquals(10f + 100f * 0.016f, result1.updatedGrenades[0].x)
        
        // > 0.100f should clamp to 0.100f
        val result2 = MirvClusterGrenadeMod.step(0.500f, listOf(g), config, 2)
        assertEquals(10f + 100f * 0.100f, result2.updatedGrenades[0].x)
        
        // < 0.001f should clamp to 0.001f
        val result3 = MirvClusterGrenadeMod.step(0.0001f, listOf(g), config, 2)
        assertEquals(10f + 100f * 0.001f, result3.updatedGrenades[0].x)
    }

    @Test
    fun test10_stressTest_manyGrenades() {
        val grenades = mutableListOf<MirvGrenade>()
        for (i in 0 until 100) {
            grenades.add(MirvGrenade(i, i.toFloat(), i.toFloat(), 10f, 10f, 10f, false, true))
        }
        val config = MirvClusterConfig(childCount = 6) // Each blows up into 6
        
        val result = MirvClusterGrenadeMod.step(0.016f, grenades, config, 1000)
        
        // 100 parents (inactive) + 600 children (active)
        assertEquals(700, result.updatedGrenades.size)
        assertEquals(100, result.explosions.size)
        assertEquals(1600, result.nextId) // 1000 + 600
    }

    @Test
    fun test11_determinism_checksumsMatch() {
        val grenades = listOf(
            MirvGrenade(1, 10f, 20f, 5f, 5f, 50f, false, true),
            MirvGrenade(2, -10f, -20f, -5f, -5f, 10f, true, true)
        )
        val config = MirvClusterConfig()
        
        val result1 = MirvClusterGrenadeMod.step(0.016f, grenades, config, 100)
        val result2 = MirvClusterGrenadeMod.step(0.016f, grenades, config, 100)
        
        assertEquals(result1.stateChecksum, result2.stateChecksum)
        assertEquals(result1.updatedGrenades.size, result2.updatedGrenades.size)
    }

    @Test
    fun test12_childDistributionTrajectory() {
        val parent = MirvGrenade(1, 0f, 0f, 0f, 0f, 10f, false, true)
        val config = MirvClusterConfig(childCount = 4, childSpeed = 100f)
        
        val result = MirvClusterGrenadeMod.step(0.016f, listOf(parent), config, 10)
        
        val children = result.updatedGrenades.filter { it.isChild && it.isActive }
        assertEquals(4, children.size)
        
        // 4 children should be at angles 0, 90, 180, 270 (in radians)
        // Speeds: (100, 0), (0, 100), (-100, 0), (0, -100)
        // With Float math, epsilon checks are needed
        val epsilon = 0.001f
        
        val vxs = children.map { it.vx }
        val vys = children.map { it.vy }
        
        assertTrue(vxs.any { abs(it - 100f) < epsilon && abs(vys[vxs.indexOf(it)]) < epsilon })
        assertTrue(vxs.any { abs(it) < epsilon && abs(vys[vxs.indexOf(it)] - 100f) < epsilon })
        assertTrue(vxs.any { abs(it + 100f) < epsilon && abs(vys[vxs.indexOf(it)]) < epsilon })
        assertTrue(vxs.any { abs(it) < epsilon && abs(vys[vxs.indexOf(it)] + 100f) < epsilon })
    }
}
