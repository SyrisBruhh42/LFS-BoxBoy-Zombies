package com.example.game.economy.vendors

import com.example.game.combat.BarrelNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class PlaceableRedBarrelDispenserTest {

    @Test
    fun testNominalStandardPurchase() {
        val dispenser = PlaceableRedBarrelDispenser(vendorX = 100f, vendorY = 100f)
        val attempt = dispenser.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 200, requestCryo = false)
        assertEquals(BarrelDispenseResult.SUCCESS, attempt.result)
        assertNotNull(attempt.barrel)
        assertEquals(150, attempt.costIncurred)
        assertFalse(attempt.barrel!!.isCryo)
        assertEquals(145f, attempt.barrel!!.blastRadiusPx)
        assertEquals(240, attempt.barrel!!.baseDamage)
    }

    @Test
    fun testNominalCryoPurchase() {
        val dispenser = PlaceableRedBarrelDispenser(vendorX = 100f, vendorY = 100f, baseCost = 100, cryoCostMultiplier = 1.5f)
        val attempt = dispenser.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 200, requestCryo = true)
        assertEquals(BarrelDispenseResult.SUCCESS, attempt.result)
        assertNotNull(attempt.barrel)
        assertEquals(150, attempt.costIncurred)
        assertTrue(attempt.barrel!!.isCryo)
        assertEquals(180f, attempt.barrel!!.blastRadiusPx)
        assertEquals(200, attempt.barrel!!.baseDamage)
    }

    @Test
    fun testDistanceCheckExactBoundary() {
        val dispenser = PlaceableRedBarrelDispenser(vendorX = 0f, vendorY = 0f, activationRadiusPx = 100f)
        val attempt = dispenser.attemptPurchase(playerX = 100f, playerY = 0f, currentCurrency = 500, requestCryo = false)
        assertEquals(BarrelDispenseResult.SUCCESS, attempt.result)
    }

    @Test
    fun testDistanceCheckSlightlyOverBoundary() {
        val dispenser = PlaceableRedBarrelDispenser(vendorX = 0f, vendorY = 0f, activationRadiusPx = 100f)
        val attempt = dispenser.attemptPurchase(playerX = 100.1f, playerY = 0f, currentCurrency = 500, requestCryo = false)
        assertEquals(BarrelDispenseResult.OUT_OF_RANGE, attempt.result)
        assertNull(attempt.barrel)
    }
    
    @Test
    fun testDistanceCheckVastlyOverBoundary() {
        val dispenser = PlaceableRedBarrelDispenser(vendorX = 0f, vendorY = 0f, activationRadiusPx = 100f)
        val attempt = dispenser.attemptPurchase(playerX = 1000f, playerY = 1000f, currentCurrency = 500, requestCryo = false)
        assertEquals(BarrelDispenseResult.OUT_OF_RANGE, attempt.result)
        assertNull(attempt.barrel)
    }

    @Test
    fun testInsufficientFundsStandard() {
        val dispenser = PlaceableRedBarrelDispenser(vendorX = 100f, vendorY = 100f, baseCost = 150)
        val attempt = dispenser.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 149, requestCryo = false)
        assertEquals(BarrelDispenseResult.INSUFFICIENT_FUNDS, attempt.result)
        assertNull(attempt.barrel)
    }
    
    @Test
    fun testInsufficientFundsCryo() {
        val dispenser = PlaceableRedBarrelDispenser(vendorX = 100f, vendorY = 100f, baseCost = 100, cryoCostMultiplier = 1.5f)
        val attempt = dispenser.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 149, requestCryo = true)
        assertEquals(BarrelDispenseResult.INSUFFICIENT_FUNDS, attempt.result)
        assertNull(attempt.barrel)
    }

    @Test
    fun testInvalidParameters() {
        val dispenser = PlaceableRedBarrelDispenser(vendorX = 100f, vendorY = 100f)
        
        var attempt = dispenser.attemptPurchase(playerX = Float.NaN, playerY = 100f, currentCurrency = 200, requestCryo = false)
        assertEquals(BarrelDispenseResult.INVALID_PARAMETERS, attempt.result)
        
        attempt = dispenser.attemptPurchase(playerX = 100f, playerY = Float.POSITIVE_INFINITY, currentCurrency = 200, requestCryo = false)
        assertEquals(BarrelDispenseResult.INVALID_PARAMETERS, attempt.result)
        
        attempt = dispenser.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = -1, requestCryo = false)
        assertEquals(BarrelDispenseResult.INVALID_PARAMETERS, attempt.result)
    }

    @Test
    fun testSerializationRoundtrip() {
        val config = DispenserConfig(vendorX = 10f, vendorY = 20f, baseCost = 200, cryoCostMultiplier = 2.0f, activationRadiusPx = 50f, maxActiveBarrels = 5)
        val map = config.toMap()
        val restored = DispenserConfig.fromMap(map)
        assertEquals(config, restored)
        
        val snapshot = DispenserStateSnapshot(activeBarrelCount = 3, nextBarrelId = 1005, seed = 123456L, checksum = 987654321L)
        val snapshotMap = snapshot.toMap()
        val restoredSnapshot = DispenserStateSnapshot.fromMap(snapshotMap)
        assertEquals(snapshot, restoredSnapshot)
    }

    @Test
    fun testChecksumValidation() {
        val dispenser = PlaceableRedBarrelDispenser(vendorX = 100f, vendorY = 100f)
        val snapshot = dispenser.getSnapshot()
        
        assertTrue(dispenser.restoreSnapshot(snapshot))
        
        val manipulatedSnapshot = snapshot.copy(activeBarrelCount = snapshot.activeBarrelCount + 1)
        assertFalse(dispenser.restoreSnapshot(manipulatedSnapshot))
        
        val anotherSnapshot = dispenser.getSnapshot()
        assertTrue(dispenser.restoreSnapshot(anotherSnapshot))
    }

    @Test
    fun testMaxActiveBarrelsConstraint() {
        val config = DispenserConfig(vendorX = 100f, vendorY = 100f, maxActiveBarrels = 2)
        val dispenser = PlaceableRedBarrelDispenser(config)
        
        var attempt = dispenser.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 500, requestCryo = false)
        assertEquals(BarrelDispenseResult.SUCCESS, attempt.result)
        
        attempt = dispenser.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 500, requestCryo = false)
        assertEquals(BarrelDispenseResult.SUCCESS, attempt.result)
        
        attempt = dispenser.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 500, requestCryo = false)
        assertEquals(BarrelDispenseResult.AT_CAPACITY, attempt.result)
        assertNull(attempt.barrel)
        
        dispenser.notifyBarrelDestroyed()
        
        attempt = dispenser.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 500, requestCryo = false)
        assertEquals(BarrelDispenseResult.SUCCESS, attempt.result)
    }

    @Test
    fun testSeededDeterminism() {
        val config = DispenserConfig(vendorX = 100f, vendorY = 100f)
        val seed = 999L
        
        val dispenser1 = PlaceableRedBarrelDispenser(config, initialSeed = seed)
        val attempt1a = dispenser1.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 500, requestCryo = false)
        val attempt1b = dispenser1.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 500, requestCryo = false)
        
        val dispenser2 = PlaceableRedBarrelDispenser(config, initialSeed = seed)
        val attempt2a = dispenser2.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 500, requestCryo = false)
        val attempt2b = dispenser2.attemptPurchase(playerX = 100f, playerY = 100f, currentCurrency = 500, requestCryo = false)
        
        assertEquals(attempt1a.barrel!!.x, attempt2a.barrel!!.x)
        assertEquals(attempt1a.barrel!!.y, attempt2a.barrel!!.y)
        assertEquals(attempt1b.barrel!!.x, attempt2b.barrel!!.x)
        assertEquals(attempt1b.barrel!!.y, attempt2b.barrel!!.y)
    }
}
