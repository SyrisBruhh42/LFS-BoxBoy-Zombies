package com.example.game.loot.borderlands.catalog

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class JakobsAbyssManufacturerTraitTest {

    @Test
    fun testStandardAttack_noCrit() {
        val enemies = listOf(JakobsEnemy(1, 0f, 0f, 100))
        val state = JakobsAttackState(10, false, 1, 50f)
        val result = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        assertEquals(10, result.finalDamage)
        assertFalse(result.wasLethal)
        assertFalse(result.ricochetResult.didRicochet)
        assertEquals(90, result.updatedEnemies[0].hp)
    }

    @Test
    fun testCriticalAttack_nonLethal() {
        val enemies = listOf(JakobsEnemy(1, 0f, 0f, 100))
        val state = JakobsAttackState(10, true, 1, 50f)
        val result = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        // Base 10 * (2.0 + 0.45) = 24.5 -> 24
        assertEquals(24, result.finalDamage)
        assertFalse(result.wasLethal)
        assertFalse(result.ricochetResult.didRicochet)
        assertEquals(76, result.updatedEnemies[0].hp)
    }

    @Test
    fun testCriticalAttack_lethal_noOtherEnemies() {
        val enemies = listOf(JakobsEnemy(1, 0f, 0f, 20))
        val state = JakobsAttackState(10, true, 1, 50f)
        val result = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        assertEquals(24, result.finalDamage)
        assertTrue(result.wasLethal)
        assertFalse(result.ricochetResult.didRicochet)
        assertEquals(-4, result.updatedEnemies[0].hp)
    }

    @Test
    fun testCriticalAttack_lethal_withRicochet() {
        val enemies = listOf(
            JakobsEnemy(1, 0f, 0f, 20),
            JakobsEnemy(2, 10f, 0f, 50)
        )
        val state = JakobsAttackState(10, true, 1, 50f)
        val result = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        assertEquals(24, result.finalDamage)
        assertTrue(result.wasLethal)
        assertTrue(result.ricochetResult.didRicochet)
        assertEquals(2, result.ricochetResult.ricochetTargetId)
        assertEquals(24, result.ricochetResult.ricochetDamage)

        val target1 = result.updatedEnemies.find { it.id == 1 }!!
        val target2 = result.updatedEnemies.find { it.id == 2 }!!
        assertEquals(-4, target1.hp)
        assertEquals(26, target2.hp) // 50 - 24
    }

    @Test
    fun testRicochet_selectsClosestTarget() {
        val enemies = listOf(
            JakobsEnemy(1, 0f, 0f, 20),
            JakobsEnemy(2, 30f, 0f, 50),
            JakobsEnemy(3, 10f, 0f, 50) // Closer
        )
        val state = JakobsAttackState(10, true, 1, 50f)
        val result = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        assertTrue(result.ricochetResult.didRicochet)
        assertEquals(3, result.ricochetResult.ricochetTargetId)
    }

    @Test
    fun testRicochet_outOfRange() {
        val enemies = listOf(
            JakobsEnemy(1, 0f, 0f, 20),
            JakobsEnemy(2, 60f, 0f, 50)
        )
        val state = JakobsAttackState(10, true, 1, 50f)
        val result = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        assertTrue(result.wasLethal)
        assertFalse(result.ricochetResult.didRicochet)
    }

    @Test
    fun testRicochet_ignoresDeadTargets() {
        val enemies = listOf(
            JakobsEnemy(1, 0f, 0f, 20),
            JakobsEnemy(2, 10f, 0f, 0), // Dead
            JakobsEnemy(3, 20f, 0f, 50) // Alive
        )
        val state = JakobsAttackState(10, true, 1, 50f)
        val result = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        assertTrue(result.ricochetResult.didRicochet)
        assertEquals(3, result.ricochetResult.ricochetTargetId)
    }

    @Test
    fun testAttack_invalidTargetId() {
        val enemies = listOf(JakobsEnemy(1, 0f, 0f, 100))
        val state = JakobsAttackState(10, true, 999, 50f)
        val result = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        assertEquals(24, result.finalDamage)
        assertFalse(result.wasLethal)
        assertFalse(result.ricochetResult.didRicochet)
        assertEquals(100, result.updatedEnemies[0].hp) // No damage dealt
    }

    @Test
    fun testNegativeBaseDamage_degradation() {
        val enemies = listOf(JakobsEnemy(1, 0f, 0f, 100))
        val state = JakobsAttackState(-10, false, 1, 50f)
        val result = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        assertEquals(0, result.finalDamage)
        assertEquals(100, result.updatedEnemies[0].hp)
    }

    @Test
    fun testNaNAndInfinity_degradation() {
        val enemies = listOf(
            JakobsEnemy(1, Float.NaN, Float.POSITIVE_INFINITY, 20),
            JakobsEnemy(2, 10f, 10f, 50)
        )
        val state = JakobsAttackState(10, true, 1, Float.NaN)
        val result = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        assertEquals(24, result.finalDamage)
        assertTrue(result.wasLethal)
        // ricochetRadius NaN defaults to 0, so no ricochet
        assertFalse(result.ricochetResult.didRicochet)
    }

    @Test
    fun testSerializationRoundtrips() {
        val enemy = JakobsEnemy(1, 10f, 20f, 100)
        val enemyMap = enemy.toMap()
        val restoredEnemy = JakobsEnemy.fromMap(enemyMap)
        assertEquals(enemy, restoredEnemy)

        val state = JakobsAttackState(50, true, 2, 100f)
        val stateMap = state.toMap()
        val restoredState = JakobsAttackState.fromMap(stateMap)
        assertEquals(state, restoredState)

        val ricochet = JakobsRicochetResult(true, 3, 40)
        val ricochetMap = ricochet.toMap()
        val restoredRicochet = JakobsRicochetResult.fromMap(ricochetMap)
        assertEquals(ricochet, restoredRicochet)

        val result = JakobsAttackResult(120, true, ricochet, listOf(enemy))
        val resultMap = result.toMap()
        val restoredResult = JakobsAttackResult.fromMap(resultMap)
        assertEquals(result, restoredResult)
    }

    @Test
    fun testDeterminismAndStress() {
        val rng = Random(42)
        val enemies = mutableListOf<JakobsEnemy>()
        for (i in 0 until 1000) {
            enemies.add(JakobsEnemy(i, rng.nextFloat() * 1000f, rng.nextFloat() * 1000f, rng.nextInt(10, 30)))
        }

        val state = JakobsAttackState(10, true, 500, 200f) // Targeting enemy 500
        val result1 = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        // Run again with exact same inputs
        val result2 = JakobsAbyssManufacturerTraitEngine.processAttack(state, enemies)

        assertEquals(result1.finalDamage, result2.finalDamage)
        assertEquals(result1.wasLethal, result2.wasLethal)
        assertEquals(result1.ricochetResult.didRicochet, result2.ricochetResult.didRicochet)
        assertEquals(result1.ricochetResult.ricochetTargetId, result2.ricochetResult.ricochetTargetId)

        // Final sanity check
        assertTrue(result1.wasLethal)
    }
}
