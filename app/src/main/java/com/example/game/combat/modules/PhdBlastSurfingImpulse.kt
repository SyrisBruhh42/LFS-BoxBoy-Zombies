package com.example.game.combat.modules

import com.example.game.combat.ChainDetonationResult
import kotlin.math.hypot

data class BlastSurfingState(
    val vx: Float,
    val vy: Float,
    val isAirDashReloadTriggered: Boolean,
    val selfDamageApplied: Int
)

object PhdBlastSurfingImpulse {
    const val AIR_DASH_RELOAD_IMPULSE_THRESHOLD = 500f

    /**
     * Applies the blast impulse to the current velocity vectors, ensuring zero self-damage
     * (the PhD effect) and triggering an air-dash reload if the magnitude is large enough.
     */
    fun applyBlastSurfing(
        currentVx: Float,
        currentVy: Float,
        detonationResult: ChainDetonationResult
    ): BlastSurfingState {
        val newVx = currentVx + detonationResult.blastSurfImpulseVx
        val newVy = currentVy + detonationResult.blastSurfImpulseVy

        val impulseMag = hypot(detonationResult.blastSurfImpulseVx, detonationResult.blastSurfImpulseVy)
        val airDashReload = impulseMag >= AIR_DASH_RELOAD_IMPULSE_THRESHOLD

        return BlastSurfingState(
            vx = newVx,
            vy = newVy,
            isAirDashReloadTriggered = airDashReload,
            // Blast surfing always nullifies self-damage (PhD Flopper effect)
            selfDamageApplied = 0
        )
    }
}
