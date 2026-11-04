package com.example.game.relics.items

import androidx.annotation.Keep
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import kotlin.math.abs

@Keep
data class Relic21ArmorPiercingRoundsConfig(
    val damageBonusPerStack: Float = 0.20f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "damageBonusPerStack" to damageBonusPerStack
    )

    companion object {
        fun fromMap(map: Map<String, Any>): Relic21ArmorPiercingRoundsConfig {
            return Relic21ArmorPiercingRoundsConfig(
                damageBonusPerStack = (map["damageBonusPerStack"] as? Number)?.toFloat() ?: 0.20f
            )
        }
    }
}

@Keep
data class Relic21ArmorPiercingRoundsState(
    val stackCount: Int,
    val config: Relic21ArmorPiercingRoundsConfig = Relic21ArmorPiercingRoundsConfig()
) {
    init {
        require(stackCount >= 1) { "Relic stack count must be at least 1." }
    }

    val canBePurchasedAtVending: Boolean = false

    fun toMap(): Map<String, Any> = mapOf(
        "stackCount" to stackCount,
        "config" to config.toMap()
    )

    fun computeChecksum(): Long {
        var hash = -2128831035146727685L // FNV-1a 64-bit offset basis
        hash = (hash xor stackCount.toLong()) * 1099511628211L
        hash = (hash xor config.damageBonusPerStack.toRawBits().toLong()) * 1099511628211L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Relic21ArmorPiercingRoundsState {
            val stackCount = (map["stackCount"] as? Number)?.toInt() ?: 1
            val configMap = (map["config"] as? Map<String, Any>) ?: emptyMap()
            return Relic21ArmorPiercingRoundsState(
                stackCount = stackCount,
                config = Relic21ArmorPiercingRoundsConfig.fromMap(configMap)
            )
        }
    }
}

object Relic21ArmorPiercingRoundsEngine {

    // Evaluates if the zombie is considered a Boss or Elite
    fun isBossOrElite(archetype: ZombieArchetype): Boolean {
        return archetype == ZombieArchetype.DEVIL_BOSS ||
               archetype == ZombieArchetype.ABYSSAL_WEAVER ||
               archetype == ZombieArchetype.CALCIFIED_STALKER
    }

    // Calculates the bonus damage. Zero allocation. Returns the NEW damage (clamped to integers since damage is usually Int)
    fun calculateDamage(
        state: Relic21ArmorPiercingRoundsState,
        targetArchetype: ZombieArchetype,
        baseDamage: Int
    ): Int {
        if (baseDamage <= 0) return 0
        if (!isBossOrElite(targetArchetype)) return baseDamage

        var bonusMultiplier = state.stackCount * state.config.damageBonusPerStack
        if (!bonusMultiplier.isFinite()) bonusMultiplier = 0f
        if (bonusMultiplier < 0f) bonusMultiplier = 0f

        val finalDamageFloat = baseDamage * (1.0f + bonusMultiplier)
        return if (finalDamageFloat.isFinite()) finalDamageFloat.toInt() else baseDamage
    }

    // Overload for ZombieEntity
    fun calculateDamage(
        state: Relic21ArmorPiercingRoundsState,
        target: ZombieEntity,
        baseDamage: Int
    ): Int {
        return calculateDamage(state, target.archetype, baseDamage)
    }
}
