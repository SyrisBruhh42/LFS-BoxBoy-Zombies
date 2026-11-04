package com.example.game.economy.vendors

import androidx.annotation.Keep
import kotlin.math.abs

enum class BarricadeBlockType(val maxHp: Int, val isEnergyBased: Boolean, val baseCost: Int) {
    CONCRETE(1500, false, 500),
    HARD_LIGHT_ENERGY(800, true, 800),
    TITANIUM(3000, false, 2000)
}

@Keep
data class PlaceableBarricadeConfig(
    val id: String,
    val type: BarricadeBlockType,
    val width: Float,
    val height: Float
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "type" to type.name,
        "width" to width,
        "height" to height
    )

    companion object {
        fun fromMap(map: Map<String, Any>): PlaceableBarricadeConfig {
            return PlaceableBarricadeConfig(
                id = map["id"] as String,
                type = BarricadeBlockType.valueOf(map["type"] as String),
                width = (map["width"] as Number).toFloat(),
                height = (map["height"] as Number).toFloat()
            )
        }
    }
}

@Keep
data class PlaceableBarricadeState(
    val x: Float,
    val y: Float,
    var currentHp: Int,
    var timeSinceLastDamageSec: Float = 0f,
    var accumulatedRegen: Float = 0f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "x" to x,
        "y" to y,
        "currentHp" to currentHp,
        "timeSinceLastDamageSec" to timeSinceLastDamageSec,
        "accumulatedRegen" to accumulatedRegen
    )

    companion object {
        fun fromMap(map: Map<String, Any>): PlaceableBarricadeState {
            return PlaceableBarricadeState(
                x = (map["x"] as Number).toFloat(),
                y = (map["y"] as Number).toFloat(),
                currentHp = (map["currentHp"] as Number).toInt(),
                timeSinceLastDamageSec = (map["timeSinceLastDamageSec"] as? Number)?.toFloat() ?: 0f,
                accumulatedRegen = (map["accumulatedRegen"] as? Number)?.toFloat() ?: 0f
            )
        }
    }
}

object PlaceableBarricadeWallBlockEngine {

    const val ENERGY_REGEN_DELAY_SEC = 3.0f
    const val ENERGY_REGEN_PER_SEC = 100f
    private const val FNV_PRIME = 16777619
    private const val FNV_OFFSET_BASIS = 2166136261.toInt()

    fun calculateChecksum(config: PlaceableBarricadeConfig, state: PlaceableBarricadeState): Int {
        var hash = FNV_OFFSET_BASIS
        hash = (hash xor config.id.hashCode()) * FNV_PRIME
        hash = (hash xor config.type.name.hashCode()) * FNV_PRIME
        hash = (hash xor config.width.toBits()) * FNV_PRIME
        hash = (hash xor config.height.toBits()) * FNV_PRIME
        hash = (hash xor state.x.toBits()) * FNV_PRIME
        hash = (hash xor state.y.toBits()) * FNV_PRIME
        hash = (hash xor state.currentHp) * FNV_PRIME
        hash = (hash xor state.timeSinceLastDamageSec.toBits()) * FNV_PRIME
        hash = (hash xor state.accumulatedRegen.toBits()) * FNV_PRIME
        return hash
    }

    fun step(
        config: PlaceableBarricadeConfig,
        state: PlaceableBarricadeState,
        dtSeconds: Float
    ) {
        if (state.currentHp <= 0 || dtSeconds <= 0f || !dtSeconds.isFinite()) return

        val clampedDt = if (dtSeconds > 10.0f) 0.100f else dtSeconds.coerceAtLeast(0.001f)
        val wasReadyToRegen = state.timeSinceLastDamageSec >= ENERGY_REGEN_DELAY_SEC
        state.timeSinceLastDamageSec += clampedDt

        if (config.type.isEnergyBased && wasReadyToRegen) {
            state.accumulatedRegen += ENERGY_REGEN_PER_SEC * clampedDt
            val regenAmount = (state.accumulatedRegen + 1e-4f).toInt()
            if (regenAmount > 0) {
                state.accumulatedRegen = (state.accumulatedRegen - regenAmount).coerceAtLeast(0f)
                state.currentHp = (state.currentHp + regenAmount).coerceAtMost(config.type.maxHp)
            }
        }
    }

    fun applyDamage(config: PlaceableBarricadeConfig, state: PlaceableBarricadeState, damage: Int) {
        if (damage <= 0 || state.currentHp <= 0) return
        state.currentHp = (state.currentHp - damage).coerceAtLeast(0)
        state.timeSinceLastDamageSec = 0f
        state.accumulatedRegen = 0f
    }

    /**
     * Zero-allocation batch collision routing.
     * @param config The barricade config.
     * @param state The barricade state.
     * @param entityBounds FloatArray structured as [minX1, minY1, maxX1, maxY1, minX2, minY2...]
     * @param outMtv FloatArray structured as [mtvX1, mtvY1, mtvX2, mtvY2...] (Must be pre-allocated).
     * @param count Number of entities to process.
     */
    fun resolveBatchCollisions(
        config: PlaceableBarricadeConfig,
        state: PlaceableBarricadeState,
        entityBounds: FloatArray,
        outMtv: FloatArray,
        count: Int
    ) {
        if (state.currentHp <= 0 || count <= 0) {
            for (i in 0 until count * 2) {
                outMtv[i] = 0f
            }
            return
        }

        val wallMinX = state.x
        val wallMinY = state.y
        val wallMaxX = state.x + config.width
        val wallMaxY = state.y + config.height

        for (i in 0 until count) {
            val boundsIdx = i * 4
            val mtvIdx = i * 2

            val entMinX = entityBounds[boundsIdx]
            val entMinY = entityBounds[boundsIdx + 1]
            val entMaxX = entityBounds[boundsIdx + 2]
            val entMaxY = entityBounds[boundsIdx + 3]
            
            if (!entMinX.isFinite() || !entMinY.isFinite() || !entMaxX.isFinite() || !entMaxY.isFinite()) {
                outMtv[mtvIdx] = 0f
                outMtv[mtvIdx + 1] = 0f
                continue
            }

            val overlapLeft = entMaxX - wallMinX
            val overlapRight = wallMaxX - entMinX
            val overlapTop = entMaxY - wallMinY
            val overlapBottom = wallMaxY - entMinY

            if (overlapLeft <= 0 || overlapRight <= 0 || overlapTop <= 0 || overlapBottom <= 0) {
                outMtv[mtvIdx] = 0f
                outMtv[mtvIdx + 1] = 0f
                continue
            }

            val minOverlapX = if (overlapLeft < overlapRight) -overlapLeft else overlapRight
            val minOverlapY = if (overlapTop < overlapBottom) -overlapTop else overlapBottom

            if (abs(minOverlapX) < abs(minOverlapY)) {
                outMtv[mtvIdx] = minOverlapX
                outMtv[mtvIdx + 1] = 0f
            } else {
                outMtv[mtvIdx] = 0f
                outMtv[mtvIdx + 1] = minOverlapY
            }
        }
    }
}


fun evaluatePlaceableBarricadeWallBlockLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "PlaceableBarricadeWallBlock") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
