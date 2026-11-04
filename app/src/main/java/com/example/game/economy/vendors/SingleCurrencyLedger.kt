package com.example.game.economy.vendors

import com.example.game.economy.SingleCurrencyVendingEngine
import java.util.concurrent.atomic.AtomicInteger

/**
 * Atomic single-currency wallet (XP benched) tracking hits (+10), kills,
 * board repairs (+20), and purchases.
 */
class SingleCurrencyLedger(initialBalance: Int = 0) {
    private val balance = AtomicInteger(initialBalance)

    fun getBalance(): Int = balance.get()

    /**
     * Records a hit marker, granting the default currency reward.
     */
    fun recordHit(): Int {
        return balance.addAndGet(SingleCurrencyVendingEngine.HIT_MARKER_CURRENCY_REWARD)
    }

    /**
     * Records a kill, granting a specific reward amount (varies by kill type, e.g., melee vs headshot).
     */
    fun recordKill(reward: Int): Int {
        require(reward >= 0) { "Reward must be non-negative" }
        return balance.addAndGet(reward)
    }

    /**
     * Records a board repair on a barricade.
     */
    fun recordBoardRepair(): Int {
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
        return true
    }
}
