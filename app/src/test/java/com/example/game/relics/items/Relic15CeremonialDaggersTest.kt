package com.example.game.relics.items

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class Relic15CeremonialDaggersTest {

    private fun createZombie(id: Int, x: Float, y: Float, hp: Int = 100): ZombieEntity {
        return ZombieEntity(
            id = id,
            archetype = ZombieArchetype.SHAMBLER,
            x = x,
            y = y,
            hp = hp,
            maxHp = 100,
            attackCooldownMs = 0L
        )
    }

    @Test
    fun testNominalDaggerSpawningOnKill() {
        val engine = Relic15CeremonialDaggersEngine()
        val state = CeremonialDaggersState(stackCount = 1)
        engine.onZombieKilled(100f, 100f, state, 12345L)
        assertEquals(3, engine.getActiveDaggerCount())
    }

    @Test
    fun testSerializationRoundtrip() {
        val state = CeremonialDaggersState(stackCount = 5, canBePurchasedAtVending = false)
        val map = state.toMap()
        val restored = CeremonialDaggersState.fromMap(map)
        assertEquals(5, restored.stackCount)
        assertFalse(restored.canBePurchasedAtVending)
    }

    @Test
    fun testBoundingConstraints() {
        var threw = false
        try {
            CeremonialDaggersState(stackCount = 0)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected exception for stackCount < 1", threw)
        
        threw = false
        try {
            CeremonialDaggersState(stackCount = 1, canBePurchasedAtVending = true)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected exception for canBePurchasedAtVending = true", threw)
    }
    
    @Test
    fun testFromMapEnforcesBounds() {
        val map = mapOf("stackCount" to -5, "canBePurchasedAtVending" to false)
        val restored = CeremonialDaggersState.fromMap(map)
        assertEquals(1, restored.stackCount)
        assertFalse(restored.canBePurchasedAtVending)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testFromMapThrowsOnTrueVending() {
        val map = mapOf("stackCount" to 1, "canBePurchasedAtVending" to true)
        CeremonialDaggersState.fromMap(map)
    }

    @Test
    fun testFromMapClampsStackCount() {
        val map = mapOf("stackCount" to -5, "canBePurchasedAtVending" to false)
        val restored = CeremonialDaggersState.fromMap(map)
        assertEquals(1, restored.stackCount)
    }

    @Test
    fun testDeterministicOutputs() {
        val engine1 = Relic15CeremonialDaggersEngine()
        val engine2 = Relic15CeremonialDaggersEngine()
        val state = CeremonialDaggersState(stackCount = 2)
        
        engine1.onZombieKilled(0f, 0f, state, 999L)
        engine2.onZombieKilled(0f, 0f, state, 999L)
        
        val pos1 = engine1.getActiveDaggerPositions()
        val pos2 = engine2.getActiveDaggerPositions()
        
        assertEquals(pos1.size, pos2.size)
        for (i in pos1.indices) {
            assertEquals(pos1[i].first, pos2[i].first, 0.001f)
            assertEquals(pos1[i].second, pos2[i].second, 0.001f)
        }
    }

    @Test
    fun testHomingLogic() {
        val engine = Relic15CeremonialDaggersEngine(CeremonialDaggerConfig(speed = 100f, turnSpeedRadPerSec = 10f))
        val state = CeremonialDaggersState(stackCount = 1)
        engine.onZombieKilled(0f, 0f, state, 12345L)
        
        val target = createZombie(1, 100f, 0f)
        var hitDetected = false
        
        for (i in 0 until 50) {
            val hits = engine.stepDaggers(0.1f, listOf(target))
            if (hits.isNotEmpty()) {
                hitDetected = true
                assertEquals(1, hits[0].zombieId)
                break
            }
        }
        
        assertTrue("Daggers should have hit the target", hitDetected)
    }

    @Test
    fun testStackScaling() {
        val engine = Relic15CeremonialDaggersEngine()
        val state = CeremonialDaggersState(stackCount = 2) // damage should be 100 * (1 + 1.5*2) = 400
        engine.onZombieKilled(0f, 0f, state, 12345L)
        
        val target = createZombie(1, 0f, 0f)
        val hits = engine.stepDaggers(0.1f, listOf(target))
        
        assertTrue(hits.isNotEmpty())
        assertEquals(400, hits[0].damage)
    }

    @Test
    fun testMaxCapacityBehavior() {
        val engine = Relic15CeremonialDaggersEngine(maxDaggers = 5)
        val state = CeremonialDaggersState(stackCount = 1)
        
        engine.onZombieKilled(0f, 0f, state, 1L) // spawns 3
        assertEquals(3, engine.getActiveDaggerCount())
        
        engine.onZombieKilled(0f, 0f, state, 2L) // attempts to spawn 3, but only 2 slots left
        assertEquals(5, engine.getActiveDaggerCount())
        
        engine.onZombieKilled(0f, 0f, state, 3L) // attempts to spawn 3, 0 slots left
        assertEquals(5, engine.getActiveDaggerCount())
    }

    @Test
    fun testNanInfinityResilience() {
        val engine = Relic15CeremonialDaggersEngine()
        val state = CeremonialDaggersState(stackCount = 1)
        
        // Spawn with NaN
        engine.onZombieKilled(Float.NaN, Float.POSITIVE_INFINITY, state, 1L)
        assertEquals(0, engine.getActiveDaggerCount())
        
        // Normal spawn
        engine.onZombieKilled(0f, 0f, state, 1L)
        assertEquals(3, engine.getActiveDaggerCount())
        
        // Step with NaN dt
        engine.stepDaggers(Float.NaN, emptyList())
        assertEquals(3, engine.getActiveDaggerCount()) // Position should not change, no crash
    }

    @Test
    fun testLifetimeExpiration() {
        val engine = Relic15CeremonialDaggersEngine(CeremonialDaggerConfig(maxLifetimeSeconds = 2f))
        val state = CeremonialDaggersState(stackCount = 1)
        engine.onZombieKilled(0f, 0f, state, 1L)
        assertEquals(3, engine.getActiveDaggerCount())
        
        engine.stepDaggers(1.5f, emptyList())
        assertEquals(3, engine.getActiveDaggerCount())
        
        engine.stepDaggers(0.6f, emptyList()) // Total dt = 2.1f > 2f
        assertEquals(0, engine.getActiveDaggerCount())
    }

    @Test
    fun testTargetSwitchOnDeath() {
        val engine = Relic15CeremonialDaggersEngine(CeremonialDaggerConfig(speed = 1000f, turnSpeedRadPerSec = 100f))
        val state = CeremonialDaggersState(stackCount = 1)
        
        // Spawn dagger at (0, 0)
        engine.onZombieKilled(0f, 0f, state, 1L)
        
        val z1 = createZombie(1, 100f, 0f, hp = 0) // Dead
        val z2 = createZombie(2, -100f, 0f, hp = 100) // Alive
        
        val hits = engine.stepDaggers(0.5f, listOf(z1, z2))
        
        // It should ignore z1 and hit z2
        assertTrue(hits.isNotEmpty())
        assertEquals(2, hits[0].zombieId)
    }

    @Test
    fun testChecksumDeterminism() {
        val state1 = CeremonialDaggersState(stackCount = 3)
        val state2 = CeremonialDaggersState(stackCount = 3)
        val state3 = CeremonialDaggersState(stackCount = 4)
        
        assertEquals(state1.computeChecksum(), state2.computeChecksum())
        assertTrue(state1.computeChecksum() != state3.computeChecksum())
    }
}
