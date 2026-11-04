package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.abs

@Keep
data class StickyBombChargeConfig(
    val baseDamageMultiplier: Float = 1.8f,
    val baseProcChance: Float = 0.05f,
    val procChancePerStack: Float = 0.05f,
    val blastRadiusPx: Float = 48f,
    val fuseTimeSeconds: Float = 1.5f,
    val maxTrackedBombs: Int = 300
) {
    fun toMap(): Map<String, Any> = mapOf(
        "baseDamageMultiplier" to baseDamageMultiplier,
        "baseProcChance" to baseProcChance,
        "procChancePerStack" to procChancePerStack,
        "blastRadiusPx" to blastRadiusPx,
        "fuseTimeSeconds" to fuseTimeSeconds,
        "maxTrackedBombs" to maxTrackedBombs
    )

    companion object {
        fun fromMap(map: Map<String, Any>): StickyBombChargeConfig {
            return StickyBombChargeConfig(
                baseDamageMultiplier = (map["baseDamageMultiplier"] as? Number)?.toFloat() ?: 1.8f,
                baseProcChance = (map["baseProcChance"] as? Number)?.toFloat() ?: 0.05f,
                procChancePerStack = (map["procChancePerStack"] as? Number)?.toFloat() ?: 0.05f,
                blastRadiusPx = (map["blastRadiusPx"] as? Number)?.toFloat() ?: 48f,
                fuseTimeSeconds = (map["fuseTimeSeconds"] as? Number)?.toFloat() ?: 1.5f,
                maxTrackedBombs = (map["maxTrackedBombs"] as? Number)?.toInt() ?: 300
            )
        }
    }
}

@Keep
data class StickyBombChargeState(
    val stackCount: Int,
    var activeBombCount: Int = 0,
    val bombEntityIds: IntArray = IntArray(300),
    val bombXs: FloatArray = FloatArray(300),
    val bombYs: FloatArray = FloatArray(300),
    val bombRemainingFuses: FloatArray = FloatArray(300),
    val bombBaseDamages: IntArray = IntArray(300),
    var stateChecksum: Long = 0L
) {
    init {
        require(stackCount >= 1) { "Relic must have at least 1 stack to be active." }
    }

    val canBePurchasedAtVending: Boolean
        get() = false

    fun toMap(): Map<String, Any> {
        return mapOf(
            "stackCount" to stackCount,
            "activeBombCount" to activeBombCount,
            "bombEntityIds" to bombEntityIds.take(activeBombCount),
            "bombXs" to bombXs.take(activeBombCount),
            "bombYs" to bombYs.take(activeBombCount),
            "bombRemainingFuses" to bombRemainingFuses.take(activeBombCount),
            "bombBaseDamages" to bombBaseDamages.take(activeBombCount),
            "stateChecksum" to stateChecksum
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StickyBombChargeState) return false
        if (stackCount != other.stackCount) return false
        if (activeBombCount != other.activeBombCount) return false
        if (stateChecksum != other.stateChecksum) return false
        for (i in 0 until activeBombCount) {
            if (bombEntityIds[i] != other.bombEntityIds[i]) return false
            if (bombXs[i] != other.bombXs[i]) return false
            if (bombYs[i] != other.bombYs[i]) return false
            if (bombRemainingFuses[i] != other.bombRemainingFuses[i]) return false
            if (bombBaseDamages[i] != other.bombBaseDamages[i]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = stackCount
        result = 31 * result + activeBombCount
        result = 31 * result + stateChecksum.hashCode()
        return result
    }

    companion object {
        fun fromMap(map: Map<String, Any>): StickyBombChargeState {
            val stackCount = (map["stackCount"] as? Number)?.toInt() ?: 1
            val activeBombCount = (map["activeBombCount"] as? Number)?.toInt() ?: 0
            val state = StickyBombChargeState(stackCount = stackCount, activeBombCount = activeBombCount)

            val ids = map["bombEntityIds"] as? List<*>
            val xs = map["bombXs"] as? List<*>
            val ys = map["bombYs"] as? List<*>
            val fuses = map["bombRemainingFuses"] as? List<*>
            val damages = map["bombBaseDamages"] as? List<*>

            for (i in 0 until activeBombCount) {
                if (i < 300) {
                    state.bombEntityIds[i] = (ids?.get(i) as? Number)?.toInt() ?: 0
                    state.bombXs[i] = (xs?.get(i) as? Number)?.toFloat() ?: 0f
                    state.bombYs[i] = (ys?.get(i) as? Number)?.toFloat() ?: 0f
                    state.bombRemainingFuses[i] = (fuses?.get(i) as? Number)?.toFloat() ?: 0f
                    state.bombBaseDamages[i] = (damages?.get(i) as? Number)?.toInt() ?: 0
                }
            }
            state.stateChecksum = (map["stateChecksum"] as? Number)?.toLong() ?: 0L
            return state
        }
    }
}

object Relic20StickyBombChargeEngine {
    fun computeChecksum(state: StickyBombChargeState): Long {
        var hash = -212883103514042868L // FNV-1a offset basis
        hash = (hash xor state.stackCount.toLong()) * 1099511628211L
        hash = (hash xor state.activeBombCount.toLong()) * 1099511628211L
        for (i in 0 until state.activeBombCount) {
            hash = (hash xor state.bombEntityIds[i].toLong()) * 1099511628211L
            hash = (hash xor state.bombXs[i].toBits().toLong()) * 1099511628211L
            hash = (hash xor state.bombYs[i].toBits().toLong()) * 1099511628211L
            hash = (hash xor state.bombRemainingFuses[i].toBits().toLong()) * 1099511628211L
            hash = (hash xor state.bombBaseDamages[i].toLong()) * 1099511628211L
        }
        return hash
    }

    fun registerHit(
        config: StickyBombChargeConfig,
        state: StickyBombChargeState,
        hitEntityId: Int,
        hitX: Float,
        hitY: Float,
        attackDamage: Int,
        seed: Long
    ) {
        val procChance = config.baseProcChance + (state.stackCount - 1) * config.procChancePerStack
        val rng = kotlin.random.Random(seed xor hitEntityId.toLong() xor attackDamage.toLong())

        if (rng.nextFloat() <= procChance) {
            if (state.activeBombCount < config.maxTrackedBombs && state.activeBombCount < state.bombEntityIds.size) {
                val bombDamage = (attackDamage * config.baseDamageMultiplier).toInt()
                val idx = state.activeBombCount
                state.bombEntityIds[idx] = hitEntityId
                state.bombXs[idx] = hitX
                state.bombYs[idx] = hitY
                state.bombRemainingFuses[idx] = config.fuseTimeSeconds
                state.bombBaseDamages[idx] = bombDamage
                state.activeBombCount++
            }
        }
        state.stateChecksum = computeChecksum(state)
    }

    fun tick(
        config: StickyBombChargeConfig,
        state: StickyBombChargeState,
        dtSeconds: Float,
        entityPositionIds: IntArray,
        entityPositionXs: FloatArray,
        entityPositionYs: FloatArray,
        entityPositionCount: Int,
        outExplosionXs: FloatArray,
        outExplosionYs: FloatArray,
        outExplosionRadii: FloatArray,
        outExplosionDamages: IntArray
    ): Int {
        if (!dtSeconds.isFinite()) return 0
        val clampedDt = dtSeconds.coerceIn(0.001f, 0.100f)

        var explosionCount = 0
        var i = 0
        while (i < state.activeBombCount) {
            val entityId = state.bombEntityIds[i]

            for (j in 0 until entityPositionCount) {
                if (entityPositionIds[j] == entityId) {
                    val px = entityPositionXs[j]
                    val py = entityPositionYs[j]
                    if (px.isFinite() && py.isFinite()) {
                        state.bombXs[i] = px
                        state.bombYs[i] = py
                    }
                    break
                }
            }

            var fuse = state.bombRemainingFuses[i]
            fuse -= clampedDt

            if (fuse <= 0f) {
                if (explosionCount < outExplosionXs.size) {
                    outExplosionXs[explosionCount] = state.bombXs[i]
                    outExplosionYs[explosionCount] = state.bombYs[i]
                    outExplosionRadii[explosionCount] = config.blastRadiusPx
                    outExplosionDamages[explosionCount] = state.bombBaseDamages[i]
                    explosionCount++
                }

                val lastIdx = state.activeBombCount - 1
                state.bombEntityIds[i] = state.bombEntityIds[lastIdx]
                state.bombXs[i] = state.bombXs[lastIdx]
                state.bombYs[i] = state.bombYs[lastIdx]
                state.bombRemainingFuses[i] = state.bombRemainingFuses[lastIdx]
                state.bombBaseDamages[i] = state.bombBaseDamages[lastIdx]
                state.activeBombCount--
            } else {
                state.bombRemainingFuses[i] = fuse
                i++
            }
        }

        state.stateChecksum = computeChecksum(state)
        return explosionCount
    }
}
