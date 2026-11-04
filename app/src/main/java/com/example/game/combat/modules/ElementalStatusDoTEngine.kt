package com.example.game.combat.modules

import com.example.game.ElementalAffinity
import com.example.game.ZombieEntity

data class ActiveStatusEffect(
    val element: ElementalAffinity,
    val sourceDamage: Float,
    var remainingTicks: Int,
    var timeUntilNextTickMs: Long
)

data class DoTTickResult(
    val zombieId: Int,
    val damageTaken: Int,
    val isLethal: Boolean
)

class ElementalStatusDoTEngine(
    val tickRateMs: Long = 500L,
    val maxTicks: Int = 6,
    val burnDamageMultiplier: Float = 0.25f,
    val corrosiveMeltMultiplier: Float = 0.3f,
    val voltaicStripMultiplier: Float = 0.4f
) {
    // Zombie ID to list of active effects
    private val activeEffects = mutableMapOf<Int, MutableList<ActiveStatusEffect>>()

    // Cryo freeze state - maps Zombie ID to freeze duration left in ms
    private val frozenZombies = mutableMapOf<Int, Long>()
    
    fun applyStatusEffect(zombieId: Int, element: ElementalAffinity, baseDamage: Float) {
        if (element == ElementalAffinity.KINETIC) return // Kinetic doesn't have DoT
        
        if (element == ElementalAffinity.CRYO) {
            // Cryo freezes instead of standard DoT
            val freezeDuration = (baseDamage * 100f).toLong().coerceAtMost(3000L)
            frozenZombies[zombieId] = (frozenZombies[zombieId] ?: 0L) + freezeDuration
        } else {
            val effects = activeEffects.getOrPut(zombieId) { mutableListOf() }
            effects.add(
                ActiveStatusEffect(
                    element = element,
                    sourceDamage = baseDamage,
                    remainingTicks = maxTicks,
                    timeUntilNextTickMs = tickRateMs
                )
            )
        }
    }

    fun tick(deltaMs: Long, zombies: List<ZombieEntity>): List<DoTTickResult> {
        val results = mutableListOf<DoTTickResult>()
        val deadZombieIds = mutableSetOf<Int>()

        // Tick freeze durations
        val frozenIterator = frozenZombies.entries.iterator()
        while (frozenIterator.hasNext()) {
            val entry = frozenIterator.next()
            val newDuration = entry.value - deltaMs
            if (newDuration <= 0) {
                frozenIterator.remove()
            } else {
                entry.setValue(newDuration)
            }
        }

        // Tick DoT effects
        for (zombie in zombies) {
            val effects = activeEffects[zombie.id] ?: continue
            val iterator = effects.iterator()
            var totalDamage = 0f
            
            while (iterator.hasNext()) {
                val effect = iterator.next()
                effect.timeUntilNextTickMs -= deltaMs
                
                if (effect.timeUntilNextTickMs <= 0) {
                    val damage = when (effect.element) {
                        ElementalAffinity.INCENDIARY -> effect.sourceDamage * burnDamageMultiplier
                        ElementalAffinity.CORROSIVE -> effect.sourceDamage * corrosiveMeltMultiplier
                        ElementalAffinity.VOLTAIC -> effect.sourceDamage * voltaicStripMultiplier
                        else -> 0f
                    }
                    totalDamage += damage
                    effect.timeUntilNextTickMs += tickRateMs
                    effect.remainingTicks--
                    
                    if (effect.remainingTicks <= 0) {
                        iterator.remove()
                    }
                }
            }
            
            if (totalDamage > 0) {
                val actualDamage = totalDamage.toInt().coerceAtLeast(1)
                zombie.hp -= actualDamage
                val isLethal = zombie.hp <= 0
                results.add(DoTTickResult(zombie.id, actualDamage, isLethal))
                if (isLethal) {
                    deadZombieIds.add(zombie.id)
                }
            }
            
            if (effects.isEmpty()) {
                activeEffects.remove(zombie.id)
            }
        }
        
        deadZombieIds.forEach { 
            activeEffects.remove(it)
            frozenZombies.remove(it)
        }

        return results
    }
    
    fun isFrozen(zombieId: Int): Boolean {
        return frozenZombies.containsKey(zombieId)
    }
    
    fun clear() {
        activeEffects.clear()
        frozenZombies.clear()
    }
}


fun evaluateElementalStatusDoTEngineLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "ElementalStatusDoTEngine") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
