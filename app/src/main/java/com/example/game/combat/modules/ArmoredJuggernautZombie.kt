package com.example.game.combat.modules

import com.example.game.ElementalAffinity
import kotlin.math.max

data class ArmoredJuggernautState(
    val baseZombieId: Int,
    val armorHp: Int,
    val maxArmorHp: Int
) {
    val isArmorStripped: Boolean
        get() = armorHp <= 0
}

object ArmoredJuggernautCombatLogic {

    /**
     * Applies damage to an Armored Juggernaut Zombie.
     * Heavy plated armor requires corrosive rounds or explosive barrels to strip effectively.
     *
     * @return Pair of updated state and any "pass-through" damage that should hit the core HP.
     */
    fun applyDamage(
        state: ArmoredJuggernautState,
        rawDamage: Int,
        element: ElementalAffinity,
        isBarrelExplosion: Boolean
    ): Pair<ArmoredJuggernautState, Int> {
        if (state.isArmorStripped) {
            return state to rawDamage
        }

        val effectiveArmorDamage = if (isBarrelExplosion) {
            state.armorHp // Instant strip
        } else {
            val multiplier = when (element) {
                ElementalAffinity.CORROSIVE -> 2.0f
                else -> 0.25f
            }
            (rawDamage * multiplier).toInt()
        }

        val remainingArmor = max(0, state.armorHp - effectiveArmorDamage)

        // If armor broke from this hit, we don't pass through leftover damage from the *same* shot
        // UNLESS it's a huge hit? For Boxhead style, armor usually absorbs the whole hit that breaks it.
        // Let's have it absorb the hit that breaks it, returning 0 pass-through, unless it's already broken.
        val passThrough = if (remainingArmor <= 0 && state.armorHp <= 0) rawDamage else 0

        return state.copy(armorHp = remainingArmor) to passThrough
    }
}
