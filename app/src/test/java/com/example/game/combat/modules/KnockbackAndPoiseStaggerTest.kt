package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class KnockbackAndPoiseStaggerTest {

    @Test
    fun testCalculateKnockback_Shotgun_CloseRange() {
        val impulse = KnockbackAndPoiseStagger.calculateKnockback("Shotgun", 0f, 0f)
        assertEquals(800f, impulse.vx, 0.001f)
        assertEquals(0f, impulse.vy, 0.001f)
    }

    @Test
    fun testCalculateKnockback_Shotgun_MidRange() {
        // Distance 150 (half of 300 max range) should give half force: 400
        // Angle PI/2 (straight up) means vy = 400, vx = 0
        val impulse = KnockbackAndPoiseStagger.calculateKnockback("Shotgun", 150f, (PI / 2).toFloat())
        assertEquals(0f, impulse.vx, 0.01f)
        assertEquals(400f, impulse.vy, 0.01f)
    }

    @Test
    fun testCalculateKnockback_Shotgun_Out_Of_Range() {
        val impulse = KnockbackAndPoiseStagger.calculateKnockback("Shotgun", 350f, 0f)
        assertEquals(0f, impulse.vx, 0.001f)
        assertEquals(0f, impulse.vy, 0.001f)
    }

    @Test
    fun testCalculateKnockback_Korsakov() {
        val impulse = KnockbackAndPoiseStagger.calculateKnockback("Korsakov", 0f, PI.toFloat())
        assertEquals(-400f, impulse.vx, 0.01f)
        assertEquals(0f, impulse.vy, 0.01f)
    }

    @Test
    fun testCalculateStagger_Shotgun() {
        val staggerLowPoise = KnockbackAndPoiseStagger.calculateStagger("Shotgun", 50f)
        assertTrue(staggerLowPoise.isStaggered)
        assertEquals(30, staggerLowPoise.remainingStaggerFrames)

        val staggerHighPoise = KnockbackAndPoiseStagger.calculateStagger("Shotgun", 150f)
        assertFalse(staggerHighPoise.isStaggered)
        assertEquals(0, staggerHighPoise.remainingStaggerFrames)
    }

    @Test
    fun testCalculateStagger_Korsakov() {
        val staggerLowPoise = KnockbackAndPoiseStagger.calculateStagger("Korsakov", 25f)
        assertTrue(staggerLowPoise.isStaggered)
        assertEquals(15, staggerLowPoise.remainingStaggerFrames)
    }

    @Test
    fun testStepStaggerRecovery() {
        val state = StaggerState(isStaggered = true, remainingStaggerFrames = 2)
        val nextState1 = KnockbackAndPoiseStagger.stepStaggerRecovery(state)
        assertTrue(nextState1.isStaggered)
        assertEquals(1, nextState1.remainingStaggerFrames)

        val nextState2 = KnockbackAndPoiseStagger.stepStaggerRecovery(nextState1)
        assertFalse(nextState2.isStaggered)
        assertEquals(0, nextState2.remainingStaggerFrames)

        val nextState3 = KnockbackAndPoiseStagger.stepStaggerRecovery(nextState2)
        assertFalse(nextState3.isStaggered)
        assertEquals(0, nextState3.remainingStaggerFrames)
    }
}
