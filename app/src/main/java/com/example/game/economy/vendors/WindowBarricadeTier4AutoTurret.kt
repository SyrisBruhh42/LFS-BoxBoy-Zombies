package com.example.game.economy.vendors

import com.example.game.economy.BarricadeWindowTier

class WindowBarricadeTier4AutoTurret(
    var currentHp: Int = BarricadeWindowTier.TIER_4_AUTO_SENTRY_MOUNT.maxHp
) {
    val tier = BarricadeWindowTier.TIER_4_AUTO_SENTRY_MOUNT

    fun applyDamage(amount: Int) {
        currentHp = (currentHp - amount).coerceAtLeast(0)
    }

    fun isDestroyed(): Boolean = currentHp <= 0

    fun firePulseLaser(enemyDistance: Float): Int {
        if (isDestroyed()) return 0
        return if (enemyDistance <= 150f) tier.counterAttackDps else 0
    }

    fun repair(currencyAvailable: Int): Int {
        val missingHp = tier.maxHp - currentHp
        if (missingHp <= 0) return 0

        // Repair costs 2 currency per 1 HP up to available currency
        val costPerHp = 2
        val maxHpToRepair = currencyAvailable / costPerHp
        val hpToRepair = minOf(missingHp, maxHpToRepair)

        currentHp += hpToRepair
        return hpToRepair * costPerHp // Returns the currency spent
    }
}
