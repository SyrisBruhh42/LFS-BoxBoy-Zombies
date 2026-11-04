package com.example.game.loot.borderlands.catalog

import com.example.game.ZombieArchetype
import com.example.game.ZombieEntity
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class NovaBlastShieldGeneratorTest {

    @Test
    fun testNominalNovaTrigger() {
        val config = NovaShieldConfig(
            novaRadiusPx = 150f,
            novaBaseDamage = 120,
            isCryoNova = false,
            rechargeDelaySeconds = 3.0f,
            rechargeRatePerSecond = 15f,
            maxCapacity = 100f
        )
        val state = NovaShieldState(currentCapacity = 50f, isFullyRecharged = true)

        val zombies = listOf(
            ZombieEntity(1, ZombieArchetype.SHAMBLER, 100f, 100f, 200, 200), // Within range
            ZombieEntity(2, ZombieArchetype.SHAMBLER, 300f, 300f, 200, 200)  // Out of range
        )

        val result = NovaBlastShieldGenerator.processDamage(
            damageAmount = 60f,
            config = config,
            state = state,
            playerX = 100f,
            playerY = 100f,
            zombies = zombies
        )

        assertTrue(result.triggered)
        assertEquals(1, result.hitCount)
        assertEquals(80, zombies[0].hp) // 200 - 120
        assertEquals(200, zombies[1].hp)
        assertEquals(0f, state.currentCapacity)
        assertTrue(state.hasNovaTriggered)
        assertFalse(state.isFullyRecharged)
        assertEquals(0f, state.timeSinceLastDamage)
    }

    @Test
    fun testNoNovaOnPartialDepletion() {
        val config = NovaShieldConfig(maxCapacity = 100f)
        val state = NovaShieldState(currentCapacity = 100f, isFullyRecharged = true)

        val result = NovaBlastShieldGenerator.processDamage(
            damageAmount = 50f,
            config = config,
            state = state,
            playerX = 100f,
            playerY = 100f,
            zombies = emptyList()
        )

        assertFalse(result.triggered)
        assertEquals(50f, state.currentCapacity)
        assertFalse(state.hasNovaTriggered)
        assertFalse(state.isFullyRecharged)
    }

    @Test
    fun testNoNovaIfAlreadyTriggered() {
        val config = NovaShieldConfig()
        val state = NovaShieldState(currentCapacity = 50f, isFullyRecharged = false, hasNovaTriggered = true)

        val result = NovaBlastShieldGenerator.processDamage(
            damageAmount = 60f,
            config = config,
            state = state,
            playerX = 100f,
            playerY = 100f,
            zombies = emptyList()
        )

        assertFalse(result.triggered)
        assertEquals(0f, state.currentCapacity)
    }

    @Test
    fun testRechargeDelay() {
        val config = NovaShieldConfig(rechargeDelaySeconds = 3f, rechargeRatePerSecond = 10f, maxCapacity = 100f)
        val state = NovaShieldState(currentCapacity = 0f, timeSinceLastDamage = 0f, isFullyRecharged = false, hasNovaTriggered = true)

        // Step before delay
        for (i in 1..20) {
            NovaBlastShieldGenerator.stepRecharge(0.1f, config, state) // total 2.0s
        }
        assertEquals(0f, state.currentCapacity)

        // Step past delay (needs 1.0s more to hit 3.0s, then we step an extra 0.5s)
        for (i in 1..15) {
            NovaBlastShieldGenerator.stepRecharge(0.1f, config, state) // total 1.5s
        }

        // When timeSinceLastDamage reaches 3.0s (after 10 loops), the remaining 5 loops (0.5s)
        // will add capacity. 5 loops * 0.1s dt = 0.5s of recharge.
        // 0.5s * 10f rate = 5f capacity
        assertEquals(5f, state.currentCapacity, 0.01f)
        assertFalse(state.isFullyRecharged)
    }

    @Test
    fun testFullRechargeResetsNova() {
        val config = NovaShieldConfig(rechargeDelaySeconds = 1f, rechargeRatePerSecond = 50f, maxCapacity = 100f)
        val state = NovaShieldState(currentCapacity = 90f, timeSinceLastDamage = 2f, isFullyRecharged = false, hasNovaTriggered = true)

        for (i in 1..5) {
            NovaBlastShieldGenerator.stepRecharge(0.1f, config, state) // total 0.5s
        }

        // 5 loops * 0.1s = 0.5s -> 0.5s * 50f rate = 25f added.
        // 90f + 25f = 115f, which should be clamped to 100f
        assertEquals(100f, state.currentCapacity)
        assertTrue(state.isFullyRecharged)
        assertFalse(state.hasNovaTriggered)
    }

    @Test
    fun testDeterminism() {
        val config = NovaShieldConfig(novaRadiusPx = 200f, novaBaseDamage = 150)

        fun runScenario(): NovaBlastResult {
            val state = NovaShieldState(currentCapacity = 50f, isFullyRecharged = true)
            val zombies = listOf(ZombieEntity(1, ZombieArchetype.SHAMBLER, 50f, 50f, 200, 200))
            return NovaBlastShieldGenerator.processDamage(100f, config, state, 0f, 0f, zombies)
        }

        val res1 = runScenario()
        val res2 = runScenario()

        assertEquals(res1, res2)
    }

    @Test
    fun testNegativeDamageRecovery() {
        val config = NovaShieldConfig()
        val state = NovaShieldState(currentCapacity = 100f, isFullyRecharged = true)

        val result = NovaBlastShieldGenerator.processDamage(
            damageAmount = -10f,
            config = config,
            state = state,
            playerX = 0f,
            playerY = 0f,
            zombies = emptyList()
        )

        assertFalse(result.triggered)
        assertEquals(100f, state.currentCapacity)
    }

    @Test
    fun testNanDamageRecovery() {
        val config = NovaShieldConfig()
        val state = NovaShieldState(currentCapacity = 100f, isFullyRecharged = true)

        val result = NovaBlastShieldGenerator.processDamage(
            damageAmount = Float.NaN,
            config = config,
            state = state,
            playerX = 0f,
            playerY = 0f,
            zombies = emptyList()
        )

        assertFalse(result.triggered)
        assertEquals(100f, state.currentCapacity)
    }

    @Test
    fun testNanDtRecovery() {
        val config = NovaShieldConfig(maxCapacity = 100f)
        val state = NovaShieldState(currentCapacity = 50f, timeSinceLastDamage = 0f)

        NovaBlastShieldGenerator.stepRecharge(Float.NaN, config, state)
        assertEquals(50f, state.currentCapacity)
        assertEquals(0f, state.timeSinceLastDamage)
    }

    @Test
    fun testDtClamping() {
        val config = NovaShieldConfig(maxCapacity = 100f, rechargeDelaySeconds = 0f, rechargeRatePerSecond = 10f)
        val state = NovaShieldState(currentCapacity = 50f, timeSinceLastDamage = 0f)

        // Test lower bound (0.001f)
        NovaBlastShieldGenerator.stepRecharge(0.0001f, config, state)
        assertEquals(50.01f, state.currentCapacity, 0.0001f)

        // Reset
        state.currentCapacity = 50f

        // Test upper bound (0.100f)
        NovaBlastShieldGenerator.stepRecharge(1.0f, config, state)
        assertEquals(51f, state.currentCapacity, 0.001f) // 10f * 0.1f = 1f
    }

    @Test
    fun testMaxCapacityStress() {
        val config = NovaShieldConfig(novaRadiusPx = 1000f, novaBaseDamage = 10)
        val state = NovaShieldState(currentCapacity = 10f, isFullyRecharged = true)

        // 300 zombies
        val zombies = List(300) { i ->
            ZombieEntity(i, ZombieArchetype.SHAMBLER, 50f, 50f, 20, 20)
        }

        val result = NovaBlastShieldGenerator.processDamage(20f, config, state, 0f, 0f, zombies)

        assertTrue(result.triggered)
        assertEquals(300, result.hitCount)
        assertEquals(3000, result.hitCount * config.novaBaseDamage)

        zombies.forEach {
            assertEquals(10, it.hp)
        }
    }

    @Test
    fun testSerializationParity() {
        val config = NovaShieldConfig(
            novaRadiusPx = 125.5f,
            novaBaseDamage = 150,
            isCryoNova = true,
            rechargeDelaySeconds = 2.5f,
            rechargeRatePerSecond = 20f,
            maxCapacity = 150f
        )
        val state = NovaShieldState(
            currentCapacity = 75.25f,
            timeSinceLastDamage = 1.25f,
            isFullyRecharged = false,
            hasNovaTriggered = true
        )

        val configMap = config.toMap()
        val stateMap = state.toMap()

        val config2 = NovaShieldConfig.fromMap(configMap)
        val state2 = NovaShieldState.fromMap(stateMap)

        assertEquals(config, config2)
        assertEquals(state, state2)
        assertEquals(state.calculateChecksum(), state2.calculateChecksum())
    }

    @Test
    fun testSerializationNanRecovery() {
        val configMap = mapOf<String, Any>(
            "novaRadiusPx" to Float.NaN,
            "maxCapacity" to Float.POSITIVE_INFINITY
        )
        val config = NovaShieldConfig.fromMap(configMap)

        assertEquals(150f, config.novaRadiusPx) // Default fallback
        assertEquals(100f, config.maxCapacity) // Default fallback

        val stateMap = mapOf<String, Any>(
            "currentCapacity" to Float.NaN,
            "timeSinceLastDamage" to Float.NEGATIVE_INFINITY
        )
        val state = NovaShieldState.fromMap(stateMap)

        assertEquals(100f, state.currentCapacity) // Default fallback
        assertEquals(0f, state.timeSinceLastDamage) // Default fallback
    }

}
