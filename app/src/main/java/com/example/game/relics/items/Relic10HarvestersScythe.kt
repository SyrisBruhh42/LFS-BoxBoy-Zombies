package com.example.game.relics.items

import androidx.annotation.Keep
import com.example.game.relics.PassiveRelicKind

/**
 * Track 6: Risk of Rain 2 Rare-Drop-Only Passive Skill Relic.
 * Relic10HarvestersScythe - Bio-Harvester Scythe
 * Effect: +5% critical strike chance (base), critical hits heal 8 HP (+4 HP per stack).
 */
@Keep
data class HarvestersScytheState(
    val stackCount: Int
) {
    init {
        require(stackCount >= 0) { "stackCount must be >= 0" }
    }

    val critChanceBonus: Float
        get() = if (stackCount > 0) 0.05f else 0.0f

    val healAmountOnCrit: Int
        get() = if (stackCount > 0) 8 + (stackCount - 1) * 4 else 0

    fun toMap(): Map<String, Any> = mapOf(
        "stackCount" to stackCount
    )

    companion object {
        fun fromMap(map: Map<String, Any>): HarvestersScytheState {
            val count = (map["stackCount"] as? Number)?.toInt() ?: 0
            return HarvestersScytheState(count.coerceAtLeast(0))
        }
    }
}

object Relic10HarvestersScytheEngine {
    
    /**
     * Invariant: canBePurchasedAtVending = false (drop-only).
     */
    val CAN_BE_PURCHASED_AT_VENDING: Boolean = false
    
    val RELIC_KIND: PassiveRelicKind = PassiveRelicKind.HARVESTERS_SCYTHE

    /**
     * Processes a critical hit event, calculating the HP healed based on the scythe stacks.
     * Returns the amount to heal, which is clamped by the caller to maxHp.
     * Guaranteed zero per-frame heap churn.
     */
    fun processCritHeal(state: HarvestersScytheState): Int {
        if (state.stackCount <= 0) return 0
        return state.healAmountOnCrit
    }

    /**
     * Calculates the FNV-1a checksum of the state.
     */
    fun computeChecksum(state: HarvestersScytheState): Long {
        var hash = -2128831035142426939L // FNV offset basis
        val bytes = byteArrayOf(
            (state.stackCount shr 24).toByte(),
            (state.stackCount shr 16).toByte(),
            (state.stackCount shr 8).toByte(),
            state.stackCount.toByte()
        )
        for (b in bytes) {
            hash = hash xor b.toLong()
            hash = hash * 1099511628211L // FNV prime
        }
        return hash
    }
}


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [Relic10HarvestersScythe].
 */
data class Relic10HarvestersScytheTelemetryReceipt(
    val moduleName: String = "Relic10HarvestersScythe",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeRelic10HarvestersScytheTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): Relic10HarvestersScytheTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "Relic10HarvestersScythe") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return Relic10HarvestersScytheTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateRelic10HarvestersScytheLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic10HarvestersScythe") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
