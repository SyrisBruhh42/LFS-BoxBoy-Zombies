package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import kotlin.math.max
import kotlin.math.min

@Keep
enum class MaliwanElement(val id: Int) {
    INCENDIARY(1),
    VOLTAIC(2)
}

@Keep
data class MaliwanPrismState(
    val currentElement: MaliwanElement = MaliwanElement.INCENDIARY,
    val isSwitching: Boolean = false,
    val switchProgress: Float = 0f,
    val switchDuration: Float = 0.5f,
    val baseDamageMultiplier: Float = 1.0f,
    val elementalEffectChance: Float = 0.25f,
    val stateChecksum: Long = 0L
) {
    fun toJsonMap(): Map<String, Any> {
        return mapOf(
            "currentElement" to currentElement.id,
            "isSwitching" to isSwitching,
            "switchProgress" to switchProgress,
            "switchDuration" to switchDuration,
            "baseDamageMultiplier" to baseDamageMultiplier,
            "elementalEffectChance" to elementalEffectChance,
            "stateChecksum" to computeChecksum()
        )
    }

    fun computeChecksum(): Long {
        var hash = -2128831035L
        hash = (hash xor currentElement.id.toLong()) * 16777619L
        hash = (hash xor (if (isSwitching) 1L else 0L)) * 16777619L
        hash = (hash xor switchProgress.toBits().toLong()) * 16777619L
        hash = (hash xor switchDuration.toBits().toLong()) * 16777619L
        hash = (hash xor baseDamageMultiplier.toBits().toLong()) * 16777619L
        hash = (hash xor elementalEffectChance.toBits().toLong()) * 16777619L
        return hash
    }
    
    companion object {
        fun fromJsonMap(map: Map<String, Any>): MaliwanPrismState {
            val elementId = (map["currentElement"] as? Number)?.toInt() ?: 1
            val currentElement = if (elementId == 2) MaliwanElement.VOLTAIC else MaliwanElement.INCENDIARY
            return MaliwanPrismState(
                currentElement = currentElement,
                isSwitching = map["isSwitching"] as? Boolean ?: false,
                switchProgress = (map["switchProgress"] as? Number)?.toFloat() ?: 0f,
                switchDuration = (map["switchDuration"] as? Number)?.toFloat() ?: 0.5f,
                baseDamageMultiplier = (map["baseDamageMultiplier"] as? Number)?.toFloat() ?: 1.0f,
                elementalEffectChance = (map["elementalEffectChance"] as? Number)?.toFloat() ?: 0.25f
            ).let { 
                val expectedChecksum = (map["stateChecksum"] as? Number)?.toLong() ?: it.computeChecksum()
                it.copy(stateChecksum = expectedChecksum)
            }
        }
        
        fun createDefault(): MaliwanPrismState {
            return MaliwanPrismState().let { it.copy(stateChecksum = it.computeChecksum()) }
        }
    }
}

@Keep
data class MaliwanPrismShotResult(
    val damageDealt: Float,
    val appliedElement: MaliwanElement,
    val didTriggerEffect: Boolean
)

object MaliwanPrismManufacturerTrait {
    
    fun startSwitch(state: MaliwanPrismState): MaliwanPrismState {
        if (state.isSwitching) return state
        return state.copy(
            isSwitching = true,
            switchProgress = 0f
        ).let { it.copy(stateChecksum = it.computeChecksum()) }
    }
    
    fun tickSwitch(state: MaliwanPrismState, dtSeconds: Float): MaliwanPrismState {
        if (!state.isSwitching) return state

        var safeDt = dtSeconds
        if (safeDt.isNaN() || safeDt.isInfinite()) {
            safeDt = 0.016f
        }
        safeDt = max(0.001f, min(safeDt, 0.100f))

        var newProgress = state.switchProgress + (safeDt / max(state.switchDuration, 0.01f))
        var newSwitching = true
        var newElement = state.currentElement

        if (newProgress >= 1.0f) {
            newProgress = 0f
            newSwitching = false
            newElement = if (state.currentElement == MaliwanElement.INCENDIARY) {
                MaliwanElement.VOLTAIC
            } else {
                MaliwanElement.INCENDIARY
            }
        }
        
        return state.copy(
            currentElement = newElement,
            isSwitching = newSwitching,
            switchProgress = newProgress
        ).let { it.copy(stateChecksum = it.computeChecksum()) }
    }
    
    fun computeShot(state: MaliwanPrismState, baseDamage: Float, randomValue: Float): MaliwanPrismShotResult {
        var safeRandom = randomValue
        if (safeRandom.isNaN() || safeRandom.isInfinite()) {
            safeRandom = 0.999f
        }
        safeRandom = max(0f, min(safeRandom, 1f))
        
        var safeBase = baseDamage
        if (safeBase.isNaN() || safeBase.isInfinite() || safeBase < 0f) {
            safeBase = 0f
        }

        // Damage penalty during switching (e.g. 50% damage when switching)
        val switchingPenalty = if (state.isSwitching) 0.5f else 1.0f
        val finalDamage = safeBase * state.baseDamageMultiplier * switchingPenalty
        
        // Cannot trigger effect while switching
        val triggersEffect = !state.isSwitching && safeRandom < state.elementalEffectChance

        return MaliwanPrismShotResult(
            damageDealt = finalDamage,
            appliedElement = state.currentElement,
            didTriggerEffect = triggersEffect
        )
    }
}


fun evaluateMaliwanPrismManufacturerTraitLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "MaliwanPrismManufacturerTrait") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
