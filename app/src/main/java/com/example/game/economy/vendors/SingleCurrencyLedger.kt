package com.example.game.economy.vendors

import com.example.game.contracts.Fnv1a64Hasher
import com.example.game.economy.SingleCurrencyVendingEngine
import java.util.concurrent.atomic.AtomicInteger

data class LedgerTelemetrySnapshot(
    val balance: Int,
    val totalEarned: Int,
    val totalSpent: Int,
    val hitMarkersRecorded: Int,
    val killsRecorded: Int,
    val repairsRecorded: Int,
    val checksum: Long
)

/**
 * Atomic single-currency wallet (XP benched) tracking hits (+10), kills,
 * board repairs (+20), and purchases.
 */
class SingleCurrencyLedger(initialBalance: Int = 0) {
    private val balance = AtomicInteger(initialBalance)
    private val totalEarned = AtomicInteger(initialBalance.coerceAtLeast(0))
    private val totalSpent = AtomicInteger(0)
    private val hitMarkersRecorded = AtomicInteger(0)
    private val killsRecorded = AtomicInteger(0)
    private val repairsRecorded = AtomicInteger(0)

    fun getBalance(): Int = balance.get()

    /**
     * Records a hit marker, granting the default currency reward.
     */
    fun recordHit(): Int {
        hitMarkersRecorded.incrementAndGet()
        totalEarned.addAndGet(SingleCurrencyVendingEngine.HIT_MARKER_CURRENCY_REWARD)
        return balance.addAndGet(SingleCurrencyVendingEngine.HIT_MARKER_CURRENCY_REWARD)
    }

    /**
     * Records a kill, granting a specific reward amount (varies by kill type, e.g., melee vs headshot).
     */
    fun recordKill(reward: Int): Int {
        require(reward >= 0) { "Reward must be non-negative" }
        killsRecorded.incrementAndGet()
        totalEarned.addAndGet(reward)
        return balance.addAndGet(reward)
    }

    /**
     * Records a board repair on a barricade.
     */
    fun recordBoardRepair(): Int {
        repairsRecorded.incrementAndGet()
        totalEarned.addAndGet(SingleCurrencyVendingEngine.BOARD_REPAIR_CURRENCY_REWARD)
        return balance.addAndGet(SingleCurrencyVendingEngine.BOARD_REPAIR_CURRENCY_REWARD)
    }

    /**
     * Checks if the ledger has enough currency to afford a cost.
     */
    fun canAfford(cost: Int): Boolean {
        require(cost >= 0) { "Cost must be non-negative" }
        return balance.get() >= cost
    }

    /**
     * Attempts to purchase an item, deducting the cost atomically.
     * Returns true if successful, false if insufficient funds.
     */
    fun attemptPurchase(cost: Int): Boolean {
        require(cost >= 0) { "Cost must be non-negative" }
        var current: Int
        do {
            current = balance.get()
            if (current < cost) {
                return false
            }
        } while (!balance.compareAndSet(current, current - cost))
        totalSpent.addAndGet(cost)
        return true
    }

    fun snapshot(): LedgerTelemetrySnapshot {
        val b = balance.get()
        val e = totalEarned.get()
        val s = totalSpent.get()
        val hHits = hitMarkersRecorded.get()
        val k = killsRecorded.get()
        val r = repairsRecorded.get()
        var hash = Fnv1a64Hasher.initial()
        hash = Fnv1a64Hasher.mixInt(hash, b)
        hash = Fnv1a64Hasher.mixInt(hash, e)
        hash = Fnv1a64Hasher.mixInt(hash, s)
        hash = Fnv1a64Hasher.mixInt(hash, hHits)
        hash = Fnv1a64Hasher.mixInt(hash, k)
        hash = Fnv1a64Hasher.mixInt(hash, r)
        return LedgerTelemetrySnapshot(
            balance = b,
            totalEarned = e,
            totalSpent = s,
            hitMarkersRecorded = hHits,
            killsRecorded = k,
            repairsRecorded = r,
            checksum = hash
        )
    }
}


fun evaluateSingleCurrencyLedgerLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "SingleCurrencyLedger") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
