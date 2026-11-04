package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Keep
data class AmpShieldState(
    val maxShield: Int,
    val currentShield: Int,
    val ampDamageBonusPct: Int = 40,
    val shieldRechargeDelayMs: Int = 2000,
    val shieldRechargeRatePerSec: Int = 10,
    val timeSinceLastDamageMs: Int = 0
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "maxShield" to maxShield,
            "currentShield" to currentShield,
            "ampDamageBonusPct" to ampDamageBonusPct,
            "shieldRechargeDelayMs" to shieldRechargeDelayMs,
            "shieldRechargeRatePerSec" to shieldRechargeRatePerSec,
            "timeSinceLastDamageMs" to timeSinceLastDamageMs
        )
    }

    fun computeChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor maxShield.toLong()) * 16777619L
        hash = (hash xor currentShield.toLong()) * 16777619L
        hash = (hash xor ampDamageBonusPct.toLong()) * 16777619L
        hash = (hash xor shieldRechargeDelayMs.toLong()) * 16777619L
        hash = (hash xor shieldRechargeRatePerSec.toLong()) * 16777619L
        hash = (hash xor timeSinceLastDamageMs.toLong()) * 16777619L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): AmpShieldState {
            return AmpShieldState(
                maxShield = (map["maxShield"] as? Number)?.toInt() ?: 100,
                currentShield = (map["currentShield"] as? Number)?.toInt() ?: 100,
                ampDamageBonusPct = (map["ampDamageBonusPct"] as? Number)?.toInt() ?: 40,
                shieldRechargeDelayMs = (map["shieldRechargeDelayMs"] as? Number)?.toInt() ?: 2000,
                shieldRechargeRatePerSec = (map["shieldRechargeRatePerSec"] as? Number)?.toInt() ?: 10,
                timeSinceLastDamageMs = (map["timeSinceLastDamageMs"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

@Keep
data class ZombieTarget(
    val id: String,
    val x: Float,
    val y: Float,
    val radius: Float = 16f,
    val hp: Int = 100
) {
    val isValid: Boolean
        get() = hp > 0 && x.isFinite() && y.isFinite() && radius.isFinite() && radius > 0f
}

/**
 * Mutable result object to avoid per-frame allocations during hot loop iterations.
 * The `impactedTargets` buffer is pre-allocated and `impactedCount` indicates the valid items.
 */
@Keep
class AmpShotEvaluationResult(maxTargets: Int = 250) {
    var newState: AmpShieldState = AmpShieldState(0, 0)
    var isAmped: Boolean = false
    var baseDamageDealt: Int = 0
    var ampDamageDealt: Int = 0
    var totalDamageDealt: Int = 0
    
    val impactedTargets: Array<String> = Array(maxTargets) { "" }
    var impactedCount: Int = 0
    
    fun getImpactedList(): List<String> {
        val list = ArrayList<String>(impactedCount)
        for (i in 0 until impactedCount) {
            list.add(impactedTargets[i])
        }
        return list
    }
}

object AmpShotShieldGenerator {
    private const val MIN_DT_SEC = 0.001f
    private const val MAX_DT_SEC = 0.100f
    
    fun generateShield(seed: Long, wave: Int): AmpShieldState {
        val safeWave = max(1, wave)
        val scaledShield = 100 + (safeWave - 1) * 25
        return AmpShieldState(
            maxShield = scaledShield,
            currentShield = scaledShield,
            ampDamageBonusPct = 40,
            shieldRechargeDelayMs = max(1000, 2000 - (safeWave - 1) * 50),
            shieldRechargeRatePerSec = 10 + (safeWave - 1) * 5,
            timeSinceLastDamageMs = 0
        )
    }

    fun step(state: AmpShieldState, dtSeconds: Float): AmpShieldState {
        val safeDt = if (dtSeconds.isNaN() || dtSeconds.isInfinite()) {
            MIN_DT_SEC
        } else {
            dtSeconds.coerceIn(MIN_DT_SEC, MAX_DT_SEC)
        }
        
        val dtMs = (safeDt * 1000f).toInt()
        
        val newTimeSinceDamage = state.timeSinceLastDamageMs + dtMs
        var newShield = state.currentShield

        if (newTimeSinceDamage >= state.shieldRechargeDelayMs && state.currentShield < state.maxShield) {
            val activeRechargeTimeMs = newTimeSinceDamage - state.shieldRechargeDelayMs
            val previousActiveRechargeTimeMs = max(0, state.timeSinceLastDamageMs - state.shieldRechargeDelayMs)
            
            val newlyRecharged = ((activeRechargeTimeMs * state.shieldRechargeRatePerSec) / 1000) - ((previousActiveRechargeTimeMs * state.shieldRechargeRatePerSec) / 1000)
            
            if (newlyRecharged > 0) {
                newShield = min(state.maxShield, state.currentShield + newlyRecharged)
            }
        }
        
        newShield = newShield.coerceIn(0, state.maxShield)

        return state.copy(
            currentShield = newShield,
            timeSinceLastDamageMs = newTimeSinceDamage
        )
    }

    fun takeDamage(state: AmpShieldState, damage: Int): AmpShieldState {
        if (damage <= 0) return state
        val newShield = max(0, state.currentShield - damage)
        return state.copy(
            currentShield = newShield,
            timeSinceLastDamageMs = 0
        )
    }

    /**
     * Reuses an existing `outResult` buffer to prevent zero-allocation per-frame churn.
     */
    fun resolveAmpShot(
        state: AmpShieldState,
        baseDamage: Int,
        playerX: Float,
        playerY: Float,
        aimAngleRad: Float,
        shotRangePx: Float,
        targets: Array<ZombieTarget>,
        targetsCount: Int,
        outResult: AmpShotEvaluationResult
    ) {
        val isAmped = state.currentShield >= state.maxShield && state.maxShield > 0
        val ampBonus = if (isAmped) (baseDamage * state.ampDamageBonusPct) / 100 else 0
        val totalDamage = baseDamage + ampBonus

        val pX = if (playerX.isFinite()) playerX else 0f
        val pY = if (playerY.isFinite()) playerY else 0f
        val angle = if (aimAngleRad.isFinite()) aimAngleRad else 0f
        val range = if (shotRangePx.isFinite() && shotRangePx > 0f) shotRangePx else 1000f

        val dx = cos(angle)
        val dy = sin(angle)
        
        val endX = pX + dx * range
        val endY = pY + dy * range

        outResult.impactedCount = 0

        val maxIter = min(targetsCount, targets.size)
        for (i in 0 until maxIter) {
            val t = targets[i]
            if (!t.isValid) continue

            val dist = pointToSegmentDistance(t.x, t.y, pX, pY, endX, endY)
            if (dist <= t.radius) {
                if (outResult.impactedCount < outResult.impactedTargets.size) {
                    outResult.impactedTargets[outResult.impactedCount] = t.id
                    outResult.impactedCount++
                }
            }
        }

        var newState = state
        var actualAmp = 0
        var totalActualDamage = baseDamage

        if (isAmped) {
            actualAmp = ampBonus
            totalActualDamage = totalDamage
            val drainAmount = max(1, actualAmp)
            val newShield = max(0, state.currentShield - drainAmount)
            newState = state.copy(
                currentShield = newShield,
                timeSinceLastDamageMs = 0
            )
        }

        outResult.newState = newState
        outResult.isAmped = isAmped
        outResult.baseDamageDealt = baseDamage
        outResult.ampDamageDealt = actualAmp
        outResult.totalDamageDealt = totalActualDamage
    }

    private fun pointToSegmentDistance(
        px: Float, py: Float,
        sx1: Float, sy1: Float,
        sx2: Float, sy2: Float
    ): Float {
        val l2 = dist2(sx1, sy1, sx2, sy2)
        if (l2 == 0f) return sqrt(dist2(px, py, sx1, sy1))
        
        var t = ((px - sx1) * (sx2 - sx1) + (py - sy1) * (sy2 - sy1)) / l2
        t = max(0f, min(1f, t))
        
        return sqrt(
            dist2(
                px, py,
                sx1 + t * (sx2 - sx1),
                sy1 + t * (sy2 - sy1)
            )
        )
    }

    private fun dist2(vx: Float, vy: Float, wx: Float, wy: Float): Float {
        val dx = vx - wx
        val dy = vy - wy
        return dx * dx + dy * dy
    }
}
