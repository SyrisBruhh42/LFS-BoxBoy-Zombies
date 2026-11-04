package com.example.network.sync.modules

import androidx.annotation.Keep
import kotlin.math.max
import kotlin.math.min

@Keep
data class WaveScaledStipendConfig(
    val joinWaveNumber: Int,
    val seed: Long,
    val activeZombiesCount: Int,
    val coopPeerCount: Int,
    val elonaRoomsUnlocked: Int,
    val dtSeconds: Float
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "joinWaveNumber" to joinWaveNumber,
            "seed" to seed,
            "activeZombiesCount" to activeZombiesCount,
            "coopPeerCount" to coopPeerCount,
            "elonaRoomsUnlocked" to elonaRoomsUnlocked,
            "dtSeconds" to dtSeconds
        )
    }

    fun computeChecksum(): Long {
        var hash = -3750763034362895579L // 64-bit FNV offset basis
        hash = (hash xor joinWaveNumber.toLong()) * 1099511628211L // FNV prime
        hash = (hash xor seed) * 1099511628211L
        hash = (hash xor activeZombiesCount.toLong()) * 1099511628211L
        hash = (hash xor coopPeerCount.toLong()) * 1099511628211L
        hash = (hash xor elonaRoomsUnlocked.toLong()) * 1099511628211L
        hash = (hash xor dtSeconds.toRawBits().toLong()) * 1099511628211L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): WaveScaledStipendConfig {
            return WaveScaledStipendConfig(
                joinWaveNumber = (map["joinWaveNumber"] as? Number)?.toInt() ?: 1,
                seed = (map["seed"] as? Number)?.toLong() ?: 0L,
                activeZombiesCount = (map["activeZombiesCount"] as? Number)?.toInt() ?: 0,
                coopPeerCount = (map["coopPeerCount"] as? Number)?.toInt() ?: 0,
                elonaRoomsUnlocked = (map["elonaRoomsUnlocked"] as? Number)?.toInt() ?: 0,
                dtSeconds = (map["dtSeconds"] as? Number)?.toFloat() ?: 0.016f
            )
        }
    }
}

@Keep
data class WaveScaledStipendResult(
    val startingCurrency: Int,
    val catchUpRelicsCount: Int,
    val isGracefulDegradationActive: Boolean,
    val checksum: Long
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "startingCurrency" to startingCurrency,
            "catchUpRelicsCount" to catchUpRelicsCount,
            "isGracefulDegradationActive" to isGracefulDegradationActive,
            "checksum" to checksum
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): WaveScaledStipendResult {
            return WaveScaledStipendResult(
                startingCurrency = (map["startingCurrency"] as? Number)?.toInt() ?: 0,
                catchUpRelicsCount = (map["catchUpRelicsCount"] as? Number)?.toInt() ?: 0,
                isGracefulDegradationActive = map["isGracefulDegradationActive"] as? Boolean ?: false,
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

object WaveScaledStipendCalculator {

    private const val BASE_STARTING_CURRENCY = 500
    private const val CURRENCY_PER_WAVE = 650
    private const val RELIC_DIVISOR = 5

    private const val MAX_JOIN_WAVE = 999
    private const val EXTREME_LOAD_ZOMBIE_THRESHOLD = 250
    private const val EXTREME_LOAD_ROOMS_THRESHOLD = 100

    fun computeStipend(config: WaveScaledStipendConfig): WaveScaledStipendResult {
        // 1. Sanitize dtSeconds
        val dt = if (config.dtSeconds.isFinite() && config.dtSeconds > 0f) config.dtSeconds else 0.016f
        val clampedDt = max(0.001f, min(0.100f, dt))

        // 2. Sanitize inputs and clamp bounds
        val wave = max(1, min(MAX_JOIN_WAVE, config.joinWaveNumber))
        val activeZombies = max(0, config.activeZombiesCount)
        val roomsUnlocked = max(0, config.elonaRoomsUnlocked)
        val peers = max(0, config.coopPeerCount)

        // 3. Evaluate extreme load gracefully
        val isExtremeLoad = activeZombies >= EXTREME_LOAD_ZOMBIE_THRESHOLD || roomsUnlocked >= EXTREME_LOAD_ROOMS_THRESHOLD

        // 4. Calculate Wave-Scaled Catch-Up Starting Currency: 500 + (wave-1)*650
        val startingCurrency = BASE_STARTING_CURRENCY + (wave - 1) * CURRENCY_PER_WAVE

        // 5. Calculate catch-up relics: wave / 5
        val catchUpRelics = wave / RELIC_DIVISOR

        val partialResult = WaveScaledStipendResult(
            startingCurrency = startingCurrency,
            catchUpRelicsCount = catchUpRelics,
            isGracefulDegradationActive = isExtremeLoad,
            checksum = 0L
        )

        val checksum = computeResultChecksum(partialResult, config.computeChecksum(), clampedDt)

        return partialResult.copy(checksum = checksum)
    }

    private fun computeResultChecksum(result: WaveScaledStipendResult, configChecksum: Long, dtSeconds: Float): Long {
        var hash = configChecksum
        hash = (hash xor result.startingCurrency.toLong()) * 1099511628211L
        hash = (hash xor result.catchUpRelicsCount.toLong()) * 1099511628211L
        hash = (hash xor (if (result.isGracefulDegradationActive) 1L else 0L)) * 1099511628211L
        hash = (hash xor dtSeconds.toRawBits().toLong()) * 1099511628211L
        return hash
    }
}
