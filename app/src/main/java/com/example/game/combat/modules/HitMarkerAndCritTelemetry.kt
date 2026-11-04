package com.example.game.combat.modules

import com.example.game.combat.BoxheadComboState
import com.example.game.economy.SingleCurrencyVendingEngine

enum class DamageNumberType {
    STANDARD,
    CRITICAL,
    ELEMENTAL
}

data class FloatingDamageNumber(
    val damageAmount: Int,
    val type: DamageNumberType,
    val startX: Float,
    val startY: Float,
    val currentX: Float = startX,
    val currentY: Float = startY,
    val lifetimeRemainingMs: Long = 1000L
)

data class HitMarkerResult(
    val currencyAwarded: Int,
    val activeNumbers: List<FloatingDamageNumber>
)

object HitMarkerAndCritTelemetry {
    const val MAX_FLOATING_NUMBERS = 50
    const val FLOAT_LIFETIME_MS = 1000L

    fun generateHitMarker(
        damageAmount: Int,
        type: DamageNumberType,
        x: Float,
        y: Float,
        comboState: BoxheadComboState,
        currentPool: List<FloatingDamageNumber>
    ): HitMarkerResult {
        val baseCurrency = SingleCurrencyVendingEngine.HIT_MARKER_CURRENCY_REWARD
        val awardedCurrency = (baseCurrency * comboState.currencyMultiplier).toInt()

        val newNumber = FloatingDamageNumber(
            damageAmount = damageAmount,
            type = type,
            startX = x,
            startY = y,
            lifetimeRemainingMs = FLOAT_LIFETIME_MS
        )

        val updatedPool = (currentPool + newNumber).takeLast(MAX_FLOATING_NUMBERS)

        return HitMarkerResult(
            currencyAwarded = awardedCurrency,
            activeNumbers = updatedPool
        )
    }

    fun stepFloatingNumbers(
        pool: List<FloatingDamageNumber>,
        deltaMs: Long
    ): List<FloatingDamageNumber> {
        return pool.mapNotNull {
            val newRemaining = it.lifetimeRemainingMs - deltaMs
            if (newRemaining > 0) {
                // Drift up slowly
                val driftProgress = (FLOAT_LIFETIME_MS - newRemaining) / FLOAT_LIFETIME_MS.toFloat()
                it.copy(
                    lifetimeRemainingMs = newRemaining,
                    currentY = it.startY - (driftProgress * 50f) 
                )
            } else {
                null
            }
        }
    }
}
