package com.example.game.relics.items

import androidx.annotation.Keep

@Keep
data class Relic17ShatteringJusticeConfig(
    val requiredHits: Int = 5,
    val baseDurationSeconds: Float = 8.0f,
    val durationPerStackSeconds: Float = 8.0f
) {
    val canBePurchasedAtVending: Boolean = false

    fun toMap(): Map<String, Any> {
        return mapOf(
            "requiredHits" to requiredHits,
            "baseDurationSeconds" to baseDurationSeconds,
            "durationPerStackSeconds" to durationPerStackSeconds,
            "canBePurchasedAtVending" to canBePurchasedAtVending
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Relic17ShatteringJusticeConfig {
            return Relic17ShatteringJusticeConfig(
                requiredHits = (map["requiredHits"] as? Number)?.toInt() ?: 5,
                baseDurationSeconds = (map["baseDurationSeconds"] as? Number)?.toFloat() ?: 8.0f,
                durationPerStackSeconds = (map["durationPerStackSeconds"] as? Number)?.toFloat() ?: 8.0f
            )
        }
    }
}

@Keep
class Relic17ShatteringJusticeState(
    var stackCount: Int,
    val trackedEnemyIds: IntArray = IntArray(300),
    val enemyHitCounts: IntArray = IntArray(300),
    val activeArmorPulverizedDurations: FloatArray = FloatArray(300),
    var activeTrackingCount: Int = 0,
    var checksum: Long = 0L
) {
    init {
        require(stackCount >= 1) { "Relic requires at least 1 stack." }
    }

    fun computeChecksum(): Long {
        var hash = -3750763034362895579L
        hash = (hash xor stackCount.toLong()) * 1099511628211L
        hash = (hash xor activeTrackingCount.toLong()) * 1099511628211L
        for (i in 0 until activeTrackingCount) {
            hash = (hash xor trackedEnemyIds[i].toLong()) * 1099511628211L
            hash = (hash xor enemyHitCounts[i].toLong()) * 1099511628211L
            hash = (hash xor activeArmorPulverizedDurations[i].toRawBits().toLong()) * 1099511628211L
        }
        checksum = hash
        return hash
    }

    fun toMap(): Map<String, Any> {
        val ids = ArrayList<Int>(activeTrackingCount)
        val counts = ArrayList<Int>(activeTrackingCount)
        val durations = ArrayList<Float>(activeTrackingCount)
        for (i in 0 until activeTrackingCount) {
            ids.add(trackedEnemyIds[i])
            counts.add(enemyHitCounts[i])
            durations.add(activeArmorPulverizedDurations[i])
        }
        return mapOf(
            "stackCount" to stackCount,
            "activeTrackingCount" to activeTrackingCount,
            "trackedEnemyIds" to ids,
            "enemyHitCounts" to counts,
            "activeArmorPulverizedDurations" to durations,
            "checksum" to checksum
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Relic17ShatteringJusticeState {
            val stackCount = (map["stackCount"] as? Number)?.toInt() ?: 1
            val activeTrackingCount = (map["activeTrackingCount"] as? Number)?.toInt() ?: 0
            val checksum = (map["checksum"] as? Number)?.toLong() ?: 0L

            val idsList = map["trackedEnemyIds"] as? List<*> ?: emptyList<Any>()
            val countsList = map["enemyHitCounts"] as? List<*> ?: emptyList<Any>()
            val durationsList = map["activeArmorPulverizedDurations"] as? List<*> ?: emptyList<Any>()

            val state = Relic17ShatteringJusticeState(stackCount = stackCount)
            state.activeTrackingCount = activeTrackingCount.coerceAtMost(300)

            for (i in 0 until state.activeTrackingCount) {
                if (i < idsList.size && i < countsList.size && i < durationsList.size) {
                    state.trackedEnemyIds[i] = (idsList[i] as? Number)?.toInt() ?: 0
                    state.enemyHitCounts[i] = (countsList[i] as? Number)?.toInt() ?: 0
                    state.activeArmorPulverizedDurations[i] = (durationsList[i] as? Number)?.toFloat() ?: 0f
                }
            }
            state.checksum = checksum
            return state
        }
    }
}

class Relic17ShatteringJusticeEngine(
    private val config: Relic17ShatteringJusticeConfig
) {
    fun processHits(
        currentState: Relic17ShatteringJusticeState,
        hitEnemyIds: IntArray
    ) {
        if (hitEnemyIds.isEmpty()) {
            currentState.computeChecksum()
            return
        }

        val maxDuration = config.baseDurationSeconds + (currentState.stackCount - 1) * config.durationPerStackSeconds

        for (i in hitEnemyIds.indices) {
            val enemyId = hitEnemyIds[i]
            var foundIdx = -1
            for (j in 0 until currentState.activeTrackingCount) {
                if (currentState.trackedEnemyIds[j] == enemyId) {
                    foundIdx = j
                    break
                }
            }

            if (foundIdx != -1) {
                currentState.enemyHitCounts[foundIdx] += 1
                if (currentState.enemyHitCounts[foundIdx] >= config.requiredHits) {
                    currentState.activeArmorPulverizedDurations[foundIdx] = maxDuration
                    currentState.enemyHitCounts[foundIdx] = 0 // reset hit count while pulverized
                }
            } else {
                if (currentState.activeTrackingCount < 300) {
                    val idx = currentState.activeTrackingCount
                    currentState.trackedEnemyIds[idx] = enemyId
                    currentState.enemyHitCounts[idx] = 1
                    currentState.activeArmorPulverizedDurations[idx] = 0f
                    currentState.activeTrackingCount++
                }
            }
        }

        currentState.computeChecksum()
    }

    fun tick(
        currentState: Relic17ShatteringJusticeState,
        dtSeconds: Float
    ) {
        val boundedDt = if (!dtSeconds.isFinite()) {
            0.001f
        } else {
            dtSeconds.coerceIn(0.001f, 0.100f)
        }

        if (currentState.activeTrackingCount == 0) {
            currentState.computeChecksum()
            return
        }

        var j = 0
        for (i in 0 until currentState.activeTrackingCount) {
            var keep = false
            if (currentState.activeArmorPulverizedDurations[i] > 0f) {
                currentState.activeArmorPulverizedDurations[i] -= boundedDt
                if (currentState.activeArmorPulverizedDurations[i] > 0f) {
                    keep = true
                } else {
                    currentState.activeArmorPulverizedDurations[i] = 0f
                }
            }
            if (currentState.enemyHitCounts[i] > 0 || keep) {
                currentState.trackedEnemyIds[j] = currentState.trackedEnemyIds[i]
                currentState.enemyHitCounts[j] = currentState.enemyHitCounts[i]
                currentState.activeArmorPulverizedDurations[j] = currentState.activeArmorPulverizedDurations[i]
                j++
            }
        }

        for (i in j until currentState.activeTrackingCount) {
            currentState.trackedEnemyIds[i] = 0
            currentState.enemyHitCounts[i] = 0
            currentState.activeArmorPulverizedDurations[i] = 0f
        }

        currentState.activeTrackingCount = j
        currentState.computeChecksum()
    }
}
