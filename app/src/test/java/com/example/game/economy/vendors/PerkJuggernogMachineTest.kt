package com.example.game.economy.vendors

import com.example.data.PerkType
import com.example.game.GameSessionState
import com.example.game.economy.VendingCatalogOffer
import com.example.game.economy.VendingMachineBrand
import org.junit.Assert.*
import org.junit.Test

class PerkJuggernogMachineTest {

    @Test
    fun testPurchaseSuccess() {
        val initialState = GameSessionState(
            points = 5000,
            playerX = 10f,
            playerY = 10f,
            hp = 100,
            maxHp = 100,
            activePerks = emptySet()
        )
        val machineState = JuggernogMachineState("m1", 10f, 10f)

        val result = PerkJuggernogMachine.processPurchase(initialState, machineState)
        assertTrue(result is JuggernogPurchaseResult.Success)

        val success = result as JuggernogPurchaseResult.Success
        assertEquals(2500, success.newState.points) // JUGGERNOG cost is 2500
        assertTrue(PerkType.JUGGERNOG in success.newState.activePerks)
        assertEquals(200, success.newState.maxHp)
        assertEquals(200, success.newState.hp)
        assertEquals(1, success.updatedMachine.usesCount)
    }

    @Test
    fun testOutOfRange() {
        val initialState = GameSessionState(
            points = 5000,
            playerX = 1000f,
            playerY = 1000f
        )
        val machineState = JuggernogMachineState("m1", 10f, 10f)

        val result = PerkJuggernogMachine.processPurchase(initialState, machineState)
        assertTrue(result is JuggernogPurchaseResult.Error)
        assertEquals("Player is out of interaction range.", (result as JuggernogPurchaseResult.Error).reason)
    }

    @Test
    fun testInsufficientFunds() {
        val initialState = GameSessionState(
            points = 100,
            playerX = 10f,
            playerY = 10f,
            hp = 100,
            maxHp = 100,
            activePerks = emptySet()
        )
        val machineState = JuggernogMachineState("m1", 10f, 10f)

        val result = PerkJuggernogMachine.processPurchase(initialState, machineState)
        assertTrue(result is JuggernogPurchaseResult.Error)
        assertEquals("Insufficient Currency Points", (result as JuggernogPurchaseResult.Error).reason)
    }

    @Test
    fun testAlreadyActive() {
        val initialState = GameSessionState(
            points = 5000,
            playerX = 10f,
            playerY = 10f,
            hp = 100,
            maxHp = 100,
            activePerks = setOf(PerkType.JUGGERNOG)
        )
        val machineState = JuggernogMachineState("m1", 10f, 10f)

        val result = PerkJuggernogMachine.processPurchase(initialState, machineState)
        assertTrue(result is JuggernogPurchaseResult.Error)
        assertEquals("Juggernog is already active.", (result as JuggernogPurchaseResult.Error).reason)
    }

    @Test
    fun testMachineInactive() {
        val initialState = GameSessionState(
            points = 5000,
            playerX = 10f,
            playerY = 10f,
            hp = 100,
            maxHp = 100,
            activePerks = emptySet()
        )
        val machineState = JuggernogMachineState("m1", 10f, 10f, isActive = false)

        val result = PerkJuggernogMachine.processPurchase(initialState, machineState)
        assertTrue(result is JuggernogPurchaseResult.Error)
        assertEquals("Juggernog Machine is offline or unpowered.", (result as JuggernogPurchaseResult.Error).reason)
    }

    @Test
    fun testVendorBrandMismatch() {
        val initialState = GameSessionState(
            points = 5000,
            playerX = 10f,
            playerY = 10f,
            hp = 100,
            maxHp = 100,
            activePerks = emptySet()
        )
        val machineState = JuggernogMachineState("m1", 10f, 10f)

        val offer = VendingCatalogOffer(
            offerId = "jugg_wrong",
            brand = VendingMachineBrand.ZED_MED_AND_SHIELDS,
            title = "Wrong",
            subtitle = "Wrong",
            costCurrency = 2500,
            perkPayload = PerkType.JUGGERNOG
        )

        val result = PerkJuggernogMachine.processPurchase(initialState, machineState, offer = offer)
        assertTrue(result is JuggernogPurchaseResult.Error)
        assertEquals("Vendor Brand Mismatch: Required PERK_A_COLA_FOUNTAIN", (result as JuggernogPurchaseResult.Error).reason)
    }

    @Test
    fun testPerkPayloadMismatch() {
        val initialState = GameSessionState(
            points = 5000,
            playerX = 10f,
            playerY = 10f,
            hp = 100,
            maxHp = 100,
            activePerks = emptySet()
        )
        val machineState = JuggernogMachineState("m1", 10f, 10f)

        val offer = VendingCatalogOffer(
            offerId = "jugg_wrong",
            brand = VendingMachineBrand.PERK_A_COLA_FOUNTAIN,
            title = "Wrong",
            subtitle = "Wrong",
            costCurrency = 2500,
            perkPayload = PerkType.SPEED_COLA
        )

        val result = PerkJuggernogMachine.processPurchase(initialState, machineState, offer = offer)
        assertTrue(result is JuggernogPurchaseResult.Error)
        assertEquals("Perk Payload Mismatch: Required JUGGERNOG", (result as JuggernogPurchaseResult.Error).reason)
    }

    @Test
    fun testShieldRegenNotActive() {
        val initialState = GameSessionState(
            hp = 100,
            maxHp = 100,
            shield = 0,
            maxShield = 100,
            activePerks = emptySet()
        )

        val newState = PerkJuggernogMachine.tickShieldRegen(initialState, 0.1f, 123L)
        assertEquals(0, newState.shield) // Should not regenerate
    }

    @Test
    fun testShieldRegenActive() {
        val initialState = GameSessionState(
            hp = 100,
            maxHp = 200, // Jugg active
            shield = 50,
            maxShield = 100,
            activePerks = setOf(PerkType.JUGGERNOG)
        )

        val config = JuggernogMachineConfig(bonusShieldRegenRate = 10.0f)

        // 0.1s * 10.0f regen = 1.0f shield
        val newState = PerkJuggernogMachine.tickShieldRegen(initialState, 0.1f, 123L, config = config)
        assertEquals(51, newState.shield)
    }

    @Test
    fun testShieldRegenCapped() {
        val initialState = GameSessionState(
            hp = 100,
            maxHp = 200,
            shield = 100,
            maxShield = 100,
            activePerks = setOf(PerkType.JUGGERNOG)
        )

        val config = JuggernogMachineConfig(bonusShieldRegenRate = 10.0f)
        val newState = PerkJuggernogMachine.tickShieldRegen(initialState, 0.1f, 123L, config = config)
        assertEquals(100, newState.shield)
    }

    @Test
    fun testShieldRegenPlayerDowned() {
        val initialState = GameSessionState(
            hp = 100,
            maxHp = 200,
            shield = 0,
            maxShield = 100,
            isPlayerDowned = true,
            activePerks = setOf(PerkType.JUGGERNOG)
        )

        val config = JuggernogMachineConfig(bonusShieldRegenRate = 10.0f)
        val newState = PerkJuggernogMachine.tickShieldRegen(initialState, 0.1f, 123L, config = config)
        assertEquals(0, newState.shield)
    }

    @Test
    fun testDeterminism() {
        val initialState = GameSessionState(
            hp = 100,
            maxHp = 200,
            shield = 50,
            maxShield = 100,
            activePerks = setOf(PerkType.JUGGERNOG)
        )

        // fractional regen test
        val config = JuggernogMachineConfig(bonusShieldRegenRate = 1.0f)
        val newState1 = PerkJuggernogMachine.tickShieldRegen(initialState, 0.1f, 42L, config = config)
        val newState2 = PerkJuggernogMachine.tickShieldRegen(initialState, 0.1f, 42L, config = config)

        assertEquals(newState1.shield, newState2.shield)
    }

    @Test
    fun testSerializationRoundtrip() {
        val config = JuggernogMachineConfig(interactionRadius = 200f, cost = 3000)
        val configMap = config.toMap()
        val restoredConfig = JuggernogMachineConfig.fromMap(configMap)
        assertEquals(config, restoredConfig)

        val state = JuggernogMachineState(machineId = "test1", x = 123f, y = 456f, isActive = false, usesCount = 5)
        val stateMap = state.toMap()
        val restoredState = JuggernogMachineState.fromMap(stateMap)
        assertEquals(state, restoredState)
    }

    @Test
    fun testChecksumParity() {
        val state1 = JuggernogMachineState(machineId = "test1", x = 123f, y = 456f, isActive = false, usesCount = 5)
        val state2 = JuggernogMachineState(machineId = "test1", x = 123f, y = 456f, isActive = false, usesCount = 5)

        assertEquals(state1.computeChecksum(), state2.computeChecksum())
    }

    @Test
    fun testNaNInjection() {
        val initialState = GameSessionState(
            points = 5000,
            playerX = Float.NaN,
            playerY = Float.NaN
        )
        val machineState = JuggernogMachineState("m1", 10f, 10f)

        val result = PerkJuggernogMachine.processPurchase(initialState, machineState)
        assertTrue(result is JuggernogPurchaseResult.Error)
        assertEquals("Player is out of interaction range.", (result as JuggernogPurchaseResult.Error).reason)

        val regenState = GameSessionState(
            hp = 100,
            maxHp = 200,
            shield = 50,
            maxShield = 100,
            activePerks = setOf(PerkType.JUGGERNOG)
        )

        val config = JuggernogMachineConfig(bonusShieldRegenRate = 10.0f)
        val newState = PerkJuggernogMachine.tickShieldRegen(regenState, Float.NaN, 123L, config = config)
        // NaN dt should be clamped/fallback to 0.016f. 0.016f * 10 = 0.16 -> fraction.
        // It shouldn't crash.
        assertTrue(newState.shield >= 50)
    }
}
