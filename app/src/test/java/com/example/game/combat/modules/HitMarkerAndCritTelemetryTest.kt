package com.example.game.combat.modules

import com.example.game.combat.BoxheadComboState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HitMarkerAndCritTelemetryTest {

    @Test
    fun `generateHitMarker awards correct currency with no combo multiplier`() {
        val comboState = BoxheadComboState(currentStreak = 0)
        val initialPool = emptyList<FloatingDamageNumber>()

        val result = HitMarkerAndCritTelemetry.generateHitMarker(
            damageAmount = 50,
            type = DamageNumberType.STANDARD,
            x = 100f,
            y = 100f,
            comboState = comboState,
            currentPool = initialPool
        )

        assertEquals(10, result.currencyAwarded)
        assertEquals(1, result.activeNumbers.size)
        
        val number = result.activeNumbers.first()
        assertEquals(50, number.damageAmount)
        assertEquals(DamageNumberType.STANDARD, number.type)
        assertEquals(100f, number.startX)
        assertEquals(100f, number.startY)
        assertEquals(1000L, number.lifetimeRemainingMs)
    }

    @Test
    fun `generateHitMarker applies combo multiplier to currency awarded`() {
        // currentStreak = 10 -> 1.35f multiplier
        val comboState = BoxheadComboState(currentStreak = 10)
        
        val result = HitMarkerAndCritTelemetry.generateHitMarker(
            damageAmount = 120,
            type = DamageNumberType.CRITICAL,
            x = 200f,
            y = 150f,
            comboState = comboState,
            currentPool = emptyList()
        )

        // 10 * 1.35 = 13.5 -> toInt() = 13
        assertEquals(13, result.currencyAwarded)
        assertEquals(DamageNumberType.CRITICAL, result.activeNumbers.first().type)
    }

    @Test
    fun `generateHitMarker enforces MAX_FLOATING_NUMBERS constraint`() {
        val comboState = BoxheadComboState()
        var pool = emptyList<FloatingDamageNumber>()
        
        for (i in 1..60) {
            val result = HitMarkerAndCritTelemetry.generateHitMarker(
                damageAmount = i,
                type = DamageNumberType.STANDARD,
                x = 0f,
                y = 0f,
                comboState = comboState,
                currentPool = pool
            )
            pool = result.activeNumbers
        }
        
        assertEquals(50, pool.size)
        // The first 10 should have been dropped, so the first one in the pool is damageAmount = 11
        assertEquals(11, pool.first().damageAmount)
        assertEquals(60, pool.last().damageAmount)
    }

    @Test
    fun `stepFloatingNumbers decays lifetime and updates position`() {
        val number = FloatingDamageNumber(
            damageAmount = 100,
            type = DamageNumberType.ELEMENTAL,
            startX = 50f,
            startY = 50f,
            lifetimeRemainingMs = 1000L
        )

        val pool = listOf(number)
        
        val steppedPool = HitMarkerAndCritTelemetry.stepFloatingNumbers(pool, 500L)
        
        assertEquals(1, steppedPool.size)
        val steppedNumber = steppedPool.first()
        
        assertEquals(500L, steppedNumber.lifetimeRemainingMs)
        // Starts at 50, drifts up by 50f over 1000ms. At 500ms, should have drifted 25f.
        assertEquals(25f, steppedNumber.currentY)
    }

    @Test
    fun `stepFloatingNumbers removes numbers when lifetime is exhausted`() {
        val number = FloatingDamageNumber(
            damageAmount = 100,
            type = DamageNumberType.STANDARD,
            startX = 50f,
            startY = 50f,
            lifetimeRemainingMs = 1000L
        )

        val pool = listOf(number)
        
        val steppedPool = HitMarkerAndCritTelemetry.stepFloatingNumbers(pool, 1000L)
        
        assertTrue(steppedPool.isEmpty())
    }
}
