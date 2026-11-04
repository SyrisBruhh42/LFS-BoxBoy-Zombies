package com.example.game.combat.modules

import com.example.game.ZombieEntity
import com.example.game.combat.BarrelNode
import com.example.game.contracts.Fnv1a64Hasher
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Result of a Cryo Barrel Detonation.
 */
data class CryoNovaResult(
    val affectedEnemyIds: List<Int>,
    val slowedEnemyIds: List<Int>,
    val brittleEnemyIds: List<Int>,
    val slowDurationMs: Long = 3500L,
    val brittleDamageMultiplier: Float = 1.65f,
    val checksum: Long = 0L
)

data class EnemyNode(
    val id: Int,
    val x: Float,
    val y: Float
)

data class CryoStatusEffectApplication(
    val zombieId: Int,
    val isBrittle: Boolean = true,
    val speedMultiplier: Float = 0.4f
)

data class CryoBarrelDetonationResult(
    val appliedEffects: List<CryoStatusEffectApplication>,
    val novaResult: CryoNovaResult
)

object CryoBarrelFreezeNova {
    const val DEFAULT_SLOW_FACTOR = 0.4f
    const val BRITTLE_CORE_RADIUS_RATIO = 0.5f
    const val DEFAULT_BRITTLE_MULTIPLIER = 1.65f

    fun detonateCryoBarrel(
        barrelX: Float,
        barrelY: Float,
        blastRadiusPx: Float = 150f,
        zombies: List<ZombieEntity>
    ): CryoBarrelDetonationResult {
        val nova = triggerNova(barrelX, barrelY, blastRadiusPx, zombies)
        val effects = zombies.filter { z ->
            hypot(z.x - barrelX, z.y - barrelY) <= blastRadiusPx
        }.map { z ->
            CryoStatusEffectApplication(
                zombieId = z.id,
                isBrittle = true,
                speedMultiplier = DEFAULT_SLOW_FACTOR
            )
        }
        return CryoBarrelDetonationResult(
            appliedEffects = effects,
            novaResult = nova
        )
    }

    fun detonate(barrel: BarrelNode, enemies: List<EnemyNode>): CryoNovaResult {
        if (!barrel.isCryo) {
            return CryoNovaResult(emptyList(), emptyList(), emptyList())
        }

        val affected = mutableListOf<Int>()
        val slowed = mutableListOf<Int>()
        val brittle = mutableListOf<Int>()
        var hash = Fnv1a64Hasher.initial()
        hash = Fnv1a64Hasher.mixInt(hash, barrel.id)

        for (enemy in enemies) {
            val dist = hypot(enemy.x - barrel.x, enemy.y - barrel.y)
            if (dist <= barrel.blastRadiusPx) {
                affected.add(enemy.id)
                slowed.add(enemy.id)
                hash = Fnv1a64Hasher.mixInt(hash, enemy.id)
                if (dist <= barrel.blastRadiusPx * BRITTLE_CORE_RADIUS_RATIO) {
                    brittle.add(enemy.id)
                }
            }
        }

        return CryoNovaResult(
            affectedEnemyIds = affected,
            slowedEnemyIds = slowed,
            brittleEnemyIds = brittle,
            slowDurationMs = 3500L,
            brittleDamageMultiplier = DEFAULT_BRITTLE_MULTIPLIER,
            checksum = hash
        )
    }

    fun triggerNova(
        barrelX: Float,
        barrelY: Float,
        blastRadiusPx: Float = 150f,
        zombies: List<ZombieEntity>
    ): CryoNovaResult {
        val barrelNode = BarrelNode(
            id = (barrelX.roundToInt() * 31) xor barrelY.roundToInt(),
            x = barrelX,
            y = barrelY,
            blastRadiusPx = blastRadiusPx,
            isCryo = true
        )
        val enemyNodes = zombies.map { EnemyNode(id = it.id, x = it.x, y = it.y) }
        return detonate(barrelNode, enemyNodes)
    }

    fun computeBrittleShatterDamage(
        baseDamage: Int,
        targetEnemyId: Int,
        novaResult: CryoNovaResult
    ): Int {
        if (baseDamage <= 0) return 0
        return when {
            targetEnemyId in novaResult.brittleEnemyIds ->
                (baseDamage * novaResult.brittleDamageMultiplier).roundToInt()
            targetEnemyId in novaResult.slowedEnemyIds ->
                (baseDamage * 1.20f).roundToInt()
            else -> baseDamage
        }
    }
}


fun evaluateCryoBarrelFreezeNovaLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "CryoBarrelFreezeNova") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
