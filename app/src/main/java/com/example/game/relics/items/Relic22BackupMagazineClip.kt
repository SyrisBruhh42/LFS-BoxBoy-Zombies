package com.example.game.relics.items

import androidx.annotation.Keep
import com.example.game.LootRarity
import com.example.game.contracts.Fnv1a64Hasher

/**
 * [BoxBoys T6-22] RoR2 Passive Relic: Backup Magazine Clip
 * Grants +1 tactical grenade / dash charge per stack.
 */
@Keep
data class Relic22BackupMagazineClipConfig(
    val relicId: String = "backup_magazine_clip",
    val displayName: String = "Backup Magazine",
    val rarity: LootRarity = LootRarity.UNCOMMON,
    val baseEffectDescription: String = "Add +1 charge of your Tactical Skill (Grenades/Dash).",
    val perStackScalingDescription: String = "+1 charge per stack.",
    val colorHex: Long = 0xFF9E9E9E,
    val baseCharges: Int = 1,
    val chargesPerStack: Int = 1,
    val cooldownReductionPerStack: Float = 0.04f
) {
    val canBePurchasedAtVending: Boolean
        get() = false
}

@Keep
data class Relic22BackupMagazineClipState(
    val stackCount: Int = 1
) {
    init {
        require(stackCount >= 1) { "Relic stackCount must be >= 1, but was $stackCount" }
    }

    val extraCharges: Int
        get() = stackCount

    fun toMap(): Map<String, Any> = mapOf("stackCount" to stackCount)

    fun computeChecksum(): Long {
        var h = Fnv1a64Hasher.initial()
        h = Fnv1a64Hasher.mixString(h, "Relic22BackupMagazineClip")
        h = Fnv1a64Hasher.mixInt(h, stackCount)
        return h
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Relic22BackupMagazineClipState {
            val count = (map["stackCount"] as? Number)?.toInt() ?: 1
            return Relic22BackupMagazineClipState(stackCount = count.coerceAtLeast(1))
        }
    }
}

data class BackupMagazineTickSnapshot(
    val totalCharges: Int,
    val extraCharges: Int,
    val effectiveCooldownSeconds: Float,
    val checksum: Long
)

object Relic22BackupMagazineClipEngine {
    fun calculateTotalCharges(
        config: Relic22BackupMagazineClipConfig,
        state: Relic22BackupMagazineClipState,
        basePlayerCharges: Int
    ): Int {
        return basePlayerCharges + config.baseCharges + (state.stackCount - 1) * config.chargesPerStack
    }

    fun calculateEffectiveCooldownSeconds(
        config: Relic22BackupMagazineClipConfig,
        state: Relic22BackupMagazineClipState,
        baseCooldownSeconds: Float = 6.0f
    ): Float {
        val reduction = (state.stackCount * config.cooldownReductionPerStack).coerceIn(0f, 0.55f)
        return (baseCooldownSeconds * (1f - reduction)).coerceAtLeast(1.5f)
    }

    fun evaluateSnapshot(
        config: Relic22BackupMagazineClipConfig = Relic22BackupMagazineClipConfig(),
        state: Relic22BackupMagazineClipState = Relic22BackupMagazineClipState(1),
        basePlayerCharges: Int = 1
    ): BackupMagazineTickSnapshot {
        val total = calculateTotalCharges(config, state, basePlayerCharges)
        val cd = calculateEffectiveCooldownSeconds(config, state)
        var h = state.computeChecksum()
        h = Fnv1a64Hasher.mixInt(h, total)
        h = Fnv1a64Hasher.mixDouble(h, cd.toDouble())
        return BackupMagazineTickSnapshot(
            totalCharges = total,
            extraCharges = state.extraCharges,
            effectiveCooldownSeconds = cd,
            checksum = h
        )
    }
}


fun evaluateRelic22BackupMagazineClipLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic22BackupMagazineClip") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
