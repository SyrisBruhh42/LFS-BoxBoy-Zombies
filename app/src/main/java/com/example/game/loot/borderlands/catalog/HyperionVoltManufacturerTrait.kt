package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import kotlin.math.max
import kotlin.math.min

/**
 * Hyperion-Volt Manufacturer Trait Config.
 * Immutable bounds for reverse-recoil accuracy stabilization and fire rate boost.
 */
@Keep
data class HyperionVoltTraitConfig(
    val maxSpreadAngle: Float = 15.0f,
    val minSpreadAngle: Float = 0.5f,
    val spreadRecoveryRatePerSec: Float = 5.0f, // How fast spread increases back to max when NOT firing
    val spreadReductionPerShot: Float = 2.5f,  // How much spread reduces per shot
    val fireRateMultiplier: Float = 1.25f,     // +25% fire rate
    val maxHeatAccumulation: Float = 100.0f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "maxSpreadAngle" to maxSpreadAngle,
        "minSpreadAngle" to minSpreadAngle,
        "spreadRecoveryRatePerSec" to spreadRecoveryRatePerSec,
        "spreadReductionPerShot" to spreadReductionPerShot,
        "fireRateMultiplier" to fireRateMultiplier,
        "maxHeatAccumulation" to maxHeatAccumulation
    )

    companion object {
        fun fromMap(map: Map<String, Any>): HyperionVoltTraitConfig {
            return HyperionVoltTraitConfig(
                maxSpreadAngle = (map["maxSpreadAngle"] as? Number)?.toFloat() ?: 15.0f,
                minSpreadAngle = (map["minSpreadAngle"] as? Number)?.toFloat() ?: 0.5f,
                spreadRecoveryRatePerSec = (map["spreadRecoveryRatePerSec"] as? Number)?.toFloat() ?: 5.0f,
                spreadReductionPerShot = (map["spreadReductionPerShot"] as? Number)?.toFloat() ?: 2.5f,
                fireRateMultiplier = (map["fireRateMultiplier"] as? Number)?.toFloat() ?: 1.25f,
                maxHeatAccumulation = (map["maxHeatAccumulation"] as? Number)?.toFloat() ?: 100.0f
            )
        }
    }
}

/**
 * Hyperion-Volt Manufacturer Trait State.
 * Mutable zero-allocation snapshot compatible with `BoxBoysNetProtocol-v3`.
 */
@Keep
data class HyperionVoltTraitState(
    var currentSpreadAngle: Float = 15.0f, // Starts at max spread
    var isFiring: Boolean = false,
    var timeSinceLastShotSec: Float = 0.0f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "currentSpreadAngle" to currentSpreadAngle,
        "isFiring" to isFiring,
        "timeSinceLastShotSec" to timeSinceLastShotSec
    )

    fun computeChecksum(): Int {
        var hash = -2128831035
        hash = (hash xor currentSpreadAngle.toBits()) * 16777619
        hash = (hash xor (if (isFiring) 1 else 0)) * 16777619
        hash = (hash xor timeSinceLastShotSec.toBits()) * 16777619
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): HyperionVoltTraitState {
            return HyperionVoltTraitState(
                currentSpreadAngle = (map["currentSpreadAngle"] as? Number)?.toFloat() ?: 15.0f,
                isFiring = (map["isFiring"] as? Boolean) ?: false,
                timeSinceLastShotSec = (map["timeSinceLastShotSec"] as? Number)?.toFloat() ?: 0.0f
            )
        }
    }
}

/**
 * Hyperion-Volt Manufacturer Trait Engine.
 * Handles reverse-recoil calculation and rate of fire logic.
 * Zero-allocation, fully deterministic, safe from NaN/Infinity.
 */
object HyperionVoltManufacturerEngine {

    /**
     * Advances the logic one frame.
     * dtSeconds is clamped to [0.001, 0.100] for determinism and stability.
     */
    fun step(
        state: HyperionVoltTraitState,
        config: HyperionVoltTraitConfig,
        dtSecondsIn: Float
    ) {
        val dtSafe = clamp(dtSecondsIn, 0.001f, 0.100f)
        val dt = if (dtSafe.isFinite()) dtSafe else 0.016f

        state.timeSinceLastShotSec += dt

        // If not firing recently (e.g., >0.1s since last shot), spread begins recovering back to max
        if (!state.isFiring || state.timeSinceLastShotSec > 0.1f) {
            state.isFiring = false
            val recoveryRate = if (config.spreadRecoveryRatePerSec.isFinite()) config.spreadRecoveryRatePerSec else 5.0f
            val newSpread = state.currentSpreadAngle + (recoveryRate * dt)
            
            val maxSpread = if (config.maxSpreadAngle.isFinite()) config.maxSpreadAngle else 15.0f
            state.currentSpreadAngle = min(newSpread, maxSpread)
        }
        
        // Final sanity clamping
        val finalMax = if (config.maxSpreadAngle.isFinite()) config.maxSpreadAngle else 15.0f
        val finalMin = if (config.minSpreadAngle.isFinite()) config.minSpreadAngle else 0.5f
        state.currentSpreadAngle = clamp(state.currentSpreadAngle, finalMin, finalMax)
        
        if (!state.currentSpreadAngle.isFinite()) {
            state.currentSpreadAngle = finalMax
        }
    }

    /**
     * Called when a shot is successfully fired.
     * Reduces the current spread (reverse-recoil).
     */
    fun onShotFired(
        state: HyperionVoltTraitState,
        config: HyperionVoltTraitConfig
    ) {
        state.isFiring = true
        state.timeSinceLastShotSec = 0.0f

        val reduction = if (config.spreadReductionPerShot.isFinite()) config.spreadReductionPerShot else 2.5f
        val newSpread = state.currentSpreadAngle - reduction

        val minSpread = if (config.minSpreadAngle.isFinite()) config.minSpreadAngle else 0.5f
        val maxSpread = if (config.maxSpreadAngle.isFinite()) config.maxSpreadAngle else 15.0f
        
        state.currentSpreadAngle = clamp(newSpread, minSpread, maxSpread)
        if (!state.currentSpreadAngle.isFinite()) {
            state.currentSpreadAngle = maxSpread
        }
    }

    /**
     * Returns the effective fire rate multiplier.
     */
    fun getEffectiveFireRate(config: HyperionVoltTraitConfig): Float {
        val fr = config.fireRateMultiplier
        return if (fr.isFinite() && fr > 0f) fr else 1.25f
    }

    private fun clamp(value: Float, min: Float, max: Float): Float {
        return max(min, min(value, max))
    }
}
