package com.example.game.economy.vendors

import com.example.game.economy.BarricadeWindowTier

data class WindowBarricadeTier2Steel(
    val windowId: String,
    var currentHp: Int,
    var isBreached: Boolean = false,
    var isUpgrading: Boolean = false
) {
    val tierInfo: BarricadeWindowTier = BarricadeWindowTier.TIER_2_STEEL_SHUTTERS
    
    fun applyDamage(damage: Int) {
        if (isBreached || isUpgrading) return
        currentHp = (currentHp - damage).coerceAtLeast(0)
        if (currentHp == 0) {
            isBreached = true
        }
    }
    
    fun repair(repairAmount: Int = 100) {
        if (isUpgrading) return
        currentHp = (currentHp + repairAmount).coerceAtMost(tierInfo.maxHp)
        if (currentHp > 0) {
            isBreached = false
        }
    }
}
