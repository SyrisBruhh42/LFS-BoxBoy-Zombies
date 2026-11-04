package com.example.game.ai.director.modules

import com.example.game.ElementalAffinity
import com.example.game.ModularWeaponInstance
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand

/**
 * Local Open-Source AI Director Module for Unified BoxBoys Track 2.
 * Analyzes squad weapon elements and recommends complementary Vending Machine rolls.
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
}
