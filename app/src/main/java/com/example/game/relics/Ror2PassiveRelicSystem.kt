package com.example.game.relics

import com.example.game.LootRarity
import kotlin.math.abs
import kotlin.random.Random

/**
 * Track 6: Risk of Rain 2 Rare-Drop-Only Passive Skill Relics.
 * Invariant: `canBePurchasedAtVending = false` for every relic!
 * Relics drop exclusively from Elite Zombies, Boxhead Devil Bosses, and Combo Milestones,
 * or are granted as catch-up relics during mid-session Drop-Pod hot-join.
 * They stack infinitely (`stackCount`) and augment passive skills outside of weapons and armor.
 */
enum class PassiveRelicKind(
    val relicId: String,
    val displayName: String,
    val rarity: LootRarity,
    val baseEffectDescription: String,
    val perStackScalingDescription: String,
    val colorHex: Long
) {
    UKULELE_ARC_LIGHTNING(
        "ukulele_arc",
        "Voltaic Ukulele",
        LootRarity.RARE,
        "25% chance on hit to chain lightning for 80% TOTAL damage",
        "+2 targets and +60px bounce radius per stack",
        0xFF00E5FF
    ),
    ATG_PLASMA_MISSILE(
        "atg_missile",
        "AtG Plasma Launcher",
        LootRarity.RARE,
        "10% chance on hit to fire a homing plasma missile for 300% damage",
        "+300% missile damage per stack",
        0xFF00FF66
    ),
    PAULS_GOAT_HOOF(
        "goat_hoof",
        "Cybernetic Strider Hoof",
        LootRarity.UNCOMMON,
        "Increases movement & kiting speed by +14%",
        "+14% movement speed per stack",
        0xFF7AE582
    ),
    SOLDIERS_SYRINGE(
        "soldiers_syringe",
        "Adrenaline Syringe",
        LootRarity.UNCOMMON,
        "Increases attack & reload cadence by +15%",
        "+15% fire & reload speed per stack",
        0xFFFFD166
    ),
    WILL_O_WISP_JAR(
        "will_o_wisp",
        "Abyssal Wisp Vessel",
        LootRarity.RARE,
        "On kill, detonate a lava pillar for 350% base damage",
        "+280% damage and +24px blast radius per stack",
        0xFFFF5722
    ),
    FROST_RELIC_ICICLE(
        "frost_relic",
        "Cryo-Core Icicle",
        LootRarity.EPIC,
        "Killing an enemy surrounds you with an orbiting ice storm",
        "+36px storm radius and +15% slow duration per stack",
        0xFF40C4FF
    ),
    BLAST_SURF_THRUSTERS(
        "blast_surf_boots",
        "PhD Blast-Surf Thrusters",
        LootRarity.EPIC,
        "Zero barrel self-damage; barrel surfing reloads active weapon & boosts impulse +20%",
        "+20% surf knockback impulse and +80 shockwave damage per stack",
        0xFFB388FF
    ),
    TESLA_COIL_BEACON(
        "tesla_coil",
        "Unstable Tesla Coil",
        LootRarity.ABYSSAL_LEGENDARY,
        "Passively zaps nearby enemies every 0.5s for 200% damage",
        "+2 simultaneous lightning arcs per stack",
        0xFFFF2A54
    ),
    CROWBAR_OPENING_STRIKE(
        "titanium_crowbar",
        "Titanium Breacher Crowbar",
        LootRarity.UNCOMMON,
        "Deal +75% damage to zombies above 90% health",
        "+75% opening strike damage per stack",
        0xFFE0E0E0
    ),
    HARVESTERS_SCYTHE(
        "harvesters_scythe",
        "Bio-Harvester Scythe",
        LootRarity.RARE,
        "+5% critical strike chance; critical hits heal 8 HP",
        "+4 HP healed on crit per stack",
        0xFF00FF66
    );

    val canBePurchasedAtVending: Boolean
        get() = false
}

data class PassiveRelicStackState(
    val stacks: Map<PassiveRelicKind, Int> = emptyMap()
) {
    val totalRelicCount: Int
        get() = stacks.values.sum()

    fun countOf(kind: PassiveRelicKind): Int = stacks[kind] ?: 0

    val moveSpeedMultiplier: Float
        get() = 1.0f + countOf(PassiveRelicKind.PAULS_GOAT_HOOF) * 0.14f

    val attackCadenceMultiplier: Float
        get() = 1.0f + countOf(PassiveRelicKind.SOLDIERS_SYRINGE) * 0.15f

    val openingStrikeMultiplier: Float
        get() = 1.0f + countOf(PassiveRelicKind.CROWBAR_OPENING_STRIKE) * 0.75f

    val critHealAmount: Int
        get() {
            val c = countOf(PassiveRelicKind.HARVESTERS_SCYTHE)
            return if (c <= 0) 0 else 8 + (c - 1) * 4
        }

    val arcLightningTargets: Int
        get() {
            val c = countOf(PassiveRelicKind.UKULELE_ARC_LIGHTNING)
            return if (c <= 0) 0 else 3 + (c - 1) * 2
        }
}

object Ror2PassiveRelicSystem {
    const val PURCHASABLE_IN_SHOPS: Boolean = false

    fun collectRelicDrop(
        current: PassiveRelicStackState,
        relic: PassiveRelicKind,
        amount: Int = 1
    ): PassiveRelicStackState {
        if (amount <= 0) return current
        val updated = current.stacks.toMutableMap()
        updated[relic] = (updated[relic] ?: 0) + amount
        return PassiveRelicStackState(updated)
    }

    fun rollRareRelicDrop(
        wave: Int,
        isBossKill: Boolean,
        isComboMilestone: Boolean,
        seed: Long
    ): PassiveRelicKind? {
        val rng = Random(seed xor (wave.toLong() * 0x9E3779B9L))
        if (!isBossKill && !isComboMilestone) {
            val dropChance = (0.04f + wave * 0.005f).coerceAtMost(0.18f)
            if (rng.nextFloat() > dropChance) return null
        }
        val pool = PassiveRelicKind.entries
        return pool[abs(rng.nextInt()) % pool.size]
    }

    /**
     * Grants `floor(wave / 5)` random RoR2 Passive Relics for a player hot-joining mid-session!
     */
    fun rollMidSessionCatchUpRelics(wave: Int, seed: Long): PassiveRelicStackState {
        val bonusCount = (wave / 5).coerceAtLeast(0)
        var state = PassiveRelicStackState()
        val rng = Random(seed xor 0x6A09E667F3BCC908L)
        val pool = PassiveRelicKind.entries
        repeat(bonusCount) {
            val chosen = pool[abs(rng.nextInt()) % pool.size]
            state = collectRelicDrop(state, chosen, 1)
        }
        return state
    }
}
