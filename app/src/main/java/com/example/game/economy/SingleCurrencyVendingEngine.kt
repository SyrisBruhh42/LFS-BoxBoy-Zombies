package com.example.game.economy

import com.example.data.PerkType
import com.example.game.ArmorPiece
import com.example.game.ArmorSlot
import com.example.game.LootRarity
import com.example.game.ModularWeaponGenerator
import com.example.game.ModularWeaponInstance
import com.example.game.RpgArmorGenerator
import kotlin.math.roundToInt

/**
 * Track 4: Single-Currency Black Ops II + Borderlands 2 Economy Engine.
 * - Benches/removes XP completely (`XP_SYSTEM_BENCHED = true`).
 * - Single Currency (`currencyPoints`) powers all purchasables:
 *   1. BO2 Debris Doors & Kiting-Loop Shortcut Gates
 *   2. 4-Tier Barricade Windows (Wood Boards -> Steel Shutters -> Cryo Stasis Grid -> Laser Turret)
 *   3. Borderlands 2 Vending Machines (Dr. Zed Med/Shields, Torgue Ammo/Ordnance, Marcus Weapons of the Day)
 *   4. BO2 Perk-a-Cola Machines, Mystery Box, and Pack-a-Punch Anvil Tiers.
 * - Risk of Rain 2 Passive Relics are strictly excluded from all vending catalogs (drop-only invariant).
 */
enum class VendingMachineBrand(
    val vendorTitle: String,
    val tagline: String,
    val accentColorHex: Long
) {
    ZED_MED_AND_SHIELDS(
        "Dr. Zed's Vita-Shield Vendor",
        "Patch up your flesh and hyper-charge your shield generator!",
        0xFF00FF66
    ),
    TORGUE_AMMO_AND_ORDNANCE(
        "Torgue Munitions & Blast Dump",
        "MAX AMMO, EXPLOSIVE BARRELS & TACTICAL GRENADE MODS!",
        0xFFFFD166
    ),
    MARCUS_WEAPONS_OF_THE_DAY(
        "Marcus Munitions Gun-Mat",
        "No refunds! Featuring a procedurally rolled Item of the Day!",
        0xFF00E5FF
    ),
    PERK_A_COLA_FOUNTAIN(
        "Black Ops Perk-a-Cola Machine",
        "Drink up for permanent survival buffs while standing!",
        0xFFFF2A54
    ),
    PACK_A_PUNCH_ANVIL(
        "Pack-a-Punch Elemental Forge",
        "Overclock weapon tier, magazine capacity & elemental rounds!",
        0xFFB388FF
    )
}

enum class BarricadeWindowTier(
    val tierIndex: Int,
    val displayName: String,
    val maxHp: Int,
    val upgradeCostCurrency: Int,
    val repairRewardPerBoard: Int,
    val counterAttackDps: Int
) {
    TIER_1_WOOD_BOARDS(1, "Timber Planks", 240, 0, 20, 0),
    TIER_2_STEEL_SHUTTERS(2, "Reinforced Steel Shutters", 520, 600, 30, 0),
    TIER_3_CRYO_LASER_GRID(3, "Cryo-Stasis Laser Grid", 850, 1350, 40, 35),
    TIER_4_AUTO_SENTRY_MOUNT(4, "Pulse Auto-Turret Chokepoint", 1300, 2400, 50, 95)
}

data class VendingCatalogOffer(
    val offerId: String,
    val brand: VendingMachineBrand,
    val title: String,
    val subtitle: String,
    val costCurrency: Int,
    val isItemOfTheDay: Boolean = false,
    val rarity: LootRarity = LootRarity.COMMON,
    val weaponPayload: ModularWeaponInstance? = null,
    val shieldArmorPayload: ArmorPiece? = null,
    val perkPayload: PerkType? = null
)

data class VendingStockSnapshot(
    val waveNumber: Int,
    val refreshCountdownSec: Int,
    val offers: List<VendingCatalogOffer>
)

object SingleCurrencyVendingEngine {
    const val XP_SYSTEM_BENCHED: Boolean = true
    const val HIT_MARKER_CURRENCY_REWARD: Int = 10
    const val BOARD_REPAIR_CURRENCY_REWARD: Int = 20

    /**
     * Generates the Borderlands 2 + Black Ops 2 Vending Machine offers for the current wave,
     * including a guaranteed high-rarity "Item of the Day" weapon and shield/armor roll.
     */
    fun generateWaveVendingStock(
        wave: Int,
        seed: Long,
        refreshCountdownSec: Int = 180
    ): VendingStockSnapshot {
        val stepSeed = seed xor (wave.toLong() * 0x517CC1B727220A95L)
        val offers = mutableListOf<VendingCatalogOffer>()

        // 1. Dr. Zed's Med & Shield Vendor
        val healCost = (250 + (wave - 1) * 40).coerceAtMost(1200)
        offers.add(
            VendingCatalogOffer(
                offerId = "zed_insta_heal_w$wave",
                brand = VendingMachineBrand.ZED_MED_AND_SHIELDS,
                title = "Insta-Vial Full Health Restore",
                subtitle = "Restores 100% HP & clears status effects",
                costCurrency = healCost,
                rarity = LootRarity.UNCOMMON
            )
        )
        val shieldOfTheDay = RpgArmorGenerator.generateProceduralArmorPiece(
            seed = stepSeed + 101L,
            wave = wave + 2,
            targetSlot = ArmorSlot.CHEST_CARAPACE,
            minRarity = LootRarity.RARE
        )
        val shieldCost = (600 + wave * 180 * shieldOfTheDay.rarity.statMultiplier).roundToInt()
        offers.add(
            VendingCatalogOffer(
                offerId = "zed_shield_iotd_w$wave",
                brand = VendingMachineBrand.ZED_MED_AND_SHIELDS,
                title = "[ITEM OF THE DAY] ${shieldOfTheDay.displayName}",
                subtitle = "+${shieldOfTheDay.bonusMaxShield} Shield | +${shieldOfTheDay.effectiveDefense} Armor (${shieldOfTheDay.setArchetype.setName})",
                costCurrency = shieldCost,
                isItemOfTheDay = true,
                rarity = shieldOfTheDay.rarity,
                shieldArmorPayload = shieldOfTheDay
            )
        )

        // 2. Torgue Ammo & Ordnance Vendor
        val ammoCost = (400 + (wave - 1) * 65).coerceAtMost(1800)
        offers.add(
            VendingCatalogOffer(
                offerId = "torgue_max_ammo_w$wave",
                brand = VendingMachineBrand.TORGUE_AMMO_AND_ORDNANCE,
                title = "Torgue Full Munitions Restock",
                subtitle = "Refills Primary, Secondary & Explosive Barrel charges",
                costCurrency = ammoCost,
                rarity = LootRarity.RARE
            )
        )

        // 3. Marcus Weapons of the Day Vendor (Standard roll + Item of the Day high-tier roll)
        val standardWeapon = ModularWeaponGenerator.generateProceduralWeapon(
            seed = stepSeed + 202L,
            wave = wave
        )
        val standardCost = (750 + wave * 160 * standardWeapon.rarity.statMultiplier).roundToInt()
        offers.add(
            VendingCatalogOffer(
                offerId = "marcus_std_wpn_w$wave",
                brand = VendingMachineBrand.MARCUS_WEAPONS_OF_THE_DAY,
                title = standardWeapon.customTitle,
                subtitle = "${standardWeapon.receiver.manufacturer.brandName} | ${standardWeapon.effectiveDamage} DMG | ${standardWeapon.magazine.elementalAffinity.displayName}",
                costCurrency = standardCost,
                rarity = standardWeapon.rarity,
                weaponPayload = standardWeapon
            )
        )

        val iotdWeapon = ModularWeaponGenerator.generateProceduralWeapon(
            seed = stepSeed + 303L,
            wave = wave + 3,
            guaranteedMinRarity = LootRarity.RARE
        )
        val iotdCost = (1200 + wave * 240 * iotdWeapon.rarity.statMultiplier).roundToInt()
        offers.add(
            VendingCatalogOffer(
                offerId = "marcus_iotd_wpn_w$wave",
                brand = VendingMachineBrand.MARCUS_WEAPONS_OF_THE_DAY,
                title = "[ITEM OF THE DAY] ${iotdWeapon.customTitle}",
                subtitle = "${iotdWeapon.receiver.manufacturer.brandName} | ${iotdWeapon.effectiveDamage} DMG | ${iotdWeapon.magazine.elementalAffinity.displayName}",
                costCurrency = iotdCost,
                isItemOfTheDay = true,
                rarity = iotdWeapon.rarity,
                weaponPayload = iotdWeapon
            )
        )

        // 4. Black Ops Perk-a-Cola Fountain
        for (perk in PerkType.entries) {
            offers.add(
                VendingCatalogOffer(
                    offerId = "perk_${perk.name.lowercase()}",
                    brand = VendingMachineBrand.PERK_A_COLA_FOUNTAIN,
                    title = perk.displayName,
                    subtitle = perk.description,
                    costCurrency = perk.cost,
                    rarity = LootRarity.EPIC,
                    perkPayload = perk
                )
            )
        }

        return VendingStockSnapshot(
            waveNumber = wave,
            refreshCountdownSec = refreshCountdownSec,
            offers = offers
        )
    }

    fun computePackAPunchCost(currentTier: Int): Int {
        return when (currentTier.coerceAtLeast(1)) {
            1 -> 2500
            2 -> 5000
            3 -> 7500
            else -> 10000
        }
    }
}
