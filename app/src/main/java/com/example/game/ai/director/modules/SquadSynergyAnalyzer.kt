package com.example.game.ai.director.modules

import com.example.game.ElementalAffinity
import com.example.game.ModularWeaponInstance
import com.example.game.contracts.Fnv1a64Hasher
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand

data class SquadSynergyReport(
    val coveredElements: Set<ElementalAffinity>,
    val missingElements: Set<ElementalAffinity>,
    val recommendedVendor: VendingMachineBrand,
    val synergyScore: Double,
    val averageWeaponDamage: Double,
    val checksum: Long
)

/**
 * Local Open-Source AI Director Module for Unified BoxBoys Track 2.
 * Analyzes squad weapon elements, unit roles, and recommends complementary Vending Machine rolls.
 */
object SquadSynergyAnalyzer {

    /**
     * Analyzes the squad's currently equipped modular weapons to determine which
     * elemental affinities are missing from their arsenal.
     *
     * If the squad already has all elements covered, it returns all elements
     * to allow maximum flexibility.
     */
    fun analyzeMissingElements(squadWeapons: List<ModularWeaponInstance>): Set<ElementalAffinity> {
        val equippedElements = squadWeapons.map { it.magazine.elementalAffinity }.toSet()
        val allElements = ElementalAffinity.values().toSet()

        val missingElements = allElements - equippedElements

        return if (missingElements.isEmpty()) {
            allElements
        } else {
            missingElements
        }
    }

    /**
     * Recommends a preferred vendor based on squad's elemental synergy needs.
     */
    fun recommendVendor(missingElements: Set<ElementalAffinity>): VendingMachineBrand {
        return if (missingElements.contains(ElementalAffinity.CRYO) || missingElements.contains(ElementalAffinity.VOLTAIC)) {
            VendingMachineBrand.MARCUS_WEAPONS_OF_THE_DAY
        } else if (missingElements.contains(ElementalAffinity.INCENDIARY) || missingElements.contains(ElementalAffinity.CORROSIVE)) {
            VendingMachineBrand.TORGUE_AMMO_AND_ORDNANCE
        } else {
            VendingMachineBrand.ZED_MED_AND_SHIELDS
        }
    }

    /**
     * Analyzes dominant squad weapon affinities and returns the canonical vendor identifier string.
     */
    fun analyzeSynergyAndRecommendVendor(squadWeapons: List<ModularWeaponInstance>): String {
        if (squadWeapons.isEmpty()) {
            return "MARCUS_LEGENDARY_ARMS"
        }
        val counts = squadWeapons.groupingBy { it.magazine.elementalAffinity }.eachCount()
        val maxCount = counts.values.maxOrNull() ?: 0
        val topAffinities = counts.filterValues { it == maxCount }.keys
        return when {
            ElementalAffinity.CRYO in topAffinities -> "ZED_CRYO_SHIELDS"
            ElementalAffinity.INCENDIARY in topAffinities || ElementalAffinity.KINETIC in topAffinities -> "TORGUE_BLAST_ORDNANCE"
            else -> "MARCUS_LEGENDARY_ARMS"
        }
    }

    /**
     * Evaluates squad unit role/elemental tags and returns a normalized synergy score in [0.0, 2.0].
     */
    fun analyzeSynergy(units: List<String>): Double {
        if (units.isEmpty()) return 1.0
        val distinctNormalized = units.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        if (distinctNormalized.isEmpty()) return 1.0
        val diversityBonus = (distinctNormalized.size.coerceAtMost(5) - 1) * 0.18
        val comboBonus = if (
            distinctNormalized.any { "CRYO" in it } &&
            distinctNormalized.any { "EXPLOSIVE" in it || "TORGUE" in it || "FIRE" in it || "INCENDIARY" in it }
        ) {
            0.25
        } else {
            0.08
        }
        return (1.0 + diversityBonus + comboBonus).coerceIn(0.5, 2.0)
    }

    /**
     * Ranks vending offers by how effectively they fill the squad's missing elemental gaps.
     */
    fun prioritizeVendingOffers(
        offers: List<VendingCatalogOffer>,
        squadWeapons: List<ModularWeaponInstance>
    ): List<VendingCatalogOffer> {
        val missing = analyzeMissingElements(squadWeapons)
        val preferredBrand = recommendVendor(missing)
        return offers.sortedByDescending { offer ->
            val brandScore = if (offer.brand == preferredBrand) 100 else 0
            val elementScore = if (offer.weaponPayload?.magazine?.elementalAffinity in missing) 200 else 0
            val iotdScore = if (offer.isItemOfTheDay) 50 else 0
            brandScore + elementScore + iotdScore + offer.rarity.ordinal * 15
        }
    }

    fun evaluateSquadSynergyReport(squadWeapons: List<ModularWeaponInstance>): SquadSynergyReport {
        val covered = squadWeapons.map { it.magazine.elementalAffinity }.toSet()
        val missing = analyzeMissingElements(squadWeapons)
        val vendor = recommendVendor(missing)
        val unitTags = squadWeapons.map { "${it.receiver.manufacturer.name}_${it.magazine.elementalAffinity.name}" }
        val score = analyzeSynergy(unitTags)
        val avgDamage = if (squadWeapons.isEmpty()) 0.0 else squadWeapons.map { it.effectiveDamage }.average()
        var h = Fnv1a64Hasher.initial()
        h = Fnv1a64Hasher.mixString(h, vendor.name)
        h = Fnv1a64Hasher.mixDouble(h, score)
        h = Fnv1a64Hasher.mixInt(h, covered.size)
        return SquadSynergyReport(
            coveredElements = covered,
            missingElements = missing,
            recommendedVendor = vendor,
            synergyScore = score,
            averageWeaponDamage = avgDamage,
            checksum = h
        )
    }
}


fun evaluateSquadSynergyAnalyzerLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "SquadSynergyAnalyzer") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
