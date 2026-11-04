package com.example.game.economy.vendors

import androidx.compose.ui.geometry.Rect
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceablePulseSentryBeaconTest {

    @Test
    fun testNominalDeploySuccess() {
        val ledger = SingleCurrencyLedger(1000)
        val obstacles = emptyList<Rect>()
        
        val result = PlaceablePulseSentryBeacon.attemptDeploy(
            id = 1,
            x = 100f,
            y = 100f,
            ledger = ledger,
            obstacles = obstacles
        )
        
        assertEquals(DeployResultStatus.SUCCESS, result.status)
        assertEquals(650, ledger.getBalance()) // 1000 - 350
        
        val state = result.initialState!!
        assertEquals(1, state.id)
        assertEquals(100f, state.x)
        assertEquals(100f, state.y)
        assertEquals(50, state.ammo)
        assertEquals(45_000L, state.ttlMs)
        assertEquals(0L, state.cooldownMs)
    }

    @Test
    fun testNominalTickWithTargetAcquisition() {
        val state = PulseSentryState(id = 1, x = 0f, y = 0f, ammo = 5, ttlMs = 5000L, cooldownMs = 0L)
        val target = ZombieEntity(id = 100, archetype = ZombieArchetype.SHAMBLER, x = 100f, y = 0f, hp = 100, maxHp = 100)
        val result = PulseSentryTickResult()
        
        PlaceablePulseSentryBeacon.tick(
            deltaSeconds = 0.05f, // 50ms
            state = state,
            targets = listOf(target),
            outResult = result
        )
        
        assertTrue(result.isActive)
        assertEquals(1, result.pulsesFiredCount)
        assertEquals(100, result.pulses[0]?.targetId)
        
        assertEquals(4, state.ammo)
        assertEquals(4950L, state.ttlMs) // 5000 - 50
        assertEquals(250L, state.cooldownMs) // config default fireRateMs
    }

    @Test
    fun testNominalTickCooldownDecay() {
        val state = PulseSentryState(id = 1, x = 0f, y = 0f, ammo = 5, ttlMs = 5000L, cooldownMs = 100L)
        val target = ZombieEntity(id = 100, archetype = ZombieArchetype.SHAMBLER, x = 100f, y = 0f, hp = 100, maxHp = 100)
        val result = PulseSentryTickResult()
        
        PlaceablePulseSentryBeacon.tick(
            deltaSeconds = 0.05f, // 50ms
            state = state,
            targets = listOf(target),
            outResult = result
        )
        
        assertTrue(result.isActive)
        assertEquals(0, result.pulsesFiredCount) // Still on cooldown
        
        assertEquals(5, state.ammo)
        assertEquals(4950L, state.ttlMs)
        assertEquals(50L, state.cooldownMs) // 100 - 50
    }

    @Test
    fun testDeployFailureInsufficientFunds() {
        val ledger = SingleCurrencyLedger(200) // Less than 350
        val obstacles = emptyList<Rect>()
        
        val result = PlaceablePulseSentryBeacon.attemptDeploy(1, 0f, 0f, ledger, obstacles)
        
        assertEquals(DeployResultStatus.INSUFFICIENT_FUNDS, result.status)
        assertEquals(200, ledger.getBalance()) // Balance unchanged
    }

    @Test
    fun testDeployFailureObstructed() {
        val ledger = SingleCurrencyLedger(1000)
        // Beacon bbox is 40x40 (x: -20 to 20, y: -20 to 20)
        val obstacles = listOf(Rect(10f, 10f, 50f, 50f)) 
        
        val result = PlaceablePulseSentryBeacon.attemptDeploy(1, 0f, 0f, ledger, obstacles)
        
        assertEquals(DeployResultStatus.OBSTRUCTED, result.status)
        assertEquals(1000, ledger.getBalance()) // Refunded/Not charged
    }

    @Test
    fun testDeployFailureInvalidCoordinates() {
        val ledger = SingleCurrencyLedger(1000)
        
        val result = PlaceablePulseSentryBeacon.attemptDeploy(1, Float.NaN, 0f, ledger, emptyList())
        assertEquals(DeployResultStatus.INVALID_COORDINATES, result.status)
        assertEquals(1000, ledger.getBalance())
    }

    @Test
    fun testTickNaNHandlingGracefulFallback() {
        val state = PulseSentryState(id = 1, x = 0f, y = 0f, ammo = 5, ttlMs = 5000L, cooldownMs = 0L)
        val result = PulseSentryTickResult()
        PlaceablePulseSentryBeacon.tick(Float.NaN, state, emptyList(), result)
        
        assertTrue(result.isActive)
        assertEquals(0, result.pulsesFiredCount)
        assertEquals(5, state.ammo)
        assertEquals(5000L, state.ttlMs) // Should not mutate on NaN
    }

    @Test
    fun testTickTTLDepletion() {
        val state = PulseSentryState(id = 1, x = 0f, y = 0f, ammo = 5, ttlMs = 50L, cooldownMs = 0L)
        val result = PulseSentryTickResult()
        PlaceablePulseSentryBeacon.tick(0.06f, state, emptyList(), result) // 60ms tick
        
        assertFalse(result.isActive)
        assertEquals(0L, state.ttlMs) // Clamped to 0
    }

    @Test
    fun testTickAmmoDepletion() {
        val state = PulseSentryState(id = 1, x = 0f, y = 0f, ammo = 1, ttlMs = 5000L, cooldownMs = 0L)
        val target = ZombieEntity(id = 100, archetype = ZombieArchetype.SHAMBLER, x = 10f, y = 0f, hp = 100, maxHp = 100)
        
        val result1 = PulseSentryTickResult()
        PlaceablePulseSentryBeacon.tick(0.05f, state, listOf(target), result1)
        assertFalse(result1.isActive)
        assertEquals(1, result1.pulsesFiredCount)
        assertEquals(0, state.ammo)
        
        // Next tick should be inactive due to ammo
        val result2 = PulseSentryTickResult()
        PlaceablePulseSentryBeacon.tick(0.05f, state, listOf(target), result2)
        assertFalse(result2.isActive)
        assertEquals(0, result2.pulsesFiredCount)
    }

    @Test
    fun testTargetAcquisitionIgnoresDeadAndOut_of_Range() {
        val state = PulseSentryState(id = 1, x = 0f, y = 0f, ammo = 5, ttlMs = 5000L, cooldownMs = 0L)
        val deadTarget = ZombieEntity(id = 100, archetype = ZombieArchetype.SHAMBLER, x = 10f, y = 0f, hp = 0, maxHp = 100)
        val outOfRangeTarget = ZombieEntity(id = 101, archetype = ZombieArchetype.SHAMBLER, x = 1000f, y = 0f, hp = 100, maxHp = 100)
        
        val result = PulseSentryTickResult()
        PlaceablePulseSentryBeacon.tick(0.05f, state, listOf(deadTarget, outOfRangeTarget), result)
        
        assertTrue(result.isActive)
        assertEquals(0, result.pulsesFiredCount) // Fired nothing
        assertEquals(5, state.ammo) // Ammo intact
    }

    @Test
    fun testDeterminismIdenticalChecksums() {
        val state1 = PulseSentryState(1, 10.5f, -20.3f, 42, 12345L, 50L)
        val state2 = PulseSentryState(1, 10.5f, -20.3f, 42, 12345L, 50L)
        
        assertEquals(state1.computeChecksum(), state2.computeChecksum())
        
        val mutatedState = state1.copy(ammo = 41)
        assertTrue(state1.computeChecksum() != mutatedState.computeChecksum())
    }

    @Test
    fun testSerializationRoundtripParity() {
        val config = PulseSentryConfig(maxAmmo = 99, fireRateMs = 123L)
        val configMap = config.toMap()
        val configRestored = PulseSentryConfig.fromMap(configMap)
        assertEquals(config, configRestored)

        val state = PulseSentryState(1, 10.5f, -20.3f, 42, 12345L, 50L)
        val stateMap = state.toMap()
        val stateRestored = PulseSentryState.fromMap(stateMap)
        
        assertEquals(state.id, stateRestored.id)
        assertEquals(state.x, stateRestored.x)
        assertEquals(state.y, stateRestored.y)
        assertEquals(state.ammo, stateRestored.ammo)
        assertEquals(state.ttlMs, stateRestored.ttlMs)
        assertEquals(state.cooldownMs, stateRestored.cooldownMs)
    }
}
