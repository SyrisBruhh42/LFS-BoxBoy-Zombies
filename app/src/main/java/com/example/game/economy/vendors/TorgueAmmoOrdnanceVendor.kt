package com.example.game.economy.vendors

import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand
import com.example.game.LootRarity

object TorgueAmmoOrdnanceVendor {
    fun generateOffers(wave: Int): List<VendingCatalogOffer> {
        val offers = mutableListOf<VendingCatalogOffer>()
        
        // 1. Full Ammo Restock
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

        // 2. Explosive Barrel Packs
        val barrelCost = (200 + (wave - 1) * 50).coerceAtMost(1200)
        offers.add(
            VendingCatalogOffer(
                offerId = "torgue_barrel_pack_w$wave",
                brand = VendingMachineBrand.TORGUE_AMMO_AND_ORDNANCE,
                title = "Torgue Explosive Barrel Pack",
                subtitle = "Deploys 3 high-yield explosive barrels",
                costCurrency = barrelCost,
                rarity = LootRarity.UNCOMMON
            )
        )

        // 3. Grenade Mod Item of the Day
        val iotdCost = (800 + (wave - 1) * 120).coerceAtMost(3000)
        offers.add(
            VendingCatalogOffer(
                offerId = "torgue_grenade_iotd_w$wave",
                brand = VendingMachineBrand.TORGUE_AMMO_AND_ORDNANCE,
                title = "[ITEM OF THE DAY] MIRV Grenade Mod",
                subtitle = "Tactical Grenade Mod: splits into 5 smaller explosives",
                costCurrency = iotdCost,
                isItemOfTheDay = true,
                rarity = LootRarity.EPIC
            )
        )

        return offers
    }
}
