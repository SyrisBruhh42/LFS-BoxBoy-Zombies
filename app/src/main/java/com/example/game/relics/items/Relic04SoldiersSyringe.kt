package com.example.game.relics.items

import androidx.annotation.Keep
import com.example.game.relics.PassiveRelicKind
import kotlin.math.max

@Keep
data class SoldiersSyringeConfig(
    val baseFireRateIncreasePerStack: Float = 0.15f,
    val baseReloadSpeedIncreasePerStack: Float = 0.15f
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "baseFireRateIncreasePerStack" to baseFireRateIncreasePerStack,
            "baseReloadSpeedIncreasePerStack" to baseReloadSpeedIncreasePerStack
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): SoldiersSyringeConfig {
            val fireRate = (map["baseFireRateIncreasePerStack"] as? Number)?.toFloat() ?: 0.15f
            val reloadSpeed = (map["baseReloadSpeedIncreasePerStack"] as? Number)?.toFloat() ?: 0.15f
            return SoldiersSyringeConfig(
                baseFireRateIncreasePerStack = if (fireRate.isFinite()) fireRate else 0.15f,
                baseReloadSpeedIncreasePerStack = if (reloadSpeed.isFinite()) reloadSpeed else 0.15f
            )
        }
    }
}

@Keep
data class SoldiersSyringeState(
    val stackCount: Int,
    val checksum: Long
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "stackCount" to stackCount,
            "checksum" to checksum
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): SoldiersSyringeState {
            val stacks = (map["stackCount"] as? Number)?.toInt() ?: 1
            val csum = (map["checksum"] as? Number)?.toLong() ?: 0L
            return SoldiersSyringeState(
                stackCount = max(1, stacks),
                checksum = csum
            )
        }
    }
}

object Relic04SoldiersSyringeEngine {
    val RELIC_KIND = PassiveRelicKind.SOLDIERS_SYRINGE
    const val CAN_BE_PURCHASED_AT_VENDING = false

    fun createInitialState(stacks: Int): SoldiersSyringeState {
        val clampedStacks = max(1, stacks)
        return SoldiersSyringeState(
            stackCount = clampedStacks,
            checksum = computeChecksum(clampedStacks)
        )
    }

    fun computeChecksum(stacks: Int): Long {
        var hash = -2128831035146727696L // FNV-1a 64-bit offset basis
        hash = hash xor (stacks.toLong() and 0xFFFFFFFFL)
        hash = hash * 1099511628211L
        return hash
    }

    fun calculateFireRateMultiplier(config: SoldiersSyringeConfig, state: SoldiersSyringeState): Float {
        if (state.stackCount < 1) return 1.0f
        
        val validStacks = if (state.checksum == computeChecksum(state.stackCount)) {
            state.stackCount
        } else {
            1
        }
        
        val multiplier = 1.0f + (validStacks * config.baseFireRateIncreasePerStack)
        return if (multiplier.isFinite()) multiplier else 1.0f
    }

    fun calculateReloadSpeedMultiplier(config: SoldiersSyringeConfig, state: SoldiersSyringeState): Float {
        if (state.stackCount < 1) return 1.0f
        
        val validStacks = if (state.checksum == computeChecksum(state.stackCount)) {
            state.stackCount
        } else {
            1
        }
        
        val multiplier = 1.0f + (validStacks * config.baseReloadSpeedIncreasePerStack)
        return if (multiplier.isFinite()) multiplier else 1.0f
    }
    
    fun calculateNewFireCooldownMs(
        baseCooldownMs: Float, 
        config: SoldiersSyringeConfig, 
        state: SoldiersSyringeState
    ): Float {
        val safeBase = if (baseCooldownMs.isFinite() && baseCooldownMs > 0f) baseCooldownMs else 0f
        if (safeBase == 0f) return 0f
        
        val multiplier = calculateFireRateMultiplier(config, state)
        val newCooldown = safeBase / multiplier
        
        return if (newCooldown.isFinite()) newCooldown else safeBase
    }
    
    fun calculateNewReloadTimeMs(
        baseReloadMs: Float,
        config: SoldiersSyringeConfig,
        state: SoldiersSyringeState
    ): Float {
        val safeBase = if (baseReloadMs.isFinite() && baseReloadMs > 0f) baseReloadMs else 0f
        if (safeBase == 0f) return 0f
        
        val multiplier = calculateReloadSpeedMultiplier(config, state)
        val newReload = safeBase / multiplier
        
        return if (newReload.isFinite()) newReload else safeBase
    }
    
    fun processTick(
        dtSeconds: Float,
        config: SoldiersSyringeConfig,
        state: SoldiersSyringeState,
        activeZombies: Int,
        coopPeers: Int
    ): SoldiersSyringeState {
        // Clamp dtSeconds as per BoxBoys engine requirements
        val safeDt = if (dtSeconds.isFinite()) dtSeconds.coerceIn(0.001f, 0.100f) else 0.016f
        
        // This relic is mostly passive stat modification, but we include a tick
        // for completeness and potential future dynamic effects.
        // Even under heavy load, we just ensure state integrity.
        
        val currentChecksum = computeChecksum(state.stackCount)
        if (state.checksum != currentChecksum) {
            // Self-healing from potential memory corruption
            return createInitialState(max(1, state.stackCount))
        }
        
        return state
    }
}


fun evaluateRelic04SoldiersSyringeLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic04SoldiersSyringe") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
