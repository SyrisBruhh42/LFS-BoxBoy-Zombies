package com.example.game.economy.vendors

import com.example.game.economy.VendingMachineBrand
import com.example.game.LootRarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorgueAmmoOrdnanceVendorTest {
    @Test
    fun testGenerateOffers() {
        val wave = 5
        val offers = TorgueAmmoOrdnanceVendor.generateOffers(wave)

        assertEquals(3, offers.size)

        // 1. Full Ammo Restock
        val ammoOffer = offers[0]
        assertEquals("torgue_max_ammo_w5", ammoOffer.offerId)
        assertEquals(VendingMachineBrand.TORGUE_AMMO_AND_ORDNANCE, ammoOffer.brand)
        assertEquals("Torgue Full Munitions Restock", ammoOffer.title)
        assertEquals("Refills Primary, Secondary & Explosive Barrel charges", ammoOffer.subtitle)
        assertEquals(660, ammoOffer.costCurrency) // 400 + 4 * 65
        assertEquals(LootRarity.RARE, ammoOffer.rarity)
        assertTrue(!ammoOffer.isItemOfTheDay)

        // 2. Explosive Barrel Packs
        val barrelOffer = offers[1]
        assertEquals("torgue_barrel_pack_w5", barrelOffer.offerId)
        assertEquals(VendingMachineBrand.TORGUE_AMMO_AND_ORDNANCE, barrelOffer.brand)
        assertEquals("Torgue Explosive Barrel Pack", barrelOffer.title)
        assertEquals("Deploys 3 high-yield explosive barrels", barrelOffer.subtitle)
        assertEquals(400, barrelOffer.costCurrency) // 200 + 4 * 50
        assertEquals(LootRarity.UNCOMMON, barrelOffer.rarity)
        assertTrue(!barrelOffer.isItemOfTheDay)

        // 3. Grenade Mod Item of the Day
        val iotdOffer = offers[2]
        assertEquals("torgue_grenade_iotd_w5", iotdOffer.offerId)
        assertEquals(VendingMachineBrand.TORGUE_AMMO_AND_ORDNANCE, iotdOffer.brand)
        assertEquals("[ITEM OF THE DAY] MIRV Grenade Mod", iotdOffer.title)
        assertEquals("Tactical Grenade Mod: splits into 5 smaller explosives", iotdOffer.subtitle)
        assertEquals(1280, iotdOffer.costCurrency) // 800 + 4 * 120
        assertEquals(LootRarity.EPIC, iotdOffer.rarity)
        assertTrue(iotdOffer.isItemOfTheDay)
    }

    @Test
    fun testGenerateOffersMaxCost() {
        val wave = 100
        val offers = TorgueAmmoOrdnanceVendor.generateOffers(wave)

        assertEquals(3, offers.size)

        // 1. Full Ammo Restock (Max 1800)
        assertEquals(1800, offers[0].costCurrency)

        // 2. Explosive Barrel Packs (Max 1200)
        assertEquals(1200, offers[1].costCurrency)

        // 3. Grenade Mod Item of the Day (Max 3000)
        assertEquals(3000, offers[2].costCurrency)
    }
}
