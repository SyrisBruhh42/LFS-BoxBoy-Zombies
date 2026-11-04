package com.example.game.combat.modules

import org.junit.Assert.*
import org.junit.Test

class ShadowStalkerAssassinsTest {

    @Test
    fun testStalkerRevealedByLanternCone() {
        val stalker = StalkerState(x = 100f, y = 0f)
        val lantern = PlayerLantern(
            x = 0f,
            y = 0f,
            facingAngleRad = 0f,
            coneHalfSpreadRad = Math.toRadians(45.0).toFloat(),
            rangePx = 200f
        )
        
        val updated = ShadowStalkerAssassins.stepStalkers(listOf(stalker), lantern, 16L)
        assertTrue(updated[0].isRevealed)
        assertEquals(ShadowStalkerAssassins.REVEAL_DURATION_MS, updated[0].revelationTimerMs)
    }

    @Test
    fun testStalkerOutsideLanternCone() {
        val stalker = StalkerState(x = 0f, y = 100f) // 90 degrees, outside of 45 degree spread
        val lantern = PlayerLantern(
            x = 0f,
            y = 0f,
            facingAngleRad = 0f,
            coneHalfSpreadRad = Math.toRadians(45.0).toFloat(),
            rangePx = 200f
        )
        
        val updated = ShadowStalkerAssassins.stepStalkers(listOf(stalker), lantern, 16L)
        assertFalse(updated[0].isRevealed)
    }
    
    @Test
    fun testStalkerRevealedByMuzzleFlash() {
        val stalker = StalkerState(x = 0f, y = 100f) // Outside cone
        val lantern = PlayerLantern(
            x = 0f,
            y = 0f,
            facingAngleRad = 0f,
            coneHalfSpreadRad = Math.toRadians(45.0).toFloat(),
            rangePx = 200f,
            muzzleFlashBloomRadiusPx = 150f
        )
        
        val updated = ShadowStalkerAssassins.stepStalkers(listOf(stalker), lantern, 16L)
        assertTrue(updated[0].isRevealed)
    }

    @Test
    fun testStalkerRevelationTimerDecays() {
        val stalker = StalkerState(x = 500f, y = 500f, isRevealed = true, revelationTimerMs = 100L)
        val lantern = PlayerLantern(x = 0f, y = 0f, facingAngleRad = 0f)
        
        // Step 1: decay timer
        val updated1 = ShadowStalkerAssassins.stepStalkers(listOf(stalker), lantern, 50L)
        assertTrue(updated1[0].isRevealed)
        assertEquals(50L, updated1[0].revelationTimerMs)
        
        // Step 2: timer expires
        val updated2 = ShadowStalkerAssassins.stepStalkers(updated1, lantern, 50L)
        assertFalse(updated2[0].isRevealed)
        assertEquals(0L, updated2[0].revelationTimerMs)
    }
}
