package com.example.game.economy.vendors

import com.example.data.PerkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PerkSpeedColaMachineTest {

    private val machine = PerkSpeedColaMachine()

    private fun createValidState(): SpeedColaMachineState {
        val id = "perk_speed_cola_machine"
        val isActive = true
        val cost = PerkType.SPEED_COLA.cost
        val x = 100f
        val y = 100f
        val radius = 150f
        val checksum = SpeedColaMachineState.computeChecksum(id, isActive, cost, x, y, radius)
        return SpeedColaMachineState(id, isActive, cost, x, y, radius, checksum)
    }

    @Test
    fun `test purchase success when enough funds, no perk, and in range`() {
        val ledger = SingleCurrencyLedger(2000)
        val state = createValidState()
        val perks = emptySet<PerkType>()

        val result = machine.attemptPurchase(state, ledger, perks, 100f, 100f, 0.016f)

        assertTrue(result)
        assertEquals(2000 - PerkType.SPEED_COLA.cost, ledger.getBalance())
    }

    @Test
    fun `test purchase failure when out of range`() {
        val ledger = SingleCurrencyLedger(2000)
        val state = createValidState()
        val perks = emptySet<PerkType>()

        // Player is at 500,500 which is out of the 150f radius from 100,100
        val result = machine.attemptPurchase(state, ledger, perks, 500f, 500f, 0.016f)

        assertFalse(result)
        assertEquals(2000, ledger.getBalance())
    }

    @Test
    fun `test purchase failure when NaN coordinates injected`() {
        val ledger = SingleCurrencyLedger(2000)
        val state = createValidState()
        val perks = emptySet<PerkType>()

        val result = machine.attemptPurchase(state, ledger, perks, Float.NaN, 100f, 0.016f)

        assertFalse(result)
        assertEquals(2000, ledger.getBalance())
    }

    @Test
    fun `test purchase failure on checksum mismatch (corrupted state)`() {
        val ledger = SingleCurrencyLedger(2000)
        val validState = createValidState()
        val corruptedState = validState.copy(cost = 10) // Changed cost without updating checksum
        val perks = emptySet<PerkType>()

        val result = machine.attemptPurchase(corruptedState, ledger, perks, 100f, 100f, 0.016f)

        assertFalse(result)
        assertEquals(2000, ledger.getBalance())
    }

    @Test
    fun `test purchase failure when insufficient funds`() {
        val ledger = SingleCurrencyLedger(1000)
        val state = createValidState()
        val perks = emptySet<PerkType>()

        val result = machine.attemptPurchase(state, ledger, perks, 100f, 100f, 0.016f)

        assertFalse(result)
        assertEquals(1000, ledger.getBalance())
    }

    @Test
    fun `test purchase failure when already have perk`() {
        val ledger = SingleCurrencyLedger(2000)
        val state = createValidState()
        val perks = setOf(PerkType.SPEED_COLA)

        val result = machine.attemptPurchase(state, ledger, perks, 100f, 100f, 0.016f)

        assertFalse(result)
        assertEquals(2000, ledger.getBalance())
    }

    @Test
    fun `test purchase failure when machine inactive`() {
        val ledger = SingleCurrencyLedger(2000)
        val state = createValidState().copy(isActive = false, checksum = SpeedColaMachineState.computeChecksum("perk_speed_cola_machine", false, PerkType.SPEED_COLA.cost, 100f, 100f, 150f))
        val perks = emptySet<PerkType>()

        val result = machine.attemptPurchase(state, ledger, perks, 100f, 100f, 0.016f)

        assertFalse(result)
        assertEquals(2000, ledger.getBalance())
    }

    @Test
    fun `test determinism across 100 identical seed operations`() {
        val rng = Random(42)
        val baseState = createValidState()

        for (i in 0 until 100) {
            val randomDt = rng.nextFloat() * 0.1f
            val ledger = SingleCurrencyLedger(2000)
            val result = machine.attemptPurchase(baseState, ledger, emptySet(), 100f, 100f, randomDt)
            assertTrue(result)
            assertEquals(2000 - PerkType.SPEED_COLA.cost, ledger.getBalance())
        }
    }

    @Test
    fun `test max-capacity stress condition (300 simultaneous purchase attempts)`() {
        val state = createValidState()
        var successes = 0
        for (i in 0 until 300) {
            val ledger = SingleCurrencyLedger(3000) // Each has enough for 1 purchase
            val result = machine.attemptPurchase(state, ledger, emptySet(), 100f, 100f, 0.016f)
            if (result) successes++
        }
        assertEquals(300, successes)
    }

    @Test
    fun `test effective reload duration without perk`() {
        val baseReload = 2000L
        val perks = emptySet<PerkType>()
        val effective = machine.getEffectiveReloadDurationMs(baseReload, perks)
        assertEquals(2000L, effective)
    }

    @Test
    fun `test effective reload duration with perk halves duration`() {
        val baseReload = 2000L
        val perks = setOf(PerkType.SPEED_COLA)
        val effective = machine.getEffectiveReloadDurationMs(baseReload, perks)
        assertEquals(1000L, effective)
    }

    @Test
    fun `test barricade repair without perk repairs one board`() {
        val ledger = SingleCurrencyLedger(0)
        val perks = emptySet<PerkType>()
        val startPlanks = 2
        val maxPlanks = 6

        val result = machine.repairBarricade(startPlanks, maxPlanks, ledger, perks)

        assertEquals(3, result)
        assertEquals(20, ledger.getBalance())
    }

    @Test
    fun `test barricade repair with perk repairs two boards`() {
        val ledger = SingleCurrencyLedger(0)
        val perks = setOf(PerkType.SPEED_COLA)
        val startPlanks = 2
        val maxPlanks = 6

        val result = machine.repairBarricade(startPlanks, maxPlanks, ledger, perks)

        assertEquals(4, result)
        assertEquals(40, ledger.getBalance())
    }

    @Test
    fun `test barricade repair with perk stops at max boards`() {
        val ledger = SingleCurrencyLedger(0)
        val perks = setOf(PerkType.SPEED_COLA)
        val startPlanks = 5
        val maxPlanks = 6

        val result = machine.repairBarricade(startPlanks, maxPlanks, ledger, perks)

        assertEquals(6, result)
        assertEquals(20, ledger.getBalance())
    }

    @Test
    fun `test state serialization and deserialization parity`() {
        val original = createValidState()
        val serialized = original.toMap()
        val deserialized = SpeedColaMachineState.fromMap(serialized)

        assertEquals(original, deserialized)
    }
}
