package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import kotlin.math.sqrt

@Keep
data class BioHazeTraitConfig(
    val extraPierceCount: Int = 1,
    val splashRadiusPx: Float = 45f,
    val splashDamagePercent: Float = 0.35f,
    val maxSplashTargets: Int = 6,
    val baseSplashDps: Int = 20,
    val splashDurationSeconds: Float = 3.0f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "extraPierceCount" to extraPierceCount,
        "splashRadiusPx" to splashRadiusPx,
        "splashDamagePercent" to splashDamagePercent,
        "maxSplashTargets" to maxSplashTargets,
        "baseSplashDps" to baseSplashDps,
        "splashDurationSeconds" to splashDurationSeconds
    )

    companion object {
        fun fromMap(map: Map<String, Any>): BioHazeTraitConfig {
            return BioHazeTraitConfig(
                extraPierceCount = (map["extraPierceCount"] as? Number)?.toInt() ?: 1,
                splashRadiusPx = (map["splashRadiusPx"] as? Number)?.toFloat() ?: 45f,
                splashDamagePercent = (map["splashDamagePercent"] as? Number)?.toFloat() ?: 0.35f,
                maxSplashTargets = (map["maxSplashTargets"] as? Number)?.toInt() ?: 6,
                baseSplashDps = (map["baseSplashDps"] as? Number)?.toInt() ?: 20,
                splashDurationSeconds = (map["splashDurationSeconds"] as? Number)?.toFloat() ?: 3.0f
            )
        }
    }
}

@Keep
data class BioHazeSplashSnapshot(
    val x: Float,
    val y: Float,
    val radius: Float,
    val dps: Int,
    val remainingDurationSeconds: Float,
    val sourceChecksum: Long
) {
    fun toMap(): Map<String, Any> = mapOf(
        "x" to x,
        "y" to y,
        "radius" to radius,
        "dps" to dps,
        "remainingDurationSeconds" to remainingDurationSeconds,
        "sourceChecksum" to sourceChecksum
    )

    companion object {
        fun fromMap(map: Map<String, Any>): BioHazeSplashSnapshot {
            return BioHazeSplashSnapshot(
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                radius = (map["radius"] as? Number)?.toFloat() ?: 0f,
                dps = (map["dps"] as? Number)?.toInt() ?: 0,
                remainingDurationSeconds = (map["remainingDurationSeconds"] as? Number)?.toFloat() ?: 0f,
                sourceChecksum = (map["sourceChecksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

@Keep
class BioHazeSplashState(
    var x: Float,
    var y: Float,
    var radius: Float,
    var dps: Int,
    var remainingDurationSeconds: Float,
    val sourceChecksum: Long
) {
    fun getFnv1aChecksum(): Long {
        var hash = -3750763034362895579L // FNV offset basis
        val prime = 1099511628211L

        hash = (hash xor java.lang.Float.floatToIntBits(x).toLong()) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(y).toLong()) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(radius).toLong()) * prime
        hash = (hash xor dps.toLong()) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(remainingDurationSeconds).toLong()) * prime
        hash = (hash xor sourceChecksum) * prime

        return hash
    }

    fun toSnapshot(): BioHazeSplashSnapshot {
        return BioHazeSplashSnapshot(x, y, radius, dps, remainingDurationSeconds, sourceChecksum)
    }

    companion object {
        fun fromSnapshot(snapshot: BioHazeSplashSnapshot): BioHazeSplashState {
            return BioHazeSplashState(
                x = snapshot.x,
                y = snapshot.y,
                radius = snapshot.radius,
                dps = snapshot.dps,
                remainingDurationSeconds = snapshot.remainingDurationSeconds,
                sourceChecksum = snapshot.sourceChecksum
            )
        }
    }
}

object BioHazeManufacturerEngine {

    fun generateDeterministicChecksum(seed: Long, impactX: Float, impactY: Float): Long {
        var hash = -3750763034362895579L
        val prime = 1099511628211L
        hash = (hash xor seed) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(impactX).toLong()) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(impactY).toLong()) * prime
        return hash
    }

    fun createSplashPuddle(
        config: BioHazeTraitConfig,
        impactX: Float,
        impactY: Float,
        initialDamage: Int,
        seed: Long
    ): BioHazeSplashState? {
        if (!impactX.isFinite() || !impactY.isFinite() || config.splashRadiusPx <= 0f || config.splashDurationSeconds <= 0f) {
            return null
        }

        val effectiveDps = (initialDamage * config.splashDamagePercent).toInt().coerceAtLeast(config.baseSplashDps)
        val checksum = generateDeterministicChecksum(seed, impactX, impactY)

        return BioHazeSplashState(
            x = impactX,
            y = impactY,
            radius = config.splashRadiusPx.coerceIn(10f, 150f),
            dps = effectiveDps,
            remainingDurationSeconds = config.splashDurationSeconds.coerceIn(0.1f, 10f),
            sourceChecksum = checksum
        )
    }

    fun stepPuddles(
        puddles: MutableList<BioHazeSplashState>,
        dtSeconds: Float
    ) {
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)
        var i = 0
        while (i < puddles.size) {
            val p = puddles[i]
            p.remainingDurationSeconds -= dt
            if (p.remainingDurationSeconds <= 0f || !p.x.isFinite() || !p.y.isFinite() || p.radius <= 0f) {
                // Swap and pop for zero-allocation removal
                if (i < puddles.size - 1) {
                    puddles[i] = puddles[puddles.size - 1]
                    puddles.removeAt(puddles.size - 1)
                } else {
                    puddles.removeAt(puddles.size - 1)
                }
            } else {
                i++
            }
        }
    }

    /**
     * Applies damage to targets within splash puddles.
     * Uses pre-allocated arrays where possible or primitive math to avoid heap churn.
     * `targetX`, `targetY`, `targetHp` are parallel arrays representing entities.
     * Returns an array of total damage dealt to each corresponding index.
     */
    fun applySplashDamage(
        puddles: List<BioHazeSplashState>,
        targetX: FloatArray,
        targetY: FloatArray,
        targetHp: IntArray,
        targetRadius: FloatArray,
        dtSeconds: Float,
        outDamageAccumulator: FloatArray
    ) {
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)
        val numTargets = targetX.size
        
        // Zero out accumulator
        for (i in 0 until numTargets) {
            outDamageAccumulator[i] = 0f
        }

        for (pIdx in puddles.indices) {
            val p = puddles[pIdx]
            val maxR = p.radius
            val pDps = p.dps
            val px = p.x
            val py = p.y
            
            for (tIdx in 0 until numTargets) {
                if (targetHp[tIdx] <= 0) continue

                val tx = targetX[tIdx]
                val ty = targetY[tIdx]
                val tr = targetRadius[tIdx]
                
                if (!tx.isFinite() || !ty.isFinite()) continue

                val dx = tx - px
                val dy = ty - py
                val distSq = dx * dx + dy * dy
                val touchDist = (maxR + tr)
                
                if (distSq <= touchDist * touchDist) {
                    outDamageAccumulator[tIdx] += pDps * dt
                }
            }
        }
    }
}
