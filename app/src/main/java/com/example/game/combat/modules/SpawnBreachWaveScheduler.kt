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
