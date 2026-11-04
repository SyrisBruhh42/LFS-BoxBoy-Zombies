package com.example.game.combat.modules

import kotlin.math.hypot

data class AcidPuddle(
    val id: Int,
    val x: Float,
    val y: Float,
    val radiusPx: Float = 45f,
    val damagePerSecond: Float = 25f,
    val remainingLifetimeMs: Long = 6000L,
    val colorHex: Long = 0xFF00FF66
)

object AcidCrawlerPuddleHazardEngine {
    private var nextPuddleId = 0

    /**
     * Acid Crawlers gain frenzy speed as their health drops.
     */
    fun computeCrawlerSpeedMultiplier(hp: Int, maxHp: Int): Float {
        if (maxHp <= 0) return 1.0f
        val ratio = hp.toFloat() / maxHp.toFloat()
        return when {
            ratio <= 0.33f -> 1.75f
            ratio <= 0.66f -> 1.35f
            else -> 1.0f
        }
    }

    /**
     * Drops a corrosive puddle hazard when an Acid Crawler dies.
     */
    fun spawnPuddleOnDeath(crawlerX: Float, crawlerY: Float): AcidPuddle {
        return AcidPuddle(
            id = nextPuddleId++,
            x = crawlerX,
            y = crawlerY
        )
    }

    /**
     * Steps the active acid puddles, decaying their lifetime and applying continuous corrosive damage to players standing within them.
     */
    fun stepPuddles(
        puddles: List<AcidPuddle>,
        playerX: Float,
        playerY: Float,
        deltaMs: Long
    ): Pair<List<AcidPuddle>, Float> {
        val activePuddles = mutableListOf<AcidPuddle>()
        var playerDamageReceived = 0f

        for (puddle in puddles) {
            val updatedLifetime = puddle.remainingLifetimeMs - deltaMs
            if (updatedLifetime > 0) {
                activePuddles.add(puddle.copy(remainingLifetimeMs = updatedLifetime))

                val dist = hypot(playerX - puddle.x, playerY - puddle.y)
                if (dist <= puddle.radiusPx) {
                    playerDamageReceived += puddle.damagePerSecond * (deltaMs / 1000f)
                }
            }
        }

        return activePuddles to playerDamageReceived
    }
}
