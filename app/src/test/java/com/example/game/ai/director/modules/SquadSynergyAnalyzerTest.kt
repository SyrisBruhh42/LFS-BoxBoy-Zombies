package com.example.game.ai.director.modules

import com.example.game.ElementalAffinity
import com.example.game.LootRarity
import com.example.game.ModularWeaponInstance
import com.example.game.WeaponBarrelPart
import com.example.game.WeaponMagazinePart
import com.example.game.WeaponManufacturer
import com.example.game.WeaponOpticPart
import com.example.game.WeaponReceiverPart
import org.junit.Assert.assertEquals
import org.junit.Test

class SquadSynergyAnalyzerTest {

    private fun createWeaponWithAffinity(affinity: ElementalAffinity): ModularWeaponInstance {
        return ModularWeaponInstance(
            instanceId = "test_wpn_${affinity.name}",
            baseWeaponId = "pistol",
            customTitle = "Test ${affinity.name} Weapon",
            rarity = LootRarity.COMMON,
            itemLevel = 1,
            receiver = WeaponReceiverPart(
                name = "Test Receiver",
                manufacturer = WeaponManufacturer.KORSAKOV_ARMORIES,
                baseDamageBonus = 1.0f,
                fireRateBonus = 1.0f
            ),
            barrel = WeaponBarrelPart(
                name = "Test Barrel",
                spreadModifier = 1.0f,
                bonusPierce = 0,
                bonusPellets = 0,
                rangeBonusPx = 0f
            ),
            magazine = WeaponMagazinePart(
                name = "Test Magazine",
                magSizeBonus = 0,
                reloadSpeedMultiplier = 1.0f,
                elementalAffinity = affinity
            ),
            optic = WeaponOpticPart(
                name = "Test Optic",
                lightLockCritMultiplier = 1.0f,
                lanternRangeBonusPx = 0f,
                lightLockConeBonusRad = 0f
            )
        )
    }

    @Test
    fun testEmptySquadWeaponsReturnsMarcus() {
        val result = SquadSynergyAnalyzer.analyzeSynergyAndRecommendVendor(emptyList())
        assertEquals("MARCUS_LEGENDARY_ARMS", result)
    }

    @Test
    fun testCryoMostCommonReturnsZed() {
        val weapons = listOf(
            createWeaponWithAffinity(ElementalAffinity.CRYO),
            createWeaponWithAffinity(ElementalAffinity.CRYO),
            createWeaponWithAffinity(ElementalAffinity.KINETIC)
        )
        val result = SquadSynergyAnalyzer.analyzeSynergyAndRecommendVendor(weapons)
        assertEquals("ZED_CRYO_SHIELDS", result)
    }
    
    @Test
    fun testCryoTieReturnsZed() {
        val weapons = listOf(
            createWeaponWithAffinity(ElementalAffinity.CRYO),
            createWeaponWithAffinity(ElementalAffinity.KINETIC)
        )
        val result = SquadSynergyAnalyzer.analyzeSynergyAndRecommendVendor(weapons)
        assertEquals("ZED_CRYO_SHIELDS", result)
    }

    @Test
    fun testIncendiaryMostCommonReturnsTorgue() {
        val weapons = listOf(
            createWeaponWithAffinity(ElementalAffinity.INCENDIARY),
            createWeaponWithAffinity(ElementalAffinity.INCENDIARY),
            createWeaponWithAffinity(ElementalAffinity.VOLTAIC)
        )
        val result = SquadSynergyAnalyzer.analyzeSynergyAndRecommendVendor(weapons)
        assertEquals("TORGUE_BLAST_ORDNANCE", result)
    }

    @Test
    fun testKineticMostCommonReturnsTorgue() {
        val weapons = listOf(
            createWeaponWithAffinity(ElementalAffinity.KINETIC),
            createWeaponWithAffinity(ElementalAffinity.KINETIC),
            createWeaponWithAffinity(ElementalAffinity.CORROSIVE)
        )
        val result = SquadSynergyAnalyzer.analyzeSynergyAndRecommendVendor(weapons)
        assertEquals("TORGUE_BLAST_ORDNANCE", result)
    }
    
    @Test
    fun testIncendiaryTieWithKineticReturnsTorgue() {
        val weapons = listOf(
            createWeaponWithAffinity(ElementalAffinity.KINETIC),
            createWeaponWithAffinity(ElementalAffinity.INCENDIARY)
        )
        val result = SquadSynergyAnalyzer.analyzeSynergyAndRecommendVendor(weapons)
        assertEquals("TORGUE_BLAST_ORDNANCE", result)
    }

    @Test
    fun testVoltaicMostCommonReturnsMarcus() {
        val weapons = listOf(
            createWeaponWithAffinity(ElementalAffinity.VOLTAIC),
            createWeaponWithAffinity(ElementalAffinity.VOLTAIC),
            createWeaponWithAffinity(ElementalAffinity.KINETIC)
        )
        val result = SquadSynergyAnalyzer.analyzeSynergyAndRecommendVendor(weapons)
        assertEquals("MARCUS_LEGENDARY_ARMS", result)
    }

    @Test
    fun testCorrosiveMostCommonReturnsMarcus() {
        val weapons = listOf(
            createWeaponWithAffinity(ElementalAffinity.CORROSIVE),
            createWeaponWithAffinity(ElementalAffinity.CORROSIVE),
            createWeaponWithAffinity(ElementalAffinity.INCENDIARY)
        )
        val result = SquadSynergyAnalyzer.analyzeSynergyAndRecommendVendor(weapons)
        assertEquals("MARCUS_LEGENDARY_ARMS", result)
    }
}
