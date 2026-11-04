package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.max

@Keep
data class KorsakovTraitConfig(
    val kineticDamageMultiplier: Float = 1.25f,
    val baseExplosiveRadiusPx: Float = 150f,
    val baseKnockbackForce: Float = 400f,
    val minDamageThreshold: Float = 5f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "kineticDamageMultiplier" to kineticDamageMultiplier,
        "baseExplosiveRadiusPx" to baseExplosiveRadiusPx,
        "baseKnockbackForce" to baseKnockbackForce,
        "minDamageThreshold" to minDamageThreshold
    )

    companion object {
        fun fromMap(map: Map<String, Any>): KorsakovTraitConfig {
            return KorsakovTraitConfig(
                kineticDamageMultiplier = (map["kineticDamageMultiplier"] as? Number)?.toFloat() ?: 1.25f,
                baseExplosiveRadiusPx = (map["baseExplosiveRadiusPx"] as? Number)?.toFloat() ?: 150f,
                baseKnockbackForce = (map["baseKnockbackForce"] as? Number)?.toFloat() ?: 400f,
                minDamageThreshold = (map["minDamageThreshold"] as? Number)?.toFloat() ?: 5f
            )
        }
    }
}

@Keep
data class KorsakovSlugExplosion(
    val id: Int,
    val x: Float,
    val y: Float,
    val radiusPx: Float,
    val damage: Float,
    val knockbackForce: Float,
    val activeTimeMs: Int = 0
)

@Keep
data class KorsakovTraitSnapshot(
    val config: KorsakovTraitConfig,
    val activeExplosions: List<KorsakovSlugExplosion>,
    val internalStateSeed: Int
) {
    fun calculateChecksum(): Long {
        var hash = 2166136261L // FNV-1a offset basis
        
        fun mix(value: Float) {
            val bits = value.toRawBits().toLong() and 0xFFFFFFFFL
            hash = (hash xor (bits and 0xFF)) * 16777619L
            hash = (hash xor ((bits ushr 8) and 0xFF)) * 16777619L
            hash = (hash xor ((bits ushr 16) and 0xFF)) * 16777619L
            hash = (hash xor ((bits ushr 24) and 0xFF)) * 16777619L
        }
        
        fun mixInt(value: Int) {
            hash = (hash xor (value.toLong() and 0xFF)) * 16777619L
            hash = (hash xor ((value.toLong() ushr 8) and 0xFF)) * 16777619L
            hash = (hash xor ((value.toLong() ushr 16) and 0xFF)) * 16777619L
            hash = (hash xor ((value.toLong() ushr 24) and 0xFF)) * 16777619L
        }

        mix(config.kineticDamageMultiplier)
        mix(config.baseExplosiveRadiusPx)
        mix(config.baseKnockbackForce)
        mix(config.minDamageThreshold)
        
        mixInt(internalStateSeed)
        mixInt(activeExplosions.size)
        
        for (exp in activeExplosions) {
            mixInt(exp.id)
            mix(exp.x)
            mix(exp.y)
            mix(exp.radiusPx)
            mix(exp.damage)
            mix(exp.knockbackForce)
            mixInt(exp.activeTimeMs)
        }
        
        return hash
    }
    
    fun toMap(): Map<String, Any> {
        val explosionsList = activeExplosions.map { exp ->
            mapOf(
                "id" to exp.id,
                "x" to exp.x,
                "y" to exp.y,
                "radiusPx" to exp.radiusPx,
                "damage" to exp.damage,
                "knockbackForce" to exp.knockbackForce,
                "activeTimeMs" to exp.activeTimeMs
            )
        }
        return mapOf(
            "config" to config.toMap(),
            "activeExplosions" to explosionsList,
            "internalStateSeed" to internalStateSeed
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): KorsakovTraitSnapshot {
            val configMap = map["config"] as? Map<String, Any> ?: emptyMap()
            val rawExplosions = map["activeExplosions"] as? List<Map<String, Any>> ?: emptyList()
            
            val explosions = rawExplosions.map { exp ->
                KorsakovSlugExplosion(
                    id = (exp["id"] as? Number)?.toInt() ?: 0,
                    x = (exp["x"] as? Number)?.toFloat() ?: 0f,
                    y = (exp["y"] as? Number)?.toFloat() ?: 0f,
                    radiusPx = (exp["radiusPx"] as? Number)?.toFloat() ?: 0f,
                    damage = (exp["damage"] as? Number)?.toFloat() ?: 0f,
                    knockbackForce = (exp["knockbackForce"] as? Number)?.toFloat() ?: 0f,
                    activeTimeMs = (exp["activeTimeMs"] as? Number)?.toInt() ?: 0
                )
            }
            return KorsakovTraitSnapshot(
                config = KorsakovTraitConfig.fromMap(configMap),
                activeExplosions = explosions,
                internalStateSeed = (map["internalStateSeed"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

class KorsakovManufacturerEngine(
    private var config: KorsakovTraitConfig = KorsakovTraitConfig(),
    private var currentSeed: Int = 12345
) {
    private val activeExplosions = mutableListOf<KorsakovSlugExplosion>()
    private var nextExplosionId = 1
    
    val resultDamage = FloatArray(1024)
    val resultVx = FloatArray(1024)
    val resultVy = FloatArray(1024)

    fun calculateBaseDamage(baseDamage: Float): Float {
        if (!baseDamage.isFinite() || baseDamage <= 0f) return 0f
        return baseDamage * config.kineticDamageMultiplier
    }

    fun spawnExplosiveSlug(x: Float, y: Float, baseDamage: Float) {
        if (!x.isFinite() || !y.isFinite() || !baseDamage.isFinite() || baseDamage <= 0f) {
            return
        }
        
        // Deterministic PRNG for slight variance
        currentSeed = (currentSeed * 1103515245 + 12345) and 0x7FFFFFFF
        val normalizedRand = (currentSeed and 0xFFFF).toFloat() / 0xFFFF
        val variance = 0.9f + (normalizedRand * 0.2f) // 0.9 to 1.1

        val explosion = KorsakovSlugExplosion(
            id = nextExplosionId++,
            x = x,
            y = y,
            radiusPx = config.baseExplosiveRadiusPx * variance,
            damage = calculateBaseDamage(baseDamage) * 0.5f * variance,
            knockbackForce = config.baseKnockbackForce * variance
        )
        activeExplosions.add(explosion)
    }

    fun stepExplosions(
        dtSeconds: Float,
        targetCount: Int,
        targetX: FloatArray,
        targetY: FloatArray,
        targetMasses: FloatArray
    ) {
        val clampedDt = dtSeconds.coerceIn(0.001f, 0.100f)
        
        for (i in 0 until targetCount) {
            resultDamage[i] = 0f
            resultVx[i] = 0f
            resultVy[i] = 0f
        }

        val iMax = activeExplosions.size
        for (j in 0 until iMax) {
            val exp = activeExplosions[j]
            for (i in 0 until targetCount) {
                val tx = targetX[i]
                val ty = targetY[i]
                val mass = targetMasses[i]
                
                if (!tx.isFinite() || !ty.isFinite() || !mass.isFinite() || mass <= 0f) {
                    continue
                }

                val dx = tx - exp.x
                val dy = ty - exp.y
                val dist = hypot(dx, dy)

                if (dist <= exp.radiusPx) {
                    val falloff = max(0f, 1f - (dist / exp.radiusPx))
                    val appliedDamage = exp.damage * falloff
                    
                    if (appliedDamage >= config.minDamageThreshold) {
                        resultDamage[i] += appliedDamage
                        
                        val force = exp.knockbackForce * falloff
                        val massScaling = max(0.1f, 1f / mass) // heavier enemies take less knockback
                        val adjustedForce = force * massScaling
                        
                        val angle = if (dist > 0.01f) {
                            kotlin.math.atan2(dy, dx)
                        } else {
                            // deterministic fallback angle
                            0f 
                        }
                        
                        // we apply instantaneous velocity (impulse) rather than acceleration
                        resultVx[i] += adjustedForce * cos(angle)
                        resultVy[i] += adjustedForce * sin(angle)
                    }
                }
            }
        }
        
        // Clear all explosions after processing (one-shot mechanic)
        activeExplosions.clear()
    }

    fun getSnapshot(): KorsakovTraitSnapshot {
        return KorsakovTraitSnapshot(
            config = config,
            activeExplosions = activeExplosions.toList(),
            internalStateSeed = currentSeed
        )
    }

    fun loadSnapshot(snapshot: KorsakovTraitSnapshot) {
        config = snapshot.config
        currentSeed = snapshot.internalStateSeed
        activeExplosions.clear()
        activeExplosions.addAll(snapshot.activeExplosions)
    }
}


fun evaluateKorsakovManufacturerTraitLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "KorsakovManufacturerTrait") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
