package com.example.game.combat.modules

import kotlin.math.hypot

data class Turret(
    val id: String,
    var x: Float,
    var y: Float,
    var baseFireRateDelayMs: Long,
    var currentFireRateDelayMs: Long,
    var empDebuffRemainingMs: Long = 0L
)

data class ScreamerZombie(
    val id: String,
    var x: Float,
    var y: Float,
    var empChargeTimeMs: Long = 0L,
    var isCharging: Boolean = false,
    var isInterrupted: Boolean = false
)

object VoltaicScreamerZombieLogic {
    const val EMP_CHARGE_REQUIRED_MS = 2000L
    const val EMP_RADIUS = 300f
    const val EMP_DEBUFF_DURATION_MS = 5000L
    const val EMP_FIRE_RATE_MULTIPLIER = 2.0f // 2x delay = slower fire rate

    fun updateScreamer(
        screamer: ScreamerZombie,
        deltaMs: Long,
        turrets: List<Turret>
    ) {
        if (screamer.isInterrupted) {
            screamer.isCharging = false
            screamer.empChargeTimeMs = 0L
            return
        }

        if (screamer.isCharging) {
            screamer.empChargeTimeMs += deltaMs
            if (screamer.empChargeTimeMs >= EMP_CHARGE_REQUIRED_MS) {
                dischargeEmp(screamer, turrets)
                screamer.isCharging = false
                screamer.empChargeTimeMs = 0L
            }
        }
    }

    private fun dischargeEmp(screamer: ScreamerZombie, turrets: List<Turret>) {
        for (turret in turrets) {
            val dist = hypot(turret.x - screamer.x, turret.y - screamer.y)
            if (dist <= EMP_RADIUS) {
                turret.empDebuffRemainingMs = EMP_DEBUFF_DURATION_MS
                turret.currentFireRateDelayMs = (turret.baseFireRateDelayMs * EMP_FIRE_RATE_MULTIPLIER).toLong()
            }
        }
    }

    fun updateTurret(turret: Turret, deltaMs: Long) {
        if (turret.empDebuffRemainingMs > 0) {
            turret.empDebuffRemainingMs -= deltaMs
            if (turret.empDebuffRemainingMs <= 0) {
                turret.empDebuffRemainingMs = 0
                turret.currentFireRateDelayMs = turret.baseFireRateDelayMs
            }
        }
    }
}
