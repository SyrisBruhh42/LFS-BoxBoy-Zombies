package com.example.game.combat.modules

import com.example.game.combat.BarrelNode
import kotlin.math.hypot

/**
 * Result of a Cryo Barrel Detonation.
 */
data class CryoNovaResult(
    val affectedEnemyIds: List<Int>,
    val slowedEnemyIds: List<Int>,
    val brittleEnemyIds: List<Int>
)

data class EnemyNode(
    val id: Int,
    val x: Float,
    val y: Float
)

object CryoBarrelFreezeNova {

    fun detonate(barrel: BarrelNode, enemies: List<EnemyNode>): CryoNovaResult {
        if (!barrel.isCryo) {
            return CryoNovaResult(emptyList(), emptyList(), emptyList())
        }

        val affected = mutableListOf<Int>()
        val slowed = mutableListOf<Int>()
        val brittle = mutableListOf<Int>()

        for (enemy in enemies) {
            val dist = hypot(enemy.x - barrel.x, enemy.y - barrel.y)
            if (dist <= barrel.blastRadiusPx) {
                affected.add(enemy.id)
                slowed.add(enemy.id)
                if (dist <= barrel.blastRadiusPx * 0.5f) {
                    brittle.add(enemy.id)
                }
            }
        }

        return CryoNovaResult(
            affectedEnemyIds = affected,
            slowedEnemyIds = slowed,
            brittleEnemyIds = brittle
        )
    }
}
