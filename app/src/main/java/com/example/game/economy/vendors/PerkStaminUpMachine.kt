package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.data.PerkType
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * State representing a Stamin-Up machine instance in the BoxBoys single-currency economy.
 */
@Keep
data class StaminUpMachineState(
    val machineId: Int,
    val x: Float,
    val y: Float,
    val isPowered: Boolean = true,
    val currentCost: Int = PerkType.STAMIN_UP.cost,
    val ambientRotationDeg: Float = 0f
) {
    /**
     * Compute a simple FNV-1a checksum to ensure determinism/parity.
     */
    fun computeFnv1aChecksum(): Long {
        var hash = 2166136261L
        
        fun hashInt(value: Int) {
            hash = (hash xor (value.toLong() and 0xFFFFFFFFL)) * 16777619L
        }
        
        fun hashFloat(value: Float) {
            hashInt(value.toBits())
        }

        hashInt(machineId)
        hashFloat(x)
        hashFloat(y)
        hashInt(if (isPowered) 1 else 0)
        hashInt(currentCost)
        hashFloat(ambientRotationDeg)
        
        return hash
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "machineId" to machineId,
            "x" to x,
            "y" to y,
            "isPowered" to isPowered,
            "currentCost" to currentCost,
            "ambientRotationDeg" to ambientRotationDeg
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): StaminUpMachineState {
            return StaminUpMachineState(
                machineId = (map["machineId"] as? Number)?.toInt() ?: 0,
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                isPowered = map["isPowered"] as? Boolean ?: true,
                currentCost = (map["currentCost"] as? Number)?.toInt() ?: PerkType.STAMIN_UP.cost,
                ambientRotationDeg = (map["ambientRotationDeg"] as? Number)?.toFloat() ?: 0f
            )
        }
    }
}

/**
 * Represents the outcome of attempting to purchase from the Stamin-Up machine.
 */
enum class StaminUpPurchaseResult {
    SUCCESS,
    INSUFFICIENT_FUNDS,
    OUT_OF_RANGE,
    ALREADY_OWNED,
    NOT_POWERED
}

/**
 * Encapsulates a purchase attempt result, including new state values if applicable.
 */
data class StaminUpDispenseAttempt(
    val result: StaminUpPurchaseResult,
    val newCurrencyPoints: Int,
    val perkGranted: PerkType?
)

/**
 * The core domain logic for the Stamin-Up perk machine (BoxBoys Track 4).
 * Enforces frame-rate-independent determinism, pure-JVM constraints, and bounds checking.
 */
class PerkStaminUpMachine {

    companion object {
        const val ACTIVATION_RADIUS_PX = 72f
        const val ROTATION_SPEED_DEG_PER_SEC = 45f
        const val STAMIN_UP_SPEED_MULTIPLIER = 1.28f
    }

    /**
     * Frame-rate independent update function to step internal machine state (e.g., ambient animations).
     *
     * @param state The current immutable StaminUpMachineState
     * @param dtSeconds The time elapsed since last frame, will be clamped to [0.001, 0.100].
     * @return The updated StaminUpMachineState.
     */
    fun step(state: StaminUpMachineState, dtSeconds: Float): StaminUpMachineState {
        if (!state.isPowered) return state

        // Clamp delta time, recovering from NaN or Infinity
        val safeDt = if (dtSeconds.isFinite()) max(0.001f, min(0.100f, dtSeconds)) else 0.001f

        var nextRot = state.ambientRotationDeg + (ROTATION_SPEED_DEG_PER_SEC * safeDt)
        if (nextRot >= 360f) {
            nextRot -= 360f
        }

        return state.copy(ambientRotationDeg = nextRot)
    }

    /**
     * Evaluates a player's attempt to purchase Stamin-Up.
     * 
     * @param state The current machine state
     * @param playerX Player's X coordinate
     * @param playerY Player's Y coordinate
     * @param currentCurrency Player's single currency balance
     * @param activePerks Set of perks the player currently owns
     * @return A dispense attempt containing the outcome.
     */
    fun attemptPurchase(
        state: StaminUpMachineState,
        playerX: Float,
        playerY: Float,
        currentCurrency: Int,
        activePerks: Set<PerkType>
    ): StaminUpDispenseAttempt {
        
        // Recover from non-finite player coordinates
        val safePlayerX = if (playerX.isFinite()) playerX else 0f
        val safePlayerY = if (playerY.isFinite()) playerY else 0f
        val safeMachineX = if (state.x.isFinite()) state.x else 0f
        val safeMachineY = if (state.y.isFinite()) state.y else 0f

        if (!state.isPowered) {
            return StaminUpDispenseAttempt(
                result = StaminUpPurchaseResult.NOT_POWERED,
                newCurrencyPoints = currentCurrency,
                perkGranted = null
            )
        }

        val distance = hypot(safePlayerX - safeMachineX, safePlayerY - safeMachineY)
        if (distance > ACTIVATION_RADIUS_PX) {
            return StaminUpDispenseAttempt(
                result = StaminUpPurchaseResult.OUT_OF_RANGE,
                newCurrencyPoints = currentCurrency,
                perkGranted = null
            )
        }

        if (PerkType.STAMIN_UP in activePerks) {
            return StaminUpDispenseAttempt(
                result = StaminUpPurchaseResult.ALREADY_OWNED,
                newCurrencyPoints = currentCurrency,
                perkGranted = null
            )
        }

        if (currentCurrency < state.currentCost) {
            return StaminUpDispenseAttempt(
                result = StaminUpPurchaseResult.INSUFFICIENT_FUNDS,
                newCurrencyPoints = currentCurrency,
                perkGranted = null
            )
        }

        return StaminUpDispenseAttempt(
            result = StaminUpPurchaseResult.SUCCESS,
            newCurrencyPoints = currentCurrency - state.currentCost,
            perkGranted = PerkType.STAMIN_UP
        )
    }

    /**
     * Calculates a player's effective movement speed multiplier given their perks.
     */
    fun calculateSpeedMultiplier(activePerks: Set<PerkType>): Float {
        return if (PerkType.STAMIN_UP in activePerks) {
            STAMIN_UP_SPEED_MULTIPLIER
        } else {
            1.0f
        }
    }
}


fun evaluatePerkStaminUpMachineLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "PerkStaminUpMachine") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
