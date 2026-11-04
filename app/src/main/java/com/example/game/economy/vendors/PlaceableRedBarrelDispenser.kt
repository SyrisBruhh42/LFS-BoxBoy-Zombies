package com.example.game.economy.vendors

import com.example.game.combat.BarrelNode
import kotlin.math.hypot

enum class BarrelDispenseResult {
    SUCCESS,
    INSUFFICIENT_FUNDS,
    OUT_OF_RANGE
}

data class DispenseAttempt(
    val result: BarrelDispenseResult,
    val barrel: BarrelNode? = null,
    val costIncurred: Int = 0
)

class PlaceableRedBarrelDispenser(
    val vendorX: Float,
    val vendorY: Float,
    val baseCost: Int = 150,
    val cryoCostMultiplier: Float = 1.5f,
    val activationRadiusPx: Float = 64f
) {
    private var nextBarrelId = 1000

    fun attemptPurchase(
        playerX: Float,
        playerY: Float,
        currentCurrency: Int,
        requestCryo: Boolean
    ): DispenseAttempt {
        val distance = hypot(playerX - vendorX, playerY - vendorY)
        if (distance > activationRadiusPx) {
            return DispenseAttempt(BarrelDispenseResult.OUT_OF_RANGE)
        }

        val actualCost = if (requestCryo) (baseCost * cryoCostMultiplier).toInt() else baseCost

        if (currentCurrency < actualCost) {
            return DispenseAttempt(BarrelDispenseResult.INSUFFICIENT_FUNDS)
        }

        val dispensedBarrel = BarrelNode(
            id = nextBarrelId++,
            x = vendorX,
            y = vendorY,
            isCryo = requestCryo,
            blastRadiusPx = 145f,
            baseDamage = 240,
            detonated = false
        )

        return DispenseAttempt(
            result = BarrelDispenseResult.SUCCESS,
            barrel = dispensedBarrel,
            costIncurred = actualCost
        )
    }
}
