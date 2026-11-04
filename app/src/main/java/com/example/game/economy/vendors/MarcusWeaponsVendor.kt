package com.example.game.economy.vendors

import com.example.game.LootRarity
import com.example.game.ModularWeaponGenerator
import com.example.game.contracts.Fnv1a64Hasher
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand
import kotlin.math.roundToInt

data class MarcusCatalogManifest(
    val wave: Int,
    val offers: List<VendingCatalogOffer>,
    val checksum: Long
)

object MarcusWeaponsVendor {
    const val VENDOR_SLOGAN = "No refunds! Marcus Munitions Gun-Mat!"

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

    /**
     * Builds a signed Marcus catalog manifest with deterministic FNV-1a verification.
     */
    fun generateSignedManifest(wave: Int, stepSeed: Long): MarcusCatalogManifest {
        val offers = generateOffers(wave, stepSeed)
        var h = Fnv1a64Hasher.initial()
        h = Fnv1a64Hasher.mixInt(h, wave)
        h = Fnv1a64Hasher.mixLong(h, stepSeed)
        for (o in offers) {
            h = Fnv1a64Hasher.mixString(h, o.offerId)
            h = Fnv1a64Hasher.mixInt(h, o.costCurrency)
            h = Fnv1a64Hasher.mixString(h, o.rarity.name)
        }
        return MarcusCatalogManifest(
            wave = wave,
            offers = offers,
            checksum = h
        )
    }

    fun computeTradeInDiscount(displacedWeaponRarity: LootRarity?, rawCost: Int): Int {
        if (displacedWeaponRarity == null) return rawCost
        val credit = displacedWeaponRarity.salvageScrapValue
        return (rawCost - credit).coerceAtLeast(100)
    }
}


fun evaluateMarcusWeaponsVendorLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "MarcusWeaponsVendor") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
