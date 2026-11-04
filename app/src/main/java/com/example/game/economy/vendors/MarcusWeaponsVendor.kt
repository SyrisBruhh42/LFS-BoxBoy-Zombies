package com.example.game.economy.vendors

import com.example.game.LootRarity
import com.example.game.ModularWeaponGenerator
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand
import kotlin.math.roundToInt

object MarcusWeaponsVendor {

    /**
     * Generates standard and Item of the Day weapon offers for Marcus Munitions.
     */
    fun generateOffers(wave: Int, stepSeed: Long): List<VendingCatalogOffer> {
        val offers = mutableListOf<VendingCatalogOffer>()

        // Standard Weapon Roll
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

        // Item of the Day High-Tier Roll
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

        return offers
    }
}
