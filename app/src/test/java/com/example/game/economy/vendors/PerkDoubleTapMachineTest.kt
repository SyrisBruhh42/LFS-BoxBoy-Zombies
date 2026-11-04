package com.example.game.economy.vendors

import com.example.data.PerkType
import com.example.game.GameSessionState
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerkDoubleTapMachineTest {

    @Test
    fun `test purchase success`() {
        val initialState = GameSessionState(points = 2500, activePerks = emptySet())
        val offer = VendingCatalogOffer(
            offerId = "perk_double_tap",
            brand = VendingMachineBrand.PERK_A_COLA_FOUNTAIN,
            title = PerkType.DOUBLE_TAP.displayName,
            subtitle = PerkType.DOUBLE_TAP.description,
            costCurrency = PerkType.DOUBLE_TAP.cost,
            perkPayload = PerkType.DOUBLE_TAP
        )

        val result = PerkDoubleTapMachine.processPurchase(initialState, offer)

        assertTrue(result is PerkDoubleTapMachine.PurchaseResult.Success)
        val success = result as PerkDoubleTapMachine.PurchaseResult.Success
        assertEquals(500, success.newState.points) // 2500 - 2000
        assertTrue(success.newState.activePerks.contains(PerkType.DOUBLE_TAP))
    }

    @Test
    fun `test insufficient funds`() {
        val initialState = GameSessionState(points = 1500, activePerks = emptySet())
        val offer = VendingCatalogOffer(
            offerId = "perk_double_tap",
            brand = VendingMachineBrand.PERK_A_COLA_FOUNTAIN,
            title = PerkType.DOUBLE_TAP.displayName,
            subtitle = PerkType.DOUBLE_TAP.description,
            costCurrency = PerkType.DOUBLE_TAP.cost,
            perkPayload = PerkType.DOUBLE_TAP
        )

        val result = PerkDoubleTapMachine.processPurchase(initialState, offer)

        assertTrue(result is PerkDoubleTapMachine.PurchaseResult.Error)
        assertEquals("Insufficient Currency Points", (result as PerkDoubleTapMachine.PurchaseResult.Error).reason)
    }

    @Test
    fun `test already active`() {
        val initialState = GameSessionState(points = 2500, activePerks = setOf(PerkType.DOUBLE_TAP))
        val offer = VendingCatalogOffer(
            offerId = "perk_double_tap",
            brand = VendingMachineBrand.PERK_A_COLA_FOUNTAIN,
            title = PerkType.DOUBLE_TAP.displayName,
            subtitle = PerkType.DOUBLE_TAP.description,
            costCurrency = PerkType.DOUBLE_TAP.cost,
            perkPayload = PerkType.DOUBLE_TAP
        )

        val result = PerkDoubleTapMachine.processPurchase(initialState, offer)

        assertTrue(result is PerkDoubleTapMachine.PurchaseResult.Error)
        assertEquals("Double Tap II is already active", (result as PerkDoubleTapMachine.PurchaseResult.Error).reason)
    }

    @Test
    fun `test brand mismatch`() {
        val initialState = GameSessionState(points = 2500, activePerks = emptySet())
        val offer = VendingCatalogOffer(
            offerId = "perk_double_tap",
            brand = VendingMachineBrand.MARCUS_WEAPONS_OF_THE_DAY,
            title = PerkType.DOUBLE_TAP.displayName,
            subtitle = PerkType.DOUBLE_TAP.description,
            costCurrency = PerkType.DOUBLE_TAP.cost,
            perkPayload = PerkType.DOUBLE_TAP
        )

        val result = PerkDoubleTapMachine.processPurchase(initialState, offer)

        assertTrue(result is PerkDoubleTapMachine.PurchaseResult.Error)
        assertEquals("Vendor Brand Mismatch: Required PERK_A_COLA_FOUNTAIN", (result as PerkDoubleTapMachine.PurchaseResult.Error).reason)
    }

    @Test
    fun `test payload mismatch`() {
        val initialState = GameSessionState(points = 2500, activePerks = emptySet())
        val offer = VendingCatalogOffer(
            offerId = "perk_juggernog",
            brand = VendingMachineBrand.PERK_A_COLA_FOUNTAIN,
            title = PerkType.JUGGERNOG.displayName,
            subtitle = PerkType.JUGGERNOG.description,
            costCurrency = PerkType.JUGGERNOG.cost,
            perkPayload = PerkType.JUGGERNOG
        )

        val result = PerkDoubleTapMachine.processPurchase(initialState, offer)

        assertTrue(result is PerkDoubleTapMachine.PurchaseResult.Error)
        assertEquals("Offer payload is not Double Tap II", (result as PerkDoubleTapMachine.PurchaseResult.Error).reason)
    }

    @Test
    fun `test computeEnhancedFireCooldown`() {
        // 1000 / 1.35 = 740.74 -> 741
        assertEquals(741L, PerkDoubleTapMachine.computeEnhancedFireCooldown(1000L))
        // 500 / 1.35 = 370.37 -> 370
        assertEquals(370L, PerkDoubleTapMachine.computeEnhancedFireCooldown(500L))
        // Zero or negative
        assertEquals(0L, PerkDoubleTapMachine.computeEnhancedFireCooldown(0L))
        assertEquals(0L, PerkDoubleTapMachine.computeEnhancedFireCooldown(-500L))
    }

    @Test
    fun `test getBonusProjectileCount`() {
        assertEquals(2, PerkDoubleTapMachine.getBonusProjectileCount(1))
        assertEquals(6, PerkDoubleTapMachine.getBonusProjectileCount(5))
        assertEquals(0, PerkDoubleTapMachine.getBonusProjectileCount(0))
        assertEquals(0, PerkDoubleTapMachine.getBonusProjectileCount(-1))
    }

    @Test
    fun `test deterministic computeEnhancedFireCooldown`() {
        val baseMs = 125L
        val res1 = PerkDoubleTapMachine.computeEnhancedFireCooldown(baseMs)
        val res2 = PerkDoubleTapMachine.computeEnhancedFireCooldown(baseMs)
        assertEquals(res1, res2)
        assertEquals(93L, res1) // 125 / 1.35 = 92.59 -> 93
    }

    @Test
    fun `test deterministic getBonusProjectileCount`() {
        val res1 = PerkDoubleTapMachine.getBonusProjectileCount(8)
        val res2 = PerkDoubleTapMachine.getBonusProjectileCount(8)
        assertEquals(res1, res2)
        assertEquals(9, res1)
    }

    @Test
    fun `test computeEnhancedFireCooldown max input`() {
        val res = PerkDoubleTapMachine.computeEnhancedFireCooldown(Long.MAX_VALUE)
        // Note: Long.MAX_VALUE / 1.35f might lose precision due to float conversion, but it shouldn't crash
        assertTrue(res > 0)
    }

    @Test
    fun `test exact 2000 cost leaves zero points`() {
        val initialState = GameSessionState(points = 2000, activePerks = emptySet())
        val offer = VendingCatalogOffer(
            offerId = "perk_double_tap",
            brand = VendingMachineBrand.PERK_A_COLA_FOUNTAIN,
            title = PerkType.DOUBLE_TAP.displayName,
            subtitle = PerkType.DOUBLE_TAP.description,
            costCurrency = PerkType.DOUBLE_TAP.cost,
            perkPayload = PerkType.DOUBLE_TAP
        )

        val result = PerkDoubleTapMachine.processPurchase(initialState, offer)

        assertTrue(result is PerkDoubleTapMachine.PurchaseResult.Success)
        val success = result as PerkDoubleTapMachine.PurchaseResult.Success
        assertEquals(0, success.newState.points)
        assertTrue(success.newState.activePerks.contains(PerkType.DOUBLE_TAP))
    }

    @Test
    fun `test multiple perks state preservation`() {
        val initialState = GameSessionState(points = 2500, activePerks = setOf(PerkType.JUGGERNOG, PerkType.SPEED_COLA))
        val offer = VendingCatalogOffer(
            offerId = "perk_double_tap",
            brand = VendingMachineBrand.PERK_A_COLA_FOUNTAIN,
            title = PerkType.DOUBLE_TAP.displayName,
            subtitle = PerkType.DOUBLE_TAP.description,
            costCurrency = PerkType.DOUBLE_TAP.cost,
            perkPayload = PerkType.DOUBLE_TAP
        )

        val result = PerkDoubleTapMachine.processPurchase(initialState, offer)

        assertTrue(result is PerkDoubleTapMachine.PurchaseResult.Success)
        val success = result as PerkDoubleTapMachine.PurchaseResult.Success
        assertEquals(500, success.newState.points)
        assertTrue(success.newState.activePerks.contains(PerkType.DOUBLE_TAP))
        assertTrue(success.newState.activePerks.contains(PerkType.JUGGERNOG))
        assertTrue(success.newState.activePerks.contains(PerkType.SPEED_COLA))
        assertEquals(3, success.newState.activePerks.size)
    }
}
