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
