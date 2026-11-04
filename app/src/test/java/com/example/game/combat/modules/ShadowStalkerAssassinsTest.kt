package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ShadowStalkerAssassinsTest {

    @Test
    fun testStalkerInLanternConeUncloaks() {
        val playerX = 100f
        val playerY = 100f
        val aimAngleRad = 0f // Aiming right (positive X)

        // Stalker within lantern range (440f) and within cone angle (+- 0.56 rad)
        val dist = 200f
        val stalkerX = playerX + dist * cos(0.2f)
        val stalkerY = playerY + dist * sin(0.2f)

        val initialStalker = ShadowStalker(zombieId = 1, isCloaked = true, revelationDecayTimerMs = 0L, x = stalkerX, y = stalkerY)

        val result = ShadowStalkerAssassinsEngine.stepVisibility(
            stalkers = listOf(initialStalker),
            playerX = playerX,
            playerY = playerY,
            aimAngleRad = aimAngleRad,
            isMuzzleFlashActive = false,
            deltaMs = 16L
        )

        val updatedStalker = result.first()
        assertFalse("Stalker should be revealed in lantern cone", updatedStalker.isCloaked)
        assertEquals(ShadowStalkerAssassinsEngine.MAX_REVELATION_DECAY_MS, updatedStalker.revelationDecayTimerMs)
    }

    @Test
    fun testStalkerOutsideLanternConeRemainsCloaked() {
        val playerX = 100f
        val playerY = 100f
        val aimAngleRad = 0f // Aiming right (positive X)

        // Stalker within lantern range but OUTSIDE cone angle (+- 0.56 rad)
        val dist = 200f
        val stalkerX = playerX + dist * cos(PI.toFloat()) // Behind player
        val stalkerY = playerY + dist * sin(PI.toFloat())

        val initialStalker = ShadowStalker(zombieId = 1, isCloaked = true, revelationDecayTimerMs = 0L, x = stalkerX, y = stalkerY)

        val result = ShadowStalkerAssassinsEngine.stepVisibility(
            stalkers = listOf(initialStalker),
            playerX = playerX,
            playerY = playerY,
            aimAngleRad = aimAngleRad,
            isMuzzleFlashActive = false,
            deltaMs = 16L
        )

        val updatedStalker = result.first()
        assertTrue("Stalker should remain cloaked outside lantern cone", updatedStalker.isCloaked)
    }

    @Test
    fun testStalkerNearMuzzleFlashUncloaks() {
        val playerX = 100f
        val playerY = 100f
        val aimAngleRad = 0f

        // Stalker behind player (outside cone), but within muzzle flash range (520f)
        val dist = 400f
        val stalkerX = playerX + dist * cos(PI.toFloat())
        val stalkerY = playerY + dist * sin(PI.toFloat())

        val initialStalker = ShadowStalker(zombieId = 1, isCloaked = true, revelationDecayTimerMs = 0L, x = stalkerX, y = stalkerY)

        val result = ShadowStalkerAssassinsEngine.stepVisibility(
            stalkers = listOf(initialStalker),
            playerX = playerX,
            playerY = playerY,
            aimAngleRad = aimAngleRad,
            isMuzzleFlashActive = true,
            deltaMs = 16L
        )

        val updatedStalker = result.first()
        assertFalse("Stalker should be revealed by muzzle flash", updatedStalker.isCloaked)
        assertEquals(ShadowStalkerAssassinsEngine.MAX_REVELATION_DECAY_MS, updatedStalker.revelationDecayTimerMs)
    }

    @Test
    fun testStalkerRevelationDecaysOverTime() {
        val playerX = 100f
        val playerY = 100f
        val aimAngleRad = 0f

        // Stalker far away from player
        val stalkerX = 1000f
        val stalkerY = 1000f

        // Stalker was previously revealed
        val initialStalker = ShadowStalker(
            zombieId = 1,
            isCloaked = false,
            revelationDecayTimerMs = 50L,
            x = stalkerX,
            y = stalkerY
        )

        // Step 1: Decay time by 30ms -> remaining 20ms
        var result = ShadowStalkerAssassinsEngine.stepVisibility(
            stalkers = listOf(initialStalker),
            playerX = playerX,
            playerY = playerY,
            aimAngleRad = aimAngleRad,
            isMuzzleFlashActive = false,
            deltaMs = 30L
        )

        var updatedStalker = result.first()
        assertFalse("Stalker should still be revealed", updatedStalker.isCloaked)
        assertEquals(20L, updatedStalker.revelationDecayTimerMs)

        // Step 2: Decay time by another 30ms -> should hit 0 and cloak
        result = ShadowStalkerAssassinsEngine.stepVisibility(
            stalkers = result,
            playerX = playerX,
            playerY = playerY,
            aimAngleRad = aimAngleRad,
            isMuzzleFlashActive = false,
            deltaMs = 30L
        )

        updatedStalker = result.first()
        assertTrue("Stalker should now be cloaked after decay timer reaches 0", updatedStalker.isCloaked)
        assertEquals(0L, updatedStalker.revelationDecayTimerMs)
    }
}
