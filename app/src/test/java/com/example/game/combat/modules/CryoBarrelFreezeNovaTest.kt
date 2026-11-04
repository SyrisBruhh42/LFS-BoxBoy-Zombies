package com.example.game.combat.modules

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryoBarrelFreezeNovaTest {

    @Test
    fun testDetonationAppliesCryoStatusToZombiesInRadius() {
        val zombies = listOf(
            ZombieEntity(id = 1, archetype = ZombieArchetype.SHAMBLER, x = 100f, y = 100f, hp = 100, maxHp = 100),
            ZombieEntity(id = 2, archetype = ZombieArchetype.SPRINTER, x = 120f, y = 100f, hp = 100, maxHp = 100)
        )

        val result = CryoBarrelFreezeNova.detonateCryoBarrel(
            barrelX = 100f,
            barrelY = 100f,
            blastRadiusPx = 50f,
            zombies = zombies
        )

        assertEquals(2, result.appliedEffects.size)
        
        val effect1 = result.appliedEffects.find { it.zombieId == 1 }
        val effect2 = result.appliedEffects.find { it.zombieId == 2 }

        assertTrue(effect1 != null && effect1.isBrittle && effect1.speedMultiplier == 0.4f)
        assertTrue(effect2 != null && effect2.isBrittle && effect2.speedMultiplier == 0.4f)
    }

    @Test
    fun testDetonationIgnoresZombiesOutsideRadius() {
        val zombies = listOf(
            ZombieEntity(id = 1, archetype = ZombieArchetype.SHAMBLER, x = 100f, y = 100f, hp = 100, maxHp = 100),
            ZombieEntity(id = 2, archetype = ZombieArchetype.SPRINTER, x = 200f, y = 100f, hp = 100, maxHp = 100) // Outside radius
        )

        val result = CryoBarrelFreezeNova.detonateCryoBarrel(
            barrelX = 100f,
            barrelY = 100f,
            blastRadiusPx = 50f,
            zombies = zombies
        )

        assertEquals(1, result.appliedEffects.size)
        
        val effect = result.appliedEffects.first()
        assertEquals(1, effect.zombieId)
        assertTrue(effect.isBrittle)
        assertEquals(0.4f, effect.speedMultiplier)
    }

    @Test
    fun testDetonationEdgeCaseExactlyOnRadius() {
        val zombies = listOf(
            ZombieEntity(id = 1, archetype = ZombieArchetype.SHAMBLER, x = 150f, y = 100f, hp = 100, maxHp = 100)
        )

        val result = CryoBarrelFreezeNova.detonateCryoBarrel(
            barrelX = 100f,
            barrelY = 100f,
            blastRadiusPx = 50f,
            zombies = zombies
        )

        assertEquals(1, result.appliedEffects.size)
        val effect = result.appliedEffects.first()
        assertEquals(1, effect.zombieId)
        assertTrue(effect.isBrittle)
    }
}
