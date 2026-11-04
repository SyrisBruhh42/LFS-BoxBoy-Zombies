package com.example.game.ai.director.modules

import com.example.game.GameSessionState
import kotlin.random.Random

object EmergencySupplyDropDirector {

    data class SupplyDropDirective(
        val shouldDropAmmo: Boolean,
        val shouldDropBarrel: Boolean,
        val targetX: Float,
        val targetY: Float
    )

    fun evaluateSupplyDrop(
        state: GameSessionState,
        seed: Long = 19960227L
    ): SupplyDropDirective {
        val hpRatio = state.hp.toFloat() / state.maxHp.coerceAtLeast(1).toFloat()
        val activeZombiesCount = state.zombies.count { it.hp > 0 }
        
        val maxAmmo = state.currentMaxMagSize
        val currentAmmo = state.currentAmmoCount
        val ammoRatio = currentAmmo.toFloat() / maxAmmo.coerceAtLeast(1).toFloat()

        val highThreat = activeZombiesCount >= 5 || hpRatio <= 0.3f
        val lowAmmo = ammoRatio <= 0.2f

        val rng = Random(seed xor state.mapSeed)

        return SupplyDropDirective(
            shouldDropAmmo = lowAmmo,
            shouldDropBarrel = highThreat,
            targetX = state.playerX + (rng.nextFloat() * 100f - 50f),
            targetY = state.playerY + (rng.nextFloat() * 100f - 50f)
        )
    }
}
