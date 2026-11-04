package com.example.game.combat.modules

enum class DevilBossPhase {
    PHASE_1,
    PHASE_2,
    PHASE_3
}

data class DevilBossState(
    val phase: DevilBossPhase,
    val isEnraged: Boolean,
    val fireballVolleyCount: Int,
    val canChargeSlam: Boolean,
    val barrelDamageMultiplier: Float
)

object BoxheadDevilBossPhase {
    fun evaluatePhase(hp: Int, maxHp: Int): DevilBossState {
        val hpPercentage = hp.toFloat() / maxHp.toFloat()

        return when {
            hpPercentage <= 0.33f -> DevilBossState(
                phase = DevilBossPhase.PHASE_3,
                isEnraged = true,
                fireballVolleyCount = 5,
                canChargeSlam = true,
                barrelDamageMultiplier = 0.0f
            )
            hpPercentage <= 0.66f -> DevilBossState(
                phase = DevilBossPhase.PHASE_2,
                isEnraged = true,
                fireballVolleyCount = 3,
                canChargeSlam = true,
                barrelDamageMultiplier = 0.5f
            )
            else -> DevilBossState(
                phase = DevilBossPhase.PHASE_1,
                isEnraged = false,
                fireballVolleyCount = 1,
                canChargeSlam = false,
                barrelDamageMultiplier = 1.0f
            )
        }
    }
}
