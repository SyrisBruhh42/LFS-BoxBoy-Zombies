package com.example.game.combat.modules

import com.example.data.WeaponSpec
import com.example.game.ModularWeaponInstance
import com.example.game.WeaponManufacturer
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import kotlin.math.cos
import kotlin.math.sin

data class KnockbackImpulse(
    val vx: Float,
    val vy: Float,
    val staggerRecoveryFrames: Int
)

object KnockbackAndPoiseStagger {

    /**
     * Computes the heavy-caliber knockback impulse and stagger recovery frames 
     * based on the weapon and modular manufacturer.
     */
    fun computeImpact(
        weapon: WeaponSpec,
        modularInstance: ModularWeaponInstance?,
        zombie: ZombieEntity,
        hitAngleRad: Float
    ): KnockbackImpulse {
        // Base knockback magnitude
        var baseMag = 0f
        var staggerFrames = 0
        
        when (weapon.shortCode) {
            "12G-SG" -> { // Shotgun
                baseMag = 180f
                staggerFrames = 12
            }
            "RAIL" -> { // Railgun
                baseMag = 250f
                staggerFrames = 16
            }
            "RAY-MK2" -> { // Ray-Box Mk II
                baseMag = 300f
                staggerFrames = 20
            }
            "M1911" -> { // Pistol
                baseMag = 40f
                staggerFrames = 2
            }
            "UZI" -> { // UZI
                baseMag = 15f
                staggerFrames = 0
            }
            "GALIL" -> { // AR
                baseMag = 60f
                staggerFrames = 4
            }
            else -> {
                baseMag = 30f
                staggerFrames = 1
            }
        }
        
        // Korsakov manufacturer adds heavy knockback
        if (modularInstance != null && modularInstance.receiver.manufacturer == WeaponManufacturer.KORSAKOV_ARMORIES) {
            baseMag *= 1.4f // 40% more knockback magnitude
            staggerFrames += 6 // Added stagger frames
        }
        
        // Boss resists knockback and stagger
        if (zombie.archetype == ZombieArchetype.DEVIL_BOSS) {
            baseMag *= 0.15f
            staggerFrames = 0
        }
        
        // Crawlers take extra knockback
        if (zombie.archetype == ZombieArchetype.CRAWLER) {
            baseMag *= 1.3f
            staggerFrames += 2
        }

        return KnockbackImpulse(
            vx = cos(hitAngleRad) * baseMag,
            vy = sin(hitAngleRad) * baseMag,
            staggerRecoveryFrames = staggerFrames
        )
    }
}


fun evaluateKnockbackAndPoiseStaggerLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "KnockbackAndPoiseStagger") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
