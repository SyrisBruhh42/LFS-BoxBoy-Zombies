package com.example.game.combat.modules

import com.example.game.combat.BarrelNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryoBarrelFreezeNovaTest {

    @Test
    fun testCryoNovaDetonation() {
        val barrel = BarrelNode(id = 1, x = 0f, y = 0f, isCryo = true, blastRadiusPx = 100f)
        val enemies = listOf(
            EnemyNode(id = 10, x = 10f, y = 10f), // Brittle & Slowed
            EnemyNode(id = 11, x = 70f, y = 70f), // Slowed (dist ~99)
            EnemyNode(id = 12, x = 200f, y = 200f) // Unaffected
        )

        val result = CryoBarrelFreezeNova.detonate(barrel, enemies)

        assertEquals(2, result.affectedEnemyIds.size)
        assertTrue(result.affectedEnemyIds.containsAll(listOf(10, 11)))

        assertEquals(2, result.slowedEnemyIds.size)
        assertTrue(result.slowedEnemyIds.containsAll(listOf(10, 11)))

        assertEquals(1, result.brittleEnemyIds.size)
        assertTrue(result.brittleEnemyIds.contains(10))
    }

    @Test
    fun testNonCryoBarrel() {
        val barrel = BarrelNode(id = 1, x = 0f, y = 0f, isCryo = false, blastRadiusPx = 100f)
        val enemies = listOf(EnemyNode(id = 10, x = 10f, y = 10f))

        val result = CryoBarrelFreezeNova.detonate(barrel, enemies)

        assertEquals(0, result.affectedEnemyIds.size)
    }
}
