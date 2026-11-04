package com.example.game.combat.modules

import com.example.game.ElementalAffinity
import com.example.game.ZombieEntity
import com.example.game.contracts.Fnv1a64Hasher
import kotlin.math.max
import kotlin.math.roundToInt

class ArmoredJuggernautZombie(
    var entity: ZombieEntity,
    var armorHp: Int,
    val maxArmorHp: Int = armorHp
) {
    fun takeDamage(baseDamage: Int, affinity: ElementalAffinity, isExplosiveBarrel: Boolean = false) {
        if (baseDamage <= 0) return
        if (armorHp <= 0) {
            entity = entity.copy(hp = max(0, entity.hp - baseDamage))
            return
        }
        val armorMultiplier = if (isExplosiveBarrel || affinity == ElementalAffinity.CORROSIVE) 1.0f else 0.1f
        val effectiveArmorDmg = (baseDamage * armorMultiplier).roundToInt()
        if (effectiveArmorDmg < armorHp) {
            armorHp -= effectiveArmorDmg
        } else {
            val rawUsedForArmor = (armorHp / armorMultiplier).roundToInt()
            val overflowRawDamage = max(0, baseDamage - rawUsedForArmor)
            armorHp = 0
            if (overflowRawDamage > 0) {
                entity = entity.copy(hp = max(0, entity.hp - overflowRawDamage))
            }
        }
    }
}

data class ArmoredJuggernautState(
    val baseZombieId: Int,
    val armorHp: Int,
    val maxArmorHp: Int
) {
    val isArmorStripped: Boolean
        get() = armorHp <= 0

    val armorIntegrityRatio: Float
        get() = if (maxArmorHp <= 0) 0f else (armorHp.toFloat() / maxArmorHp.toFloat()).coerceIn(0f, 1f)

    fun toMap(): Map<String, Any> = mapOf(
        "baseZombieId" to baseZombieId,
        "armorHp" to armorHp,
        "maxArmorHp" to maxArmorHp,
        "isArmorStripped" to isArmorStripped
    )

    fun computeChecksum(): Long {
        var h = Fnv1a64Hasher.initial()
        h = Fnv1a64Hasher.mixInt(h, baseZombieId)
        h = Fnv1a64Hasher.mixInt(h, armorHp)
        h = Fnv1a64Hasher.mixInt(h, maxArmorHp)
        return h
    }
}

object ArmoredJuggernautCombatLogic {
    const val CORROSIVE_ARMOR_MULTIPLIER = 2.0f
    const val NON_CORROSIVE_ARMOR_MULTIPLIER = 0.25f

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
                ElementalAffinity.CORROSIVE -> CORROSIVE_ARMOR_MULTIPLIER
                else -> NON_CORROSIVE_ARMOR_MULTIPLIER
            }
            (rawDamage * multiplier).toInt()
        }

        val remainingArmor = max(0, state.armorHp - effectiveArmorDamage)
        val passThrough = if (remainingArmor <= 0 && state.armorHp <= 0) rawDamage else 0

        return state.copy(armorHp = remainingArmor) to passThrough
    }

    /**
     * Creates a wave-scaled Juggernaut armor state for Elite waves.
     */
    fun spawnWaveScaledJuggernaut(zombieId: Int, wave: Int): ArmoredJuggernautState {
        val safeWave = wave.coerceAtLeast(1)
        val scaledMaxArmor = (220 * (1.0 + (safeWave - 1) * 0.18)).roundToInt()
        return ArmoredJuggernautState(
            baseZombieId = zombieId,
            armorHp = scaledMaxArmor,
            maxArmorHp = scaledMaxArmor
        )
    }

    /**
     * Computes movement speed penalty while heavy armor plates remain attached.
     */
    fun computeJuggernautSpeedMultiplier(state: ArmoredJuggernautState): Float {
        return if (state.isArmorStripped) 1.25f else 0.72f
    }
}


fun evaluateArmoredJuggernautZombieLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "ArmoredJuggernautZombie") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
