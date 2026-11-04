package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.abs
import kotlin.math.hypot

@Keep
data class WillOWispVesselConfig(
    val baseDamageMultiplier: Float = 3.5f, // 350%
    val perStackDamageMultiplier: Float = 2.8f, // 280%
    val baseRadiusPx: Float = 48f,
    val perStackRadiusPx: Float = 24f,
    val fuseTimeSeconds: Float = 0.5f,
    val canBePurchasedAtVending: Boolean = false // Invariant
) {
    fun toMap(): Map<String, Any> = mapOf(
        "baseDamageMultiplier" to baseDamageMultiplier,
        "perStackDamageMultiplier" to perStackDamageMultiplier,
        "baseRadiusPx" to baseRadiusPx,
        "perStackRadiusPx" to perStackRadiusPx,
        "fuseTimeSeconds" to fuseTimeSeconds,
        "canBePurchasedAtVending" to canBePurchasedAtVending
    )

    companion object {
        fun fromMap(map: Map<String, Any>): WillOWispVesselConfig = WillOWispVesselConfig(
            baseDamageMultiplier = (map["baseDamageMultiplier"] as? Number)?.toFloat() ?: 3.5f,
            perStackDamageMultiplier = (map["perStackDamageMultiplier"] as? Number)?.toFloat() ?: 2.8f,
            baseRadiusPx = (map["baseRadiusPx"] as? Number)?.toFloat() ?: 48f,
            perStackRadiusPx = (map["perStackRadiusPx"] as? Number)?.toFloat() ?: 24f,
            fuseTimeSeconds = (map["fuseTimeSeconds"] as? Number)?.toFloat() ?: 0.5f,
            canBePurchasedAtVending = map["canBePurchasedAtVending"] as? Boolean ?: false
        )
    }
}

@Keep
data class WillOWispVesselState(
    var activeCount: Int = 0,
    val xCoords: FloatArray = FloatArray(MAX_CAPACITY),
    val yCoords: FloatArray = FloatArray(MAX_CAPACITY),
    val damagePayloads: FloatArray = FloatArray(MAX_CAPACITY),
    val radii: FloatArray = FloatArray(MAX_CAPACITY),
    val timeRemaining: FloatArray = FloatArray(MAX_CAPACITY)
) {
    fun getChecksum(): Long {
        var hash = -2128831035157713293L
        hash = (hash xor activeCount.toLong()) * 1099511628211L
        for (i in 0 until activeCount) {
            hash = (hash xor xCoords[i].toRawBits().toLong()) * 1099511628211L
            hash = (hash xor yCoords[i].toRawBits().toLong()) * 1099511628211L
            hash = (hash xor damagePayloads[i].toRawBits().toLong()) * 1099511628211L
            hash = (hash xor radii[i].toRawBits().toLong()) * 1099511628211L
            hash = (hash xor timeRemaining[i].toRawBits().toLong()) * 1099511628211L
        }
        return hash
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "activeCount" to activeCount,
            "xCoords" to xCoords.take(activeCount).toList(),
            "yCoords" to yCoords.take(activeCount).toList(),
            "damagePayloads" to damagePayloads.take(activeCount).toList(),
            "radii" to radii.take(activeCount).toList(),
            "timeRemaining" to timeRemaining.take(activeCount).toList()
        )
    }

    companion object {
        const val MAX_CAPACITY = 300

        fun fromMap(map: Map<String, Any>): WillOWispVesselState {
            val state = WillOWispVesselState()
            val active = (map["activeCount"] as? Number)?.toInt() ?: 0
            val xList = (map["xCoords"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
            val yList = (map["yCoords"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
            val dmgList = (map["damagePayloads"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
            val rList = (map["radii"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
            val tList = (map["timeRemaining"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()

            val count = active.coerceIn(0, MAX_CAPACITY).coerceAtMost(xList.size).coerceAtMost(yList.size).coerceAtMost(dmgList.size).coerceAtMost(rList.size).coerceAtMost(tList.size)
            state.activeCount = count
            for (i in 0 until count) {
                state.xCoords[i] = xList[i]
                state.yCoords[i] = yList[i]
                state.damagePayloads[i] = dmgList[i]
                state.radii[i] = rList[i]
                state.timeRemaining[i] = tList[i]
            }
            return state
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WillOWispVesselState) return false
        return this.getChecksum() == other.getChecksum()
    }

    override fun hashCode(): Int {
        return getChecksum().hashCode()
    }
}

class Relic05WillOWispVesselEngine(
    val config: WillOWispVesselConfig = WillOWispVesselConfig()
) {

    fun calculateDamage(baseWeaponDamage: Float, stackCount: Int): Float {
        val stacks = stackCount.coerceAtLeast(1)
        val multiplier = config.baseDamageMultiplier + (stacks - 1) * config.perStackDamageMultiplier
        val dmg = baseWeaponDamage * multiplier
        return if (dmg.isFinite() && dmg > 0f) dmg else 0f
    }

    fun calculateRadius(stackCount: Int): Float {
        val stacks = stackCount.coerceAtLeast(1)
        val radius = config.baseRadiusPx + (stacks - 1) * config.perStackRadiusPx
        return if (radius.isFinite() && radius > 0f) radius else config.baseRadiusPx
    }

    fun registerKill(
        state: WillOWispVesselState,
        x: Float,
        y: Float,
        baseWeaponDamage: Float,
        stackCount: Int
    ): Boolean {
        if (!x.isFinite() || !y.isFinite() || stackCount < 1) return false
        if (state.activeCount >= WillOWispVesselState.MAX_CAPACITY) return false // Graceful degradation

        val index = state.activeCount
        state.xCoords[index] = x
        state.yCoords[index] = y
        state.damagePayloads[index] = calculateDamage(baseWeaponDamage, stackCount)
        state.radii[index] = calculateRadius(stackCount)
        state.timeRemaining[index] = config.fuseTimeSeconds
        
        state.activeCount++
        return true
    }

    data class DetonationResult(
        val x: Float,
        val y: Float,
        val radius: Float,
        val damage: Float
    )

    fun tick(
        state: WillOWispVesselState,
        dtSeconds: Float
    ): List<DetonationResult> {
        val clampedDt = dtSeconds.coerceIn(0.001f, 0.100f)
        val detonations = mutableListOf<DetonationResult>()
        
        var i = 0
        while (i < state.activeCount) {
            state.timeRemaining[i] -= clampedDt
            
            if (state.timeRemaining[i] <= 0f) {
                // Detonate
                detonations.add(
                    DetonationResult(
                        x = state.xCoords[i],
                        y = state.yCoords[i],
                        radius = state.radii[i],
                        damage = state.damagePayloads[i]
                    )
                )
                
                // Swap-and-pop removal
                val lastIdx = state.activeCount - 1
                if (i < lastIdx) {
                    state.xCoords[i] = state.xCoords[lastIdx]
                    state.yCoords[i] = state.yCoords[lastIdx]
                    state.damagePayloads[i] = state.damagePayloads[lastIdx]
                    state.radii[i] = state.radii[lastIdx]
                    state.timeRemaining[i] = state.timeRemaining[lastIdx]
                }
                state.activeCount--
                // Note: Do not increment `i` since we swapped in a new element at `i`
            } else {
                i++
            }
        }
        
        return detonations
    }

    /**
     * Applies spatial AoE damage to targets.
     * Operates purely on FloatArrays to avoid allocations in loop.
     */
    fun applyAoeDamage(
        detonation: DetonationResult,
        targetX: FloatArray,
        targetY: FloatArray,
        targetHp: FloatArray,
        targetCount: Int
    ) {
        val safeCount = targetCount.coerceAtMost(targetX.size).coerceAtMost(targetY.size).coerceAtMost(targetHp.size)
        val maxDistSq = detonation.radius * detonation.radius
        
        for (i in 0 until safeCount) {
            if (targetHp[i] <= 0f) continue
            
            val dx = targetX[i] - detonation.x
            val dy = targetY[i] - detonation.y
            val distSq = dx * dx + dy * dy
            
            if (distSq <= maxDistSq && distSq.isFinite()) {
                targetHp[i] -= detonation.damage
                if (targetHp[i] < 0f) targetHp[i] = 0f
            }
        }
    }
}


fun evaluateRelic05WillOWispVesselLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic05WillOWispVessel") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
