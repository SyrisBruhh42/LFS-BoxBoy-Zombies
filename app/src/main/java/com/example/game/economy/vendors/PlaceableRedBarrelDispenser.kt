package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.game.combat.BarrelNode
import kotlin.math.hypot
import kotlin.math.roundToInt

@Keep
enum class BarrelDispenseResult {
    SUCCESS,
    INSUFFICIENT_FUNDS,
    OUT_OF_RANGE,
    AT_CAPACITY,
    INVALID_PARAMETERS
}

@Keep
data class DispenserConfig(
    val vendorX: Float = 0f,
    val vendorY: Float = 0f,
    val baseCost: Int = 150,
    val cryoCostMultiplier: Float = 1.5f,
    val activationRadiusPx: Float = 64f,
    val maxActiveBarrels: Int = 10
) {
    fun toMap(): Map<String, Any> = mapOf(
        "vendorX" to vendorX,
        "vendorY" to vendorY,
        "baseCost" to baseCost,
        "cryoCostMultiplier" to cryoCostMultiplier,
        "activationRadiusPx" to activationRadiusPx,
        "maxActiveBarrels" to maxActiveBarrels
    )

    companion object {
        fun fromMap(map: Map<String, Any>): DispenserConfig {
            return DispenserConfig(
                vendorX = (map["vendorX"] as? Number)?.toFloat() ?: 0f,
                vendorY = (map["vendorY"] as? Number)?.toFloat() ?: 0f,
                baseCost = (map["baseCost"] as? Number)?.toInt() ?: 150,
                cryoCostMultiplier = (map["cryoCostMultiplier"] as? Number)?.toFloat() ?: 1.5f,
                activationRadiusPx = (map["activationRadiusPx"] as? Number)?.toFloat() ?: 64f,
                maxActiveBarrels = (map["maxActiveBarrels"] as? Number)?.toInt() ?: 10
            )
        }
    }
}

@Keep
data class DispenserStateSnapshot(
    val activeBarrelCount: Int,
    val nextBarrelId: Int,
    val seed: Long,
    val checksum: Long
) {
    fun toMap(): Map<String, Any> = mapOf(
        "activeBarrelCount" to activeBarrelCount,
        "nextBarrelId" to nextBarrelId,
        "seed" to seed,
        "checksum" to checksum
    )

    companion object {
        fun fromMap(map: Map<String, Any>): DispenserStateSnapshot {
            return DispenserStateSnapshot(
                activeBarrelCount = (map["activeBarrelCount"] as? Number)?.toInt() ?: 0,
                nextBarrelId = (map["nextBarrelId"] as? Number)?.toInt() ?: 1000,
                seed = (map["seed"] as? Number)?.toLong() ?: 0L,
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

@Keep
data class DispenseAttempt(
    val result: BarrelDispenseResult,
    val barrel: BarrelNode? = null,
    val costIncurred: Int = 0
)

class PlaceableRedBarrelDispenser(
    val config: DispenserConfig,
    initialSeed: Long = 12345L
) {
    constructor(
        vendorX: Float,
        vendorY: Float,
        baseCost: Int = 150,
        cryoCostMultiplier: Float = 1.5f,
        activationRadiusPx: Float = 64f,
        maxActiveBarrels: Int = 10,
        initialSeed: Long = 12345L
    ) : this(
        config = DispenserConfig(vendorX, vendorY, baseCost, cryoCostMultiplier, activationRadiusPx, maxActiveBarrels),
        initialSeed = initialSeed
    )

    private var nextBarrelId = 1000
    private var activeBarrelCount = 0
    private var currentSeed = initialSeed

    private fun nextRandomFloat(): Float {
        currentSeed = (currentSeed * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        return (currentSeed ushr 24).toFloat() / (1 shl 24).toFloat()
    }

    private fun generateChecksum(): Long {
        var hash = -2128831035146727653L // FNV offset basis
        val data = intArrayOf(activeBarrelCount, nextBarrelId, (currentSeed and 0xFFFFFFFFL).toInt(), (currentSeed ushr 32).toInt())
        for (value in data) {
            hash = hash xor value.toLong()
            hash = hash * 1099511628211L // FNV prime
        }
        return hash
    }

    fun getSnapshot(): DispenserStateSnapshot {
        return DispenserStateSnapshot(activeBarrelCount, nextBarrelId, currentSeed, generateChecksum())
    }

    fun restoreSnapshot(snapshot: DispenserStateSnapshot): Boolean {
        var hash = -2128831035146727653L // FNV offset basis
        val data = intArrayOf(snapshot.activeBarrelCount, snapshot.nextBarrelId, (snapshot.seed and 0xFFFFFFFFL).toInt(), (snapshot.seed ushr 32).toInt())
        for (value in data) {
            hash = hash xor value.toLong()
            hash = hash * 1099511628211L
        }
        if (hash != snapshot.checksum) {
            return false // Checksum mismatch
        }
        activeBarrelCount = snapshot.activeBarrelCount
        nextBarrelId = snapshot.nextBarrelId
        currentSeed = snapshot.seed
        return true
    }

    fun attemptPurchase(
        playerX: Float,
        playerY: Float,
        currentCurrency: Int,
        requestCryo: Boolean,
        placementOffsetX: Float = 0f,
        placementOffsetY: Float = 0f
    ): DispenseAttempt {
        if (!playerX.isFinite() || !playerY.isFinite() || !placementOffsetX.isFinite() || !placementOffsetY.isFinite()) {
            return DispenseAttempt(BarrelDispenseResult.INVALID_PARAMETERS)
        }
        if (currentCurrency < 0) {
             return DispenseAttempt(BarrelDispenseResult.INVALID_PARAMETERS)
        }

        val distance = hypot(playerX - config.vendorX, playerY - config.vendorY)
        if (distance > config.activationRadiusPx) {
            return DispenseAttempt(BarrelDispenseResult.OUT_OF_RANGE)
        }

        val actualCost = if (requestCryo) (config.baseCost * config.cryoCostMultiplier).roundToInt() else config.baseCost

        if (currentCurrency < actualCost) {
            return DispenseAttempt(BarrelDispenseResult.INSUFFICIENT_FUNDS)
        }

        if (activeBarrelCount >= config.maxActiveBarrels) {
            return DispenseAttempt(BarrelDispenseResult.AT_CAPACITY)
        }

        // Add some deterministic jitter
        val jitterX = (nextRandomFloat() - 0.5f) * 10f
        val jitterY = (nextRandomFloat() - 0.5f) * 10f

        val dispensedBarrel = BarrelNode(
            id = nextBarrelId++,
            x = config.vendorX + placementOffsetX + jitterX,
            y = config.vendorY + placementOffsetY + jitterY,
            isCryo = requestCryo,
            blastRadiusPx = if (requestCryo) 180f else 145f,
            baseDamage = if (requestCryo) 200 else 240,
            detonated = false
        )

        activeBarrelCount++

        return DispenseAttempt(
            result = BarrelDispenseResult.SUCCESS,
            barrel = dispensedBarrel,
            costIncurred = actualCost
        )
    }

    fun notifyBarrelDestroyed() {
        if (activeBarrelCount > 0) {
            activeBarrelCount--
        }
    }
}
