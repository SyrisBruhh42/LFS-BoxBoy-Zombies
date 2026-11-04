package com.example.network.sync.modules

import androidx.annotation.Keep
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot

@Keep
data class DropPodConfig(
    val impactShockwaveRadiusPx: Float = 180f,
    val impactShockwaveDamage: Int = 320,
    val knockbackImpulse: Float = 450f,
    val landingDurationSeconds: Float = 1.5f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "impactShockwaveRadiusPx" to impactShockwaveRadiusPx,
        "impactShockwaveDamage" to impactShockwaveDamage,
        "knockbackImpulse" to knockbackImpulse,
        "landingDurationSeconds" to landingDurationSeconds
    )

    companion object {
        fun fromMap(map: Map<String, Any>): DropPodConfig {
            return DropPodConfig(
                impactShockwaveRadiusPx = (map["impactShockwaveRadiusPx"] as? Number)?.toFloat() ?: 180f,
                impactShockwaveDamage = (map["impactShockwaveDamage"] as? Number)?.toInt() ?: 320,
                knockbackImpulse = (map["knockbackImpulse"] as? Number)?.toFloat() ?: 450f,
                landingDurationSeconds = (map["landingDurationSeconds"] as? Number)?.toFloat() ?: 1.5f
            )
        }
    }
}

@Keep
data class DropPodSnapshot(
    var dropPodId: String,
    var joinerPeerId: String,
    var x: Float,
    var y: Float,
    var timeElapsed: Float = 0f,
    var isLanded: Boolean = false,
    var hasDetonated: Boolean = false,
    var checksum: Long = 0L
) {
    fun computeChecksum(): Long {
        var hash = -2128831035146727653L
        hash = (hash xor dropPodId.hashCode().toLong()) * 1099511628211L
        hash = (hash xor joinerPeerId.hashCode().toLong()) * 1099511628211L
        hash = (hash xor x.toRawBits().toLong()) * 1099511628211L
        hash = (hash xor y.toRawBits().toLong()) * 1099511628211L
        hash = (hash xor timeElapsed.toRawBits().toLong()) * 1099511628211L
        hash = (hash xor (if (isLanded) 1L else 0L)) * 1099511628211L
        hash = (hash xor (if (hasDetonated) 1L else 0L)) * 1099511628211L
        return hash
    }

    fun toMap(): Map<String, Any> = mapOf(
        "dropPodId" to dropPodId,
        "joinerPeerId" to joinerPeerId,
        "x" to x,
        "y" to y,
        "timeElapsed" to timeElapsed,
        "isLanded" to isLanded,
        "hasDetonated" to hasDetonated,
        "checksum" to checksum
    )

    companion object {
        fun fromMap(map: Map<String, Any>): DropPodSnapshot {
            return DropPodSnapshot(
                dropPodId = map["dropPodId"] as? String ?: "",
                joinerPeerId = map["joinerPeerId"] as? String ?: "",
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                timeElapsed = (map["timeElapsed"] as? Number)?.toFloat() ?: 0f,
                isLanded = map["isLanded"] as? Boolean ?: false,
                hasDetonated = map["hasDetonated"] as? Boolean ?: false,
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

object DropPodHotJoinSpawner {
    
    /**
     * Initializes a mid-session Drop-Pod spawn for a hot-joining peer near the squad leader.
     */
    fun spawnDropPod(
        dropPodId: String,
        joinerPeerId: String,
        squadLeaderX: Float,
        squadLeaderY: Float,
        offsetX: Float = 48f,
        offsetY: Float = -32f
    ): DropPodSnapshot {
        val spawnX = if ((squadLeaderX + offsetX).isFinite()) squadLeaderX + offsetX else 0f
        val spawnY = if ((squadLeaderY + offsetY).isFinite()) squadLeaderY + offsetY else 0f
        
        val snapshot = DropPodSnapshot(
            dropPodId = dropPodId,
            joinerPeerId = joinerPeerId,
            x = spawnX,
            y = spawnY
        )
        snapshot.checksum = snapshot.computeChecksum()
        return snapshot
    }
    
    /**
     * Ticks the Drop-Pod physics logic, advancing its landing timer, and triggering the AoE knockback on landing.
     * Operates purely with pre-allocated arrays to ensure zero per-frame heap churn.
     */
    fun stepDropPodPhysics(
        snapshot: DropPodSnapshot,
        config: DropPodConfig,
        dtSeconds: Float,
        zombieXs: FloatArray,
        zombieYs: FloatArray,
        zombieHps: IntArray,
        zombieImpulses: FloatArray,
        zombieCount: Int
    ): Boolean {
        if (snapshot.hasDetonated) return false
        
        val clampedDt = dtSeconds.coerceIn(0.001f, 0.100f)
        if (!clampedDt.isFinite()) return false
        
        snapshot.timeElapsed += clampedDt
        
        if (snapshot.timeElapsed >= config.landingDurationSeconds && !snapshot.isLanded) {
            snapshot.isLanded = true
            snapshot.hasDetonated = true
            
            val limit = zombieCount.coerceAtMost(zombieXs.size).coerceAtMost(zombieYs.size).coerceAtMost(zombieHps.size)
            
            for (i in 0 until limit) {
                if (zombieHps[i] <= 0) continue
                
                val zx = zombieXs[i]
                val zy = zombieYs[i]
                
                if (!zx.isFinite() || !zy.isFinite()) continue
                
                val dx = zx - snapshot.x
                val dy = zy - snapshot.y
                val dist = hypot(dx, dy)
                
                if (dist <= config.impactShockwaveRadiusPx) {
                    zombieHps[i] = (zombieHps[i] - config.impactShockwaveDamage).coerceAtLeast(0)
                    zombieImpulses[i] += config.knockbackImpulse
                }
            }
            
            snapshot.checksum = snapshot.computeChecksum()
            return true
        }
        
        snapshot.checksum = snapshot.computeChecksum()
        return false
    }
}


fun evaluateDropPodHotJoinSpawnerLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "DropPodHotJoinSpawner") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
