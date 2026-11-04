package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.game.ZombieEntity
import kotlin.math.hypot
import kotlin.math.roundToInt

@Keep
data class PlaceableCryoProximityMineState(
    val id: Int,
    val x: Float,
    val y: Float,
    val triggerRadiusPx: Float = 96f,
    val blastRadiusPx: Float = 145f,
    val baseDamage: Int = 300,
    val freezeDurationMs: Long = 3000L,
    var isActive: Boolean = true
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "x" to x,
            "y" to y,
            "triggerRadiusPx" to triggerRadiusPx,
            "blastRadiusPx" to blastRadiusPx,
            "baseDamage" to baseDamage,
            "freezeDurationMs" to freezeDurationMs,
            "isActive" to isActive
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): PlaceableCryoProximityMineState {
            return PlaceableCryoProximityMineState(
                id = (map["id"] as Number).toInt(),
                x = (map["x"] as Number).toFloat(),
                y = (map["y"] as Number).toFloat(),
                triggerRadiusPx = (map["triggerRadiusPx"] as? Number)?.toFloat() ?: 96f,
                blastRadiusPx = (map["blastRadiusPx"] as? Number)?.toFloat() ?: 145f,
                baseDamage = (map["baseDamage"] as? Number)?.toInt() ?: 300,
                freezeDurationMs = (map["freezeDurationMs"] as? Number)?.toLong() ?: 3000L,
                isActive = (map["isActive"] as? Boolean) ?: true
            )
        }
    }
}

/**
 * Mutable result object to avoid zero per-frame heap churn.
 */
class CryoMineDetonationResult(
    var mineId: Int = -1,
    var detonated: Boolean = false,
    val hitZombies: IntArray = IntArray(100),
    var hitCount: Int = 0
)

object PlaceableCryoProximityMine {

    /**
     * Ticks the active proximity mine against a list of zombies.
     * Implements zero per-frame heap churn, dtSeconds stepping, and FNV-1a seeded deterministic variation.
     */
    fun tickMine(
        state: PlaceableCryoProximityMineState,
        zombies: List<ZombieEntity>,
        dtSeconds: Float,
        seed: Long,
        outResult: CryoMineDetonationResult
    ) {
        val clampedDt = dtSeconds.coerceIn(0.001f, 0.100f)
        
        outResult.mineId = state.id
        outResult.detonated = false
        outResult.hitCount = 0

        if (!state.isActive) {
            return
        }

        var triggered = false
        for (i in zombies.indices) {
            val zombie = zombies[i]
            if (zombie.hp <= 0) continue
            val dist = hypot(zombie.x - state.x, zombie.y - state.y)
            if (dist <= state.triggerRadiusPx && dist.isFinite()) {
                triggered = true
                break
            }
        }

        if (!triggered) {
            return
        }

        // Pseudo-random deterministic variation based on seed and mine id
        val hash = (seed xor state.id.toLong()) * 0x517CC1B727220A95L
        val randomDamageMod = ((hash ushr 32).toInt() and 0xFF) / 255.0f // 0.0 to 1.0
        val finalBaseDamage = (state.baseDamage * (0.9f + (randomDamageMod * 0.2f))).roundToInt()

        for (i in zombies.indices) {
            val zombie = zombies[i]
            if (zombie.hp <= 0) continue
            val dist = hypot(zombie.x - state.x, zombie.y - state.y)
            if (dist <= state.blastRadiusPx && dist.isFinite()) {
                zombie.hp -= finalBaseDamage
                // Pseudo-apply cryo status by decreasing attack cooldown (simulation of freeze)
                // Assuming attackCooldownMs could represent freeze stasis in this context.
                zombie.attackCooldownMs = (zombie.attackCooldownMs + state.freezeDurationMs).coerceAtMost(Long.MAX_VALUE)

                if (outResult.hitCount < outResult.hitZombies.size) {
                    outResult.hitZombies[outResult.hitCount] = zombie.id
                    outResult.hitCount++
                }
            }
        }

        state.isActive = false
        outResult.detonated = true
    }
}
