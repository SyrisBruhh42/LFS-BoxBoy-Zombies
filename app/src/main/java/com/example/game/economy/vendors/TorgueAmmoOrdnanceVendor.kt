package com.example.game.economy.vendors

import com.example.game.LootRarity
import com.example.game.contracts.Fnv1a64Hasher
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand

data class TorgueOrdnanceBundle(
    val wave: Int,
    val offers: List<VendingCatalogOffer>,
    val bonusBarrelCharges: Int,
    val checksum: Long
)

object TorgueAmmoOrdnanceVendor {
    const val VENDOR_SLOGAN = "EXPLOSIONS?! BUY TORGUE MUNITIONS!"
    const val DEFAULT_BARREL_PACK_CHARGES = 3

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

    fun buildOrdnanceBundle(wave: Int): TorgueOrdnanceBundle {
        val safeWave = wave.coerceAtLeast(1)
        val offers = generateOffers(safeWave)
        val bonusCharges = DEFAULT_BARREL_PACK_CHARGES + (safeWave / 8)
        var h = Fnv1a64Hasher.initial()
        h = Fnv1a64Hasher.mixInt(h, safeWave)
        h = Fnv1a64Hasher.mixInt(h, bonusCharges)
        for (o in offers) {
            h = Fnv1a64Hasher.mixString(h, o.offerId)
            h = Fnv1a64Hasher.mixInt(h, o.costCurrency)
        }
        return TorgueOrdnanceBundle(
            wave = safeWave,
            offers = offers,
            bonusBarrelCharges = bonusCharges,
            checksum = h
        )
    }

    fun calculateEmergencyAmmoRestockCost(wave: Int, missingMagazineRatio: Float): Int {
        val baseCost = (400 + (wave.coerceAtLeast(1) - 1) * 65).coerceAtMost(1800)
        val clampedRatio = missingMagazineRatio.coerceIn(0.15f, 1.0f)
        return (baseCost * clampedRatio).toInt().coerceAtLeast(100)
    }
}


fun evaluateTorgueAmmoOrdnanceVendorLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "TorgueAmmoOrdnanceVendor") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
