package com.example.game.economy.vendors

import com.example.game.ZombieEntity
import com.example.game.ZombieArchetype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class PlaceableCryoProximityMineTest {

    private fun createZombie(id: Int, x: Float, y: Float, hp: Int = 500): ZombieEntity {
        return ZombieEntity(id = id, archetype = ZombieArchetype.SHAMBLER, x = x, y = y, hp = hp, maxHp = hp)
    }

    private fun getExpectedDamage(baseDamage: Int, seed: Long, mineId: Int): Int {
        val hash = (seed xor mineId.toLong()) * 0x517CC1B727220A95L
        val randomDamageMod = ((hash ushr 32).toInt() and 0xFF) / 255.0f
        return (baseDamage * (0.9f + (randomDamageMod * 0.2f))).roundToInt()
    }

    @Test
    fun testMineDoesNotDetonateIfZombiesOutsideTriggerRadius() {
        val mine = PlaceableCryoProximityMineState(id = 1, x = 100f, y = 100f, triggerRadiusPx = 50f)
        val zombies = listOf(
            createZombie(id = 10, x = 200f, y = 200f),
            createZombie(id = 11, x = 0f, y = 0f)
        )
        val result = CryoMineDetonationResult()

        PlaceableCryoProximityMine.tickMine(mine, zombies, dtSeconds = 0.016f, seed = 123L, outResult = result)
        assertTrue(mine.isActive)
        assertFalse(result.detonated)
        assertEquals(0, result.hitCount)
    }

    @Test
    fun testMineDetonatesAndHitsWithinBlastRadius() {
        val mine = PlaceableCryoProximityMineState(id = 2, x = 100f, y = 100f, triggerRadiusPx = 50f, blastRadiusPx = 100f, baseDamage = 200, freezeDurationMs = 3000L)
        val zombies = listOf(
            createZombie(id = 20, x = 120f, y = 120f), // Inside trigger
            createZombie(id = 21, x = 180f, y = 100f), // Outside trigger, inside blast
            createZombie(id = 22, x = 300f, y = 300f)  // Outside blast
        )

        val hpBefore20 = zombies[0].hp
        val hpBefore21 = zombies[1].hp
        val hpBefore22 = zombies[2].hp

        val result = CryoMineDetonationResult()
        PlaceableCryoProximityMine.tickMine(mine, zombies, 0.016f, 456L, result)

        assertFalse(mine.isActive)
        assertTrue(result.detonated)
        assertEquals(2, result.hitCount)
        val hits = result.hitZombies.take(2).toList()
        assertTrue(hits.containsAll(listOf(20, 21)))

        val expectedDamage = getExpectedDamage(200, 456L, 2)
        assertEquals(hpBefore20 - expectedDamage, zombies[0].hp)
        assertEquals(hpBefore21 - expectedDamage, zombies[1].hp)
        assertEquals(hpBefore22, zombies[2].hp)
        assertEquals(3000L, zombies[0].attackCooldownMs)
        assertEquals(3000L, zombies[1].attackCooldownMs)
    }

    @Test
    fun testInactiveMineDoesNotDetonate() {
        val mine = PlaceableCryoProximityMineState(id = 3, x = 100f, y = 100f, isActive = false)
        val zombies = listOf(
            createZombie(id = 30, x = 100f, y = 100f)
        )

        val result = CryoMineDetonationResult()
        PlaceableCryoProximityMine.tickMine(mine, zombies, 0.016f, 123L, result)
        assertFalse(mine.isActive)
        assertFalse(result.detonated)
    }

    @Test
    fun testSerializationRoundTrip() {
        val mine = PlaceableCryoProximityMineState(
            id = 42,
            x = -15.5f,
            y = 120.2f,
            triggerRadiusPx = 60f,
            blastRadiusPx = 200f,
            baseDamage = 450,
            freezeDurationMs = 5000L,
            isActive = false
        )

        val map = mine.toMap()
        val deserialized = PlaceableCryoProximityMineState.fromMap(map)

        assertEquals(mine, deserialized)
    }

    @Test
    fun testDeadZombiesDoNotTriggerMine() {
        val mine = PlaceableCryoProximityMineState(id = 5, x = 100f, y = 100f, triggerRadiusPx = 50f)
        val zombies = listOf(
            createZombie(id = 50, x = 100f, y = 100f, hp = 0),
            createZombie(id = 51, x = 100f, y = 100f, hp = -10)
        )

        val result = CryoMineDetonationResult()
        PlaceableCryoProximityMine.tickMine(mine, zombies, 0.016f, 123L, result)
        assertTrue(mine.isActive)
        assertFalse(result.detonated)
    }

    @Test
    fun testMineDetonatesButHitsNothingElse() {
        val mine = PlaceableCryoProximityMineState(id = 6, x = 100f, y = 100f, triggerRadiusPx = 50f, blastRadiusPx = 60f, baseDamage = 100)
        val zombies = listOf(
            createZombie(id = 60, x = 110f, y = 100f)
        )

        val hpBefore = zombies[0].hp
        val result = CryoMineDetonationResult()
        PlaceableCryoProximityMine.tickMine(mine, zombies, 0.016f, 789L, result)

        assertFalse(mine.isActive)
        assertTrue(result.detonated)
        assertEquals(1, result.hitCount)
        assertEquals(60, result.hitZombies[0])
        val expectedDamage = getExpectedDamage(100, 789L, 6)
        assertEquals(hpBefore - expectedDamage, zombies[0].hp)
    }

    @Test
    fun testDeterminismSameInputSameOutput() {
        val mine1 = PlaceableCryoProximityMineState(id = 7, x = 50f, y = 50f)
        val mine2 = mine1.copy()

        val zombies1 = listOf(createZombie(1, 60f, 60f))
        val zombies2 = listOf(createZombie(1, 60f, 60f))

        val result1 = CryoMineDetonationResult()
        val result2 = CryoMineDetonationResult()
        PlaceableCryoProximityMine.tickMine(mine1, zombies1, 0.016f, 999L, result1)
        PlaceableCryoProximityMine.tickMine(mine2, zombies2, 0.016f, 999L, result2)

        assertEquals(mine1, mine2)
        assertEquals(result1.detonated, result2.detonated)
        assertEquals(result1.hitCount, result2.hitCount)
        assertEquals(zombies1[0].hp, zombies2[0].hp)
    }

    @Test
    fun testInfinityNanHandling() {
        val mine = PlaceableCryoProximityMineState(id = 8, x = 0f, y = 0f)
        val zombies = listOf(
            createZombie(1, Float.NaN, Float.NaN),
            createZombie(2, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            createZombie(3, 10f, 10f)
        )

        val result = CryoMineDetonationResult()
        PlaceableCryoProximityMine.tickMine(mine, zombies, 0.016f, 123L, result)
        assertTrue(result.detonated)
        assertEquals(1, result.hitCount)
        assertEquals(3, result.hitZombies[0])
    }

    @Test
    fun testLargeZombieCountStressTest() {
        val mine = PlaceableCryoProximityMineState(id = 9, x = 0f, y = 0f, blastRadiusPx = 100f)
        val zombies = (1..300).map { i ->
            createZombie(i, x = (i % 10).toFloat(), y = (i % 10).toFloat())
        }

        val result = CryoMineDetonationResult(hitZombies = IntArray(300))
        PlaceableCryoProximityMine.tickMine(mine, zombies, 0.016f, 123L, result)
        assertTrue(result.detonated)
        assertEquals(300, result.hitCount)
    }

    @Test
    fun testMultipleMinesSameZombies() {
         val mine1 = PlaceableCryoProximityMineState(id = 10, x = 10f, y = 10f, blastRadiusPx = 20f, baseDamage = 100)
         val mine2 = PlaceableCryoProximityMineState(id = 11, x = 200f, y = 200f, blastRadiusPx = 20f, baseDamage = 100)

         val zombies = listOf(
             createZombie(1, 15f, 15f), // hits mine 1
             createZombie(2, 205f, 205f) // hits mine 2
         )

         val hp1Before = zombies[0].hp
         val hp2Before = zombies[1].hp

         val result1 = CryoMineDetonationResult()
         val result2 = CryoMineDetonationResult()
         PlaceableCryoProximityMine.tickMine(mine1, zombies, 0.016f, 123L, result1)
         PlaceableCryoProximityMine.tickMine(mine2, zombies, 0.016f, 456L, result2)

         assertTrue(result1.detonated)
         assertTrue(result2.detonated)

         val dmg1 = getExpectedDamage(100, 123L, 10)
         val dmg2 = getExpectedDamage(100, 456L, 11)

         assertEquals(hp1Before - dmg1, zombies[0].hp)
         assertEquals(hp2Before - dmg2, zombies[1].hp)
    }

    @Test
    fun testDeadZombiesInBlastRadiusNotHit() {
        val mine = PlaceableCryoProximityMineState(id = 12, x = 0f, y = 0f, triggerRadiusPx = 20f, blastRadiusPx = 50f)
        val zombies = listOf(
            createZombie(1, 5f, 5f),
            createZombie(2, 10f, 10f, hp = 0)
        )

        val result = CryoMineDetonationResult()
        PlaceableCryoProximityMine.tickMine(mine, zombies, 0.016f, 123L, result)
        assertTrue(result.detonated)
        assertEquals(1, result.hitCount)
        assertEquals(1, result.hitZombies[0])
    }

    @Test
    fun testExtremeCoordinates() {
        val mine = PlaceableCryoProximityMineState(id = 13, x = 1e6f, y = 1e6f, triggerRadiusPx = 50f)
        val zombies = listOf(
            createZombie(1, 1e6f + 10f, 1e6f + 10f)
        )
        val result = CryoMineDetonationResult()
        PlaceableCryoProximityMine.tickMine(mine, zombies, 0.016f, 123L, result)
        assertTrue(result.detonated)
    }

    @Test
    fun testNoZombiesPresent() {
        val mine = PlaceableCryoProximityMineState(id = 14, x = 0f, y = 0f)
        val zombies = emptyList<ZombieEntity>()

        val result = CryoMineDetonationResult()
        PlaceableCryoProximityMine.tickMine(mine, zombies, 0.016f, 123L, result)
        assertTrue(mine.isActive)
        assertFalse(result.detonated)
        assertEquals(0, result.hitCount)
    }

}
