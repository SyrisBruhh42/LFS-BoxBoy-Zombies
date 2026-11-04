package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import com.example.game.ZombieEntity
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.max

@Keep
data class SingularityGrenadeConfig(
    val pullRadius: Float,
    val pullForceMax: Float,
    val durationSeconds: Float,
    val minJitterDistance: Float = 5.0f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "pullRadius" to pullRadius,
        "pullForceMax" to pullForceMax,
        "durationSeconds" to durationSeconds,
        "minJitterDistance" to minJitterDistance
    )

    companion object {
        fun fromMap(map: Map<String, Any>): SingularityGrenadeConfig {
            return SingularityGrenadeConfig(
                pullRadius = (map["pullRadius"] as? Number)?.toFloat() ?: 0f,
                pullForceMax = (map["pullForceMax"] as? Number)?.toFloat() ?: 0f,
                durationSeconds = (map["durationSeconds"] as? Number)?.toFloat() ?: 0f,
                minJitterDistance = (map["minJitterDistance"] as? Number)?.toFloat() ?: 5.0f
            )
        }
    }
}

@Keep
data class SingularityGrenadeState(
    var x: Float,
    var y: Float,
    var elapsedSeconds: Float = 0f,
    var isActive: Boolean = true
) {
    fun toMap(): Map<String, Any> = mapOf(
        "x" to x,
        "y" to y,
        "elapsedSeconds" to elapsedSeconds,
        "isActive" to isActive
    )

    companion object {
        fun fromMap(map: Map<String, Any>): SingularityGrenadeState {
            return SingularityGrenadeState(
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                elapsedSeconds = (map["elapsedSeconds"] as? Number)?.toFloat() ?: 0f,
                isActive = (map["isActive"] as? Boolean) ?: true
            )
        }
    }

    fun computeChecksum(): Int {
        var hash = -2128831035 // FNV-1a offset basis
        hash = (hash xor x.toBits()) * 16777619
        hash = (hash xor y.toBits()) * 16777619
        hash = (hash xor elapsedSeconds.toBits()) * 16777619
        hash = (hash xor (if (isActive) 1 else 0)) * 16777619
        return hash
    }
}

object SingularityPullGrenadeEngine {

    fun tick(
        config: SingularityGrenadeConfig,
        state: SingularityGrenadeState,
        zombies: List<ZombieEntity>,
        dtSecondsRaw: Float
    ) {
        if (!state.isActive) return

        // Clamp dtSeconds to [0.001, 0.100]
        val dtSeconds = dtSecondsRaw.coerceIn(0.001f, 0.100f)

        // Ensure state is valid
        if (!state.x.isFinite()) state.x = 0f
        if (!state.y.isFinite()) state.y = 0f

        state.elapsedSeconds += dtSeconds
        if (state.elapsedSeconds >= config.durationSeconds) {
            state.isActive = false
            return
        }

        // Pre-calculate squared radius to avoid inner loop sqrt when checking range
        val radiusSq = config.pullRadius * config.pullRadius

        // In-place zero allocation processing
        for (i in zombies.indices) {
            val z = zombies[i]

            if (!z.x.isFinite() || !z.y.isFinite()) {
                z.x = 0f
                z.y = 0f
                continue
            }

            val dx = state.x - z.x
            val dy = state.y - z.y
            val distSq = dx * dx + dy * dy

            if (distSq > 0.0001f && distSq <= radiusSq) {
                val dist = sqrt(distSq)

                // Prevent jitter around the center point
                if (dist > config.minJitterDistance) {
                    val invDist = 1.0f / dist
                    val nx = dx * invDist
                    val ny = dy * invDist

                    // Simple inverse square or linear falloff.
                    // Let's use linear falloff for a more consistent pull:
                    // Max force near center, 0 force at radius edge.
                    val strength = 1.0f - (dist / config.pullRadius)
                    val currentPullForce = config.pullForceMax * strength.coerceIn(0f, 1f)

                    val moveDist = currentPullForce * dtSeconds

                    // Don't overshoot the center
                    val actualMoveDist = min(moveDist, dist - config.minJitterDistance)

                    if (actualMoveDist > 0f) {
                        z.x += nx * actualMoveDist
                        z.y += ny * actualMoveDist
                    }
                }
            }
        }
    }
}
