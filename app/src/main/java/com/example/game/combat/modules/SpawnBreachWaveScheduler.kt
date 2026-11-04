package com.example.game.combat.modules

import com.example.game.GameSessionState
import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import kotlin.random.Random

data class BreachPoint(
    val x: Float,
    val y: Float,
    val type: BreachType
)

enum class BreachType {
    WINDOW, FRONTIER
}

data class BreachResult(
    val nextWaveNumber: Int,
    val spawnedEntities: List<ZombieEntity>
)

object SpawnBreachWaveScheduler {
    
    fun scheduleNextWave(
        state: GameSessionState,
        breachPoints: List<BreachPoint>,
        baseJitterRadius: Float = 1.0f,
        seed: Long = System.currentTimeMillis()
    ): BreachResult {
        val nextWaveNumber = state.wave + 1
        val random = Random(seed)
        val entities = mutableListOf<ZombieEntity>()
        
        // Example deterministic formula: wave * 2 + 3 entities total
        val count = nextWaveNumber * 2 + 3
        
        for (i in 0 until count) {
            val bp = breachPoints.randomOrNull(random) ?: continue
            val jitterX = (random.nextFloat() * 2 - 1) * baseJitterRadius
            val jitterY = (random.nextFloat() * 2 - 1) * baseJitterRadius
            
            val spawnX = bp.x + jitterX
            val spawnY = bp.y + jitterY
            
            // Just picking archetypes deterministically for testability
            val archetype = if (i % 5 == 0 && nextWaveNumber >= 5) {
                ZombieArchetype.CALCIFIED_STALKER
            } else if (i % 3 == 0) {
                ZombieArchetype.SPRINTER
            } else {
                ZombieArchetype.SHAMBLER
            }
            
            val hp = when (archetype) {
                ZombieArchetype.SHAMBLER -> 100
                ZombieArchetype.SPRINTER -> 75
                ZombieArchetype.CALCIFIED_STALKER -> 200
                else -> 100
            }
            
            entities.add(
                ZombieEntity(
                    id = random.nextInt(Int.MAX_VALUE),
                    archetype = archetype,
                    x = spawnX,
                    y = spawnY,
                    hp = hp,
                    maxHp = hp
                )
            )
        }
        
        return BreachResult(
            nextWaveNumber = nextWaveNumber,
            spawnedEntities = entities
        )
    }
}


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [SpawnBreachWaveScheduler].
 */
data class SpawnBreachWaveSchedulerTelemetryReceipt(
    val moduleName: String = "SpawnBreachWaveScheduler",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeSpawnBreachWaveSchedulerTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): SpawnBreachWaveSchedulerTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "SpawnBreachWaveScheduler") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return SpawnBreachWaveSchedulerTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateSpawnBreachWaveSchedulerLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "SpawnBreachWaveScheduler") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
