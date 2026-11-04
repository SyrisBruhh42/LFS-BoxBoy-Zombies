package com.example.network.sync.modules

import androidx.annotation.Keep
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@Keep
enum class CoopSharedEventType {
    DOOR_UNLOCKED,
    BARREL_DETONATED,
    VENDING_PURCHASE,
    BOSS_SPAWNED,
    UNKNOWN
}

@Keep
data class CoopSharedEventConfig(
    val maxQueueCapacity: Int = 300,
    val maxTransmissionBytes: Int = 15000,
    val boundsMinX: Float = -2400f,
    val boundsMaxX: Float = 2400f,
    val boundsMinY: Float = -2400f,
    val boundsMaxY: Float = 2400f
)

@Keep
data class CoopSharedEventSnapshot(
    val pendingCount: Int,
    val latestChecksum: Long
)

@Keep
data class CoopSharedEvent(
    val type: CoopSharedEventType,
    val x: Float,
    val y: Float,
    val targetId: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "type" to type.name,
        "x" to x,
        "y" to y,
        "targetId" to targetId
    )

    companion object {
        fun fromMap(map: Map<String, Any>): CoopSharedEvent {
            val typeStr = map["type"] as? String ?: CoopSharedEventType.UNKNOWN.name
            val type = try {
                CoopSharedEventType.valueOf(typeStr)
            } catch (e: Exception) {
                CoopSharedEventType.UNKNOWN
            }
            return CoopSharedEvent(
                type = type,
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                targetId = (map["targetId"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

class CoopSharedEventBroadcaster(val config: CoopSharedEventConfig = CoopSharedEventConfig()) {
    
    // Zero-allocation pre-allocated primitive arrays
    private val types = Array(config.maxQueueCapacity) { CoopSharedEventType.UNKNOWN }
    private val xs = FloatArray(config.maxQueueCapacity)
    private val ys = FloatArray(config.maxQueueCapacity)
    private val targetIds = IntArray(config.maxQueueCapacity)
    
    private var head = 0
    private var tail = 0
    private var count = 0
    
    fun enqueueEvent(type: CoopSharedEventType, x: Float, y: Float, targetId: Int) {
        val safeX = if (x.isFinite()) x.coerceIn(config.boundsMinX, config.boundsMaxX) else 0f
        val safeY = if (y.isFinite()) y.coerceIn(config.boundsMinY, config.boundsMaxY) else 0f
        
        if (count < config.maxQueueCapacity) {
            types[tail] = type
            xs[tail] = safeX
            ys[tail] = safeY
            targetIds[tail] = targetId
            tail = (tail + 1) % config.maxQueueCapacity
            count++
        } else {
            // Overwrite oldest (Graceful degradation under extreme load)
            types[head] = type
            xs[head] = safeX
            ys[head] = safeY
            targetIds[head] = targetId
            head = (head + 1) % config.maxQueueCapacity
            tail = (tail + 1) % config.maxQueueCapacity
        }
    }
    
    /**
     * Domain algorithm for ticking/broadcasting. 
     * Applies dt clamping and deterministic variation.
     */
    fun tick(dtSeconds: Float, seed: Long): List<CoopSharedEvent> {
        if (count == 0) return emptyList()
        
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)
        val rng = Random(seed)
        
        val readyEvents = ArrayList<CoopSharedEvent>(count)
        var current = head
        for (i in 0 until count) {
            // Apply slight deterministic variation to spatial coordinates
            val varX = (rng.nextFloat() - 0.5f) * 0.1f * dt
            val varY = (rng.nextFloat() - 0.5f) * 0.1f * dt
            
            readyEvents.add(
                CoopSharedEvent(
                    type = types[current],
                    x = (xs[current] + varX).coerceIn(config.boundsMinX, config.boundsMaxX),
                    y = (ys[current] + varY).coerceIn(config.boundsMinY, config.boundsMaxY),
                    targetId = targetIds[current]
                )
            )
            current = (current + 1) % config.maxQueueCapacity
        }
        
        // Reset queue
        head = 0
        tail = 0
        count = 0
        
        return readyEvents
    }
    
    fun getSnapshot(): CoopSharedEventSnapshot {
        return CoopSharedEventSnapshot(
            pendingCount = count,
            latestChecksum = computeChecksum()
        )
    }
    
    private fun computeChecksum(): Long {
        var hash = -2128831035L // FNV-1a offset basis
        val prime = 16777619L
        
        var current = head
        for (i in 0 until count) {
            hash = (hash xor types[current].ordinal.toLong()) * prime
            hash = (hash xor xs[current].toBits().toLong()) * prime
            hash = (hash xor ys[current].toBits().toLong()) * prime
            hash = (hash xor targetIds[current].toLong()) * prime
            current = (current + 1) % config.maxQueueCapacity
        }
        
        return hash
    }
}


fun evaluateCoopSharedEventBroadcasterLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "CoopSharedEventBroadcaster") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
