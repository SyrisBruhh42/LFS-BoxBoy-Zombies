package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.data.PerkType
import com.example.game.GameSessionState
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand
import kotlin.math.roundToInt

/**
 * BO2 Double Tap II station granting +35% fire rate and bonus projectile split.
 */
object PerkDoubleTapMachine {

    @Keep
    sealed class PurchaseResult {
        @Keep
        data class Success(
            val newState: GameSessionState,
            val message: String
        ) : PurchaseResult()

        @Keep
        data class Error(val reason: String) : PurchaseResult()
    }

    /**
     * Tries to purchase Double Tap II from the vendor offer.
     */
    fun processPurchase(
        state: GameSessionState,
        offer: VendingCatalogOffer
    ): PurchaseResult {
        if (offer.brand != VendingMachineBrand.PERK_A_COLA_FOUNTAIN) {
            return PurchaseResult.Error("Vendor Brand Mismatch: Required PERK_A_COLA_FOUNTAIN")
        }

        if (offer.perkPayload != PerkType.DOUBLE_TAP) {
            return PurchaseResult.Error("Offer payload is not Double Tap II")
        }

        if (state.activePerks.contains(PerkType.DOUBLE_TAP)) {
            return PurchaseResult.Error("Double Tap II is already active")
        }

        if (state.points < offer.costCurrency) {
            return PurchaseResult.Error("Insufficient Currency Points")
        }

        val newPoints = state.points - offer.costCurrency
        val newPerks = state.activePerks + PerkType.DOUBLE_TAP
        
        return PurchaseResult.Success(
            newState = state.copy(
                points = newPoints,
                activePerks = newPerks
            ),
            message = "Purchased Double Tap II: +35% fire rate & bonus split!"
        )
    }

    /**
     * Applies the +35% fire rate increase by calculating the new cooldown.
     * Original cooldown / 1.35.
     */
    fun computeEnhancedFireCooldown(baseCooldownMs: Long): Long {
        if (baseCooldownMs <= 0L) return 0L
        return (baseCooldownMs / 1.35f).roundToInt().toLong()
    }
    
    /**
     * Determines if an extra projectile is spawned (bonus projectile split).
     */
    fun getBonusProjectileCount(baseCount: Int): Int {
        if (baseCount <= 0) return 0
        return baseCount + 1
    }
}
