package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.game.ZombieEntity
import com.example.game.economy.BarricadeWindowTier

@Keep
data class AutoTurretState(
    var currentHp: Int = BarricadeWindowTier.TIER_4_AUTO_SENTRY_MOUNT.maxHp,
    var accumulatorMs: Float = 0f,
    var isDestroyed: Boolean = false,
    var randomSeed: Int = 12345
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "currentHp" to currentHp,
            "accumulatorMs" to accumulatorMs,
            "isDestroyed" to isDestroyed,
            "randomSeed" to randomSeed
        )
    }

    fun computeChecksum(): Long {
        var hash = -2128831035157718042L
        hash = (hash xor currentHp.toLong()) * 1099511628211L
        hash = (hash xor accumulatorMs.toBits().toLong()) * 1099511628211L
        hash = (hash xor (if (isDestroyed) 1L else 0L)) * 1099511628211L
        hash = (hash xor randomSeed.toLong()) * 1099511628211L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): AutoTurretState {
            val maxHp = BarricadeWindowTier.TIER_4_AUTO_SENTRY_MOUNT.maxHp
            val currentHp = (map["currentHp"] as? Number)?.toInt() ?: maxHp
            val accumulatorMs = (map["accumulatorMs"] as? Number)?.toFloat() ?: 0f
            val isDestroyed = (map["isDestroyed"] as? Boolean) ?: false
            val randomSeed = (map["randomSeed"] as? Number)?.toInt() ?: 12345
            
            val safeAccumulator = if (accumulatorMs.isFinite()) accumulatorMs else 0f
            
            return AutoTurretState(
                currentHp = currentHp.coerceIn(0, maxHp),
                accumulatorMs = safeAccumulator,
                isDestroyed = isDestroyed || currentHp <= 0,
                randomSeed = randomSeed
            )
        }
    }
}

class AutoTurretEngineResult(capacity: Int = 512) {
    val hitZombieIds = IntArray(capacity)
    var hitCount: Int = 0

    fun clear() {
        hitCount = 0
    }
    
    fun addHit(id: Int) {
        if (hitCount < hitZombieIds.size) {
            hitZombieIds[hitCount] = id
            hitCount++
        }
    }
}

object WindowBarricadeTier4AutoTurret {
    const val MAX_TARGET_DISTANCE = 150f
    
    fun repair(state: AutoTurretState, currencyAvailable: Int): Int {
        val maxHp = BarricadeWindowTier.TIER_4_AUTO_SENTRY_MOUNT.maxHp
        if (state.currentHp >= maxHp) return 0
        
        val missingHp = maxHp - state.currentHp
        if (missingHp <= 0) return 0
        
        val costPerHp = 2
        val maxHpToRepair = currencyAvailable / costPerHp
        val hpToRepair = minOf(missingHp, maxHpToRepair)
        
        state.currentHp += hpToRepair
        if (state.currentHp > 0) {
            state.isDestroyed = false
        }
        return hpToRepair * costPerHp
    }

    fun applyDamage(state: AutoTurretState, amount: Int) {
        if (state.isDestroyed || amount <= 0) return
        state.currentHp = (state.currentHp - amount).coerceAtLeast(0)
        if (state.currentHp == 0) {
            state.isDestroyed = true
        }
    }

    fun processTurretTick(
        state: AutoTurretState,
        turretX: Float,
        turretY: Float,
        zombies: List<ZombieEntity>,
        dtSeconds: Float,
        outResult: AutoTurretEngineResult
    ) {
        outResult.clear()
        if (state.isDestroyed) return
        
        val dt = if (dtSeconds.isFinite()) dtSeconds.coerceIn(0.001f, 0.100f) else 0.001f
        state.accumulatorMs += (dt * 1000f)
        
        val tickRateMs = 100f
        if (state.accumulatorMs < tickRateMs) return
        
        var ticks = (state.accumulatorMs / tickRateMs).toInt()
        state.accumulatorMs -= (ticks * tickRateMs)
        
        val dps = BarricadeWindowTier.TIER_4_AUTO_SENTRY_MOUNT.counterAttackDps
        val damagePerTick = (dps * (tickRateMs / 1000f)).toInt().coerceAtLeast(1)
        
        val tX = if (turretX.isFinite()) turretX else 0f
        val tY = if (turretY.isFinite()) turretY else 0f
        
        fun lcgNext(): Int {
            state.randomSeed = (state.randomSeed * 1103515245 + 12345) and 0x7FFFFFFF
            return state.randomSeed
        }
        
        while (ticks > 0) {
            ticks--
            
            var bestTarget: ZombieEntity? = null
            var bestDistSq = Float.MAX_VALUE
            val maxDistSq = MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE
            
            for (i in zombies.indices) {
                val z = zombies[i]
                if (z.hp <= 0) continue
                
                val zX = if (z.x.isFinite()) z.x else 0f
                val zY = if (z.y.isFinite()) z.y else 0f
                
                val dx = zX - tX
                val dy = zY - tY
                val distSq = dx * dx + dy * dy
                
                if (distSq <= maxDistSq && distSq < bestDistSq) {
                    bestDistSq = distSq
                    bestTarget = z
                }
            }
            
            if (bestTarget != null) {
                val isCrit = (lcgNext() % 100) < 5
                val actualDamage = if (isCrit) damagePerTick * 2 else damagePerTick
                
                bestTarget.hp = (bestTarget.hp - actualDamage).coerceAtLeast(0)
                outResult.addHit(bestTarget.id)
            }
        }
    }
}


fun evaluateWindowBarricadeTier4AutoTurretLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "WindowBarricadeTier4AutoTurret") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
