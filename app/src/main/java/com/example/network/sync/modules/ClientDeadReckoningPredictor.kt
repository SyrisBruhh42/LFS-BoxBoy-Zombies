package com.example.network.sync.modules

import androidx.annotation.Keep
import kotlin.math.max
import kotlin.math.min

@Keep
data class ClientDeadReckoningConfig(
    val maxEntities: Int = 1000,
    val maxExtrapolationTimeSeconds: Float = 1.0f,
    val snapThresholdPx: Float = 300.0f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "maxEntities" to maxEntities,
        "maxExtrapolationTimeSeconds" to maxExtrapolationTimeSeconds,
        "snapThresholdPx" to snapThresholdPx
    )

    companion object {
        fun fromMap(map: Map<String, Any>): ClientDeadReckoningConfig {
            return ClientDeadReckoningConfig(
                maxEntities = (map["maxEntities"] as? Number)?.toInt() ?: 1000,
                maxExtrapolationTimeSeconds = (map["maxExtrapolationTimeSeconds"] as? Number)?.toFloat() ?: 1.0f,
                snapThresholdPx = (map["snapThresholdPx"] as? Number)?.toFloat() ?: 300.0f
            )
        }
    }
}

@Keep
data class ClientDeadReckoningSnapshot(
    val entityId: Int,
    val networkX: Float,
    val networkY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val timestampSeconds: Float
) {
    fun toMap(): Map<String, Any> = mapOf(
        "entityId" to entityId,
        "networkX" to networkX,
        "networkY" to networkY,
        "velocityX" to velocityX,
        "velocityY" to velocityY,
        "timestampSeconds" to timestampSeconds
    )

    companion object {
        fun fromMap(map: Map<String, Any>): ClientDeadReckoningSnapshot {
            return ClientDeadReckoningSnapshot(
                entityId = (map["entityId"] as? Number)?.toInt() ?: 0,
                networkX = (map["networkX"] as? Number)?.toFloat() ?: 0f,
                networkY = (map["networkY"] as? Number)?.toFloat() ?: 0f,
                velocityX = (map["velocityX"] as? Number)?.toFloat() ?: 0f,
                velocityY = (map["velocityY"] as? Number)?.toFloat() ?: 0f,
                timestampSeconds = (map["timestampSeconds"] as? Number)?.toFloat() ?: 0f
            )
        }
    }
}

class ClientDeadReckoningPredictor(val config: ClientDeadReckoningConfig) {
    private val maxEntities = config.maxEntities

    // Pre-allocated primitive arrays for zero per-frame heap churn
    private val active = BooleanArray(maxEntities)

    private val netX = FloatArray(maxEntities)
    private val netY = FloatArray(maxEntities)
    private val netVX = FloatArray(maxEntities)
    private val netVY = FloatArray(maxEntities)
    private val netTimestamp = FloatArray(maxEntities)

    private val prevX = FloatArray(maxEntities)
    private val prevY = FloatArray(maxEntities)
    private val prevVX = FloatArray(maxEntities)
    private val prevVY = FloatArray(maxEntities)
    private val prevTimestamp = FloatArray(maxEntities)

    private val outX = FloatArray(maxEntities)
    private val outY = FloatArray(maxEntities)
    private val outVX = FloatArray(maxEntities)
    private val outVY = FloatArray(maxEntities)

    fun updateNetworkState(snapshot: ClientDeadReckoningSnapshot) {
        if (!snapshot.networkX.isFinite() || !snapshot.networkY.isFinite() ||
            !snapshot.velocityX.isFinite() || !snapshot.velocityY.isFinite() ||
            !snapshot.timestampSeconds.isFinite()) return

        val id = snapshot.entityId
        if (id < 0 || id >= maxEntities) return

        if (!active[id]) {
            active[id] = true

            netX[id] = snapshot.networkX
            netY[id] = snapshot.networkY
            netVX[id] = snapshot.velocityX
            netVY[id] = snapshot.velocityY
            netTimestamp[id] = snapshot.timestampSeconds

            prevX[id] = snapshot.networkX
            prevY[id] = snapshot.networkY
            prevVX[id] = snapshot.velocityX
            prevVY[id] = snapshot.velocityY
            prevTimestamp[id] = snapshot.timestampSeconds

            outX[id] = snapshot.networkX
            outY[id] = snapshot.networkY
            outVX[id] = snapshot.velocityX
            outVY[id] = snapshot.velocityY
        } else {
            // Drop out-of-order packets
            if (snapshot.timestampSeconds <= netTimestamp[id]) return

            val dx = snapshot.networkX - outX[id]
            val dy = snapshot.networkY - outY[id]
            val distSq = dx * dx + dy * dy

            prevX[id] = netX[id]
            prevY[id] = netY[id]
            prevVX[id] = netVX[id]
            prevVY[id] = netVY[id]
            prevTimestamp[id] = netTimestamp[id]

            // Snap if teleporting/drifting too far
            if (distSq > config.snapThresholdPx * config.snapThresholdPx) {
                prevX[id] = snapshot.networkX
                prevY[id] = snapshot.networkY
                outX[id] = snapshot.networkX
                outY[id] = snapshot.networkY
            }

            netX[id] = snapshot.networkX
            netY[id] = snapshot.networkY
            netVX[id] = snapshot.velocityX
            netVY[id] = snapshot.velocityY
            netTimestamp[id] = snapshot.timestampSeconds
        }
    }

    fun removeEntity(entityId: Int) {
        if (entityId in 0 until maxEntities) {
            active[entityId] = false
        }
    }

    fun step(dtSeconds: Float, globalTimeSeconds: Float) {
        if (!dtSeconds.isFinite() || !globalTimeSeconds.isFinite()) return
        val clampedDt = max(0.001f, min(0.100f, dtSeconds))

        for (i in 0 until maxEntities) {
            if (!active[i]) continue

            val t0 = prevTimestamp[i]
            val t1 = netTimestamp[i]
            val duration = t1 - t0

            if (duration > 0.0001f) {
                val elapsed = globalTimeSeconds - t0

                if (elapsed <= duration) {
                    val t = elapsed / duration
                    val t2 = t * t
                    val t3 = t2 * t

                    // Hermite Basis functions
                    val h00 = 2f * t3 - 3f * t2 + 1f
                    val h10 = t3 - 2f * t2 + t
                    val h01 = -2f * t3 + 3f * t2
                    val h11 = t3 - t2

                    outX[i] = h00 * prevX[i] + h10 * duration * prevVX[i] + h01 * netX[i] + h11 * duration * netVX[i]
                    outY[i] = h00 * prevY[i] + h10 * duration * prevVY[i] + h01 * netY[i] + h11 * duration * netVY[i]

                    // Hermite Derivatives (velocity interpolation)
                    val dh00 = 6f * t2 - 6f * t
                    val dh10 = 3f * t2 - 4f * t + 1f
                    val dh01 = -6f * t2 + 6f * t
                    val dh11 = 3f * t2 - 2f * t

                    outVX[i] = (dh00 * prevX[i] + dh10 * duration * prevVX[i] + dh01 * netX[i] + dh11 * duration * netVX[i]) / duration
                    outVY[i] = (dh00 * prevY[i] + dh10 * duration * prevVY[i] + dh01 * netY[i] + dh11 * duration * netVY[i]) / duration
                } else {
                    val overTime = globalTimeSeconds - t1
                    val clampedOver = min(overTime, config.maxExtrapolationTimeSeconds)

                    outX[i] = netX[i] + netVX[i] * clampedOver
                    outY[i] = netY[i] + netVY[i] * clampedOver
                    outVX[i] = netVX[i]
                    outVY[i] = netVY[i]
                }
            } else {
                val overTime = globalTimeSeconds - t1
                val clampedOver = max(0f, min(overTime, config.maxExtrapolationTimeSeconds))
                outX[i] = netX[i] + netVX[i] * clampedOver
                outY[i] = netY[i] + netVY[i] * clampedOver
                outVX[i] = netVX[i]
                outVY[i] = netVY[i]
            }
        }
    }

    fun getX(entityId: Int): Float = if (entityId in 0 until maxEntities && active[entityId]) outX[entityId] else Float.NaN
    fun getY(entityId: Int): Float = if (entityId in 0 until maxEntities && active[entityId]) outY[entityId] else Float.NaN
    fun getVX(entityId: Int): Float = if (entityId in 0 until maxEntities && active[entityId]) outVX[entityId] else Float.NaN
    fun getVY(entityId: Int): Float = if (entityId in 0 until maxEntities && active[entityId]) outVY[entityId] else Float.NaN
    fun isActive(entityId: Int): Boolean = if (entityId in 0 until maxEntities) active[entityId] else false

    // FNV-1a 32-bit deterministic checksum
    fun computeChecksum(): Int {
        var hash = -2128831035 // 2166136261 (signed 32-bit offset basis)
        for (i in 0 until maxEntities) {
            if (active[i]) {
                hash = hash xor i
                hash = hash * 16777619
                hash = hash xor outX[i].toBits()
                hash = hash * 16777619
                hash = hash xor outY[i].toBits()
                hash = hash * 16777619
                hash = hash xor outVX[i].toBits()
                hash = hash * 16777619
                hash = hash xor outVY[i].toBits()
                hash = hash * 16777619
            }
        }
        return hash
    }
}
