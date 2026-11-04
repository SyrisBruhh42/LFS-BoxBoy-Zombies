package com.example.game.economy.vendors

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.hypot

class PerkPhdFlopperMachineTest {

    @Test
    fun testPurchaseSuccess() {
        val ledger = SingleCurrencyLedger(2500)
        val state = PhdFlopperState(isPowered = true, stockRemaining = 1)
        val config = PhdFlopperConfig(purchaseCost = 2000, activationRadiusPx = 150f)

        val result = PerkPhdFlopperMachine.attemptPurchase(
            state, ledger, 100f, 100f, 100f, 100f, config
        )

        assertTrue(result.success)
        assertEquals(0, result.newState.stockRemaining)
        assertEquals(500, result.remainingCurrency)
        assertEquals("PhD Flopper Purchased!", result.message)
    }

    @Test
    fun testPurchaseInsufficientFunds() {
        val ledger = SingleCurrencyLedger(1000)
        val state = PhdFlopperState(isPowered = true, stockRemaining = 1)
        val result = PerkPhdFlopperMachine.attemptPurchase(
            state, ledger, 100f, 100f, 100f, 100f
        )

        assertFalse(result.success)
        assertEquals(1, result.newState.stockRemaining)
        assertEquals(1000, result.remainingCurrency)
        assertEquals("Insufficient funds", result.message)
    }

    @Test
    fun testPurchaseOutOfRange() {
        val ledger = SingleCurrencyLedger(2500)
        val state = PhdFlopperState(isPowered = true, stockRemaining = 1)
        val config = PhdFlopperConfig(activationRadiusPx = 50f)
        val result = PerkPhdFlopperMachine.attemptPurchase(
            state, ledger, 0f, 0f, 100f, 100f, config
        )

        assertFalse(result.success)
        assertEquals("Out of range", result.message)
    }

    @Test
    fun testPurchaseUnpowered() {
        val ledger = SingleCurrencyLedger(2500)
        val state = PhdFlopperState(isPowered = false, stockRemaining = 1)
        val result = PerkPhdFlopperMachine.attemptPurchase(
            state, ledger, 100f, 100f, 100f, 100f
        )

        assertFalse(result.success)
        assertEquals("Machine is unpowered", result.message)
    }

    @Test
    fun testPurchaseOutOfStock() {
        val ledger = SingleCurrencyLedger(2500)
        val state = PhdFlopperState(isPowered = true, stockRemaining = 0)
        val result = PerkPhdFlopperMachine.attemptPurchase(
            state, ledger, 100f, 100f, 100f, 100f
        )

        assertFalse(result.success)
        assertEquals("Out of stock", result.message)
    }

    @Test
    fun testDiveShockwaveNominal() {
        val config = PhdFlopperConfig(diveShockwaveBaseDamage = 1000f, diveShockwaveRadiusPx = 100f)
        val targets = listOf(
            Pair(0f, 0f),    // Epicenter (dist = 0, falloff = 1.0, dmg = 1000)
            Pair(50f, 0f),   // Midpoint (dist = 50, falloff = 0.5, dmg = 500)
            Pair(100f, 0f),  // Edge (dist = 100, falloff = 0.0, dmg = 0)
            Pair(101f, 0f)   // Out of range (dist = 101, falloff = N/A)
        )
        val result = PerkPhdFlopperMachine.computeDiveShockwave(0f, 0f, targets, config)

        assertEquals(3, result.hitCount)
        assertEquals(1500f, result.totalDamageApplied, 0.01f)
    }

    @Test
    fun testDiveShockwaveMaxCapacityStress() {
        val config = PhdFlopperConfig(diveShockwaveBaseDamage = 1000f, diveShockwaveRadiusPx = 100f)
        val targets = MutableList(300) { Pair(10f, 10f) } // 300 entities within range
        val dist = hypot(10f, 10f)
        val falloff = max(0f, 1f - (dist / 100f))
        val expectedDamagePerTarget = 1000f * falloff

        val result = PerkPhdFlopperMachine.computeDiveShockwave(0f, 0f, targets, config)

        assertEquals(300, result.hitCount)
        // Allow a small delta for floating point precision issues
        assertTrue(kotlin.math.abs((expectedDamagePerTarget * 300) - result.totalDamageApplied) < 5f)
    }

    @Test
    fun testIsExplosiveImmune() {
        assertTrue(PerkPhdFlopperMachine.isExplosiveImmune(true))
        assertFalse(PerkPhdFlopperMachine.isExplosiveImmune(false))
    }

    @Test
    fun testSerializationRoundtrip() {
        val config = PhdFlopperConfig(1500, 100f, 600f, 300f)
        val map = config.toMap()
        val restoredConfig = PhdFlopperConfig.fromMap(map)
        assertEquals(config, restoredConfig)

        val state = PhdFlopperState(isPowered = true, stockRemaining = 5)
        val stateMap = state.toMap()
        val restoredState = PhdFlopperState.fromMap(stateMap)
        assertEquals(state, restoredState)
    }

    @Test
    fun testStateChecksum() {
        val state1 = PhdFlopperState(isPowered = true, stockRemaining = 1)
        val state2 = PhdFlopperState(isPowered = true, stockRemaining = 1)
        val state3 = PhdFlopperState(isPowered = false, stockRemaining = 1)

        assertEquals(state1.computeChecksum(), state2.computeChecksum())
        assertNotEquals(state1.computeChecksum(), state3.computeChecksum())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAttemptPurchaseNanCoords() {
        val ledger = SingleCurrencyLedger(2500)
        val state = PhdFlopperState(isPowered = true, stockRemaining = 1)
        PerkPhdFlopperMachine.attemptPurchase(state, ledger, Float.NaN, 0f, 0f, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testComputeDiveShockwaveNanCoords() {
        val targets = listOf(Pair(0f, 0f))
        PerkPhdFlopperMachine.computeDiveShockwave(Float.NaN, 0f, targets)
    }
}
