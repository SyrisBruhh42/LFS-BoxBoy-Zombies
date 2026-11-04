package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class FriendlyFireAndReviveBubbleTest {

    @Test
    fun testIsFriendlyFire() {
        val projFriendly = FriendlyFireAndReviveBubble.Projectile(id = 1, x = 0f, y = 0f, vx = 0f, vy = 0f, isFriendly = true)
        val projEnemy = FriendlyFireAndReviveBubble.Projectile(id = 2, x = 0f, y = 0f, vx = 0f, vy = 0f, isFriendly = false)
        val targetFriendly = FriendlyFireAndReviveBubble.Target(id = 1, x = 0f, y = 0f, radius = 10f, isFriendly = true)
        val targetEnemy = FriendlyFireAndReviveBubble.Target(id = 2, x = 0f, y = 0f, radius = 10f, isFriendly = false)

        assertTrue(FriendlyFireAndReviveBubble.isFriendlyFire(projFriendly, targetFriendly))
        assertFalse(FriendlyFireAndReviveBubble.isFriendlyFire(projFriendly, targetEnemy))
        assertFalse(FriendlyFireAndReviveBubble.isFriendlyFire(projEnemy, targetFriendly))
        assertTrue(FriendlyFireAndReviveBubble.isFriendlyFire(projEnemy, targetEnemy))
    }

    @Test
    fun testZeroFriendlyFireProjectilePassThrough() {
        val projFriendly = FriendlyFireAndReviveBubble.Projectile(id = 1, x = 10f, y = 10f, vx = 0f, vy = 0f, radius = 5f, isFriendly = true)
        val targetFriendly = FriendlyFireAndReviveBubble.Target(id = 1, x = 10f, y = 10f, radius = 5f, isFriendly = true)
        val targetEnemy = FriendlyFireAndReviveBubble.Target(id = 2, x = 20f, y = 20f, radius = 5f, isFriendly = false)

        // It should NOT filter the projectile, as the collision is friendly
        // Wait, the filter function filters OUT friendly fire hits? 
        // Zero pass through means what exactly? 
        // "Zero friendly-fire projectile pass-through" means projectiles don't get blocked by friendly targets or don't hurt them.
        
        val validHits = FriendlyFireAndReviveBubble.filterValidHits(listOf(projFriendly), listOf(targetFriendly, targetEnemy))
        assertEquals(0, validHits.size) // Shouldn't hit friendly target, doesn't hit enemy target because of distance
        
        val targetEnemyClose = FriendlyFireAndReviveBubble.Target(id = 3, x = 12f, y = 12f, radius = 5f, isFriendly = false)
        val validHitsClose = FriendlyFireAndReviveBubble.filterValidHits(listOf(projFriendly), listOf(targetFriendly, targetEnemyClose))
        assertEquals(1, validHitsClose.size) // Should only hit enemy target
    }

    @Test
    fun testCoopReviveTetherProgressCircle() {
        var state = FriendlyFireAndReviveBubble.ReviveState(isDowned = true, reviveProgress = 0f, bleedoutRemainingMs = 30000L)
        val reviverNear = FriendlyFireAndReviveBubble.Entity(id = 2, x = 50f, y = 0f, isFriendly = true) // distance 50
        val reviverFar = FriendlyFireAndReviveBubble.Entity(id = 3, x = 100f, y = 0f, isFriendly = true) // distance 100
        
        // Progress increases with near reviver
        state = FriendlyFireAndReviveBubble.calculateReviveProgress(
            downedState = state,
            downedX = 0f,
            downedY = 0f,
            revivers = listOf(reviverNear),
            deltaMs = 1500L,
            reviveDurationMs = 3000f,
            reviveRadius = 90f
        )
        assertTrue(state.isDowned)
        assertEquals(0.5f, state.reviveProgress, 0.01f)
        assertEquals(28500L, state.bleedoutRemainingMs)

        // Progress decays without near reviver
        state = FriendlyFireAndReviveBubble.calculateReviveProgress(
            downedState = state,
            downedX = 0f,
            downedY = 0f,
            revivers = listOf(reviverFar),
            deltaMs = 1500L, // decays at 1/6000 per ms, so 0.25f decay
            reviveDurationMs = 3000f,
            reviveRadius = 90f
        )
        assertTrue(state.isDowned)
        assertEquals(0.25f, state.reviveProgress, 0.01f)

        // Progress finishes
        state = FriendlyFireAndReviveBubble.calculateReviveProgress(
            downedState = state,
            downedX = 0f,
            downedY = 0f,
            revivers = listOf(reviverNear),
            deltaMs = 2500L, // 2500 / 3000 = 0.833, 0.25 + 0.833 > 1.0
            reviveDurationMs = 3000f,
            reviveRadius = 90f
        )
        assertFalse(state.isDowned)
        assertEquals(0f, state.reviveProgress, 0.01f)
        assertEquals(30000L, state.bleedoutRemainingMs)
    }
}
