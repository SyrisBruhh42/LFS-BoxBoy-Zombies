package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import com.example.game.GameSessionState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

@Keep
data class TransfusionGrenadeConfig(
    val fuseTimeSeconds: Float = 2.0f,
    val explosionRadius: Float = 150.0f,
    val damage: Int = 120,
    val maxTrails: Int = 8,
    val trailSpeed: Float = 200.0f,
    val healingPerTrail: Int = 15
) {
    init {
        require(fuseTimeSeconds > 0f) { "fuseTimeSeconds must be positive" }
        require(explosionRadius > 0f) { "explosionRadius must be positive" }
        require(maxTrails in 1..16) { "maxTrails must be between 1 and 16" }
        require(trailSpeed > 0f) { "trailSpeed must be positive" }
        require(healingPerTrail >= 0) { "healingPerTrail cannot be negative" }
    }
}

@Keep
class TransfusionGrenadeState(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var fuseTimerSeconds: Float = 0f,
    var hasDetonated: Boolean = false,
    var trailsActiveCount: Int = 0,
    // Maximum 16 trails
    val trailX: FloatArray = FloatArray(16),
    val trailY: FloatArray = FloatArray(16),
    val trailTargetId: Array<String> = Array(16) { "" }, // Empty string means self player
    val trailActive: BooleanArray = BooleanArray(16)
) {
    fun computeChecksum(): Long {
        var hash = -2128831035146727653L // FNV offset basis
        hash = hash xor x.toBits().toLong()
        hash = hash * 1099511628211L
        hash = hash xor y.toBits().toLong()
        hash = hash * 1099511628211L
        hash = hash xor vx.toBits().toLong()
        hash = hash * 1099511628211L
        hash = hash xor vy.toBits().toLong()
        hash = hash * 1099511628211L
        hash = hash xor fuseTimerSeconds.toBits().toLong()
        hash = hash * 1099511628211L
        hash = hash xor (if (hasDetonated) 1L else 0L)
        hash = hash * 1099511628211L
        hash = hash xor trailsActiveCount.toLong()
        hash = hash * 1099511628211L

        for (i in 0 until 16) {
            hash = hash xor trailX[i].toBits().toLong()
            hash = hash * 1099511628211L
            hash = hash xor trailY[i].toBits().toLong()
            hash = hash * 1099511628211L
            hash = hash xor (if (trailActive[i]) 1L else 0L)
            hash = hash * 1099511628211L
            hash = hash xor trailTargetId[i].hashCode().toLong()
            hash = hash * 1099511628211L
        }
        return hash
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "x" to x,
            "y" to y,
            "vx" to vx,
            "vy" to vy,
            "fuseTimerSeconds" to fuseTimerSeconds,
            "hasDetonated" to hasDetonated,
            "trailsActiveCount" to trailsActiveCount,
            "trailX" to trailX.toList(),
            "trailY" to trailY.toList(),
            "trailTargetId" to trailTargetId.toList(),
            "trailActive" to trailActive.toList()
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): TransfusionGrenadeState {
            val state = TransfusionGrenadeState(
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                vx = (map["vx"] as? Number)?.toFloat() ?: 0f,
                vy = (map["vy"] as? Number)?.toFloat() ?: 0f,
                fuseTimerSeconds = (map["fuseTimerSeconds"] as? Number)?.toFloat() ?: 0f,
                hasDetonated = map["hasDetonated"] as? Boolean ?: false,
                trailsActiveCount = (map["trailsActiveCount"] as? Number)?.toInt() ?: 0
            )

            val tx = map["trailX"] as? List<*>
            val ty = map["trailY"] as? List<*>
            val tti = map["trailTargetId"] as? List<*>
            val ta = map["trailActive"] as? List<*>

            for (i in 0 until 16) {
                state.trailX[i] = (tx?.getOrNull(i) as? Number)?.toFloat() ?: 0f
                state.trailY[i] = (ty?.getOrNull(i) as? Number)?.toFloat() ?: 0f
                state.trailTargetId[i] = tti?.getOrNull(i) as? String ?: ""
                state.trailActive[i] = ta?.getOrNull(i) as? Boolean ?: false
            }

            return state
        }
    }
}

class TransfusionGrenadeEngine(private val config: TransfusionGrenadeConfig) {

    data class HealingEvent(val targetId: String, val amount: Int)

    fun tick(
        state: TransfusionGrenadeState,
        dtSeconds: Float,
        gameSession: GameSessionState,
        randomSeed: Long = 0L
    ): List<HealingEvent> {
        if (!dtSeconds.isFinite() || dtSeconds <= 0f) return emptyList()
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)

        if (!state.hasDetonated) {
            state.x += state.vx * dt
            state.y += state.vy * dt
            state.fuseTimerSeconds += dt

            if (state.fuseTimerSeconds >= config.fuseTimeSeconds) {
                detonate(state, gameSession, randomSeed)
            }
            return emptyList()
        } else {
            return tickTrails(state, dt, gameSession)
        }
    }

    private fun detonate(state: TransfusionGrenadeState, gameSession: GameSessionState, randomSeed: Long) {
        state.hasDetonated = true
        var damageDealtCount = 0

        for (zombie in gameSession.zombies) {
            if (zombie.hp > 0 && hypot(zombie.x - state.x, zombie.y - state.y) <= config.explosionRadius) {
                zombie.hp -= config.damage
                damageDealtCount++
            }
        }

        val numTrails = min(damageDealtCount, config.maxTrails)
        if (numTrails > 0) {
            val random = Random(randomSeed)

            val validTargets = mutableListOf<String>()
            if (gameSession.hp > 0) {
                validTargets.add("")
            }
            for (peer in gameSession.coopPeers) {
                if (peer.hp > 0) {
                    validTargets.add(peer.peerId)
                }
            }

            if (validTargets.isNotEmpty()) {
                state.trailsActiveCount = numTrails
                for (i in 0 until numTrails) {
                    state.trailActive[i] = true
                    state.trailX[i] = state.x
                    state.trailY[i] = state.y
                    state.trailTargetId[i] = validTargets[random.nextInt(validTargets.size)]
                }
            }
        }
    }

    private fun tickTrails(state: TransfusionGrenadeState, dt: Float, gameSession: GameSessionState): List<HealingEvent> {
        var healingEvents: MutableList<HealingEvent>? = null
        for (i in 0 until 16) {
            if (state.trailActive[i]) {
                val targetId = state.trailTargetId[i]
                var targetX = 0f
                var targetY = 0f
                var targetAlive = false

                if (targetId.isEmpty()) {
                    targetX = gameSession.playerX
                    targetY = gameSession.playerY
                    targetAlive = gameSession.hp > 0
                } else {
                    val peer = gameSession.coopPeers.find { it.peerId == targetId }
                    if (peer != null) {
                        targetX = peer.x
                        targetY = peer.y
                        targetAlive = peer.hp > 0
                    }
                }

                if (targetAlive) {
                    val dx = targetX - state.trailX[i]
                    val dy = targetY - state.trailY[i]
                    val dist = hypot(dx, dy)
                    val moveDist = config.trailSpeed * dt

                    if (dist <= moveDist || !moveDist.isFinite()) {
                        state.trailActive[i] = false
                        state.trailsActiveCount--
                        if (healingEvents == null) healingEvents = mutableListOf(); healingEvents.add(HealingEvent(targetId, config.healingPerTrail))
                    } else {
                        val angle = atan2(dy, dx)
                        state.trailX[i] += cos(angle) * moveDist
                        state.trailY[i] += sin(angle) * moveDist
                    }
                } else {
                    state.trailActive[i] = false
                    state.trailsActiveCount--
                }
            }
        }
        return healingEvents ?: emptyList()
    }
}
