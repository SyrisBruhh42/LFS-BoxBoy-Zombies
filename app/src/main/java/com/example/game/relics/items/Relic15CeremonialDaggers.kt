package com.example.game.relics.items

import androidx.annotation.Keep
import com.example.game.ZombieEntity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Keep
data class CeremonialDaggerConfig(
    val baseDamage: Int = 100,
    val damageMultiplierPerStack: Float = 1.5f, // +150% damage per stack (total = base * (1 + 1.5 * stack))
    val daggersPerKill: Int = 3,
    val speed: Float = 400f,
    val maxLifetimeSeconds: Float = 5.0f,
    val searchRadius: Float = 600f,
    val turnSpeedRadPerSec: Float = 6.0f
)

@Keep
data class CeremonialDaggersState(
    val stackCount: Int = 1,
    val canBePurchasedAtVending: Boolean = false
) {
    init {
        require(stackCount >= 1) { "CeremonialDaggersState must have stackCount >= 1" }
        require(!canBePurchasedAtVending) { "CeremonialDaggersState drop-only invariant violated" }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "stackCount" to stackCount,
            "canBePurchasedAtVending" to canBePurchasedAtVending
        )
    }

    fun computeChecksum(): Long {
        var hash = -2128831035146727685L // FNV offset basis
        hash = (hash xor stackCount.toLong()) * 1099511628211L
        hash = (hash xor (if (canBePurchasedAtVending) 1L else 0L)) * 1099511628211L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): CeremonialDaggersState {
            val count = (map["stackCount"] as? Number)?.toInt() ?: 1
            val vend = map["canBePurchasedAtVending"] as? Boolean ?: false
            return CeremonialDaggersState(
                stackCount = count.coerceAtLeast(1),
                canBePurchasedAtVending = vend
            )
        }
    }
}

class Relic15CeremonialDaggersEngine(
    val config: CeremonialDaggerConfig = CeremonialDaggerConfig(),
    maxDaggers: Int = 300
) {
    // Zero-allocation parallel arrays for active daggers
    private val activeFlags = BooleanArray(maxDaggers)
    private val xPos = FloatArray(maxDaggers)
    private val yPos = FloatArray(maxDaggers)
    private val dx = FloatArray(maxDaggers)
    private val dy = FloatArray(maxDaggers)
    private val targetId = IntArray(maxDaggers)
    private val lifetime = FloatArray(maxDaggers)
    private val damage = IntArray(maxDaggers)

    fun reset() {
        for (i in activeFlags.indices) {
            activeFlags[i] = false
        }
    }

    fun onZombieKilled(
        killedX: Float,
        killedY: Float,
        state: CeremonialDaggersState,
        seed: Long
    ) {
        val totalDamage = (config.baseDamage * (1f + config.damageMultiplierPerStack * state.stackCount)).toInt()
        
        var currentSeed = seed
        for (i in 0 until config.daggersPerKill) {
            // LCG for spawn angle
            currentSeed = (currentSeed * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
            val randomVal = (currentSeed ushr 16).toInt()
            val angle = (randomVal % 360) * (Math.PI / 180.0).toFloat()
            
            spawnDagger(
                x = killedX,
                y = killedY,
                dirX = cos(angle),
                dirY = sin(angle),
                dmg = totalDamage
            )
        }
    }

    private fun spawnDagger(x: Float, y: Float, dirX: Float, dirY: Float, dmg: Int) {
        if (!x.isFinite() || !y.isFinite() || !dirX.isFinite() || !dirY.isFinite()) return
        
        for (i in activeFlags.indices) {
            if (!activeFlags[i]) {
                activeFlags[i] = true
                xPos[i] = x
                yPos[i] = y
                dx[i] = dirX
                dy[i] = dirY
                targetId[i] = -1
                lifetime[i] = 0f
                damage[i] = dmg
                break
            }
        }
    }

    fun stepDaggers(
        dtSeconds: Float,
        zombies: List<ZombieEntity>
    ): List<DaggerHit> {
        if (!dtSeconds.isFinite() || dtSeconds <= 0f) return emptyList()
        val lifeDt = dtSeconds.coerceIn(0.001f, 10.0f)
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)

        val hits = mutableListOf<DaggerHit>()
        
        for (i in activeFlags.indices) {
            if (!activeFlags[i]) continue
            
            lifetime[i] += lifeDt
            if (lifetime[i] > config.maxLifetimeSeconds || !lifetime[i].isFinite()) {
                activeFlags[i] = false
                continue
            }

            // Pre-movement proximity collision check
            var immediateHit = false
            for (z in zombies) {
                if (z.hp > 0) {
                    val distToZombie = hypot(z.x - xPos[i], z.y - yPos[i])
                    if (distToZombie < 20f && distToZombie.isFinite()) {
                        hits.add(DaggerHit(z.id, damage[i]))
                        activeFlags[i] = false
                        immediateHit = true
                        break
                    }
                }
            }
            if (immediateHit) continue
            
            // 1. Target acquisition / re-evaluation
            if (targetId[i] == -1 || !isZombieAliveAndValid(targetId[i], zombies)) {
                targetId[i] = findClosestValidTarget(xPos[i], yPos[i], zombies)
            }
            
            // 2. Homing steering
            if (targetId[i] != -1) {
                val target = getZombieById(targetId[i], zombies)
                if (target != null) {
                    val toTargetX = target.x - xPos[i]
                    val toTargetY = target.y - yPos[i]
                    val dist = hypot(toTargetX, toTargetY)
                    
                    if (dist.isFinite() && dist > 0.01f) {
                        val desiredDx = toTargetX / dist
                        val desiredDy = toTargetY / dist
                        
                        val currentAngle = atan2(dy[i], dx[i])
                        val desiredAngle = atan2(desiredDy, desiredDx)
                        
                        var angleDiff = desiredAngle - currentAngle
                        while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI.toFloat()
                        while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI.toFloat()
                        
                        val maxTurn = config.turnSpeedRadPerSec * dt
                        val turn = angleDiff.coerceIn(-maxTurn, maxTurn)
                        
                        val newAngle = currentAngle + turn
                        dx[i] = cos(newAngle)
                        dy[i] = sin(newAngle)
                    }
                } else {
                    targetId[i] = -1
                }
            }
            
            // 3. Apply velocity
            val moveX = dx[i] * config.speed * dt
            val moveY = dy[i] * config.speed * dt
            if (moveX.isFinite() && moveY.isFinite()) {
                xPos[i] += moveX
                yPos[i] += moveY
            } else {
                activeFlags[i] = false
                continue
            }
            
            // 4. Collision check
            var hitDetected = false
            for (z in zombies) {
                if (z.hp > 0) {
                    val distToZombie = hypot(z.x - xPos[i], z.y - yPos[i])
                    if (distToZombie < 20f && distToZombie.isFinite()) {
                        hits.add(DaggerHit(z.id, damage[i]))
                        activeFlags[i] = false
                        hitDetected = true
                        break
                    }
                }
            }
        }
        
        return hits
    }

    private fun isZombieAliveAndValid(id: Int, zombies: List<ZombieEntity>): Boolean {
        for (z in zombies) {
            if (z.id == id) {
                return z.hp > 0
            }
        }
        return false
    }

    private fun getZombieById(id: Int, zombies: List<ZombieEntity>): ZombieEntity? {
        for (z in zombies) {
            if (z.id == id) {
                return z
            }
        }
        return null
    }

    private fun findClosestValidTarget(x: Float, y: Float, zombies: List<ZombieEntity>): Int {
        var bestId = -1
        var bestDist = config.searchRadius
        
        for (z in zombies) {
            if (z.hp > 0) {
                val dist = hypot(z.x - x, z.y - y)
                if (dist < bestDist && dist.isFinite()) {
                    bestDist = dist
                    bestId = z.id
                }
            }
        }
        
        return bestId
    }
    
    fun getActiveDaggerCount(): Int {
        var count = 0
        for (active in activeFlags) {
            if (active) count++
        }
        return count
    }
    
    fun getActiveDaggerPositions(): List<Pair<Float, Float>> {
        val list = mutableListOf<Pair<Float, Float>>()
        for (i in activeFlags.indices) {
            if (activeFlags[i]) {
                list.add(Pair(xPos[i], yPos[i]))
            }
        }
        return list
    }
}

data class DaggerHit(
    val zombieId: Int,
    val damage: Int
)


fun evaluateRelic15CeremonialDaggersLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic15CeremonialDaggers") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
