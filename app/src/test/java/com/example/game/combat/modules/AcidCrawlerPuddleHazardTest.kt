package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcidCrawlerPuddleHazardTest {

    @Test
    fun testFrenzySpeed() {
        val maxHp = 100

        // hp > 66% => 1.0x
        var multiplier = AcidCrawlerPuddleHazardEngine.computeCrawlerSpeedMultiplier(100, maxHp)
        assertEquals(1.0f, multiplier, 0.01f)

        multiplier = AcidCrawlerPuddleHazardEngine.computeCrawlerSpeedMultiplier(67, maxHp)
        assertEquals(1.0f, multiplier, 0.01f)

        // 33% < hp <= 66% => 1.35x
        multiplier = AcidCrawlerPuddleHazardEngine.computeCrawlerSpeedMultiplier(66, maxHp)
        assertEquals(1.35f, multiplier, 0.01f)

        multiplier = AcidCrawlerPuddleHazardEngine.computeCrawlerSpeedMultiplier(34, maxHp)
        assertEquals(1.35f, multiplier, 0.01f)

        // hp <= 33% => 1.75x
        multiplier = AcidCrawlerPuddleHazardEngine.computeCrawlerSpeedMultiplier(33, maxHp)
        assertEquals(1.75f, multiplier, 0.01f)

        multiplier = AcidCrawlerPuddleHazardEngine.computeCrawlerSpeedMultiplier(10, maxHp)
        assertEquals(1.75f, multiplier, 0.01f)
    }

    @Test
    fun testSpawnOnDeath() {
        val crawlerX = 150f
        val crawlerY = 250f

        val puddle = AcidCrawlerPuddleHazardEngine.spawnPuddleOnDeath(crawlerX, crawlerY)

        assertNotNull(puddle)
        assertEquals(150f, puddle.x, 0.01f)
        assertEquals(250f, puddle.y, 0.01f)
        assertEquals(6000L, puddle.remainingLifetimeMs)
    }

    @Test
    fun testPuddleDamageAndDecay() {
        val puddle = AcidPuddle(
            id = 0,
            x = 100f,
            y = 100f,
            radiusPx = 45f,
            damagePerSecond = 25f,
            remainingLifetimeMs = 6000L
        )

        // Player inside puddle
        val (activePuddles, damageReceived) = AcidCrawlerPuddleHazardEngine.stepPuddles(
            puddles = listOf(puddle),
            playerX = 110f, // inside (10 units away)
            playerY = 100f,
            deltaMs = 1000L
        )

        assertEquals(1, activePuddles.size)
        assertEquals(5000L, activePuddles[0].remainingLifetimeMs)
        assertEquals(25f, damageReceived, 0.01f)

        // Player outside puddle
        val (activePuddles2, damageReceived2) = AcidCrawlerPuddleHazardEngine.stepPuddles(
            puddles = listOf(puddle),
            playerX = 200f, // outside (>45 units away)
            playerY = 100f,
            deltaMs = 1000L
        )

        assertEquals(1, activePuddles2.size)
        assertEquals(5000L, activePuddles2[0].remainingLifetimeMs)
        assertEquals(0f, damageReceived2, 0.01f)

        // Decay to zero
        val (activePuddles3, damageReceived3) = AcidCrawlerPuddleHazardEngine.stepPuddles(
            puddles = listOf(puddle),
            playerX = 110f,
            playerY = 100f,
            deltaMs = 6000L
        )

        assertTrue(activePuddles3.isEmpty())
    }
}
