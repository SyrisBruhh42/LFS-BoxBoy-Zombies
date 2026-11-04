package com.example.game.combat.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class AcidCrawlerPuddleHazardTest {

    @Test
    fun testCrawlerMovement() {
        val crawler = AcidCrawler(0f, 0f, 0.1f, 100)
        AcidCrawlerPuddleHazard.updateCrawlerMovement(crawler, 100f, 0f, 100L)

        assertEquals(10f, crawler.x, 0.1f)
        assertEquals(0f, crawler.y, 0.1f)
    }

    @Test
    fun testCrawlerDeathCreatesPuddle() {
        val crawler = AcidCrawler(50f, 50f, 0.1f, 0)
        val puddle = AcidCrawlerPuddleHazard.handleCrawlerDeath(crawler)

        assertTrue(crawler.isDead)
        assertNotNull(puddle)
        assertEquals(50f, puddle!!.x, 0.1f)
        assertEquals(50f, puddle.y, 0.1f)
    }

    @Test
    fun testCrawlerDeathTwiceDoesNotCreateMultiplePuddles() {
        val crawler = AcidCrawler(50f, 50f, 0.1f, 0)
        val puddle1 = AcidCrawlerPuddleHazard.handleCrawlerDeath(crawler)
        val puddle2 = AcidCrawlerPuddleHazard.handleCrawlerDeath(crawler)

        assertNotNull(puddle1)
        assertNull(puddle2)
    }

    @Test
    fun testPuddleDecay() {
        val puddles = mutableListOf(
            AcidPuddle(0f, 0f, 50f, 10, 1000L),
            AcidPuddle(10f, 10f, 50f, 10, 2000L)
        )

        AcidCrawlerPuddleHazard.updatePuddles(puddles, 1500L)

        assertEquals(1, puddles.size)
        assertEquals(500L, puddles[0].remainingDurationMs)
    }

    @Test
    fun testPlayerDamage() {
        val puddles = listOf(
            AcidPuddle(0f, 0f, 50f, 10, 1000L)
        )

        // player inside
        val dmgIn = AcidCrawlerPuddleHazard.calculatePuddleDamageToPlayer(0f, 0f, puddles, 1000L)
        assertEquals(10, dmgIn)

        // player outside
        val dmgOut = AcidCrawlerPuddleHazard.calculatePuddleDamageToPlayer(100f, 100f, puddles, 1000L)
        assertEquals(0, dmgOut)
    }
}
