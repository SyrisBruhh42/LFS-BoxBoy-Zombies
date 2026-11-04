package com.example.game.combat.modules

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import com.example.game.combat.BarrelNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolatileBoomerZombieTest {

    @Test
    fun `test boomer explosion kills nearby zombies`() {
        val boomer = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.SHAMBLER, // Or any suitable archetype
            x = 100f,
            y = 100f,
            hp = 100,
            maxHp = 100
        )

        val targetZombie1 = ZombieEntity(
            id = 2,
            archetype = ZombieArchetype.SHAMBLER,
            x = 150f, // Distance 50 <= 185
            y = 100f,
            hp = 300,
            maxHp = 300
        )
        
        val targetZombie2 = ZombieEntity(
            id = 3,
            archetype = ZombieArchetype.SHAMBLER,
            x = 400f, // Distance 300 > 185
            y = 100f,
            hp = 300,
            maxHp = 300
        )

        val zombies = listOf(boomer, targetZombie1, targetZombie2)
        val barrels = emptyList<BarrelNode>()

        val result = VolatileBoomerZombie.triggerDeathExplosion(
            boomer = boomer,
            playerX = 1000f,
            playerY = 1000f,
            zombies = zombies,
            barrels = barrels
        )

        assertEquals(1, result.zombiesKilled)
        assertEquals(350, result.totalZombieDamage)
        assertEquals(0, result.playerSelfDamage)
        
        val survivingTarget1 = result.survivingZombies.find { it.id == 2 }
        val survivingTarget2 = result.survivingZombies.find { it.id == 3 }
        
        assertTrue(survivingTarget1 == null) // Dead because hp was 300, took 350 damage
        assertEquals(300, survivingTarget2?.hp) // Took no damage
    }

    @Test
    fun `test boomer explosion triggers chain reaction with barrels`() {
        val boomer = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.SHAMBLER,
            x = 100f,
            y = 100f,
            hp = 100,
            maxHp = 100
        )

        val barrel1 = BarrelNode(
            id = 101,
            x = 200f, // Distance 100 <= 185 (Boomer range)
            y = 100f,
            blastRadiusPx = 145f
        )
        
        val barrel2 = BarrelNode(
            id = 102,
            x = 300f, // Distance 100 from barrel1 <= 145 (Barrel1 range)
            y = 100f,
            blastRadiusPx = 145f
        )
        
        val targetZombie = ZombieEntity(
            id = 2,
            archetype = ZombieArchetype.SHAMBLER,
            x = 400f, // Distance 100 from barrel2 <= 145 (Barrel2 range), Distance 300 from boomer > 185
            y = 100f,
            hp = 200, // barrel damage is 240
            maxHp = 200
        )

        val zombies = listOf(boomer, targetZombie)
        val barrels = listOf(barrel1, barrel2)

        val result = VolatileBoomerZombie.triggerDeathExplosion(
            boomer = boomer,
            playerX = 1000f,
            playerY = 1000f,
            zombies = zombies,
            barrels = barrels
        )

        assertEquals(listOf(101, 102), result.detonatedBarrelIds.sorted())
        assertEquals(2, result.chainDepth) // At least 2
        assertEquals(1, result.zombiesKilled)
        assertEquals(240, result.totalZombieDamage)
        
        val survivingTarget = result.survivingZombies.find { it.id == 2 }
        assertTrue(survivingTarget == null)
    }

    @Test
    fun `test boomer explosion damages player if in range`() {
        val boomer = ZombieEntity(
            id = 1,
            archetype = ZombieArchetype.SHAMBLER,
            x = 100f,
            y = 100f,
            hp = 100,
            maxHp = 100
        )

        val result = VolatileBoomerZombie.triggerDeathExplosion(
            boomer = boomer,
            playerX = 250f, // Distance 150 <= 185
            playerY = 100f,
            zombies = listOf(boomer),
            barrels = emptyList()
        )

        assertEquals(350, result.playerSelfDamage)
    }
}
