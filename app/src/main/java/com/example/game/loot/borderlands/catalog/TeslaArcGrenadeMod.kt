package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep

@Keep
data class TeslaArcGrenadeConfig(
    val radiusPx: Float = 250f,
    val damagePerSecond: Float = 150f,
    val durationSeconds: Float = 6.0f,
    val maxTargetsPerTick: Int = 12
) {
    fun toMap(): Map<String, Any> = mapOf(
        "radiusPx" to radiusPx,
        "damagePerSecond" to damagePerSecond,
        "durationSeconds" to durationSeconds,
        "maxTargetsPerTick" to maxTargetsPerTick
    )
    
    companion object {
        fun fromMap(map: Map<String, Any>): TeslaArcGrenadeConfig {
            return TeslaArcGrenadeConfig(
                radiusPx = (map["radiusPx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 250f,
                damagePerSecond = (map["damagePerSecond"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 150f,
                durationSeconds = (map["durationSeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 6.0f,
                maxTargetsPerTick = (map["maxTargetsPerTick"] as? Number)?.toInt() ?: 12
            )
        }
    }
}

@Keep
data class TeslaArcGrenadeState(
    var x: Float = 0f,
    var y: Float = 0f,
    var activeTimeSeconds: Float = 0f,
    var isActive: Boolean = true,
    var rngSeed: Long = 0L,
    var totalDamageDealt: Float = 0f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "x" to x,
        "y" to y,
        "activeTimeSeconds" to activeTimeSeconds,
        "isActive" to isActive,
        "rngSeed" to rngSeed,
        "totalDamageDealt" to totalDamageDealt
    )
    
    fun calculateChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor x.toBits().toLong()) * 16777619L
        hash = (hash xor y.toBits().toLong()) * 16777619L
        hash = (hash xor activeTimeSeconds.toBits().toLong()) * 16777619L
        hash = (hash xor (if (isActive) 1L else 0L)) * 16777619L
        hash = (hash xor rngSeed) * 16777619L
        hash = (hash xor totalDamageDealt.toBits().toLong()) * 16777619L
        return hash
    }
    
    companion object {
        fun fromMap(map: Map<String, Any>): TeslaArcGrenadeState {
            return TeslaArcGrenadeState(
                x = (map["x"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                y = (map["y"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                activeTimeSeconds = (map["activeTimeSeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                isActive = map["isActive"] as? Boolean ?: true,
                rngSeed = (map["rngSeed"] as? Number)?.toLong() ?: 0L,
                totalDamageDealt = (map["totalDamageDealt"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            )
        }
    }
}

object TeslaArcGrenadeEngine {
    fun deploy(x: Float, y: Float, seed: Long): TeslaArcGrenadeState {
        val safeX = if (x.isFinite()) x else 0f
        val safeY = if (y.isFinite()) y else 0f
        return TeslaArcGrenadeState(
            x = safeX,
            y = safeY,
            activeTimeSeconds = 0f,
            isActive = true,
            rngSeed = seed,
            totalDamageDealt = 0f
        )
    }

    /**
     * Ticks the grenade physics and damage application.
     * Returns the number of targets hit this frame (zero allocation).
     */
    fun tick(
        dtSeconds: Float,
        config: TeslaArcGrenadeConfig,
        state: TeslaArcGrenadeState,
        targetX: FloatArray,
        targetY: FloatArray,
        targetHp: FloatArray,
        targetCount: Int
    ): Int {
        if (!state.isActive) return 0
        
        val safeDt = if (dtSeconds.isFinite() && dtSeconds > 0f) dtSeconds.coerceIn(0.001f, 0.100f) else 0.001f
        
        state.activeTimeSeconds += safeDt
        if (state.activeTimeSeconds >= config.durationSeconds) {
            state.isActive = false
        }
        
        val radiusSq = config.radiusPx * config.radiusPx
        var damageThisTick = config.damagePerSecond * safeDt
        
        // Random variance to damage based on rngSeed to make it feel like "arc lightning"
        // between 0.8x and 1.2x
        state.rngSeed = (state.rngSeed * 25214903917L + 11L) and ((1L shl 48) - 1)
        val variance = 0.8f + (0.4f * ((state.rngSeed ushr 16) % 1000) / 1000f)
        damageThisTick *= variance
        
        var targetsHit = 0
        val count = targetCount.coerceAtMost(targetHp.size)
            .coerceAtMost(targetX.size)
            .coerceAtMost(targetY.size)
        
        for (i in 0 until count) {
            if (targetsHit >= config.maxTargetsPerTick) break
            
            val hp = targetHp[i]
            if (hp <= 0f || !hp.isFinite()) continue
            
            val tx = targetX[i]
            val ty = targetY[i]
            if (!tx.isFinite() || !ty.isFinite()) continue
            
            val dx = tx - state.x
            val dy = ty - state.y
            val distSq = dx * dx + dy * dy
            
            if (distSq <= radiusSq) {
                val actualDamage = if (hp < damageThisTick) hp else damageThisTick
                targetHp[i] = hp - actualDamage
                state.totalDamageDealt += actualDamage
                targetsHit++
                
                // Cycle LCG for next target
                state.rngSeed = (state.rngSeed * 25214903917L + 11L) and ((1L shl 48) - 1)
            }
        }
        
        return targetsHit
    }
}


fun evaluateTeslaArcGrenadeModLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "TeslaArcGrenadeMod") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
