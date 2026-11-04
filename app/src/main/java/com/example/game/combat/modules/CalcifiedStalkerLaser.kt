package com.example.game.combat.modules

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Represents the Calcified Stalker's laser telegraph attack logic, encapsulating
 * sightline trajectory, charge duration, and the critical dodge window timing.
 */
class CalcifiedStalkerLaser(
    val sourceId: Int = 0,
    val sourceZombieId: Int = sourceId,
    var startX: Float = 0f,
    var startY: Float = 0f,
    var aimAngleRad: Float = 0f,
    val range: Float = 440f,
    var endX: Float = startX + cos(aimAngleRad) * range,
    var endY: Float = startY + sin(aimAngleRad) * range,
    val totalChargeMs: Long = 850L,
    var chargeRemainingMs: Long = totalChargeMs,
    val dodgeWindowMs: Long = 200L // The final X ms before firing where the player can dodge.
) {
    val chargeFraction: Float
        get() = (1f - (chargeRemainingMs.toFloat() / totalChargeMs.coerceAtLeast(1L).toFloat())).coerceIn(0f, 1f)

    /**
     * Steps the laser charge forward by the given deltaMs.
     * Returns true if the charge has completed (time to fire).
     */
    fun step(deltaMs: Long): Boolean {
        chargeRemainingMs -= deltaMs
        if (chargeRemainingMs < 0L) {
            chargeRemainingMs = 0L
        }
        return chargeRemainingMs == 0L
    }

    /**
     * Checks if the laser is within the critical dodge window.
     */
    fun isInDodgeWindow(): Boolean {
        return chargeRemainingMs in 0L..dodgeWindowMs
    }

    fun isWithinDodgeWindow(): Boolean {
        return chargeRemainingMs in 0L..dodgeWindowMs
    }

    /**
     * Checks if the target (player or object) intersects with the laser sightline.
     * Uses a simple distance to line segment algorithm.
     */
    fun intersectsTarget(targetX: Float, targetY: Float, targetRadius: Float): Boolean {
        val dx = endX - startX
        val dy = endY - startY
        val lengthSq = dx * dx + dy * dy

        if (lengthSq == 0f) {
            // Start and end are the same
            val dist = hypot(targetX - startX, targetY - startY)
            return dist <= targetRadius
        }

        // Project target onto line segment
        var t = ((targetX - startX) * dx + (targetY - startY) * dy) / lengthSq
        t = t.coerceIn(0f, 1f)

        val closestX = startX + t * dx
        val closestY = startY + t * dy

        val distToClosest = hypot(targetX - closestX, targetY - closestY)
        return distToClosest <= targetRadius
    }
}


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [CalcifiedStalkerLaser].
 */
data class CalcifiedStalkerLaserTelemetryReceipt(
    val moduleName: String = "CalcifiedStalkerLaser",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeCalcifiedStalkerLaserTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): CalcifiedStalkerLaserTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "CalcifiedStalkerLaser") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return CalcifiedStalkerLaserTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateCalcifiedStalkerLaserLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "CalcifiedStalkerLaser") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
