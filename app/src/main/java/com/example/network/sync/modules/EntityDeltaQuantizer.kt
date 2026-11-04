package com.example.network.sync.modules

import androidx.annotation.Keep
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Keep
data class QuantizedEntity(
    val entityId: Int,
    val x16: Int, // Uses 16 bits
    val y16: Int,
    val angle16: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "entityId" to entityId,
        "x16" to x16,
        "y16" to y16,
        "angle16" to angle16
    )

    companion object {
        fun fromMap(map: Map<String, Any>): QuantizedEntity {
            return QuantizedEntity(
                entityId = (map["entityId"] as? Number)?.toInt() ?: 0,
                x16 = (map["x16"] as? Number)?.toInt() ?: 0,
                y16 = (map["y16"] as? Number)?.toInt() ?: 0,
                angle16 = (map["angle16"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

@Keep
data class EntityDeltaPayload(
    val tickId: Int,
    val entities: List<QuantizedEntity>,
    val checksum: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "tickId" to tickId,
        "entities" to entities.map { it.toMap() },
        "checksum" to checksum
    )

    companion object {
        fun fromMap(map: Map<String, Any>): EntityDeltaPayload {
            val entitiesList = (map["entities"] as? List<*>)?.mapNotNull { item ->
                @Suppress("UNCHECKED_CAST")
                (item as? Map<String, Any>)?.let { QuantizedEntity.fromMap(it) }
            } ?: emptyList()

            return EntityDeltaPayload(
                tickId = (map["tickId"] as? Number)?.toInt() ?: 0,
                entities = entitiesList,
                checksum = (map["checksum"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

@Keep
data class EntityDeltaQuantizerConfig(
    val maxEntities: Int = 300,
    val worldMinX: Float = -2400f,
    val worldMaxX: Float = 2400f,
    val worldMinY: Float = -2400f,
    val worldMaxY: Float = 2400f
)

class EntityDeltaQuantizer(
    private val config: EntityDeltaQuantizerConfig = EntityDeltaQuantizerConfig()
) {
    // Parallel arrays for zero-allocation performance
    private val entityIds = IntArray(config.maxEntities)
    private val entityX = FloatArray(config.maxEntities)
    private val entityY = FloatArray(config.maxEntities)
    private val entityAngle = FloatArray(config.maxEntities)
    private var entityCount = 0
    private var currentTick = 0

    fun addEntity(id: Int, x: Float, y: Float, angle: Float) {
        if (entityCount >= config.maxEntities) return

        var safeX = x
        var safeY = y
        var safeAngle = angle

        if (!safeX.isFinite()) safeX = 0f
        if (!safeY.isFinite()) safeY = 0f
        if (!safeAngle.isFinite()) safeAngle = 0f

        safeX = max(config.worldMinX, min(config.worldMaxX, safeX))
        safeY = max(config.worldMinY, min(config.worldMaxY, safeY))

        // Normalize angle to [0, 2PI)
        var normAngle = safeAngle % TWO_PI
        if (normAngle < 0) normAngle += TWO_PI

        entityIds[entityCount] = id
        entityX[entityCount] = safeX
        entityY[entityCount] = safeY
        entityAngle[entityCount] = normAngle
        entityCount++
    }

    fun clear() {
        entityCount = 0
    }

    fun tick(dtSeconds: Float) {
        var dt = dtSeconds
        if (!dt.isFinite()) dt = 0.016f
        dt = max(0.001f, min(0.100f, dt))

        // Tick logic - typically would perform prediction or interpolation,
        // for this test scenario we simply update tick
        currentTick++
    }

    fun setTick(tick: Int) {
        currentTick = tick
    }

    fun generatePayload(): EntityDeltaPayload {
        val quantizedList = ArrayList<QuantizedEntity>(entityCount)
        for (i in 0 until entityCount) {
            val qx = quantizePosition(entityX[i], config.worldMinX, config.worldMaxX)
            val qy = quantizePosition(entityY[i], config.worldMinY, config.worldMaxY)
            val qAngle = quantizeAngle(entityAngle[i])
            quantizedList.add(QuantizedEntity(entityIds[i], qx, qy, qAngle))
        }
        
        val checksum = computeChecksum(currentTick, quantizedList)
        return EntityDeltaPayload(currentTick, quantizedList, checksum)
    }
    
    fun getEntityCount(): Int = entityCount

    companion object {
        const val MAX_UINT16 = 65535
        const val TWO_PI = (2.0 * PI).toFloat()

        fun quantizePosition(value: Float, minVal: Float, maxVal: Float): Int {
            var safeVal = value
            if (!safeVal.isFinite()) safeVal = 0f
            val clamped = max(minVal, min(maxVal, safeVal))
            
            val range = maxVal - minVal
            if (range <= 0f) return 0
            
            val normalized = (clamped - minVal) / range
            return (normalized * MAX_UINT16).roundToInt().coerceIn(0, MAX_UINT16)
        }

        fun dequantizePosition(quantized: Int, minVal: Float, maxVal: Float): Float {
            val clamped = quantized.coerceIn(0, MAX_UINT16)
            val normalized = clamped.toFloat() / MAX_UINT16.toFloat()
            val range = maxVal - minVal
            if (range <= 0f) return minVal
            return minVal + normalized * range
        }

        fun quantizeAngle(angle: Float): Int {
            var safeAngle = angle
            if (!safeAngle.isFinite()) safeAngle = 0f
            var normAngle = safeAngle % TWO_PI
            if (normAngle < 0) normAngle += TWO_PI
            val normalized = normAngle / TWO_PI
            return (normalized * MAX_UINT16).roundToInt().coerceIn(0, MAX_UINT16)
        }

        fun dequantizeAngle(quantized: Int): Float {
            val clamped = quantized.coerceIn(0, MAX_UINT16)
            val normalized = clamped.toFloat() / MAX_UINT16.toFloat()
            return normalized * TWO_PI
        }

        fun computeChecksum(tickId: Int, entities: List<QuantizedEntity>): Int {
            var hash = (2166136261L).toInt()
            val prime = 16777619
            
            hash = hash xor tickId
            hash = hash * prime

            for (i in entities.indices) {
                val e = entities[i]
                hash = hash xor e.entityId
                hash = hash * prime
                
                hash = hash xor e.x16
                hash = hash * prime
                
                hash = hash xor e.y16
                hash = hash * prime
                
                hash = hash xor e.angle16
                hash = hash * prime
            }
            return hash
        }
    }
}


fun evaluateEntityDeltaQuantizerLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "EntityDeltaQuantizer") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
