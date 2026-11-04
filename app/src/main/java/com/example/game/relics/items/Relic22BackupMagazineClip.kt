package com.example.game.relics.items

import androidx.annotation.Keep
import com.example.game.LootRarity

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
    val chargesPerStack: Int = 1
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

    companion object {
        fun fromMap(map: Map<String, Any>): Relic22BackupMagazineClipState {
            val count = (map["stackCount"] as? Number)?.toInt() ?: 1
            return Relic22BackupMagazineClipState(stackCount = count.coerceAtLeast(1))
        }
    }
}

object Relic22BackupMagazineClipEngine {
    fun calculateTotalCharges(
        config: Relic22BackupMagazineClipConfig,
        state: Relic22BackupMagazineClipState,
        basePlayerCharges: Int
    ): Int {
        return basePlayerCharges + config.baseCharges + (state.stackCount - 1) * config.chargesPerStack
    }
}
