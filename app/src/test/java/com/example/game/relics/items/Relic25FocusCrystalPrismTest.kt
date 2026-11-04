package com.example.game.relics.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Relic25FocusCrystalPrismTest {

    @Test
    fun `config defaults and drop-only invariant`() {
        val config = Relic25FocusCrystalPrismConfig()
        assertEquals(0.20f, config.baseMultiplierPerStack, 0.001f)
        assertEquals(130.0f, config.triggerRadiusPx, 0.001f)
        assertFalse("Focus Crystal Prism MUST be a drop-only relic", config.canBePurchasedAtVending)
    }

    @Test
    fun `config toMap and fromMap serialization roundtrip`() {
        val config = Relic25FocusCrystalPrismConfig(baseMultiplierPerStack = 0.35f, triggerRadiusPx = 150f)
        val map = config.toMap()

        val restored = Relic25FocusCrystalPrismConfig.fromMap(map)
        assertEquals(config.baseMultiplierPerStack, restored.baseMultiplierPerStack, 0.001f)
        assertEquals(config.triggerRadiusPx, restored.triggerRadiusPx, 0.001f)
        assertFalse(restored.canBePurchasedAtVending)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects negative multiplier`() {
        Relic25FocusCrystalPrismConfig(baseMultiplierPerStack = -0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects negative radius`() {
        Relic25FocusCrystalPrismConfig(triggerRadiusPx = -10f)
    }

    @Test
    fun `state serialization and stack count minimum bound`() {
        val state = Relic25FocusCrystalPrismState(stackCount = 5)
        val map = state.toMap()

        val restored = Relic25FocusCrystalPrismState.fromMap(map)
        assertEquals(5, restored.stackCount)
        assertEquals(state.computeChecksum(), restored.computeChecksum())

        // Coerced bounds test for fromMap
        val badMap = mapOf("stackCount" to 0)
        val clamped = Relic25FocusCrystalPrismState.fromMap(badMap)
        assertEquals(1, clamped.stackCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `state rejects stack count less than 1`() {
        Relic25FocusCrystalPrismState(stackCount = 0)
    }

    @Test
    fun `engine multiplier applied within radius`() {
        val config = Relic25FocusCrystalPrismConfig()
        val state = Relic25FocusCrystalPrismState(stackCount = 1) // +20%

        val playerX = 100f
        val playerY = 100f
        val targetX = 100f + 100f // dx=100, dy=0, dist=100 (<= 130)
        val targetY = 100f

        val mult = Relic25FocusCrystalPrismEngine.evaluateDamageMultiplier(config, state, playerX, playerY, targetX, targetY)
        assertEquals(1.20f, mult, 0.001f)
    }

    @Test
    fun `engine multiplier scales with stack count`() {
        val config = Relic25FocusCrystalPrismConfig()
        val state = Relic25FocusCrystalPrismState(stackCount = 4) // +80%

        val mult = Relic25FocusCrystalPrismEngine.evaluateDamageMultiplier(config, state, 0f, 0f, 50f, 50f)
        assertEquals(1.80f, mult, 0.001f)
    }

    @Test
    fun `engine multiplier absent outside radius`() {
        val config = Relic25FocusCrystalPrismConfig()
        val state = Relic25FocusCrystalPrismState(stackCount = 10) // Huge bonus if triggered

        val mult = Relic25FocusCrystalPrismEngine.evaluateDamageMultiplier(config, state, 0f, 0f, 131f, 0f)
        assertEquals(1.0f, mult, 0.001f)
    }

    @Test
    fun `engine batch evaluation processes correctly`() {
        val config = Relic25FocusCrystalPrismConfig()
        val state = Relic25FocusCrystalPrismState(stackCount = 2) // +40% (1.4f)

        val targetXs = floatArrayOf(50f, 150f, 0f, 200f) // Distances: ~70, ~212, 0, ~282
        val targetYs = floatArrayOf(50f, 150f, 0f, 200f)
        val outMultipliers = FloatArray(4)

        Relic25FocusCrystalPrismEngine.evaluateDamageMultiplierBatch(
            config, state, 0f, 0f, targetXs, targetYs, outMultipliers, 4
        )

        assertEquals(1.4f, outMultipliers[0], 0.001f) // Inside
        assertEquals(1.0f, outMultipliers[1], 0.001f) // Outside
        assertEquals(1.4f, outMultipliers[2], 0.001f) // Inside (exactly on player)
        assertEquals(1.0f, outMultipliers[3], 0.001f) // Outside
    }

    @Test
    fun `engine batch evaluation degrades gracefully on NaN`() {
        val config = Relic25FocusCrystalPrismConfig()
        val state = Relic25FocusCrystalPrismState(stackCount = 1)

        val targetXs = floatArrayOf(50f, Float.NaN, 50f)
        val targetYs = floatArrayOf(50f, 50f, Float.NaN)
        val outMultipliers = FloatArray(3)

        Relic25FocusCrystalPrismEngine.evaluateDamageMultiplierBatch(
            config, state, 0f, 0f, targetXs, targetYs, outMultipliers, 3
        )

        assertEquals(1.2f, outMultipliers[0], 0.001f)
        assertEquals(1.0f, outMultipliers[1], 0.001f)
        assertEquals(1.0f, outMultipliers[2], 0.001f)
    }

    @Test
    fun `engine single evaluation degrades gracefully on NaN`() {
        val config = Relic25FocusCrystalPrismConfig()
        val state = Relic25FocusCrystalPrismState(stackCount = 1)

        val mult1 = Relic25FocusCrystalPrismEngine.evaluateDamageMultiplier(config, state, Float.NaN, 0f, 50f, 50f)
        val mult2 = Relic25FocusCrystalPrismEngine.evaluateDamageMultiplier(config, state, 0f, 0f, Float.POSITIVE_INFINITY, 50f)

        assertEquals(1.0f, mult1, 0.001f)
        assertEquals(1.0f, mult2, 0.001f)
    }

    @Test
    fun `checksum is deterministic`() {
        val state1 = Relic25FocusCrystalPrismState(stackCount = 42)
        val state2 = Relic25FocusCrystalPrismState(stackCount = 42)
        val state3 = Relic25FocusCrystalPrismState(stackCount = 43)

        assertEquals(state1.computeChecksum(), state2.computeChecksum())
        assertTrue(state1.computeChecksum() != state3.computeChecksum())
    }
}
