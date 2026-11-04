package com.example.game.combat.modules

import com.example.game.FortificationEntity
import com.example.game.FortificationType
import kotlin.math.hypot

data class VoltaicScreamerZombie(
    val id: Int,
    var x: Float,
    var y: Float,
    var hp: Int,
    val maxHp: Int,
    var isScreaming: Boolean = false,
    var screechDurationMs: Long = 0L,
    var screechCooldownMs: Long = 0L
)

object VoltaicScreamerEngine {
    const val SCREECH_RADIUS = 300f
    const val MAX_SCREECH_DURATION_MS = 2000L
    const val SCREECH_COOLDOWN_MS = 5000L

    fun stepScreamers(
        screamers: List<VoltaicScreamerZombie>,
        turrets: List<FortificationEntity>,
        deltaMs: Long
    ): Pair<List<VoltaicScreamerZombie>, List<FortificationEntity>> {
        
        val survivingScreamers = mutableListOf<VoltaicScreamerZombie>()
        
        // We will mutate turrets directly if they are affected by the screech
        val updatedTurrets = turrets.map { it.copy() }
        
        for (screamer in screamers) {
            if (screamer.hp <= 0) continue
            
            var screeching = screamer.isScreaming
            var dur = screamer.screechDurationMs
            var cd = screamer.screechCooldownMs
            
            if (screeching) {
                dur = (dur - deltaMs).coerceAtLeast(0L)
                if (dur == 0L) {
                    screeching = false
                    cd = SCREECH_COOLDOWN_MS
                } else {
                    // Apply EMP effect to nearby turrets
                    for (turret in updatedTurrets) {
                        if (turret.kind == FortificationType.AUTO_TURRET_BEACON) {
                            if (hypot(turret.x - screamer.x, turret.y - screamer.y) <= SCREECH_RADIUS) {
                                turret.fireCooldownMs += (deltaMs * 0.75f).toLong() 
                            }
                        }
                    }
                }
            } else {
                cd = (cd - deltaMs).coerceAtLeast(0L)
                if (cd == 0L) {
                    screeching = true
                    dur = MAX_SCREECH_DURATION_MS
                }
            }
            
            survivingScreamers.add(screamer.copy(
                isScreaming = screeching,
                screechDurationMs = dur,
                screechCooldownMs = cd
            ))
        }
        
        return survivingScreamers to updatedTurrets
    }
}


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [VoltaicScreamerZombie].
 */
data class VoltaicScreamerZombieTelemetryReceipt(
    val moduleName: String = "VoltaicScreamerZombie",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeVoltaicScreamerZombieTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): VoltaicScreamerZombieTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "VoltaicScreamerZombie") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return VoltaicScreamerZombieTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateVoltaicScreamerZombieLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "VoltaicScreamerZombie") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
