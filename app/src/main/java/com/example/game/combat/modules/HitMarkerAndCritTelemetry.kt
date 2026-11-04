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


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [HitMarkerAndCritTelemetry].
 */
data class HitMarkerAndCritTelemetryTelemetryReceipt(
    val moduleName: String = "HitMarkerAndCritTelemetry",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeHitMarkerAndCritTelemetryTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): HitMarkerAndCritTelemetryTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "HitMarkerAndCritTelemetry") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return HitMarkerAndCritTelemetryTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateHitMarkerAndCritTelemetryLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "HitMarkerAndCritTelemetry") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
