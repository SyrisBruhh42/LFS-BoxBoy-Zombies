package com.example.game.combat.modules

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NecroSummonerPriestTest {

    @Test
    fun testPriestSummonsSkeletonAfterCooldown() {
        val priest = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.ABYSSAL_WEAVER,
            x = 100f,
            y = 100f,
            hp = 100,
            maxHp = 100
        )
        val zombies = mutableListOf(priest)

        val comp = NecroSummonerComponent(
            priestZombieId = 1,
            timeSinceLastSummonMs = 4000L,
            cooldownMs = 5000L,
            maxSkeletons = 3
        )
        var components = listOf(comp)

        // Tick before cooldown
        components = NecroSummonerEngine.tick(deltaMs = 500L, components = components, zombies = zombies)
        assertEquals(1, zombies.size) // Only priest
        assertEquals(4500L, components[0].timeSinceLastSummonMs)
        assertEquals(0, components[0].activeSkeletons.size)

        // Tick after cooldown
        components = NecroSummonerEngine.tick(deltaMs = 600L, components = components, zombies = zombies)
        assertEquals(2, zombies.size) // Priest + 1 skeleton
        assertEquals(0L, components[0].timeSinceLastSummonMs)
        assertEquals(1, components[0].activeSkeletons.size)

        val skeleton = zombies.find { it.id != 1 }!!
        assertEquals(ZombieArchetype.SHAMBLER, skeleton.archetype)
        assertEquals(15, skeleton.hp)
    }

    @Test
    fun testPriestDiscardedWhenEliminated() {
        val priest = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.ABYSSAL_WEAVER,
            x = 100f,
            y = 100f,
            hp = 0, // Eliminated
            maxHp = 100
        )
        val zombies = mutableListOf(priest)

        val comp = NecroSummonerComponent(
            priestZombieId = 1,
            cooldownMs = 5000L,
            maxSkeletons = 3
        )
        var components = listOf(comp)

        components = NecroSummonerEngine.tick(deltaMs = 1000L, components = components, zombies = zombies)
        assertTrue(components.isEmpty())
        assertEquals(1, zombies.size) // Still just the dead priest in the list
    }

    @Test
    fun testMaxSkeletonsCap() {
        val priest = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.ABYSSAL_WEAVER,
            x = 100f,
            y = 100f,
            hp = 100,
            maxHp = 100
        )
        val zombies = mutableListOf(priest)

        val comp = NecroSummonerComponent(
            priestZombieId = 1,
            timeSinceLastSummonMs = 5000L, // Ready to summon
            cooldownMs = 5000L,
            maxSkeletons = 2 // Cap at 2
        )
        var components = listOf(comp)

        // Summon 1
        components = NecroSummonerEngine.tick(deltaMs = 100L, components = components, zombies = zombies)
        assertEquals(2, zombies.size)
        assertEquals(1, components[0].activeSkeletons.size)

        // Fast forward time
        components[0].timeSinceLastSummonMs = 5000L

        // Summon 2
        components = NecroSummonerEngine.tick(deltaMs = 100L, components = components, zombies = zombies)
        assertEquals(3, zombies.size)
        assertEquals(2, components[0].activeSkeletons.size)

        // Fast forward time again
        components[0].timeSinceLastSummonMs = 5000L

        // Attempt Summon 3 (should fail due to cap)
        components = NecroSummonerEngine.tick(deltaMs = 100L, components = components, zombies = zombies)
        assertEquals(3, zombies.size) // No new skeleton
        assertEquals(2, components[0].activeSkeletons.size)

        // Kill one skeleton
        val firstSkeleton = zombies.find { it.id == components[0].activeSkeletons[0] }!!
        firstSkeleton.hp = 0

        // Fast forward time and try again
        components[0].timeSinceLastSummonMs = 5000L
        components = NecroSummonerEngine.tick(deltaMs = 100L, components = components, zombies = zombies)

        // Should spawn a new one now since one died
        assertEquals(4, zombies.size) // Priest + 2 dead/active skeletons + new one
        assertEquals(2, components[0].activeSkeletons.size)
    }
}
