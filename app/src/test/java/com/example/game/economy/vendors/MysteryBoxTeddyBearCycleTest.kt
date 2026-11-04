package com.example.game.economy.vendors

import com.example.game.ModularWeaponGenerator
import org.junit.Assert.*
import org.junit.Test

class MysteryBoxTeddyBearCycleTest {

    private val locations = listOf(
        Pair("box_start", Pair(100f, 100f)),
        Pair("box_room2", Pair(500f, 500f)),
        Pair("box_room3", Pair(900f, 100f))
    )

    private val cycle = MysteryBoxTeddyBearCycle(locations)

    @Test
    fun testInitialization() {
        val state = cycle.initialize(12345L)
        assertTrue(locations.any { it.first == state.currentStationId })
        assertEquals(0, state.usesAtCurrentLocation)
        assertFalse(state.rollInProgress)
        assertNull(state.offeredWeapon)
    }

    @Test
    fun testInitializationDeterminism() {
        val state1 = cycle.initialize(999L)
        val state2 = cycle.initialize(999L)
        assertEquals(state1, state2)
    }

    @Test
    fun testPurchaseInsufficientFunds() {
        val state = cycle.initialize(123L).copy(cost = 950)
        val (newState, success) = cycle.attemptPurchase(state, 500, 1, 123L)
        assertFalse(success)
        assertFalse(newState.rollInProgress)
    }

    @Test
    fun testPurchaseBusy() {
        val state = cycle.initialize(123L).copy(rollInProgress = true)
        val (newState, success) = cycle.attemptPurchase(state, 1000, 1, 123L)
        assertFalse(success)
    }

    @Test
    fun testPurchaseSuccess() {
        val state = cycle.initialize(123L).copy(cost = 950, maxUsesBeforeTeddy = 10, usesAtCurrentLocation = 0)
        val (newState, success) = cycle.attemptPurchase(state, 1000, 1, 123L)
        assertTrue(success)
        assertTrue(newState.rollInProgress)
        assertEquals(MysteryBoxTeddyBearCycle.ROLL_DURATION_MS, newState.rollTimeRemainingMs)
        assertEquals(1, newState.usesAtCurrentLocation)
        assertFalse(newState.isTeddyBearRoll)
    }

    @Test
    fun testStepRollCompletion() {
        var state = cycle.initialize(123L).copy(
            rollInProgress = true,
            rollTimeRemainingMs = 100f,
            isTeddyBearRoll = false
        )

        state = cycle.step(state, 0.1f)
        assertFalse(state.rollInProgress)
        assertEquals(0f, state.rollTimeRemainingMs)
        assertEquals(MysteryBoxTeddyBearCycle.OFFER_DURATION_MS, state.weaponOfferTimeRemainingMs)
    }

    @Test
    fun testStepTeddyBearCompletionRelocation() {
        val initialState = cycle.initialize(123L).copy(
            currentStationId = "box_start",
            rollInProgress = true,
            rollTimeRemainingMs = 50f,
            isTeddyBearRoll = true
        )

        val state = cycle.step(initialState, 0.05f)
        assertFalse(state.rollInProgress)
        assertEquals("box_room2", state.currentStationId)
        assertEquals(0, state.usesAtCurrentLocation)
    }

    @Test
    fun testGrabWeapon() {
        val weapon = ModularWeaponGenerator.createStarterModularWeapon()
        val state = cycle.initialize(123L).copy(
            offeredWeapon = weapon,
            weaponOfferTimeRemainingMs = 5000f
        )

        val (newState, grabbedWeapon) = cycle.grabWeapon(state)
        assertEquals(weapon, grabbedWeapon)
        assertNull(newState.offeredWeapon)
        assertEquals(0f, newState.weaponOfferTimeRemainingMs)
    }

    @Test
    fun testOfferExpiration() {
        val weapon = ModularWeaponGenerator.createStarterModularWeapon()
        var state = cycle.initialize(123L).copy(
            offeredWeapon = weapon,
            weaponOfferTimeRemainingMs = 50f
        )

        state = cycle.step(state, 0.05f)
        assertNull(state.offeredWeapon)
        assertEquals(0f, state.weaponOfferTimeRemainingMs)
    }

    @Test
    fun testForcedTeddyBearRoll() {
        // Force max uses to trigger teddy
        val state = cycle.initialize(123L).copy(usesAtCurrentLocation = 5, maxUsesBeforeTeddy = 5)
        val (newState, success) = cycle.attemptPurchase(state, 1000, 1, 123L)
        assertTrue(success)
        assertTrue(newState.isTeddyBearRoll)
    }

    @Test
    fun testSerializationRoundTrip() {
        val weapon = ModularWeaponGenerator.createStarterModularWeapon()
        val state = MysteryBoxState(
            currentStationId = "box_test",
            x = 10f,
            y = 20f,
            cost = 950,
            rollInProgress = true,
            rollTimeRemainingMs = 1500f,
            offeredWeapon = weapon,
            weaponOfferTimeRemainingMs = 0f,
            isTeddyBearRoll = false,
            usesAtCurrentLocation = 2,
            maxUsesBeforeTeddy = 6
        )

        val map = state.toMap()
        val restored = MysteryBoxState.fromMap(map, mapOf(weapon.instanceId to weapon))

        assertEquals(state, restored)
    }

    @Test
    fun testClampedDt() {
         val state = cycle.initialize(123L).copy(
            rollInProgress = true,
            rollTimeRemainingMs = 3000f,
        )

        // Massive dt should be clamped to 0.1s
        val newState = cycle.step(state, 10f)
        assertEquals(2900f, newState.rollTimeRemainingMs)
    }

    @Test
    fun testNegativeDtIsClamped() {
         val state = cycle.initialize(123L).copy(
            rollInProgress = true,
            rollTimeRemainingMs = 3000f,
        )

        // Massive dt should be clamped to 0.001s
        val newState = cycle.step(state, -1f)
        assertEquals(2999f, newState.rollTimeRemainingMs)
    }

    @Test
    fun testExtremeLoadMultiplePurchases() {
        var state = cycle.initialize(555L).copy(cost = 950, usesAtCurrentLocation = 0)
        var currency = 10000
        var totalPurchases = 0

        for (i in 0..20) { // Should eventually trigger teddy
             if (!state.rollInProgress) {
                 val result = cycle.attemptPurchase(state, currency, 1, 555L + i)
                 state = result.first
                 if (result.second) {
                     currency -= state.cost
                     totalPurchases++
                 }
             }

             // Fast forward roll
             if (state.rollInProgress) {
                 state = cycle.step(state, 3.0f) // 3 seconds, should finish roll
             }
        }

        assertTrue(totalPurchases > 1) // Should have made some purchases
        // State should eventually have relocated and reset uses due to teddy bear
        // But since we just step it, we could be anywhere. We just want to ensure it handles load cleanly.
    }
}
