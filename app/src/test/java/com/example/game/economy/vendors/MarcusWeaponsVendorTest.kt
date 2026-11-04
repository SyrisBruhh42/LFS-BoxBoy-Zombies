package com.example.game.economy.vendors

import com.example.game.LootRarity
import com.example.game.economy.VendingMachineBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarcusWeaponsVendorTest {

    @Test
    fun testGenerateOffersCreatesStandardAndIotd() {
        val wave = 5
        val stepSeed = 12345L
        
        val offers = MarcusWeaponsVendor.generateOffers(wave, stepSeed)
        
        assertEquals("Should generate exactly 2 offers", 2, offers.size)
        
        val standardOffer = offers[0]
        assertEquals("marcus_std_wpn_w$wave", standardOffer.offerId)
        assertEquals(VendingMachineBrand.MARCUS_WEAPONS_OF_THE_DAY, standardOffer.brand)
        assertFalse("Standard offer should NOT be item of the day", standardOffer.isItemOfTheDay)
        assertNotNull("Weapon payload should not be null", standardOffer.weaponPayload)
        
        val iotdOffer = offers[1]
        assertEquals("marcus_iotd_wpn_w$wave", iotdOffer.offerId)
        assertEquals(VendingMachineBrand.MARCUS_WEAPONS_OF_THE_DAY, iotdOffer.brand)
        assertTrue("IOTD offer SHOULD be item of the day", iotdOffer.isItemOfTheDay)
        assertNotNull("IOTD weapon payload should not be null", iotdOffer.weaponPayload)
        assertTrue("IOTD title should start with prefix", iotdOffer.title.startsWith("[ITEM OF THE DAY]"))
    }

    @Test
    fun testIotdHasMinimumRareRarity() {
        val wave = 1 // Early wave to test guaranteed rarity
        val stepSeed = 9999L
        
        val offers = MarcusWeaponsVendor.generateOffers(wave, stepSeed)
        val iotdOffer = offers.find { it.isItemOfTheDay }
        
        assertNotNull(iotdOffer)
        val rarityOrdinal = iotdOffer!!.rarity.ordinal
        val rareOrdinal = LootRarity.RARE.ordinal
        
        assertTrue("IOTD should be at least RARE rarity", rarityOrdinal >= rareOrdinal)
    }

    @Test
    fun testCostsScaleWithWaveNumber() {
        val wave1 = 1
        val wave10 = 10
        val stepSeed = 5555L
        
        val offersWave1 = MarcusWeaponsVendor.generateOffers(wave1, stepSeed)
        val offersWave10 = MarcusWeaponsVendor.generateOffers(wave10, stepSeed)
        
        val standardCostWave1 = offersWave1.find { !it.isItemOfTheDay }?.costCurrency ?: 0
        val standardCostWave10 = offersWave10.find { !it.isItemOfTheDay }?.costCurrency ?: 0
        
        assertTrue("Wave 10 standard cost should be higher than Wave 1 standard cost", standardCostWave10 > standardCostWave1)
        
        val iotdCostWave1 = offersWave1.find { it.isItemOfTheDay }?.costCurrency ?: 0
        val iotdCostWave10 = offersWave10.find { it.isItemOfTheDay }?.costCurrency ?: 0
        
        assertTrue("Wave 10 IOTD cost should be higher than Wave 1 IOTD cost", iotdCostWave10 > iotdCostWave1)
    }
}
