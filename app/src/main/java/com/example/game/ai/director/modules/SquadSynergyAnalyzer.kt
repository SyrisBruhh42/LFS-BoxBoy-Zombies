package com.example.game.ai.director.modules

import com.example.game.ElementalAffinity
import com.example.game.ModularWeaponInstance

object SquadSynergyAnalyzer {

    /**
     * Analyzes the squad's weapon loadout and recommends a synergistic Vending Machine vendor tag.
     */
    fun analyzeSynergyAndRecommendVendor(squadWeapons: List<ModularWeaponInstance>): String {
        if (squadWeapons.isEmpty()) {
            return "MARCUS_LEGENDARY_ARMS"
        }

        // Count occurrences of each ElementalAffinity in the squad's weapons
        val elementCounts = mutableMapOf<ElementalAffinity, Int>()
        for (weapon in squadWeapons) {
            val affinity = weapon.magazine.elementalAffinity
            elementCounts[affinity] = elementCounts.getOrDefault(affinity, 0) + 1
        }

        // Find the maximum count
        val maxCount = elementCounts.values.maxOrNull() ?: 0

        // Find all affinities that have the maximum count (could be a tie)
        val mostCommonAffinities = elementCounts.filterValues { it == maxCount }.keys

        // Apply recommendation logic based on the most common elements
        if (mostCommonAffinities.contains(ElementalAffinity.CRYO)) {
            return "ZED_CRYO_SHIELDS"
        } else if (mostCommonAffinities.contains(ElementalAffinity.INCENDIARY) || mostCommonAffinities.contains(ElementalAffinity.KINETIC)) {
            return "TORGUE_BLAST_ORDNANCE"
        } else {
            return "MARCUS_LEGENDARY_ARMS"
        }
    }
}
