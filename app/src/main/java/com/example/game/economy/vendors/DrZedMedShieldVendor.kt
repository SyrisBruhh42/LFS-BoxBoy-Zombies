package com.example.game.economy.vendors

import com.example.game.BoxBoyGameEngine
import com.example.game.GameSessionState
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand

object DrZedMedShieldVendor {
    
    sealed class PurchaseResult {
        data class Success(val newState: GameSessionState, val message: String) : PurchaseResult()
        data class Error(val reason: String) : PurchaseResult()
    }

    fun processPurchase(
        state: GameSessionState,
        offer: VendingCatalogOffer
    ): PurchaseResult {
        if (offer.brand != VendingMachineBrand.ZED_MED_AND_SHIELDS) {
            return PurchaseResult.Error("Vendor Brand Mismatch: Required ZED_MED_AND_SHIELDS")
        }
        
        if (state.points < offer.costCurrency) {
            return PurchaseResult.Error("Insufficient Currency Points")
        }

        return if (offer.isItemOfTheDay && offer.shieldArmorPayload != null) {
            val armorPiece = offer.shieldArmorPayload
            
            // Add the piece to the ArmorRigState
            val updatedSlots = state.armorRig.equippedSlots.toMutableMap()
            updatedSlots[armorPiece.slot] = armorPiece
            val newArmorRig = state.armorRig.copy(equippedSlots = updatedSlots)
            
            val oldBonus = state.armorRig.totalBonusMaxShield
            val newBonus = newArmorRig.totalBonusMaxShield
            val deltaShield = newBonus - oldBonus
            val newMaxShield = state.maxShield + deltaShield
            
            // Purchasing shield restores full shields up to the new max
            val newPoints = state.points - offer.costCurrency
            
            PurchaseResult.Success(
                newState = state.copy(
                    points = newPoints,
                    armorRig = newArmorRig,
                    maxShield = newMaxShield,
                    shield = newMaxShield
                ),
                message = "Purchased Item of the Day: ${armorPiece.displayName}"
            )
        } else if (offer.offerId.startsWith("zed_insta_heal")) {
            // Instant Health Restore
            if (state.hp >= state.maxHp) {
                return PurchaseResult.Error("HP is already full")
            }
            
            val newPoints = state.points - offer.costCurrency
            PurchaseResult.Success(
                newState = state.copy(
                    points = newPoints,
                    hp = state.maxHp
                ),
                message = "Purchased Insta-Vial Full Health Restore"
            )
        } else {
            // Assume Shield Patch
            if (state.shield >= state.maxShield) {
                return PurchaseResult.Error("Shield is already at maximum capacity")
            }

            val newPoints = state.points - offer.costCurrency
            PurchaseResult.Success(
                newState = state.copy(
                    points = newPoints,
                    shield = state.maxShield
                ),
                message = "Purchased Shield Generator Patch"
            )
        }
    }
}


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [DrZedMedShieldVendor].
 */
data class DrZedMedShieldVendorTelemetryReceipt(
    val moduleName: String = "DrZedMedShieldVendor",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeDrZedMedShieldVendorTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): DrZedMedShieldVendorTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "DrZedMedShieldVendor") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return DrZedMedShieldVendorTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateDrZedMedShieldVendorLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "DrZedMedShieldVendor") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
