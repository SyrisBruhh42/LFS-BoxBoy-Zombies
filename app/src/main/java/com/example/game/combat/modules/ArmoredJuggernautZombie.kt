package com.example.game.combat.modules

import com.example.game.ElementalAffinity
import com.example.game.ZombieEntity
import kotlin.math.max
import kotlin.math.roundToInt

data class ArmoredJuggernautZombie(
    val entity: ZombieEntity,
    var armorHp: Int,
    val maxArmorHp: Int
) {
    fun takeDamage(damage: Int, affinity: ElementalAffinity, isExplosiveBarrel: Boolean) {
        if (armorHp > 0) {
            val isArmorPiercing = affinity == ElementalAffinity.CORROSIVE || isExplosiveBarrel

            // If it's armor piercing, it does full damage to armor. Otherwise, heavily reduced (e.g., 10%)
            val damageToArmor = if (isArmorPiercing) {
                damage
            } else {
                (damage * 0.1f).roundToInt()
            }

            if (damageToArmor >= armorHp) {
                // Armor broken, remaining damage overflows to health
                // The overflow damage should use the standard damage multiplier logic, but for simplicity
                // we'll just subtract the equivalent base damage used to break the armor and apply the rest
                val overflowBaseDamage = if (isArmorPiercing) {
                    damage - armorHp
                } else {
                    damage - (armorHp * 10) // Since non-AP does 10% damage
                }

                armorHp = 0

                // Ensure overflow damage is not negative
                val actualOverflow = max(0, overflowBaseDamage)
                if (actualOverflow > 0) {
                    entity.hp = max(0, entity.hp - actualOverflow)
                }
            } else {
                // Armor absorbs the damage
                armorHp -= max(1, damageToArmor) // At least 1 damage to armor if hit
            }
        } else {
            // No armor, normal damage to entity
            entity.hp = max(0, entity.hp - damage)
        }
    }
}
