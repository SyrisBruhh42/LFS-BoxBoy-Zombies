package com.example.game.relics.items

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.*
import org.junit.Test

class Relic19gasolineCanisterTest {

    private fun createZombie(id: Int, x: Float, y: Float, hp: Int = 100, maxHp: Int = 100): ZombieEntity {
        return ZombieEntity(
            id = id,
            archetype = ZombieArchetype.SHAMBLER,
            x = x,
            y = y,
            hp = hp,
            maxHp = maxHp
        )
    }

    @Test
    fun testConfigSerializationRoundtrip() {
        val config = Relic19gasolineCanisterConfig(baseRadiusPx = 120f, bonusRadiusPerStackPx = 50f, bonusBurnPerStack = 0.8f)
        val map = config.toMap()
        val restored = Relic19gasolineCanisterConfig.fromMap(map)
        assertEquals(120f, restored.baseRadiusPx)
        assertEquals(50f, restored.bonusRadiusPerStackPx)
        assertEquals(0.8f, restored.bonusBurnPerStack)
    }

    @Test
    fun testConfigSerializationNull() {
        val restored = Relic19gasolineCanisterConfig.fromMap(null)
        assertEquals(100f, restored.baseRadiusPx)
        assertEquals(40f, restored.bonusRadiusPerStackPx)
        assertEquals(0.75f, restored.bonusBurnPerStack)
    }

    @Test
    fun testStateConstraintEnforcement() {
        assertThrows(IllegalArgumentException::class.java) {
            Relic19gasolineCanisterState(stackCount = 0)
        }
        val state = Relic19gasolineCanisterState(stackCount = 3)
        assertEquals(3, state.stackCount)
        assertFalse(state.canBePurchasedAtVending)
    }

    @Test
    fun testStateSerializationRoundtrip() {
        val state = Relic19gasolineCanisterState(stackCount = 5)
        val map = state.toMap()
        val restored = Relic19gasolineCanisterState.fromMap(map)
        assertEquals(5, restored.stackCount)
        assertFalse(restored.canBePurchasedAtVending)
    }

    @Test
    fun testStateSerializationMissingFields() {
        val restored = Relic19gasolineCanisterState.fromMap(emptyMap())
        assertEquals(1, restored.stackCount)
    }

    @Test
    fun testFnv1aChecksumDeterminism() {
        val state1 = Relic19gasolineCanisterState(stackCount = 1)
        val state2 = Relic19gasolineCanisterState(stackCount = 1)
        val state3 = Relic19gasolineCanisterState(stackCount = 2)

        assertEquals(state1.computeChecksum(), state2.computeChecksum())
        assertNotEquals(state1.computeChecksum(), state3.computeChecksum())
    }

    @Test
    fun testSpatialIgnitionBounds() {
        val engine = Relic19gasolineCanisterEngine()
        val state = Relic19gasolineCanisterState(stackCount = 1) // radius = 100 + 40 = 140

        val zInside1 = createZombie(1, 100f, 0f) // Dist 100, inside
        val zInside2 = createZombie(2, 140f, 0f) // Dist 140, boundary
        val zOutside = createZombie(3, 141f, 0f) // Dist 141, outside

        engine.registerKill(
            killedX = 0f,
            killedY = 0f,
            killedMaxHp = 100,
            state = state,
            zombies = listOf(zInside1, zInside2, zOutside)
        )

        assertEquals(2, engine.getActiveBurnCount())
    }

    @Test
    fun testStackScalingLogic() {
        val engine = Relic19gasolineCanisterEngine()
        val stateHighStack = Relic19gasolineCanisterState(stackCount = 10) // radius = 100 + 400 = 500

        val zFar = createZombie(1, 450f, 0f) // Dist 450, inside higher stack radius

        engine.registerKill(
            killedX = 0f,
            killedY = 0f,
            killedMaxHp = 100,
            state = stateHighStack,
            zombies = listOf(zFar)
        )

        assertEquals(1, engine.getActiveBurnCount())
    }

    @Test
    fun testDtClampingAndTick() {
        val engine = Relic19gasolineCanisterEngine()
        val state = Relic19gasolineCanisterState(stackCount = 1)
        val z = createZombie(1, 10f, 0f, hp = 1000)

        engine.registerKill(killedX = 0f, killedY = 0f, killedMaxHp = 100, state = state, zombies = listOf(z))
        assertEquals(1, engine.getActiveBurnCount())

        // Pass abnormally high dt, should be clamped to 0.100s
        engine.tick(dtSeconds = 10.0f, zombies = listOf(z))

        // 100 * 0.5 * (1 + 0.75) = 87.5 dps -> 87 dps
        // 87 * 0.100 = 8.7 -> 8 damage
        assertTrue(z.hp < 1000)
        assertTrue(z.hp > 900) // Shouldn't take 10s of damage
    }

    @Test
    fun testGracefulNanRecovery() {
        val engine = Relic19gasolineCanisterEngine()
        val state = Relic19gasolineCanisterState(stackCount = 1)

        val zNan = createZombie(1, Float.NaN, 0f)
        val zValid = createZombie(2, 10f, 0f)

        engine.registerKill(
            killedX = Float.NaN,
            killedY = 0f,
            killedMaxHp = 100,
            state = state,
            zombies = listOf(zNan, zValid)
        )

        assertEquals(0, engine.getActiveBurnCount()) // Should reject NaN coords

        engine.registerKill(
            killedX = 0f,
            killedY = 0f,
            killedMaxHp = 100,
            state = state,
            zombies = listOf(zNan, zValid)
        )

        assertEquals(1, engine.getActiveBurnCount()) // Should only ignite valid
    }

    @Test
    fun testMaxCapacityStress() {
        val engine = Relic19gasolineCanisterEngine(maxIgnitedTargets = 10)
        val state = Relic19gasolineCanisterState(stackCount = 1)

        val zombies = (1..15).map { createZombie(it, 10f, 0f) }

        engine.registerKill(
            killedX = 0f,
            killedY = 0f,
            killedMaxHp = 100,
            state = state,
            zombies = zombies
        )

        assertEquals(10, engine.getActiveBurnCount()) // Should be capped at 10
    }

    @Test
    fun testBurnDurationExpirationAndSwapPop() {
        val engine = Relic19gasolineCanisterEngine()
        val state = Relic19gasolineCanisterState(stackCount = 1)

        val z1 = createZombie(1, 10f, 0f, hp = 100)
        val z2 = createZombie(2, 10f, 0f, hp = 100)
        val z3 = createZombie(3, 10f, 0f, hp = 100)

        engine.registerKill(killedX = 0f, killedY = 0f, killedMaxHp = 100, state = state, zombies = listOf(z1, z2, z3))
        assertEquals(3, engine.getActiveBurnCount())

        // Tick past 3000ms duration (using 0.1s steps to respect clamping)
        for (i in 0 until 35) {
            engine.tick(0.1f, listOf(z1, z2, z3))
        }

        assertEquals(0, engine.getActiveBurnCount()) // All should be removed via swap and pop
    }

    @Test
    fun testDeadZombieBurnRemoval() {
        val engine = Relic19gasolineCanisterEngine()
        val state = Relic19gasolineCanisterState(stackCount = 1)

        val z1 = createZombie(1, 10f, 0f, hp = 1)

        engine.registerKill(killedX = 0f, killedY = 0f, killedMaxHp = 100, state = state, zombies = listOf(z1))
        assertEquals(1, engine.getActiveBurnCount())

        engine.tick(0.1f, listOf(z1)) // Should kill z1
        assertTrue(z1.hp <= 0) // Ensure it's dead or <= 0

        engine.tick(0.1f, listOf(z1)) // Next tick should remove dead zombie burn
        assertEquals(0, engine.getActiveBurnCount())
    }

    @Test
    fun testIgniteExistingRefreshesDuration() {
        val engine = Relic19gasolineCanisterEngine()
        val state = Relic19gasolineCanisterState(stackCount = 1)

        val z = createZombie(1, 10f, 0f)

        engine.registerKill(killedX = 0f, killedY = 0f, killedMaxHp = 100, state = state, zombies = listOf(z))

        for (i in 0 until 15) {
            engine.tick(0.1f, listOf(z)) // Advance 1.5s
        }

        engine.registerKill(killedX = 0f, killedY = 0f, killedMaxHp = 100, state = state, zombies = listOf(z))
        assertEquals(1, engine.getActiveBurnCount()) // Still 1, just refreshed

        // Should survive another 3s (30 ticks)
        for (i in 0 until 25) {
            engine.tick(0.1f, listOf(z))
        }

        assertEquals(1, engine.getActiveBurnCount()) // Should still be active due to refresh
    }
}
