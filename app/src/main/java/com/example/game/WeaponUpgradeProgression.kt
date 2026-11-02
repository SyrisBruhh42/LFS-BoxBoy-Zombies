package com.example.game

import com.example.data.WeaponSpec
import kotlin.math.roundToInt

/**
 * Enhanced Weapon Upgrade Progression system for BoxBoy Zombies Pack-a-Punch.
 *
 * Implements mathematical scaling for damage multipliers, magazine expansion,
 * fire cooldown acceleration, reload velocity improvements, extra projectile pierce,
 * and distinct milestone perks per Pack-a-Punch tier (MK-1 through MK-5).
 */
object WeaponUpgradeProgression {

    const val MAX_UPGRADE_LEVEL = 5

    data class UpgradeTierStats(
        val level: Int,
        val tierTitle: String,
        val milestonePerk: String,
        val damageMultiplier: Float,
        val bonusMagCapacity: Int,
        val reloadTimeMultiplier: Float,
        val fireCooldownMultiplier: Float,
        val bonusPierceCount: Int
    )

    fun getTierStats(level: Int): UpgradeTierStats {
        val clampedLvl = level.coerceIn(1, MAX_UPGRADE_LEVEL)
        return when (clampedLvl) {
            1 -> UpgradeTierStats(
                level = 1,
                tierTitle = "Standard Factory Issue",
                milestonePerk = "Standard Munitions",
                damageMultiplier = 1.0f,
                bonusMagCapacity = 0,
                reloadTimeMultiplier = 1.0f,
                fireCooldownMultiplier = 1.0f,
                bonusPierceCount = 0
            )
            2 -> UpgradeTierStats(
                level = 2,
                tierTitle = "Pack-a-Punch MK-2",
                milestonePerk = "Calibrated Breech: +42% Damage & Extended Mag",
                damageMultiplier = 1.42f,
                bonusMagCapacity = 4,
                reloadTimeMultiplier = 0.92f,
                fireCooldownMultiplier = 0.94f,
                bonusPierceCount = 0
            )
            3 -> UpgradeTierStats(
                level = 3,
                tierTitle = "Pack-a-Punch MK-3",
                milestonePerk = "Hyper-Velocity Core: +84% Damage & +1 Pierce Penetration",
                damageMultiplier = 1.84f,
                bonusMagCapacity = 8,
                reloadTimeMultiplier = 0.85f,
                fireCooldownMultiplier = 0.88f,
                bonusPierceCount = 1
            )
            4 -> UpgradeTierStats(
                level = 4,
                tierTitle = "Pack-a-Punch MK-4",
                milestonePerk = "Elemental Overcharge: +126% Damage & Rapid Reciprocation",
                damageMultiplier = 2.26f,
                bonusMagCapacity = 12,
                reloadTimeMultiplier = 0.78f,
                fireCooldownMultiplier = 0.82f,
                bonusPierceCount = 1
            )
            5 -> UpgradeTierStats(
                level = 5,
                tierTitle = "Pack-a-Punch MK-5 Apex",
                milestonePerk = "Aetherial Apex Core: +168% Damage, +2 Pierce, and Hyper-Fire",
                damageMultiplier = 2.68f,
                bonusMagCapacity = 16,
                reloadTimeMultiplier = 0.70f,
                fireCooldownMultiplier = 0.75f,
                bonusPierceCount = 2
            )
            else -> getTierStats(MAX_UPGRADE_LEVEL)
        }
    }

    fun calculateUpgradeCost(baseCost: Int, currentLevel: Int): Int {
        val lvl = currentLevel.coerceIn(1, MAX_UPGRADE_LEVEL)
        return baseCost * lvl
    }

    fun calculateEffectiveDamage(
        baseDamage: Int,
        upgradeLevel: Int,
        rarityMultiplier: Float = 1.0f,
        receiverBonus: Float = 1.0f,
        affinityBonus: Float = 1.0f,
        itemLevel: Int = 1,
        anvilTier: Int = 0
    ): Int {
        val tierStats = getTierStats(upgradeLevel)
        val raw = baseDamage *
            rarityMultiplier *
            receiverBonus *
            affinityBonus *
            tierStats.damageMultiplier *
            (1f + (itemLevel - 1) * 0.14f + anvilTier * 0.18f)
        return raw.roundToInt().coerceAtLeast(10)
    }

    fun calculateEffectiveMagSize(
        baseMag: Int,
        upgradeLevel: Int,
        magazineBonus: Int = 0,
        anvilTier: Int = 0
    ): Int {
        val tierStats = getTierStats(upgradeLevel)
        return (baseMag + magazineBonus + tierStats.bonusMagCapacity + anvilTier * 3).coerceAtLeast(4)
    }

    fun calculateEffectivePierce(
        basePierce: Int,
        upgradeLevel: Int,
        barrelBonus: Int = 0
    ): Int {
        val tierStats = getTierStats(upgradeLevel)
        return (basePierce + barrelBonus + tierStats.bonusPierceCount).coerceAtLeast(1)
    }
}
