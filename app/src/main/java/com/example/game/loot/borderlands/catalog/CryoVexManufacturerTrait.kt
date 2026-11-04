package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import kotlin.math.roundToInt

@Keep
data class CryoVexTraitConfig(
    val lightLockCritMultiplier: Float = 1.30f,
    val baseFreezeThreshold: Float = 100f,
    val freezeDurationSeconds: Float = 3.0f,
    val buildupDecayPerSecond: Float = 15f
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "lightLockCritMultiplier" to lightLockCritMultiplier,
            "baseFreezeThreshold" to baseFreezeThreshold,
            "freezeDurationSeconds" to freezeDurationSeconds,
            "buildupDecayPerSecond" to buildupDecayPerSecond
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): CryoVexTraitConfig {
            return CryoVexTraitConfig(
                lightLockCritMultiplier = (map["lightLockCritMultiplier"] as? Number)?.toFloat() ?: 1.30f,
                baseFreezeThreshold = (map["baseFreezeThreshold"] as? Number)?.toFloat() ?: 100f,
                freezeDurationSeconds = (map["freezeDurationSeconds"] as? Number)?.toFloat() ?: 3.0f,
                buildupDecayPerSecond = (map["buildupDecayPerSecond"] as? Number)?.toFloat() ?: 15f
            )
        }
    }
}

@Keep
data class CryoVexTargetState(
    var entityId: Int,
    var buildup: Float,
    var isFrozen: Boolean,
    var freezeRemainingSeconds: Float,
    var lastHitTimestampMs: Long
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "entityId" to entityId,
            "buildup" to buildup,
            "isFrozen" to isFrozen,
            "freezeRemainingSeconds" to freezeRemainingSeconds,
            "lastHitTimestampMs" to lastHitTimestampMs
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): CryoVexTargetState {
            return CryoVexTargetState(
                entityId = (map["entityId"] as? Number)?.toInt() ?: 0,
                buildup = (map["buildup"] as? Number)?.toFloat() ?: 0f,
                isFrozen = map["isFrozen"] as? Boolean ?: false,
                freezeRemainingSeconds = (map["freezeRemainingSeconds"] as? Number)?.toFloat() ?: 0f,
                lastHitTimestampMs = (map["lastHitTimestampMs"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

class CryoVexManufacturerTrait(
    val config: CryoVexTraitConfig = CryoVexTraitConfig()
) {
    companion object {
        const val MAX_TRACKED_ENTITIES = 512
        const val MIN_DT_SECONDS = 0.001f
        const val MAX_DT_SECONDS = 0.100f
    }

    private val entityStates = arrayOfNulls<CryoVexTargetState>(MAX_TRACKED_ENTITIES)
    private var activeStateCount = 0

    fun step(dtSeconds: Float) {
        val dt = if (dtSeconds.isNaN() || dtSeconds.isInfinite()) {
            MIN_DT_SECONDS
        } else {
            dtSeconds.coerceIn(MIN_DT_SECONDS, MAX_DT_SECONDS)
        }

        var writeIdx = 0
        for (i in 0 until activeStateCount) {
            val state = entityStates[i] ?: continue
            
            if (state.isFrozen) {
                state.freezeRemainingSeconds -= dt
                if (state.freezeRemainingSeconds <= 0f) {
                    state.isFrozen = false
                    state.freezeRemainingSeconds = 0f
                    state.buildup = 0f // Reset buildup after freeze expires
                }
            } else {
                state.buildup -= config.buildupDecayPerSecond * dt
                if (state.buildup < 0f) state.buildup = 0f
            }

            // Keep entity if it has buildup or is frozen
            if (state.buildup > 0f || state.isFrozen) {
                entityStates[writeIdx++] = state
            }
        }
        
        // Null out remaining
        for (i in writeIdx until activeStateCount) {
            entityStates[i] = null
        }
        activeStateCount = writeIdx
    }

    fun applyHit(
        entityId: Int, 
        baseDamage: Float, 
        isLightLocked: Boolean, 
        seed: Long,
        timestampMs: Long
    ): HitResult {
        if (entityId < 0) return HitResult(0f, false, false)
        val validDamage = if (baseDamage.isNaN() || baseDamage.isInfinite() || baseDamage < 0f) 0f else baseDamage
        if (validDamage == 0f) return HitResult(0f, false, false)

        // Seeded variation: varies buildup slightly based on seed (-10% to +10%)
        // Deterministic pseudo-random based on seed + entityId
        val mixedSeed = seed xor entityId.toLong()
        val rand = ((mixedSeed * 1103515245L + 12345L) and 0x7FFFFFFFL).toFloat() / 0x7FFFFFFFL
        val variation = 0.9f + (rand * 0.2f)
        
        val buildupAmount = validDamage * 0.5f * variation
        var finalDamage = validDamage

        if (isLightLocked) {
            finalDamage *= config.lightLockCritMultiplier
        }

        var stateIdx = -1
        for (i in 0 until activeStateCount) {
            if (entityStates[i]?.entityId == entityId) {
                stateIdx = i
                break
            }
        }

        var state: CryoVexTargetState
        if (stateIdx != -1) {
            state = entityStates[stateIdx]!!
            state.lastHitTimestampMs = timestampMs
        } else {
            if (activeStateCount >= MAX_TRACKED_ENTITIES) {
                // Evict oldest non-frozen entity
                var oldestIdx = -1
                var oldestTime = Long.MAX_VALUE
                for (i in 0 until activeStateCount) {
                    val s = entityStates[i]
                    if (s != null && !s.isFrozen && s.lastHitTimestampMs < oldestTime) {
                        oldestTime = s.lastHitTimestampMs
                        oldestIdx = i
                    }
                }
                
                if (oldestIdx != -1) {
                    stateIdx = oldestIdx
                    state = entityStates[stateIdx]!!
                    state.entityId = entityId // Reuse
                    state.buildup = 0f
                    state.isFrozen = false
                    state.freezeRemainingSeconds = 0f
                    state.lastHitTimestampMs = timestampMs
                } else {
                    // All frozen? This is extreme, but evict index 0
                    stateIdx = 0
                    state = entityStates[0]!!
                    state.entityId = entityId
                    state.buildup = 0f
                    state.isFrozen = false
                    state.freezeRemainingSeconds = 0f
                    state.lastHitTimestampMs = timestampMs
                }
            } else {
                stateIdx = activeStateCount++
                state = CryoVexTargetState(entityId, 0f, false, 0f, timestampMs)
                entityStates[stateIdx] = state
            }
        }

        var justFroze = false
        if (!state.isFrozen) {
            state.buildup += buildupAmount
            if (state.buildup >= config.baseFreezeThreshold) {
                state.isFrozen = true
                state.freezeRemainingSeconds = config.freezeDurationSeconds
                state.buildup = config.baseFreezeThreshold
                justFroze = true
            }
        }

        return HitResult(finalDamage, isLightLocked, justFroze)
    }

    fun getTargetState(entityId: Int): CryoVexTargetState? {
        for (i in 0 until activeStateCount) {
            if (entityStates[i]?.entityId == entityId) {
                return entityStates[i]
            }
        }
        return null
    }
    
    fun getActiveStateCount(): Int = activeStateCount

    fun computeStateChecksum(): Long {
        var checksumSum = 0L
        for (i in 0 until activeStateCount) {
            val state = entityStates[i] ?: continue
            var h = -2128831035146330053L // FNV offset basis
            
            // Hash entityId
            h = (h xor state.entityId.toLong()) * 1099511628211L
            
            // Hash buildup (float to int bits)
            h = (h xor state.buildup.toRawBits().toLong()) * 1099511628211L
            
            // Hash isFrozen
            h = (h xor (if (state.isFrozen) 1L else 0L)) * 1099511628211L
            
            // Hash freezeRemainingSeconds
            h = (h xor state.freezeRemainingSeconds.toRawBits().toLong()) * 1099511628211L
            
            // Hash lastHitTimestampMs
            h = (h xor state.lastHitTimestampMs) * 1099511628211L
            
            // XOR-sum for order independence
            checksumSum = checksumSum xor h
        }
        return checksumSum
    }
    
    fun clear() {
        for (i in 0 until activeStateCount) {
            entityStates[i] = null
        }
        activeStateCount = 0
    }
    
    data class HitResult(
        val finalDamage: Float,
        val wasCrit: Boolean,
        val triggeredFreeze: Boolean
    )
}
