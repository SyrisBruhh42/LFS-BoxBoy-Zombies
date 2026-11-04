package com.example.game.ai.director.modules

import com.example.game.LootRarity
import com.example.game.economy.SingleCurrencyVendingEngine
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand
import com.example.game.economy.VendingStockSnapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local AI Vending Stock Synthesizer
 * Uses JSON from Ollama to modify or replace Dr. Zed and Marcus items of the day,
 * appending AI-generated affixes or stat variants.
 */
object OllamaVendingStockSynthesizer {

    fun buildOllamaVendingPrompt(wave: Int, seed: Long): String {
        return """
            You are the Vending Machine AI Director for Unified BoxBoys.
            Generate a JSON object for Wave $wave (seed $seed) with an array "overrides":
            Each override should have:
            - "targetOfferId": e.g., "zed_shield_iotd_w$wave" or "marcus_iotd_wpn_w$wave"
            - "aiFlavorTitle": A creative, custom title for the Item of the Day
            - "aiFlavorSubtitle": A custom flavor text or stats summary
            - "costMultiplier": A float multiplier for the base cost (0.8 to 1.5)
        """.trimIndent()
    }

    fun applyOllamaOverrides(
        baseSnapshot: VendingStockSnapshot,
        rawOllamaJson: String?
    ): VendingStockSnapshot {
        if (rawOllamaJson.isNullOrBlank()) {
            return baseSnapshot
        }

        try {
            val obj = JSONObject(rawOllamaJson)
            val overrides = obj.optJSONArray("overrides") ?: return baseSnapshot

            val updatedOffers = baseSnapshot.offers.map { offer ->
                var finalOffer = offer
                for (i in 0 until overrides.length()) {
                    val override = overrides.optJSONObject(i)
                    if (override != null && override.optString("targetOfferId") == offer.offerId) {
                        val aiTitle = override.optString("aiFlavorTitle", offer.title)
                        val aiSubtitle = override.optString("aiFlavorSubtitle", offer.subtitle)
                        val costMult = override.optDouble("costMultiplier", 1.0).toFloat().coerceIn(0.5f, 2.0f)
                        
                        finalOffer = finalOffer.copy(
                            title = aiTitle,
                            subtitle = aiSubtitle,
                            costCurrency = (offer.costCurrency * costMult).toInt()
                        )
                        break
                    }
                }
                finalOffer
            }

            return baseSnapshot.copy(offers = updatedOffers)
        } catch (_: Exception) {
            return baseSnapshot
        }
    }
}
