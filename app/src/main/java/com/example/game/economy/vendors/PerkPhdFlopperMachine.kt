package com.example.game.economy.vendors

import androidx.annotation.Keep
import kotlin.math.hypot
import kotlin.math.max

@Keep
data class PhdFlopperConfig(
    val purchaseCost: Int = 2000,
    val activationRadiusPx: Float = 150f,
    val diveShockwaveBaseDamage: Float = 500f,
    val diveShockwaveRadiusPx: Float = 250f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "purchaseCost" to purchaseCost,
        "activationRadiusPx" to activationRadiusPx,
        "diveShockwaveBaseDamage" to diveShockwaveBaseDamage,
        "diveShockwaveRadiusPx" to diveShockwaveRadiusPx
    )

    companion object {
        fun fromMap(map: Map<String, Any>): PhdFlopperConfig = PhdFlopperConfig(
            purchaseCost = (map["purchaseCost"] as? Number)?.toInt() ?: 2000,
            activationRadiusPx = (map["activationRadiusPx"] as? Number)?.toFloat() ?: 150f,
            diveShockwaveBaseDamage = (map["diveShockwaveBaseDamage"] as? Number)?.toFloat() ?: 500f,
            diveShockwaveRadiusPx = (map["diveShockwaveRadiusPx"] as? Number)?.toFloat() ?: 250f
        )
    }
}

@Keep
data class PhdFlopperState(
    val isPowered: Boolean = false,
    val stockRemaining: Int = 1
) {
    fun computeChecksum(): Int {
        var hash = -2128831035 // FNV offset basis
        hash = (hash xor (if (isPowered) 1 else 0)) * 16777619 // FNV prime
        hash = (hash xor stockRemaining) * 16777619
        return hash
    }

    fun toMap(): Map<String, Any> = mapOf(
        "isPowered" to isPowered,
        "stockRemaining" to stockRemaining
    )

    companion object {
        fun fromMap(map: Map<String, Any>): PhdFlopperState = PhdFlopperState(
            isPowered = (map["isPowered"] as? Boolean) ?: false,
            stockRemaining = (map["stockRemaining"] as? Number)?.toInt() ?: 1
        )
    }
}

@Keep
data class PhdFlopperPurchaseResult(
    val success: Boolean,
    val message: String,
    val newState: PhdFlopperState,
    val remainingCurrency: Int
)

@Keep
data class PhdDiveShockwaveResult(
    val hitCount: Int,
    val totalDamageApplied: Float
)

object PerkPhdFlopperMachine {

    fun attemptPurchase(
        currentState: PhdFlopperState,
        ledger: SingleCurrencyLedger,
        playerX: Float,
        playerY: Float,
        machineX: Float,
        machineY: Float,
        config: PhdFlopperConfig = PhdFlopperConfig()
    ): PhdFlopperPurchaseResult {
        require(playerX.isFinite() && playerY.isFinite() && machineX.isFinite() && machineY.isFinite()) {
            "Coordinates must be finite"
        }

        if (!currentState.isPowered) {
            return PhdFlopperPurchaseResult(false, "Machine is unpowered", currentState, ledger.getBalance())
        }

        if (currentState.stockRemaining <= 0) {
            return PhdFlopperPurchaseResult(false, "Out of stock", currentState, ledger.getBalance())
        }

        val dist = hypot(playerX - machineX, playerY - machineY)
        if (dist > config.activationRadiusPx) {
            return PhdFlopperPurchaseResult(false, "Out of range", currentState, ledger.getBalance())
        }

        if (ledger.canAfford(config.purchaseCost)) {
            val success = ledger.attemptPurchase(config.purchaseCost)
            if (success) {
                return PhdFlopperPurchaseResult(
                    success = true,
                    message = "PhD Flopper Purchased!",
                    newState = currentState.copy(stockRemaining = currentState.stockRemaining - 1),
                    remainingCurrency = ledger.getBalance()
                )
            }
        }

        return PhdFlopperPurchaseResult(false, "Insufficient funds", currentState, ledger.getBalance())
    }

    fun isExplosiveImmune(playerHasPerk: Boolean): Boolean {
        return playerHasPerk
    }

    /**
     * computeDiveShockwave applies distance-based AoE damage to targets.
     * targets is a list of Pairs where first is targetX, second is targetY.
     * In a real implementation this would mutate zombie HP or return a list of damage applications.
     * Here we just compute the raw total damage applied to simulate the shockwave.
     */
    fun computeDiveShockwave(
        playerX: Float,
        playerY: Float,
        targets: List<Pair<Float, Float>>,
        config: PhdFlopperConfig = PhdFlopperConfig()
    ): PhdDiveShockwaveResult {
        require(playerX.isFinite() && playerY.isFinite()) { "Player coordinates must be finite" }
        var hitCount = 0
        var totalDamageApplied = 0f

        for (target in targets) {
            require(target.first.isFinite() && target.second.isFinite()) { "Target coordinates must be finite" }
            val dist = hypot(playerX - target.first, playerY - target.second)
            if (dist <= config.diveShockwaveRadiusPx) {
                // Linear falloff: 100% at 0 distance, 0% at max radius
                val falloff = max(0f, 1f - (dist / config.diveShockwaveRadiusPx))
                val damage = config.diveShockwaveBaseDamage * falloff
                hitCount++
                totalDamageApplied += damage
            }
        }

        return PhdDiveShockwaveResult(hitCount, totalDamageApplied)
    }
}


fun evaluatePerkPhdFlopperMachineLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "PerkPhdFlopperMachine") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
