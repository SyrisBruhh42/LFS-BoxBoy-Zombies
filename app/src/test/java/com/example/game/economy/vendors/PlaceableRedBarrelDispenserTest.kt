package com.example.game.economy.vendors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaceableRedBarrelDispenserTest {

    @Test
    fun testPurchaseStandardBarrelSuccess() {
        val dispenser = PlaceableRedBarrelDispenser(
            vendorX = 100f,
            vendorY = 100f,
            baseCost = 150,
            cryoCostMultiplier = 1.5f,
            activationRadiusPx = 64f
        )
        
        // Player is close enough
        val result = dispenser.attemptPurchase(
            playerX = 110f,
            playerY = 110f,
            currentCurrency = 200,
            requestCryo = false
        )
        
        assertEquals(BarrelDispenseResult.SUCCESS, result.result)
        assertEquals(150, result.costIncurred)
        assertNotNull(result.barrel)
        assertFalse(result.barrel!!.isCryo)
        assertEquals(100f, result.barrel!!.x)
        assertEquals(100f, result.barrel!!.y)
    }

    @Test
    fun testPurchaseCryoBarrelSuccess() {
        val dispenser = PlaceableRedBarrelDispenser(
            vendorX = 50f,
            vendorY = 50f,
            baseCost = 100,
            cryoCostMultiplier = 2.0f, // Cryo should cost 200
            activationRadiusPx = 30f
        )
        
        // Player is exactly on it
        val result = dispenser.attemptPurchase(
            playerX = 50f,
            playerY = 50f,
            currentCurrency = 250,
            requestCryo = true
        )
        
        assertEquals(BarrelDispenseResult.SUCCESS, result.result)
        assertEquals(200, result.costIncurred)
        assertNotNull(result.barrel)
        assertTrue(result.barrel!!.isCryo)
        assertEquals(50f, result.barrel!!.x)
        assertEquals(50f, result.barrel!!.y)
    }

    @Test
    fun testPurchaseInsufficientCurrency() {
        val dispenser = PlaceableRedBarrelDispenser(
            vendorX = 0f,
            vendorY = 0f,
            baseCost = 200,
            activationRadiusPx = 100f
        )
        
        val result = dispenser.attemptPurchase(
            playerX = 0f,
            playerY = 0f,
            currentCurrency = 199, // 1 short
            requestCryo = false
        )
        
        assertEquals(BarrelDispenseResult.INSUFFICIENT_FUNDS, result.result)
        assertEquals(0, result.costIncurred)
        assertNull(result.barrel)
    }

    @Test
    fun testPurchaseCryoInsufficientCurrency() {
        val dispenser = PlaceableRedBarrelDispenser(
            vendorX = 0f,
            vendorY = 0f,
            baseCost = 100,
            cryoCostMultiplier = 1.5f,
            activationRadiusPx = 100f
        )
        
        // Cryo cost is 150
        val result = dispenser.attemptPurchase(
            playerX = 0f,
            playerY = 0f,
            currentCurrency = 149,
            requestCryo = true
        )
        
        assertEquals(BarrelDispenseResult.INSUFFICIENT_FUNDS, result.result)
        assertEquals(0, result.costIncurred)
        assertNull(result.barrel)
    }

    @Test
    fun testPurchaseOutOfRange() {
        val dispenser = PlaceableRedBarrelDispenser(
            vendorX = 200f,
            vendorY = 200f,
            baseCost = 100,
            activationRadiusPx = 50f
        )
        
        // Distance is sqrt(50^2 + 50^2) = ~70.7, which is > 50
        val result = dispenser.attemptPurchase(
            playerX = 250f,
            playerY = 250f,
            currentCurrency = 1000,
            requestCryo = false
        )
        
        assertEquals(BarrelDispenseResult.OUT_OF_RANGE, result.result)
        assertEquals(0, result.costIncurred)
        assertNull(result.barrel)
    }
}
