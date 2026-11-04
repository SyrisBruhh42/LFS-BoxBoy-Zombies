package com.example.game.relics.items

import androidx.annotation.Keep
import com.example.game.ZombieEntity
import kotlin.math.hypot

@Keep
data class Relic19gasolineCanisterConfig(
    val baseRadiusPx: Float = 100f,
    val bonusRadiusPerStackPx: Float = 40f,
    val bonusBurnPerStack: Float = 0.75f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "baseRadiusPx" to baseRadiusPx,
        "bonusRadiusPerStackPx" to bonusRadiusPerStackPx,
        "bonusBurnPerStack" to bonusBurnPerStack
    )

    companion object {
        fun fromMap(map: Map<String, Any>?): Relic19gasolineCanisterConfig {
            if (map == null) return Relic19gasolineCanisterConfig()
            return Relic19gasolineCanisterConfig(
                baseRadiusPx = (map["baseRadiusPx"] as? Number)?.toFloat() ?: 100f,
                bonusRadiusPerStackPx = (map["bonusRadiusPerStackPx"] as? Number)?.toFloat() ?: 40f,
                bonusBurnPerStack = (map["bonusBurnPerStack"] as? Number)?.toFloat() ?: 0.75f
            )
        }
    }
}

@Keep
data class Relic19gasolineCanisterState(
    val stackCount: Int
) {
    init {
        require(stackCount >= 1) { "Relic19gasolineCanister requires stackCount >= 1" }
    }

    val canBePurchasedAtVending: Boolean
        get() = false

    fun computeChecksum(): Long {
        var hash = -2128831035L
        hash = (hash xor stackCount.toLong()) * 16777619L
        return hash
    }

    fun toMap(): Map<String, Any> = mapOf(
        "stackCount" to stackCount,
        "canBePurchasedAtVending" to canBePurchasedAtVending
    )

    companion object {
        fun fromMap(map: Map<String, Any>?): Relic19gasolineCanisterState {
            val count = (map?.get("stackCount") as? Number)?.toInt() ?: 1
            return Relic19gasolineCanisterState(stackCount = maxOf(1, count))
        }
    }
}

class Relic19gasolineCanisterEngine(
    private val config: Relic19gasolineCanisterConfig = Relic19gasolineCanisterConfig(),
    private val maxIgnitedTargets: Int = 300
) {
    // Zero-allocation parallel arrays for active burns
    private val ignitedZombieIds = IntArray(maxIgnitedTargets)
    private val burnRemainingDurationMs = FloatArray(maxIgnitedTargets)
    private val burnDps = FloatArray(maxIgnitedTargets)
    private var ignitedCount = 0

    fun registerKill(
        killedX: Float,
        killedY: Float,
        killedMaxHp: Int,
        state: Relic19gasolineCanisterState,
        zombies: List<ZombieEntity>
    ) {
        if (!killedX.isFinite() || !killedY.isFinite()) return

        val radius = config.baseRadiusPx + (state.stackCount * config.bonusRadiusPerStackPx)
        val dpsMultiplier = 1.0f + (state.stackCount * config.bonusBurnPerStack)
        val damagePerSecond = (killedMaxHp * 0.5f * dpsMultiplier).coerceAtLeast(1f) // Base DPS is 50% of killed max hp
        val durationMs = 3000f // Hardcoded to 3s typical standard

        for (i in zombies.indices) {
            val z = zombies[i]
            if (z.hp <= 0) continue

            if (!z.x.isFinite() || !z.y.isFinite()) continue

            val dist = hypot(z.x - killedX, z.y - killedY)
            if (dist <= radius) {
                igniteZombie(z.id, damagePerSecond, durationMs)
            }
        }
    }

    private fun igniteZombie(zombieId: Int, dps: Float, durationMs: Float) {
        // Check if already ignited to update/refresh
        for (i in 0 until ignitedCount) {
            if (ignitedZombieIds[i] == zombieId) {
                burnRemainingDurationMs[i] = maxOf(burnRemainingDurationMs[i], durationMs)
                burnDps[i] = maxOf(burnDps[i], dps)
                return
            }
        }

        // Add new
        if (ignitedCount < maxIgnitedTargets) {
            ignitedZombieIds[ignitedCount] = zombieId
            burnRemainingDurationMs[ignitedCount] = durationMs
            burnDps[ignitedCount] = dps
            ignitedCount++
        }
    }

    fun tick(dtSeconds: Float, zombies: List<ZombieEntity>) {
        val clampedDt = dtSeconds.coerceIn(0.001f, 0.100f)
        val dtMs = clampedDt * 1000f

        var i = 0
        while (i < ignitedCount) {
            burnRemainingDurationMs[i] -= dtMs

            if (burnRemainingDurationMs[i] <= 0f) {
                // Swap and pop
                ignitedCount--
                if (i < ignitedCount) {
                    ignitedZombieIds[i] = ignitedZombieIds[ignitedCount]
                    burnRemainingDurationMs[i] = burnRemainingDurationMs[ignitedCount]
                    burnDps[i] = burnDps[ignitedCount]
                }
                // Don't increment i, we need to process the swapped element
            } else {
                val zId = ignitedZombieIds[i]
                var z: ZombieEntity? = null
                for (j in zombies.indices) {
                    if (zombies[j].id == zId) {
                        z = zombies[j]
                        break
                    }
                }

                if (z != null && z.hp > 0) {
                    val damageThisFrame = (burnDps[i] * clampedDt).toInt().coerceAtLeast(1)
                    z.hp -= damageThisFrame
                } else {
                    // Zombie dead or missing, remove burn
                    ignitedCount--
                    if (i < ignitedCount) {
                        ignitedZombieIds[i] = ignitedZombieIds[ignitedCount]
                        burnRemainingDurationMs[i] = burnRemainingDurationMs[ignitedCount]
                        burnDps[i] = burnDps[ignitedCount]
                    }
                    continue
                }
                i++
            }
        }
    }

    fun getActiveBurnCount(): Int = ignitedCount

    fun clear() {
        ignitedCount = 0
    }
}
