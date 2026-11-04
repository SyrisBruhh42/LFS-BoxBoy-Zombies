package com.example.game.combat.modules

import com.example.game.ZombieEntity
import kotlin.math.hypot

data class CryoStatusEffect(
    val zombieId: Int,
    val speedMultiplier: Float = 0.4f,
    val isBrittle: Boolean = true,
    val durationMs: Long = 4000L
)

data class CryoNovaResult(
    val appliedEffects: List<CryoStatusEffect>
)

object CryoBarrelFreezeNova {
    fun detonateCryoBarrel(
        barrelX: Float,
        barrelY: Float,
        blastRadiusPx: Float,
        zombies: List<ZombieEntity>
    ): CryoNovaResult {
        val effects = mutableListOf<CryoStatusEffect>()
        
        for (zombie in zombies) {
            val distance = hypot(zombie.x - barrelX, zombie.y - barrelY)
            if (distance <= blastRadiusPx) {
                effects.add(
                    CryoStatusEffect(
                        zombieId = zombie.id,
                        speedMultiplier = 0.4f,
                        isBrittle = true,
                        durationMs = 4000L
                    )
                )
            }
        }
        
        return CryoNovaResult(appliedEffects = effects)
    }
}
