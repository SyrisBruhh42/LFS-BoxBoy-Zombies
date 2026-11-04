package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import com.example.game.combat.modules.RedBarrel
import kotlin.math.hypot

@Keep
data class TorgueBlastConfig(
    val proximityRadiusPx: Float = 100f,
    val baseDamage: Float = 50f,
    val gyrojetAcceleration: Float = 450f,
    val maxSpeed: Float = 1200f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "proximityRadiusPx" to proximityRadiusPx,
        "baseDamage" to baseDamage,
        "gyrojetAcceleration" to gyrojetAcceleration,
        "maxSpeed" to maxSpeed
    )

    companion object {
        fun fromMap(map: Map<String, Any>): TorgueBlastConfig {
            val proximityRadiusPx = (map["proximityRadiusPx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 100f
            val baseDamage = (map["baseDamage"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 50f
            val gyrojetAcceleration = (map["gyrojetAcceleration"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 450f
            val maxSpeed = (map["maxSpeed"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 1200f
            
            return TorgueBlastConfig(
                proximityRadiusPx = proximityRadiusPx.coerceIn(10f, 1000f),
                baseDamage = baseDamage.coerceIn(0f, 10000f),
                gyrojetAcceleration = gyrojetAcceleration.coerceIn(0f, 5000f),
                maxSpeed = maxSpeed.coerceIn(10f, 5000f)
            )
        }
    }
}

@Keep
data class GyrojetProjectile(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var isActive: Boolean = true
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "x" to x,
        "y" to y,
        "vx" to vx,
        "vy" to vy,
        "isActive" to isActive
    )
    
    fun computeChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor id.toLong()) * 16777619L
        hash = (hash xor x.toBits().toLong()) * 16777619L
        hash = (hash xor y.toBits().toLong()) * 16777619L
        hash = (hash xor vx.toBits().toLong()) * 16777619L
        hash = (hash xor vy.toBits().toLong()) * 16777619L
        hash = (hash xor (if (isActive) 1L else 0L)) * 16777619L
        return hash
    }
    
    companion object {
        fun fromMap(map: Map<String, Any>): GyrojetProjectile {
            val id = (map["id"] as? Number)?.toInt() ?: 0
            val x = (map["x"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            val y = (map["y"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            val vx = (map["vx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            val vy = (map["vy"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            val isActive = map["isActive"] as? Boolean ?: true
            
            return GyrojetProjectile(id, x, y, vx, vy, isActive)
        }
    }
}

@Keep
data class TorgueBlastResult(
    val stateChecksum: Long,
    val updatedProjectiles: List<GyrojetProjectile>,
    val detonatedBarrelIds: List<Int>
)

object TorgueBlastManufacturerTrait {

    fun step(
        dtSecondsRaw: Float,
        projectiles: List<GyrojetProjectile>,
        barrels: List<RedBarrel>,
        config: TorgueBlastConfig
    ): TorgueBlastResult {
        // Clamp dtSeconds, protect against NaN/Infinity
        val dtSeconds = if (dtSecondsRaw.isFinite()) {
            dtSecondsRaw.coerceIn(0.001f, 0.100f)
        } else {
            0.016f // Default 60Hz frame time
        }

        val updatedProjectiles = ArrayList<GyrojetProjectile>(projectiles.size)
        val detonatedBarrelIds = mutableListOf<Int>()
        var checksumAccumulator = -2128831035L // FNV-1a offset basis (32-bit) but we use 64-bit

        for (i in projectiles.indices) {
            val p = projectiles[i]
            
            if (!p.isActive) {
                // Keep inactive projectiles but don't simulate them
                updatedProjectiles.add(GyrojetProjectile(p.id, p.x, p.y, p.vx, p.vy, false))
                continue
            }
            
            // Clean state handling against NaN/Infinity in input
            var px = if (p.x.isFinite()) p.x else 0f
            var py = if (p.y.isFinite()) p.y else 0f
            var pvx = if (p.vx.isFinite()) p.vx else 0f
            var pvy = if (p.vy.isFinite()) p.vy else 0f
            
            // Gyrojet acceleration
            val speedSq = pvx * pvx + pvy * pvy
            if (speedSq > 0.0001f) {
                val speed = kotlin.math.sqrt(speedSq)
                var newSpeed = speed + (config.gyrojetAcceleration * dtSeconds)
                if (newSpeed > config.maxSpeed) {
                    newSpeed = config.maxSpeed
                }
                val factor = newSpeed / speed
                pvx *= factor
                pvy *= factor
            } else {
                // Edge case: zero velocity, apply some default velocity or keep at zero
            }

            // Euler integration
            px += pvx * dtSeconds
            py += pvy * dtSeconds
            
            var detonated = false
            
            // Spatial Proximity Check
            for (j in barrels.indices) {
                val b = barrels[j]
                if (b.detonated) continue
                
                val dx = px - b.x
                val dy = py - b.y
                val distSq = dx * dx + dy * dy
                val radSq = config.proximityRadiusPx * config.proximityRadiusPx
                
                if (distSq <= radSq) {
                    // Trigger detonation
                    detonated = true
                    b.detonated = true
                    detonatedBarrelIds.add(b.id)
                }
            }
            
            val active = !detonated
            val np = GyrojetProjectile(p.id, px, py, pvx, pvy, active)
            updatedProjectiles.add(np)
            
            checksumAccumulator = (checksumAccumulator xor np.computeChecksum()) * 16777619L
        }

        return TorgueBlastResult(
            stateChecksum = checksumAccumulator,
            updatedProjectiles = updatedProjectiles,
            detonatedBarrelIds = detonatedBarrelIds
        )
    }
}


fun evaluateTorgueBlastManufacturerTraitLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "TorgueBlastManufacturerTrait") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
