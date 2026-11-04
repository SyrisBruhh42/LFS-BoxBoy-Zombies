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
