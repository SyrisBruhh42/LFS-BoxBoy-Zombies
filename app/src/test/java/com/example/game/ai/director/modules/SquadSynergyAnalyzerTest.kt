package com.example.game.ai.director.modules

import com.example.game.ElementalAffinity
import com.example.game.LootRarity
import com.example.game.ModularWeaponInstance
import com.example.game.WeaponBarrelPart
import com.example.game.WeaponMagazinePart
import com.example.game.WeaponOpticPart
import com.example.game.WeaponReceiverPart
import com.example.game.WeaponManufacturer
import org.junit.Assert.assertEquals
import org.junit.Test

class SquadSynergyAnalyzerTest {

    private fun createWeaponWithElement(element: ElementalAffinity): ModularWeaponInstance {
        return ModularWeaponInstance(
            instanceId = "test_id",
            baseWeaponId = "pistol",
            customTitle = "Test Pistol",
            rarity = LootRarity.COMMON,
            itemLevel = 1,
            anvilUpgradeTier = 0,
            receiver = WeaponReceiverPart("Test Receiver", WeaponManufacturer.KORSAKOV_ARMORIES, 1.0f, 1.0f),
            barrel = WeaponBarrelPart("Test Barrel", 0.0f, 0, 0, 1.0f),
            magazine = WeaponMagazinePart("Test Magazine", 0, 1.0f, element),
            optic = WeaponOpticPart("Test Optic", 1.0f, 100f, 0.5f)
        )
    }

    @Test
    fun testEmptySquadWeapons_recommendsAllElements() {
        val squadWeapons = emptyList<ModularWeaponInstance>()
        val missingElements = SquadSynergyAnalyzer.analyzeMissingElements(squadWeapons)

        assertEquals(ElementalAffinity.values().toSet(), missingElements)
    }

    @Test
    fun testSquadWithKineticOnly_recommendsOtherElements() {
        val squadWeapons = listOf(createWeaponWithElement(ElementalAffinity.KINETIC))
        val missingElements = SquadSynergyAnalyzer.analyzeMissingElements(squadWeapons)

        val expected = ElementalAffinity.values().toSet() - ElementalAffinity.KINETIC
        assertEquals(expected, missingElements)
    }

    @Test
    fun testSquadWithMultipleElements_recommendsRemaining() {
        val squadWeapons = listOf(
            createWeaponWithElement(ElementalAffinity.INCENDIARY),
            createWeaponWithElement(ElementalAffinity.CRYO)
        )
        val missingElements = SquadSynergyAnalyzer.analyzeMissingElements(squadWeapons)

        val expected = ElementalAffinity.values().toSet() - setOf(ElementalAffinity.INCENDIARY, ElementalAffinity.CRYO)
        assertEquals(expected, missingElements)
    }

    @Test
    fun testSquadWithAllElements_recommendsAllElements() {
        val squadWeapons = ElementalAffinity.values().map { createWeaponWithElement(it) }
        val missingElements = SquadSynergyAnalyzer.analyzeMissingElements(squadWeapons)

        assertEquals(ElementalAffinity.values().toSet(), missingElements)
    }
}
