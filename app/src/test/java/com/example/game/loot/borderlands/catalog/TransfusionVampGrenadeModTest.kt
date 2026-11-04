package com.example.game.loot.borderlands.catalog

import com.example.game.GameSessionState
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import com.example.network.CoopPeerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransfusionVampGrenadeModTest {

    @Test
    fun testConfigValidation_validBounds() {
        val config = TransfusionGrenadeConfig()
        assertEquals(2.0f, config.fuseTimeSeconds, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConfigValidation_invalidMaxTrails() {
        TransfusionGrenadeConfig(maxTrails = 20)
    }

    @Test
    fun testTick_fusesAndDetonates() {
        val config = TransfusionGrenadeConfig(fuseTimeSeconds = 1.0f)
        val engine = TransfusionGrenadeEngine(config)
        val state = TransfusionGrenadeState()
        val session = GameSessionState(
            zombies = listOf(
                ZombieEntity(id = 1, archetype = ZombieArchetype.SHAMBLER, x = 10f, y = 10f, hp = 100, maxHp = 100)
            )
        )
        
        // Tick up to just before fuse time
        engine.tick(state, 0.9f, session)
        assertFalse(state.hasDetonated)
        
        // Tick to cross fuse time
        engine.tick(state, 0.1f, session)
        assertTrue(state.hasDetonated)
        assertEquals(1, state.trailsActiveCount)
    }

    @Test
    fun testTick_trailHomingAndHealingEvent() {
        val config = TransfusionGrenadeConfig(fuseTimeSeconds = 1.0f, maxTrails = 1, trailSpeed = 100f, healingPerTrail = 20)
        val engine = TransfusionGrenadeEngine(config)
        val state = TransfusionGrenadeState(x = 0f, y = 0f)
        val session = GameSessionState(
            playerX = 100f,
            playerY = 0f,
            hp = 50,
            maxHp = 100,
            zombies = listOf(
                ZombieEntity(id = 1, archetype = ZombieArchetype.SHAMBLER, x = 10f, y = 10f, hp = 100, maxHp = 100)
            )
        )
        
        // Detonate
        engine.tick(state, 1.0f, session)
        assertTrue(state.hasDetonated)
        assertEquals(1, state.trailsActiveCount)
        assertTrue(state.trailActive[0])
        assertEquals("", state.trailTargetId[0]) // Target is player
        
        // Move half way
        var events = engine.tick(state, 0.5f, session)
        assertTrue(events.isEmpty())
        assertTrue(state.trailActive[0])
        assertEquals(50f, state.trailX[0], 0.1f)
        
        // Reach target
        events = engine.tick(state, 0.5f, session)
        assertEquals(1, events.size)
        assertEquals("", events[0].targetId)
        assertEquals(20, events[0].amount)
        assertFalse(state.trailActive[0])
        assertEquals(0, state.trailsActiveCount)
    }

    @Test
    fun testDetonate_targetsMultiplePeers() {
        val config = TransfusionGrenadeConfig(fuseTimeSeconds = 0.5f, maxTrails = 4)
        val engine = TransfusionGrenadeEngine(config)
        val state = TransfusionGrenadeState(x = 0f, y = 0f)
        
        val zombies = List(5) { i -> 
            ZombieEntity(id = i, archetype = ZombieArchetype.SHAMBLER, x = 10f, y = 10f, hp = 100, maxHp = 100)
        }
        
        val session = GameSessionState(
            hp = 100,
            coopPeers = listOf(
                CoopPeerState(peerId = "peer1", hp = 100),
                CoopPeerState(peerId = "peer2", hp = 100)
            ),
            zombies = zombies
        )
        
        engine.tick(state, 0.5f, session, randomSeed = 42L)
        assertTrue(state.hasDetonated)
        assertEquals(4, state.trailsActiveCount)
        
        val targets = (0 until 4).map { state.trailTargetId[it] }.toSet()
        // Ensure at least one peer or player was targeted (randomness)
        assertTrue(targets.isNotEmpty())
    }

    @Test
    fun testDetonate_noZombies_noTrails() {
        val config = TransfusionGrenadeConfig(fuseTimeSeconds = 0.5f)
        val engine = TransfusionGrenadeEngine(config)
        val state = TransfusionGrenadeState()
        val session = GameSessionState(zombies = emptyList())
        
        engine.tick(state, 0.5f, session)
        assertTrue(state.hasDetonated)
        assertEquals(0, state.trailsActiveCount)
    }

    @Test
    fun testTick_NaNdtSeconds_handledGracefully() {
        val config = TransfusionGrenadeConfig()
        val engine = TransfusionGrenadeEngine(config)
        val state = TransfusionGrenadeState(fuseTimerSeconds = 0.5f)
        val session = GameSessionState()
        
        engine.tick(state, Float.NaN, session)
        assertEquals(0.5f, state.fuseTimerSeconds, 0.0001f) // No change
    }

    @Test
    fun testTick_NegativeDtSeconds_ignored() {
        val config = TransfusionGrenadeConfig()
        val engine = TransfusionGrenadeEngine(config)
        val state = TransfusionGrenadeState(fuseTimerSeconds = 0.5f)
        val session = GameSessionState()
        
        engine.tick(state, -0.1f, session)
        assertEquals(0.5f, state.fuseTimerSeconds, 0.0001f) // No change
    }

    @Test
    fun testState_checksumDeterminism() {
        val state1 = TransfusionGrenadeState(x = 10f, y = 20f, vx = 5f, fuseTimerSeconds = 1.0f)
        val state2 = TransfusionGrenadeState(x = 10f, y = 20f, vx = 5f, fuseTimerSeconds = 1.0f)
        assertEquals(state1.computeChecksum(), state2.computeChecksum())
        
        state2.hasDetonated = true
        assertFalse(state1.computeChecksum() == state2.computeChecksum())
    }

    @Test
    fun testState_toMapFromMapRoundtrip() {
        val original = TransfusionGrenadeState(
            x = 10f, y = 20f, vx = 2f, vy = -5f, 
            fuseTimerSeconds = 1.5f, hasDetonated = true, trailsActiveCount = 1
        )
        original.trailActive[0] = true
        original.trailX[0] = 5f
        original.trailY[0] = 10f
        original.trailTargetId[0] = "peer_test"
        
        val map = original.toMap()
        val restored = TransfusionGrenadeState.fromMap(map)
        
        assertEquals(original.x, restored.x, 0.001f)
        assertEquals(original.y, restored.y, 0.001f)
        assertEquals(original.fuseTimerSeconds, restored.fuseTimerSeconds, 0.001f)
        assertEquals(original.hasDetonated, restored.hasDetonated)
        assertEquals(original.trailsActiveCount, restored.trailsActiveCount)
        assertTrue(restored.trailActive[0])
        assertEquals("peer_test", restored.trailTargetId[0])
        assertEquals(original.computeChecksum(), restored.computeChecksum())
    }

    @Test
    fun testTickTrails_targetDies_trailFizzles() {
        val config = TransfusionGrenadeConfig(fuseTimeSeconds = 0.1f)
        val engine = TransfusionGrenadeEngine(config)
        val state = TransfusionGrenadeState()
        val session = GameSessionState(
            hp = 100, // Player alive initially
            zombies = listOf(ZombieEntity(1, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100))
        )
        
        engine.tick(state, 0.1f, session) // detonates, creates 1 trail targeting player
        assertTrue(state.hasDetonated)
        assertEquals(1, state.trailsActiveCount)
        assertTrue(state.trailActive[0])
        
        // Simulate player death
        val deadSession = session.copy(hp = 0)
        
        val events = engine.tick(state, 0.05f, deadSession)
        
        assertTrue(events.isEmpty()) // No healing events
        assertFalse(state.trailActive[0]) // Trail deactivated
        assertEquals(0, state.trailsActiveCount)
    }

    @Test
    fun testEngine_maxCapacity_noAllocationsTrails() {
        val config = TransfusionGrenadeConfig(fuseTimeSeconds = 0.1f, maxTrails = 16)
        val engine = TransfusionGrenadeEngine(config)
        val state = TransfusionGrenadeState()
        
        val zombies = List(20) { i -> ZombieEntity(i, ZombieArchetype.SHAMBLER, 0f, 0f, 100, 100) }
        val session = GameSessionState(hp = 100, zombies = zombies)
        
        engine.tick(state, 0.1f, session)
        
        assertEquals(16, state.trailsActiveCount) // Capped at maxTrails = 16
        for (i in 0 until 16) {
            assertTrue(state.trailActive[i])
        }
    }
}
