package com.example.game.combat.modules

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import kotlin.random.Random

data class NecroSummonerComponent(
    val priestZombieId: Int,
    var timeSinceLastSummonMs: Long = 0L,
    val cooldownMs: Long = 5000L,
    val maxSkeletons: Int = 3,
    val activeSkeletons: MutableList<Int> = mutableListOf()
)

object NecroSummonerEngine {
    private var nextSkeletonId = 90000

    fun tick(
        deltaMs: Long,
        components: List<NecroSummonerComponent>,
        zombies: MutableList<ZombieEntity>
    ): List<NecroSummonerComponent> {
        val activeComponents = mutableListOf<NecroSummonerComponent>()

        for (comp in components) {
            val priest = zombies.find { it.id == comp.priestZombieId && it.hp > 0 }
            if (priest == null) {
                // Priest eliminated, component is discarded. We could also kill the skeletons here, 
                // but let's just let them live.
                continue
            }

            // Cleanup dead skeletons from our tracking list
            comp.activeSkeletons.removeAll { skeletonId ->
                val s = zombies.find { it.id == skeletonId }
                s == null || s.hp <= 0
            }

            activeComponents.add(comp)
            comp.timeSinceLastSummonMs += deltaMs

            if (comp.timeSinceLastSummonMs >= comp.cooldownMs && comp.activeSkeletons.size < comp.maxSkeletons) {
                comp.timeSinceLastSummonMs = 0L
                
                // Spawn a mini skeleton nearby
                val offsetX = (Random.nextFloat() - 0.5f) * 40f
                val offsetY = (Random.nextFloat() - 0.5f) * 40f
                
                val skeletonId = nextSkeletonId++
                
                val skeleton = ZombieEntity(
                    id = skeletonId,
                    archetype = ZombieArchetype.SHAMBLER,
                    x = priest.x + offsetX,
                    y = priest.y + offsetY,
                    hp = 15,
                    maxHp = 15
                )
                
                zombies.add(skeleton)
                comp.activeSkeletons.add(skeletonId)
            }
        }

        return activeComponents
    }
}


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [NecroSummonerPriest].
 */
data class NecroSummonerPriestTelemetryReceipt(
    val moduleName: String = "NecroSummonerPriest",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeNecroSummonerPriestTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): NecroSummonerPriestTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "NecroSummonerPriest") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return NecroSummonerPriestTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateNecroSummonerPriestLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "NecroSummonerPriest") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
