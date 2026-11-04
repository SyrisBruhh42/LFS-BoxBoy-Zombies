package com.example.game.economy.vendors

import com.example.game.ElementalAffinity
import com.example.game.GameSessionState
import com.example.game.ModularWeaponGenerator
import com.example.game.WeaponUpgradeProgression
import org.junit.Assert.*
import org.junit.Test

class PackAPunchTier1To5ForgeTest {

    private fun createInitialState(points: Int = 10000, weaponId: String = "pistol"): GameSessionState {
        val starterWeapon = ModularWeaponGenerator.createStarterModularWeapon(weaponId)
        return GameSessionState(
            points = points,
            unlockedWeaponIds = listOf(weaponId),
            weaponLevels = mapOf(weaponId to 0), // Assuming level 0 is unupgraded
            currentWeaponIndex = 0,
            modularWeaponInventory = mapOf(weaponId to starterWeapon),
            equippedModularWeapon = starterWeapon,
            currentMagAmmo = mapOf(weaponId to starterWeapon.effectiveMagSize),
            mapSeed = 42L
        )
    }

    @Test
    fun testNominalTier1Upgrade() {
        val state = createInitialState(points = 5000, weaponId = "pistol")
        
        val result = PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol")
        assertTrue(result is PackAPunchPurchaseResult.Success)
        
        val success = result as PackAPunchPurchaseResult.Success
        assertEquals(2500, success.cost) // Tier 1 cost
        assertEquals(2500, success.newState.points) // 5000 - 2500
        
        val upgradedWeapon = success.newState.modularWeaponInventory["pistol"]
        assertNotNull(upgradedWeapon)
        assertEquals(1, upgradedWeapon!!.anvilUpgradeTier)
        assertEquals(1, success.newState.weaponLevels["pistol"])
        
        // Ammo should be refilled to the new max
        assertEquals(upgradedWeapon.effectiveMagSize, success.newState.currentMagAmmo["pistol"])
        
        // Equipped weapon should also be upgraded
        assertEquals(1, success.newState.equippedModularWeapon.anvilUpgradeTier)
    }

    @Test
    fun testTier2Upgrade() {
        var state = createInitialState(points = 10000)
        state = (PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol") as PackAPunchPurchaseResult.Success).newState
        
        val result = PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol")
        assertTrue(result is PackAPunchPurchaseResult.Success)
        
        val success = result as PackAPunchPurchaseResult.Success
        assertEquals(5000, success.cost) // Tier 2 cost
        assertEquals(2, success.newState.modularWeaponInventory["pistol"]!!.anvilUpgradeTier)
        assertEquals(2, success.newState.weaponLevels["pistol"])
    }

    @Test
    fun testTier3Upgrade() {
        var state = createInitialState(points = 20000)
        state = (PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol") as PackAPunchPurchaseResult.Success).newState // Tier 1
        state = (PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol") as PackAPunchPurchaseResult.Success).newState // Tier 2
        
        val result = PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol")
        assertTrue(result is PackAPunchPurchaseResult.Success)
        
        val success = result as PackAPunchPurchaseResult.Success
        assertEquals(7500, success.cost) // Tier 3 cost
        assertEquals(3, success.newState.modularWeaponInventory["pistol"]!!.anvilUpgradeTier)
    }

    @Test
    fun testTier4ElementalOverchargeKineticToElement() {
        var state = createInitialState(points = 50000)
        // Verify starter is kinetic
        val initialWeapon = state.modularWeaponInventory["pistol"]!!
        assertEquals(ElementalAffinity.KINETIC, initialWeapon.magazine.elementalAffinity)
        
        state = (PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol") as PackAPunchPurchaseResult.Success).newState // 1
        state = (PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol") as PackAPunchPurchaseResult.Success).newState // 2
        state = (PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol") as PackAPunchPurchaseResult.Success).newState // 3
        
        val result = PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol")
        assertTrue(result is PackAPunchPurchaseResult.Success)
        val success = result as PackAPunchPurchaseResult.Success
        assertEquals(10000, success.cost)
        
        val upgradedWeapon = success.newState.modularWeaponInventory["pistol"]!!
        assertEquals(4, upgradedWeapon.anvilUpgradeTier)
        
        // Should have changed from KINETIC to an element (deterministic)
        assertNotEquals(ElementalAffinity.KINETIC, upgradedWeapon.magazine.elementalAffinity)
    }

    @Test
    fun testTier4ElementalOverchargeDoesNotOverrideExistingElement() {
        val state = createInitialState(points = 50000)
        val initialWeapon = state.modularWeaponInventory["pistol"]!!
        // Force element to INCENDIARY
        val newMag = initialWeapon.magazine.copy(elementalAffinity = ElementalAffinity.INCENDIARY)
        val modifiedWeapon = initialWeapon.copy(magazine = newMag, anvilUpgradeTier = 3)
        val stateWithModifiedWeapon = state.copy(
            modularWeaponInventory = mapOf("pistol" to modifiedWeapon),
            weaponLevels = mapOf("pistol" to 3)
        )
        
        val result = PackAPunchTier1To5Forge.processUpgradePurchase(stateWithModifiedWeapon, "pistol")
        assertTrue(result is PackAPunchPurchaseResult.Success)
        
        val upgradedWeapon = (result as PackAPunchPurchaseResult.Success).newState.modularWeaponInventory["pistol"]!!
        assertEquals(4, upgradedWeapon.anvilUpgradeTier)
        assertEquals(ElementalAffinity.INCENDIARY, upgradedWeapon.magazine.elementalAffinity) // Element should remain INCENDIARY
    }

    @Test
    fun testTier5ApexUpgrade() {
        var state = createInitialState(points = 50000)
        state = (PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol") as PackAPunchPurchaseResult.Success).newState // 1
        state = (PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol") as PackAPunchPurchaseResult.Success).newState // 2
        state = (PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol") as PackAPunchPurchaseResult.Success).newState // 3
        state = (PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol") as PackAPunchPurchaseResult.Success).newState // 4
        
        val result = PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol")
        assertTrue(result is PackAPunchPurchaseResult.Success)
        
        val success = result as PackAPunchPurchaseResult.Success
        assertEquals(10000, success.cost) // Tier 5 cost is 10000
        val upgradedWeapon = success.newState.modularWeaponInventory["pistol"]!!
        assertEquals(5, upgradedWeapon.anvilUpgradeTier)
    }

    @Test
    fun testMaxUpgradeLevelExceeded() {
        val state = createInitialState(points = 50000)
        val maxedWeapon = state.modularWeaponInventory["pistol"]!!.copy(anvilUpgradeTier = 5)
        val stateWithMaxedWeapon = state.copy(
            modularWeaponInventory = mapOf("pistol" to maxedWeapon)
        )
        
        val result = PackAPunchTier1To5Forge.processUpgradePurchase(stateWithMaxedWeapon, "pistol")
        assertTrue(result is PackAPunchPurchaseResult.Error)
        assertEquals("Weapon is already at maximum Pack-a-Punch tier", (result as PackAPunchPurchaseResult.Error).reason)
    }

    @Test
    fun testInsufficientFunds() {
        val state = createInitialState(points = 1000) // Need 2500 for Tier 1
        
        val result = PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol")
        assertTrue(result is PackAPunchPurchaseResult.Error)
        assertEquals("Insufficient Currency Points", (result as PackAPunchPurchaseResult.Error).reason)
    }

    @Test
    fun testWeaponNotInInventory() {
        val state = createInitialState(points = 10000)
        
        val result = PackAPunchTier1To5Forge.processUpgradePurchase(state, "shotgun")
        assertTrue(result is PackAPunchPurchaseResult.Error)
        assertEquals("Weapon not found in inventory", (result as PackAPunchPurchaseResult.Error).reason)
    }
    
    @Test
    fun testDeterminismOfElementalOvercharge() {
        val state1 = createInitialState(points = 50000).copy(mapSeed = 12345L)
        val stateWithTier3_1 = state1.copy(
            modularWeaponInventory = mapOf("pistol" to state1.modularWeaponInventory["pistol"]!!.copy(anvilUpgradeTier = 3))
        )
        val result1 = PackAPunchTier1To5Forge.processUpgradePurchase(stateWithTier3_1, "pistol") as PackAPunchPurchaseResult.Success
        val element1 = result1.newState.modularWeaponInventory["pistol"]!!.magazine.elementalAffinity

        val state2 = createInitialState(points = 50000).copy(mapSeed = 12345L) // Same seed
        val stateWithTier3_2 = state2.copy(
            modularWeaponInventory = mapOf("pistol" to state2.modularWeaponInventory["pistol"]!!.copy(anvilUpgradeTier = 3))
        )
        val result2 = PackAPunchTier1To5Forge.processUpgradePurchase(stateWithTier3_2, "pistol") as PackAPunchPurchaseResult.Success
        val element2 = result2.newState.modularWeaponInventory["pistol"]!!.magazine.elementalAffinity
        
        assertEquals("Same seed should produce same element", element1, element2)
        assertNotEquals(ElementalAffinity.KINETIC, element1)
    }
    
    @Test
    fun testExactFunds() {
        val state = createInitialState(points = 2500)
        
        val result = PackAPunchTier1To5Forge.processUpgradePurchase(state, "pistol")
        assertTrue(result is PackAPunchPurchaseResult.Success)
        val success = result as PackAPunchPurchaseResult.Success
        assertEquals(0, success.newState.points) // Balance becomes exactly 0
    }
    
    @Test
    fun testEquippedWeaponUpdatedProperly() {
        val state = createInitialState(points = 10000, weaponId = "shotgun")
        val result = PackAPunchTier1To5Forge.processUpgradePurchase(state, "shotgun")
        
        assertTrue(result is PackAPunchPurchaseResult.Success)
        val newState = (result as PackAPunchPurchaseResult.Success).newState
        
        val invWeapon = newState.modularWeaponInventory["shotgun"]!!
        val eqpWeapon = newState.equippedModularWeapon
        
        assertEquals(1, invWeapon.anvilUpgradeTier)
        assertEquals(1, eqpWeapon.anvilUpgradeTier)
        // They should be identical
        assertEquals(invWeapon, eqpWeapon)
    }

    @Test
    fun testNotEquippedWeaponUpdatedProperly() {
        // State where player holds pistol but we upgrade shotgun
        val starterPistol = ModularWeaponGenerator.createStarterModularWeapon("pistol")
        val starterShotgun = ModularWeaponGenerator.createStarterModularWeapon("shotgun")
        
        val state = GameSessionState(
            points = 10000,
            unlockedWeaponIds = listOf("pistol", "shotgun"),
            weaponLevels = mapOf("pistol" to 0, "shotgun" to 0),
            currentWeaponIndex = 0,
            modularWeaponInventory = mapOf("pistol" to starterPistol, "shotgun" to starterShotgun),
            equippedModularWeapon = starterPistol, // Pistol is equipped
            currentMagAmmo = mapOf("pistol" to starterPistol.effectiveMagSize, "shotgun" to starterShotgun.effectiveMagSize),
            mapSeed = 42L
        )

        val result = PackAPunchTier1To5Forge.processUpgradePurchase(state, "shotgun")
        assertTrue(result is PackAPunchPurchaseResult.Success)
        
        val newState = (result as PackAPunchPurchaseResult.Success).newState
        
        // Shotgun should be upgraded
        assertEquals(1, newState.modularWeaponInventory["shotgun"]!!.anvilUpgradeTier)
        // Pistol should NOT be upgraded
        assertEquals(0, newState.modularWeaponInventory["pistol"]!!.anvilUpgradeTier)
        // Equipped weapon should still be un-upgraded pistol
        assertEquals(0, newState.equippedModularWeapon.anvilUpgradeTier)
        assertEquals("pistol", newState.equippedModularWeapon.baseWeaponId)
    }
}
