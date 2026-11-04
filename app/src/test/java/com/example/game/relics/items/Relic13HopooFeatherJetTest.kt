package com.example.game.relics.items

import org.junit.Assert.*
import org.junit.Test

class Relic13HopooFeatherJetTest {

    @Test
    fun testConfigProperties() {
        val config = HopooFeatherJetConfig(stackCount = 3)
        assertFalse("Hopoo Feather Jet should never be purchasable", config.canBePurchasedAtVending)
        assertEquals(3, config.maxCharges)
        assertEquals(0.2f, config.invulnerabilityDurationSeconds, 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConfigRejectsZeroStacks() {
        HopooFeatherJetConfig(stackCount = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConfigRejectsNegativeStacks() {
        HopooFeatherJetConfig(stackCount = -5)
    }

    @Test
    fun testStateSerializationRoundtrip() {
        val original = HopooFeatherJetState(
            chargesRemaining = 2,
            invulnerabilityTimerSeconds = 0.15f,
            checksum = 123456789L
        )
        val map = original.toMap()
        val deserialized = HopooFeatherJetState.fromMap(map)

        assertEquals(original.chargesRemaining, deserialized.chargesRemaining)
        assertEquals(original.invulnerabilityTimerSeconds, deserialized.invulnerabilityTimerSeconds, 0.0001f)
        assertEquals(original.checksum, deserialized.checksum)
    }

    @Test
    fun testChecksumIsDeterministic() {
        val state1 = HopooFeatherJetState(chargesRemaining = 3, invulnerabilityTimerSeconds = 0.1f)
        val state2 = HopooFeatherJetState(chargesRemaining = 3, invulnerabilityTimerSeconds = 0.1f)

        val checksum1 = Relic13HopooFeatherJetEngine.generateChecksum(state1)
        val checksum2 = Relic13HopooFeatherJetEngine.generateChecksum(state2)

        assertEquals(checksum1, checksum2)
    }

    @Test
    fun testChecksumIsUnique() {
        val state1 = HopooFeatherJetState(chargesRemaining = 3, invulnerabilityTimerSeconds = 0.1f)
        val state2 = HopooFeatherJetState(chargesRemaining = 2, invulnerabilityTimerSeconds = 0.1f)
        val state3 = HopooFeatherJetState(chargesRemaining = 3, invulnerabilityTimerSeconds = 0.15f)

        val c1 = Relic13HopooFeatherJetEngine.generateChecksum(state1)
        val c2 = Relic13HopooFeatherJetEngine.generateChecksum(state2)
        val c3 = Relic13HopooFeatherJetEngine.generateChecksum(state3)

        assertNotEquals(c1, c2)
        assertNotEquals(c1, c3)
        assertNotEquals(c2, c3)
    }

    @Test
    fun testAttemptAirDashSuccess() {
        val config = HopooFeatherJetConfig(stackCount = 2)
        val state = HopooFeatherJetState(chargesRemaining = 2, invulnerabilityTimerSeconds = 0f)

        val result = Relic13HopooFeatherJetEngine.attemptAirDash(config, state)

        assertTrue(result.success)
        assertEquals(1, result.newState.chargesRemaining)
        assertEquals(0.2f, result.newState.invulnerabilityTimerSeconds, 0.0001f)
        assertEquals(Relic13HopooFeatherJetEngine.generateChecksum(result.newState), result.newState.checksum)
    }

    @Test
    fun testAttemptAirDashFailureWhenEmpty() {
        val config = HopooFeatherJetConfig(stackCount = 2)
        val state = HopooFeatherJetState(chargesRemaining = 0, invulnerabilityTimerSeconds = 0f)

        val result = Relic13HopooFeatherJetEngine.attemptAirDash(config, state)

        assertFalse(result.success)
        assertEquals(0, result.newState.chargesRemaining)
    }

    @Test
    fun testAttemptAirDashProlongsInvulnerability() {
        val config = HopooFeatherJetConfig(stackCount = 1)
        val state = HopooFeatherJetState(chargesRemaining = 1, invulnerabilityTimerSeconds = 0.5f)

        val result = Relic13HopooFeatherJetEngine.attemptAirDash(config, state)

        assertTrue(result.success)
        assertEquals(0.5f, result.newState.invulnerabilityTimerSeconds, 0.0001f)
    }

    @Test
    fun testTickReducesInvulnerabilityTimer() {
        val config = HopooFeatherJetConfig(stackCount = 1)
        val state = HopooFeatherJetState(chargesRemaining = 1, invulnerabilityTimerSeconds = 0.15f)

        val newState = Relic13HopooFeatherJetEngine.tick(config, state, 0.05f) // valid dt

        assertEquals(0.1f, newState.invulnerabilityTimerSeconds, 0.0001f)
        assertEquals(1, newState.chargesRemaining)
        assertEquals(Relic13HopooFeatherJetEngine.generateChecksum(newState), newState.checksum)
    }

    @Test
    fun testTickClampsDeltaTime() {
        val config = HopooFeatherJetConfig(stackCount = 1)
        val state = HopooFeatherJetState(chargesRemaining = 1, invulnerabilityTimerSeconds = 1.0f)

        // Exceeding max clamp
        val newState = Relic13HopooFeatherJetEngine.tick(config, state, 0.5f)

        // Clamps to 0.100
        assertEquals(0.9f, newState.invulnerabilityTimerSeconds, 0.0001f)
    }

    @Test
    fun testTickClampsBelowZero() {
        val config = HopooFeatherJetConfig(stackCount = 1)
        val state = HopooFeatherJetState(chargesRemaining = 1, invulnerabilityTimerSeconds = 0.05f)

        val newState = Relic13HopooFeatherJetEngine.tick(config, state, 0.1f)

        assertEquals(0.0f, newState.invulnerabilityTimerSeconds, 0.0001f)
    }

    @Test
    fun testTickHandlesNaNAndInfinity() {
        val config = HopooFeatherJetConfig(stackCount = 1)
        val state = HopooFeatherJetState(chargesRemaining = 1, invulnerabilityTimerSeconds = 0.5f)

        val newStateNaN = Relic13HopooFeatherJetEngine.tick(config, state, Float.NaN)
        assertEquals(0.5f, newStateNaN.invulnerabilityTimerSeconds, 0.0001f)

        val newStateInf = Relic13HopooFeatherJetEngine.tick(config, state, Float.POSITIVE_INFINITY)
        assertEquals(0.5f, newStateInf.invulnerabilityTimerSeconds, 0.0001f)
    }

    @Test
    fun testOnGroundLandedRestoresCharges() {
        val config = HopooFeatherJetConfig(stackCount = 3)
        val state = HopooFeatherJetState(chargesRemaining = 0, invulnerabilityTimerSeconds = 0.1f)

        val newState = Relic13HopooFeatherJetEngine.onGroundLanded(config, state)

        assertEquals(3, newState.chargesRemaining)
        assertEquals(0.1f, newState.invulnerabilityTimerSeconds, 0.0001f)
        assertEquals(Relic13HopooFeatherJetEngine.generateChecksum(newState), newState.checksum)
    }

    @Test
    fun testExtremeStacks() {
        val config = HopooFeatherJetConfig(stackCount = 300)
        val state = HopooFeatherJetState(chargesRemaining = 0, invulnerabilityTimerSeconds = 0f)

        val newState = Relic13HopooFeatherJetEngine.onGroundLanded(config, state)
        assertEquals(300, newState.chargesRemaining)

        var currentState = newState
        var dashes = 0
        while(true) {
            val res = Relic13HopooFeatherJetEngine.attemptAirDash(config, currentState)
            if (res.success) {
                dashes++
                currentState = res.newState
            } else {
                break
            }
        }

        assertEquals(300, dashes)
        assertEquals(0, currentState.chargesRemaining)
    }
}
