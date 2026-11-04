package com.example.network.sync.modules

import androidx.annotation.Keep

@Keep
data class ReconcilerConfig(
    val maxBufferSize: Int = 128,
    val dtSecondsClampedMin: Float = 0.001f,
    val dtSecondsClampedMax: Float = 0.100f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "maxBufferSize" to maxBufferSize,
        "dtSecondsClampedMin" to dtSecondsClampedMin,
        "dtSecondsClampedMax" to dtSecondsClampedMax
    )

    companion object {
        fun fromMap(map: Map<String, Any>): ReconcilerConfig = ReconcilerConfig(
            maxBufferSize = (map["maxBufferSize"] as? Number)?.toInt() ?: 128,
            dtSecondsClampedMin = (map["dtSecondsClampedMin"] as? Number)?.toFloat() ?: 0.001f,
            dtSecondsClampedMax = (map["dtSecondsClampedMax"] as? Number)?.toFloat() ?: 0.100f
        )
    }
}

class AuthoritativeTickReconciler(
    val config: ReconcilerConfig = ReconcilerConfig()
) {
    private val inputBuffer = arrayOfNulls<PlayerTickInput>(config.maxBufferSize)
    private var bufferHead = 0
    private var bufferSize = 0

    var currentSnapshot: AuthoritativeTickSnapshot = AuthoritativeTickSnapshot(0L, 0f, 0f, 100, 0L)
        private set

    private fun clampDt(dt: Float): Float {
        if (!dt.isFinite()) return config.dtSecondsClampedMin
        return dt.coerceIn(config.dtSecondsClampedMin, config.dtSecondsClampedMax)
    }

    private fun safeFloat(v: Float, def: Float = 0f): Float {
        return if (v.isFinite()) v else def
    }

    fun computeChecksum(tickId: Long, px: Float, py: Float, hp: Int): Long {
        var hash = 2166136261L
        hash = (hash xor tickId) * 16777619L
        hash = (hash xor px.toRawBits().toLong()) * 16777619L
        hash = (hash xor py.toRawBits().toLong()) * 16777619L
        hash = (hash xor hp.toLong()) * 16777619L
        return hash
    }

    fun addLocalInput(input: PlayerTickInput) {
        val safeInput = input.copy(
            moveX = safeFloat(input.moveX),
            moveY = safeFloat(input.moveY),
            aimAngleRad = safeFloat(input.aimAngleRad),
            dtSeconds = clampDt(input.dtSeconds)
        )
        inputBuffer[bufferHead] = safeInput
        bufferHead = (bufferHead + 1) % config.maxBufferSize
        if (bufferSize < config.maxBufferSize) {
            bufferSize++
        }
    }

    fun reconcile(serverSnapshot: AuthoritativeTickSnapshot) {
        if (serverSnapshot.tickId <= currentSnapshot.tickId && currentSnapshot.tickId != 0L) {
            return // Skip older snapshots unless it's the very first one
        }

        // Apply server snapshot
        currentSnapshot = serverSnapshot

        // Replay inputs that are newer than the server snapshot directly from circular buffer
        var idx = if (bufferSize == config.maxBufferSize) bufferHead else 0
        for (i in 0 until bufferSize) {
            val input = inputBuffer[idx]
            if (input != null && input.tickId > serverSnapshot.tickId) {
                applyInputToCurrentSnapshot(input)
            }
            idx = (idx + 1) % config.maxBufferSize
        }
    }

    private fun applyInputToCurrentSnapshot(input: PlayerTickInput) {
        val dt = clampDt(input.dtSeconds)
        val speedPxPerSec = 232f // From GameSessionState player speed

        // Deterministic LCG for slight recoil/variance when firing
        var recoilX = 0f
        var recoilY = 0f
        if (input.isFiring) {
            val seed = (input.tickId xor currentSnapshot.checksum)
            val lcg = (seed * 6364136223846793005L + 1442695040888963407L)
            recoilX = ((lcg ushr 32).toInt() % 10) / 10f
            recoilY = ((lcg ushr 48).toInt() % 10) / 10f
        }

        val moveDist = speedPxPerSec * dt
        val nextX = currentSnapshot.playerX + (input.moveX * moveDist) + recoilX
        val nextY = currentSnapshot.playerY + (input.moveY * moveDist) + recoilY

        // Update snapshot
        currentSnapshot = currentSnapshot.copy(
            tickId = input.tickId,
            playerX = nextX,
            playerY = nextY,
            checksum = computeChecksum(input.tickId, nextX, nextY, currentSnapshot.playerHp)
        )
    }
}

@Keep
data class PlayerTickInput(
    val tickId: Long,
    val moveX: Float,
    val moveY: Float,
    val isFiring: Boolean,
    val aimAngleRad: Float,
    val dtSeconds: Float
) {
    fun toMap(): Map<String, Any> = mapOf(
        "tickId" to tickId,
        "moveX" to moveX,
        "moveY" to moveY,
        "isFiring" to isFiring,
        "aimAngleRad" to aimAngleRad,
        "dtSeconds" to dtSeconds
    )

    companion object {
        fun fromMap(map: Map<String, Any>): PlayerTickInput = PlayerTickInput(
            tickId = (map["tickId"] as? Number)?.toLong() ?: 0L,
            moveX = (map["moveX"] as? Number)?.toFloat() ?: 0f,
            moveY = (map["moveY"] as? Number)?.toFloat() ?: 0f,
            isFiring = map["isFiring"] as? Boolean ?: false,
            aimAngleRad = (map["aimAngleRad"] as? Number)?.toFloat() ?: 0f,
            dtSeconds = (map["dtSeconds"] as? Number)?.toFloat() ?: 0.016f
        )
    }
}

@Keep
data class AuthoritativeTickSnapshot(
    val tickId: Long,
    val playerX: Float,
    val playerY: Float,
    val playerHp: Int,
    val checksum: Long
) {
    fun toMap(): Map<String, Any> = mapOf(
        "tickId" to tickId,
        "playerX" to playerX,
        "playerY" to playerY,
        "playerHp" to playerHp,
        "checksum" to checksum
    )

    companion object {
        fun fromMap(map: Map<String, Any>): AuthoritativeTickSnapshot = AuthoritativeTickSnapshot(
            tickId = (map["tickId"] as? Number)?.toLong() ?: 0L,
            playerX = (map["playerX"] as? Number)?.toFloat() ?: 0f,
            playerY = (map["playerY"] as? Number)?.toFloat() ?: 0f,
            playerHp = (map["playerHp"] as? Number)?.toInt() ?: 0,
            checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
        )
    }
}
