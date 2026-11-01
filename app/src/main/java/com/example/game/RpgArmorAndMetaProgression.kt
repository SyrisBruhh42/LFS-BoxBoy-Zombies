package com.example.game

import kotlin.random.Random

/**
 * PHASE 5: 7-Slot RPG Armor Rig, Dynamic Palette Swap Shader, Upgrade Anvils & Depth Escrow Meta-Progression
 */
enum class ArmorSlot(
    val displayName: String,
    val shortCode: String,
    val defaultPieceName: String
) {
    HELMET_VISOR("Helmet / Lantern Visor", "HELM", "Scavenger Optical Visor"),
    CHEST_CARAPACE("Chest Carapace", "CHEST", "Flak Kevlar Vest"),
    PAULDRONS("Shoulder Pauldrons", "PLDR", "Scrap Alloy Pauldrons"),
    GAUNTLETS("Tactical Gauntlets", "GNTL", "Field Loader Gloves"),
    EXO_LEGS("Exo-Strider Greaves", "LEGS", "Reinforced Combat Boots"),
    UTILITY_BELT("Ordnance Utility Belt", "BELT", "Ammo Rig Belt"),
    ABYSSAL_RELIC("Non-Euclidean Relic", "RELIC", "Dormant Maze Compass")
}

enum class ArmorSetArchetype(
    val setName: String,
    val twoPieceBonusDesc: String,
    val fourPieceBonusDesc: String,
    val primaryTintHex: Long,
    val secondaryTrimHex: Long,
    val visorGlowHex: Long
) {
    CHIAROSCURO_STALKER(
        "Chiaroscuro Stalker",
        "2-PC: +65px Lantern Beam Range & +15% Light-Lock Crit",
        "4-PC: +35% Illuminated Damage & wider Lantern Cone",
        0xFF1A2E35,
        0xFF00E5FF,
        0xFF90F8FF
    ),
    ABYSSAL_BULWARK(
        "Abyssal Bulwark",
        "2-PC: +45 Max Shield & +12% Damage Mitigation",
        "4-PC: +90 Max Shield & Reactive Shockwave on Shield Break",
        0xFF2B1820,
        0xFFFF2A54,
        0xFFFFD166
    ),
    OVERCLOCK_STRIDER(
        "Overclock Strider",
        "2-PC: +14% Move Speed & +18% Reload Speed",
        "4-PC: +26% Move Speed & Zero Elemental Puddle Slow",
        0xFF162B1E,
        0xFF00FF66,
        0xFF7AE582
    ),
    VOID_ALCHEMIST(
        "Void Alchemist",
        "2-PC: +20% Elemental Damage & +20% Scrap Salvage",
        "4-PC: +40% Elemental Damage & +5 Shield on Kill",
        0xFF221630,
        0xFFB388FF,
        0xFFE040FB
    )
}

data class ArmorPiece(
    val id: String,
    val slot: ArmorSlot,
    val displayName: String,
    val rarity: LootRarity,
    val setArchetype: ArmorSetArchetype,
    val itemLevel: Int = 1,
    val anvilTier: Int = 0,
    val defenseRating: Int = 8,
    val bonusMaxShield: Int = 10,
    val moveSpeedBonusPct: Int = 0,
    val reloadSpeedBonusPct: Int = 0,
    val lanternRangeBonusPx: Float = 0f
) {
    val effectiveDefense: Int
        get() = defenseRating + anvilTier * 6

    val effectiveShieldBonus: Int
        get() = bonusMaxShield + anvilTier * 8
}

data class ArmorPaletteProfile(
    val activeSetName: String,
    val chassisColorHex: Long,
    val trimColorHex: Long,
    val visorColorHex: Long,
    val auraColorHex: Long,
    val activePiecesCount: Int
)

data class ArmorRigState(
    val equippedSlots: Map<ArmorSlot, ArmorPiece> = defaultStarterRig()
) {
    val totalDefenseRating: Int
        get() = equippedSlots.values.sumOf { it.effectiveDefense }

    /**
     * Converts total defense rating into a percentage damage reduction (up to 65% cap).
     */
    val damageReductionFraction: Float
        get() {
            val base = totalDefenseRating / (totalDefenseRating + 110f)
            val bulwarkBonus = if (countForSet(ArmorSetArchetype.ABYSSAL_BULWARK) >= 2) 0.10f else 0f
            return (base + bulwarkBonus).coerceIn(0f, 0.65f)
        }

    val totalBonusMaxShield: Int
        get() {
            val slotSum = equippedSlots.values.sumOf { it.effectiveShieldBonus }
            val bulwark2 = if (countForSet(ArmorSetArchetype.ABYSSAL_BULWARK) >= 2) 45 else 0
            val bulwark4 = if (countForSet(ArmorSetArchetype.ABYSSAL_BULWARK) >= 4) 45 else 0
            return slotSum + bulwark2 + bulwark4
        }

    val totalMoveSpeedMultiplier: Float
        get() {
            val pct = equippedSlots.values.sumOf { it.moveSpeedBonusPct } +
                (if (countForSet(ArmorSetArchetype.OVERCLOCK_STRIDER) >= 2) 14 else 0) +
                (if (countForSet(ArmorSetArchetype.OVERCLOCK_STRIDER) >= 4) 12 else 0)
            return (1f + pct / 100f).coerceIn(1.0f, 1.65f)
        }

    val totalReloadSpeedMultiplier: Float
        get() {
            val pct = equippedSlots.values.sumOf { it.reloadSpeedBonusPct } +
                (if (countForSet(ArmorSetArchetype.OVERCLOCK_STRIDER) >= 2) 18 else 0)
            return (1f + pct / 100f).coerceIn(1.0f, 1.80f)
        }

    val totalLanternRangeBonusPx: Float
        get() {
            val base = equippedSlots.values.map { it.lanternRangeBonusPx }.sum()
            val stalker2 = if (countForSet(ArmorSetArchetype.CHIAROSCURO_STALKER) >= 2) 65f else 0f
            return base + stalker2
        }

    fun countForSet(set: ArmorSetArchetype): Int =
        equippedSlots.values.count { it.setArchetype == set }

    val dominantSet: Pair<ArmorSetArchetype, Int>
        get() {
            val best = ArmorSetArchetype.entries.maxByOrNull { countForSet(it) } ?: ArmorSetArchetype.OVERCLOCK_STRIDER
            return Pair(best, countForSet(best))
        }

    /**
     * Computes the real-time Armor Palette Shader profile used by the Canvas renderer
     * to tint the player's chassis, shoulder trim, visor glow, and set synergy ring.
     */
    val paletteProfile: ArmorPaletteProfile
        get() {
            val (set, count) = dominantSet
            val highestRarity = equippedSlots.values.maxByOrNull { it.rarity.ordinal }?.rarity ?: LootRarity.COMMON
            return ArmorPaletteProfile(
                activeSetName = "${set.setName} ($count/7)",
                chassisColorHex = set.primaryTintHex,
                trimColorHex = set.secondaryTrimHex,
                visorColorHex = set.visorGlowHex,
                auraColorHex = highestRarity.colorHex,
                activePiecesCount = count
            )
        }

    companion object {
        fun defaultStarterRig(): Map<ArmorSlot, ArmorPiece> {
            return ArmorSlot.entries.associateWith { slot ->
                ArmorPiece(
                    id = "starter_${slot.name.lowercase()}",
                    slot = slot,
                    displayName = slot.defaultPieceName,
                    rarity = LootRarity.COMMON,
                    setArchetype = ArmorSetArchetype.OVERCLOCK_STRIDER,
                    itemLevel = 1,
                    defenseRating = 6,
                    bonusMaxShield = 8,
                    moveSpeedBonusPct = if (slot == ArmorSlot.EXO_LEGS) 5 else 0,
                    reloadSpeedBonusPct = if (slot == ArmorSlot.GAUNTLETS) 6 else 0,
                    lanternRangeBonusPx = if (slot == ArmorSlot.HELMET_VISOR) 25f else 0f
                )
            }
        }
    }
}

/**
 * Persistent Depth Escrow & Upgrade Anvil resource state.
 */
data class DepthEscrowState(
    val abyssalScrap: Int = 120,
    val abyssalCrystals: Int = 5,
    val bankedEscrowPoints: Int = 0,
    val deepestMazeDepthReached: Int = 1,
    val totalAnvilUpgrades: Int = 0
)

object RpgArmorGenerator {

    fun generateProceduralArmorPiece(
        seed: Long,
        wave: Int,
        targetSlot: ArmorSlot? = null,
        minRarity: LootRarity? = null
    ): ArmorPiece {
        val rng = Random(seed)
        val slot = targetSlot ?: ArmorSlot.entries.random(rng)
        val rarity = ModularWeaponGenerator.rollRarity(rng, wave, minRarity)
        val set = ArmorSetArchetype.entries.random(rng)

        val baseDef = ((7 + wave * 2) * rarity.statMultiplier).toInt()
        val baseShd = ((10 + wave * 3) * rarity.statMultiplier).toInt()

        return ArmorPiece(
            id = "arm_${slot.shortCode}_${seed}_${rng.nextInt(100, 999)}",
            slot = slot,
            displayName = "${rarity.shortBadge} ${set.setName} ${slot.shortCode}",
            rarity = rarity,
            setArchetype = set,
            itemLevel = wave.coerceAtLeast(1),
            anvilTier = 0,
            defenseRating = baseDef,
            bonusMaxShield = baseShd,
            moveSpeedBonusPct = if (slot == ArmorSlot.EXO_LEGS || set == ArmorSetArchetype.OVERCLOCK_STRIDER) {
                4 + rarity.ordinal * 3
            } else 0,
            reloadSpeedBonusPct = if (slot == ArmorSlot.GAUNTLETS || slot == ArmorSlot.UTILITY_BELT) {
                5 + rarity.ordinal * 3
            } else 0,
            lanternRangeBonusPx = if (slot == ArmorSlot.HELMET_VISOR || slot == ArmorSlot.ABYSSAL_RELIC) {
                20f + rarity.ordinal * 18f
            } else 0f
        )
    }
}
