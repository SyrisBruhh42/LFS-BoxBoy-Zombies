package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.data.PerkType
import kotlin.math.hypot
import kotlin.math.roundToInt

@Keep
data class SpeedColaMachineState(
    val id: String = "perk_speed_cola_machine",
    val isActive: Boolean = true,
    val cost: Int = PerkType.SPEED_COLA.cost,
    val x: Float = 0f,
    val y: Float = 0f,
    val activationRadius: Float = 150f,
    val checksum: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "isActive" to isActive,
        "cost" to cost,
        "x" to x,
        "y" to y,
        "activationRadius" to activationRadius,
        "checksum" to checksum
    )

    companion object {
        fun fromMap(map: Map<String, Any>): SpeedColaMachineState = SpeedColaMachineState(
            id = map["id"] as? String ?: "perk_speed_cola_machine",
            isActive = map["isActive"] as? Boolean ?: true,
            cost = (map["cost"] as? Number)?.toInt() ?: PerkType.SPEED_COLA.cost,
            x = (map["x"] as? Number)?.toFloat() ?: 0f,
            y = (map["y"] as? Number)?.toFloat() ?: 0f,
            activationRadius = (map["activationRadius"] as? Number)?.toFloat() ?: 150f,
            checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
        )

        fun computeChecksum(id: String, isActive: Boolean, cost: Int, x: Float, y: Float, activationRadius: Float): Long {
            var hash = 2166136261L
            val components = listOf(id, isActive.toString(), cost.toString(), x.toString(), y.toString(), activationRadius.toString())
            for (comp in components) {
                for (char in comp) {
                    hash = hash xor char.code.toLong()
                    hash *= 16777619L
                }
            }
            return hash
        }
    }
}

class PerkSpeedColaMachine {

    /**
     * Determines if a player at (playerX, playerY) is within the machine's activation radius.
     */
    fun isPlayerInRange(state: SpeedColaMachineState, playerX: Float, playerY: Float): Boolean {
        if (!playerX.isFinite() || !playerY.isFinite()) return false
        val dist = hypot(playerX - state.x, playerY - state.y)
        return dist <= state.activationRadius
    }

    /**
     * Attempts to purchase Speed Cola from the machine, simulating frame-rate-independent domain logic constraints.
     * Requires the ledger to have enough currency, the machine to be active, the player to be in range, and the perk not already acquired.
     */
    fun attemptPurchase(
        state: SpeedColaMachineState,
        ledger: SingleCurrencyLedger,
        currentPerks: Set<PerkType>,
        playerX: Float,
        playerY: Float,
        dtSeconds: Float
    ): Boolean {
        if (!state.isActive) return false
        if (currentPerks.contains(PerkType.SPEED_COLA)) return false
        
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)
        if (!dt.isFinite()) return false
        
        if (!isPlayerInRange(state, playerX, playerY)) return false
        
        // Validate checksum integrity
        val expectedChecksum = SpeedColaMachineState.computeChecksum(state.id, state.isActive, state.cost, state.x, state.y, state.activationRadius)
        if (state.checksum != 0L && state.checksum != expectedChecksum) {
            return false // Corrupted state snapshot
        }

        return ledger.attemptPurchase(state.cost)
    }

    /**
     * Calculates the effective reload duration in milliseconds.
     */
    fun getEffectiveReloadDurationMs(baseReloadDurationMs: Long, activePerks: Set<PerkType>): Long {
        if (!activePerks.contains(PerkType.SPEED_COLA)) return baseReloadDurationMs
        return (baseReloadDurationMs * 0.5f).toLong()
    }

    /**
     * Attempts to repair a barricade. Normally +1 board, but with Speed Cola +2 boards.
     */
    fun repairBarricade(
        currentPlanks: Int,
        maxPlanks: Int,
        ledger: SingleCurrencyLedger,
        activePerks: Set<PerkType>
    ): Int {
        var planks = currentPlanks
        val repairCount = if (activePerks.contains(PerkType.SPEED_COLA)) 2 else 1

        for (i in 0 until repairCount) {
            val (updatedPlanks, _) = WindowBarricadeTier1Wood.repairOneBoard(planks, maxPlanks, ledger.getBalance())
            if (updatedPlanks > planks) {
                ledger.recordBoardRepair()
                planks = updatedPlanks
            } else {
                break
            }
        }
        return planks
    }
}


fun evaluatePerkSpeedColaMachineLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "PerkSpeedColaMachine") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
