package com.example.game.ai.director.modules

import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand
import com.example.game.economy.VendingStockSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.game.LootRarity

class OllamaVendingStockSynthesizerTest {

    @Test
    fun `test build prompt contains wave and seed`() {
        val prompt = OllamaVendingStockSynthesizer.buildOllamaVendingPrompt(5, 12345L)
        assertTrue(prompt.contains("Wave 5"))
        assertTrue(prompt.contains("12345"))
    }

    private fun mockBaseSnapshot(): VendingStockSnapshot {
        return VendingStockSnapshot(
            waveNumber = 3,
            refreshCountdownSec = 180,
            offers = listOf(
                VendingCatalogOffer(
                    offerId = "zed_shield_iotd_w3",
                    brand = VendingMachineBrand.ZED_MED_AND_SHIELDS,
                    title = "Base Shield",
                    subtitle = "Base Shield Subtitle",
                    costCurrency = 1000,
                    isItemOfTheDay = true,
                    rarity = LootRarity.RARE
                ),
                VendingCatalogOffer(
                    offerId = "marcus_iotd_wpn_w3",
                    brand = VendingMachineBrand.MARCUS_WEAPONS_OF_THE_DAY,
                    title = "Base Gun",
                    subtitle = "Base Gun Subtitle",
                    costCurrency = 1500,
                    isItemOfTheDay = true,
                    rarity = LootRarity.RARE
                )
            )
        )
    }

    @Test
    fun `test apply overrides with invalid json falls back to base snapshot`() {
        val baseSnapshot = mockBaseSnapshot()
        val result = OllamaVendingStockSynthesizer.applyOllamaOverrides(baseSnapshot, "{ invalid_json }")
        assertEquals(baseSnapshot, result)
    }

    @Test
    fun `test apply overrides modifies target items`() {
        val baseSnapshot = mockBaseSnapshot()
        
        val zedOfferId = "zed_shield_iotd_w3"
        val marcusOfferId = "marcus_iotd_wpn_w3"
        
        val json = """
            {
                "overrides": [
                    {
                        "targetOfferId": "$zedOfferId",
                        "aiFlavorTitle": "Zed's AI Shield",
                        "aiFlavorSubtitle": "AI Powered Defense",
                        "costMultiplier": 1.5
                    },
                    {
                        "targetOfferId": "$marcusOfferId",
                        "aiFlavorTitle": "Marcus AI Gun",
                        "aiFlavorSubtitle": "AI Powered Offense",
                        "costMultiplier": 0.8
                    }
                ]
            }
        """.trimIndent()
        
        val result = OllamaVendingStockSynthesizer.applyOllamaOverrides(baseSnapshot, json)
        
        val zedOffer = result.offers.find { it.offerId == zedOfferId }!!
        assertEquals("Zed's AI Shield", zedOffer.title)
        assertEquals("AI Powered Defense", zedOffer.subtitle)
        assertEquals(1500, zedOffer.costCurrency) // 1000 * 1.5
        
        val marcusOffer = result.offers.find { it.offerId == marcusOfferId }!!
        assertEquals("Marcus AI Gun", marcusOffer.title)
        assertEquals("AI Powered Offense", marcusOffer.subtitle)
        assertEquals(1200, marcusOffer.costCurrency) // 1500 * 0.8
    }
}
