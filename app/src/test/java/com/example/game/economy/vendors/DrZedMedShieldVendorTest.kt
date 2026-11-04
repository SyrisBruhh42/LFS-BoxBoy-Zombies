package com.example.game.economy.vendors

import com.example.game.ArmorSlot
import com.example.game.GameSessionState
import com.example.game.LootRarity
import com.example.game.RpgArmorGenerator
import com.example.game.economy.SingleCurrencyVendingEngine
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrZedMedShieldVendorTest {

    @Test
    fun `test Insta-Vial Full Health Restore purchase`() {
        val initialState = GameSessionState(points = 1000, hp = 10, maxHp = 100)
        
        val stock = SingleCurrencyVendingEngine.generateWaveVendingStock(wave = 1, seed = 12345L)
        val offer = stock.offers.first { it.offerId.startsWith("zed_insta_heal") }

        val result = DrZedMedShieldVendor.processPurchase(initialState, offer)
        
        assertTrue("Purchase should be successful", result is DrZedMedShieldVendor.PurchaseResult.Success)
        val success = result as DrZedMedShieldVendor.PurchaseResult.Success
        
        assertEquals("HP should be restored to maxHp", 100, success.newState.hp)
        assertEquals("Points should be deducted", 1000 - offer.costCurrency, success.newState.points)
    }

    @Test
    fun `test Insta-Vial Full Health Restore fails when HP is already full`() {
        val initialState = GameSessionState(points = 1000, hp = 100, maxHp = 100)
        
        val stock = SingleCurrencyVendingEngine.generateWaveVendingStock(wave = 1, seed = 12345L)
        val offer = stock.offers.first { it.offerId.startsWith("zed_insta_heal") }

        val result = DrZedMedShieldVendor.processPurchase(initialState, offer)
        
        assertTrue("Purchase should fail", result is DrZedMedShieldVendor.PurchaseResult.Error)
        val error = result as DrZedMedShieldVendor.PurchaseResult.Error
        assertEquals("HP is already full", error.reason)
    }

    @Test
    fun `test Item of the Day Shield purchase`() {
        val initialState = GameSessionState(points = 5000, shield = 10, maxShield = 116)
        
        val stock = SingleCurrencyVendingEngine.generateWaveVendingStock(wave = 1, seed = 12345L)
        val offer = stock.offers.first { it.offerId.startsWith("zed_shield_iotd") }

        val result = DrZedMedShieldVendor.processPurchase(initialState, offer)
        
        assertTrue("Purchase should be successful", result is DrZedMedShieldVendor.PurchaseResult.Success)
        val success = result as DrZedMedShieldVendor.PurchaseResult.Success
        
        val deltaShield = success.newState.armorRig.totalBonusMaxShield - initialState.armorRig.totalBonusMaxShield
        assertEquals("Points should be deducted", 5000 - offer.costCurrency, success.newState.points)
        assertEquals("Max shield should be updated", 116 + deltaShield, success.newState.maxShield)
        assertEquals("Shield should be replenished to max", 116 + deltaShield, success.newState.shield)
        assertTrue("ArmorRig should contain the new shield", success.newState.armorRig.equippedSlots.containsValue(offer.shieldArmorPayload))
    }

    @Test
    fun `test purchase fails due to insufficient points`() {
        val initialState = GameSessionState(points = 10, hp = 10, maxHp = 100) // Need more than 10 points
        
        val stock = SingleCurrencyVendingEngine.generateWaveVendingStock(wave = 1, seed = 12345L)
        val offer = stock.offers.first { it.offerId.startsWith("zed_insta_heal") }

        val result = DrZedMedShieldVendor.processPurchase(initialState, offer)
        
        assertTrue("Purchase should fail", result is DrZedMedShieldVendor.PurchaseResult.Error)
        val error = result as DrZedMedShieldVendor.PurchaseResult.Error
        assertEquals("Insufficient Currency Points", error.reason)
    }
    
    @Test
    fun `test purchase fails on vendor mismatch`() {
        val initialState = GameSessionState(points = 5000)
        
        val stock = SingleCurrencyVendingEngine.generateWaveVendingStock(wave = 1, seed = 12345L)
        val offer = stock.offers.first { it.brand == VendingMachineBrand.MARCUS_WEAPONS_OF_THE_DAY }

        val result = DrZedMedShieldVendor.processPurchase(initialState, offer)
        
        assertTrue("Purchase should fail", result is DrZedMedShieldVendor.PurchaseResult.Error)
        val error = result as DrZedMedShieldVendor.PurchaseResult.Error
        assertEquals("Vendor Brand Mismatch: Required ZED_MED_AND_SHIELDS", error.reason)
    }

    @Test
    fun `test Shield Generator Patch purchase`() {
        val initialState = GameSessionState(points = 1000, shield = 10, maxShield = 116)
        
        val offer = VendingCatalogOffer(
            offerId = "zed_shield_patch",
            brand = VendingMachineBrand.ZED_MED_AND_SHIELDS,
            title = "Shield Patch",
            subtitle = "Restore Shield",
            costCurrency = 200
        )

        val result = DrZedMedShieldVendor.processPurchase(initialState, offer)
        
        assertTrue("Purchase should be successful", result is DrZedMedShieldVendor.PurchaseResult.Success)
        val success = result as DrZedMedShieldVendor.PurchaseResult.Success
        
        assertEquals("Shield should be restored to maxShield", 116, success.newState.shield)
        assertEquals("Points should be deducted", 1000 - offer.costCurrency, success.newState.points)
    }
}
