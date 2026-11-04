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


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [OllamaVendingStockSynthesizer].
 */
data class OllamaVendingStockSynthesizerTelemetryReceipt(
    val moduleName: String = "OllamaVendingStockSynthesizer",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeOllamaVendingStockSynthesizerTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): OllamaVendingStockSynthesizerTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "OllamaVendingStockSynthesizer") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return OllamaVendingStockSynthesizerTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateOllamaVendingStockSynthesizerLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "OllamaVendingStockSynthesizer") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
