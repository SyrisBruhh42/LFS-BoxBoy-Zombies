package com.example.game.combat.modules

import kotlin.math.hypot

data class AcidPuddle(
    val x: Float,
    val y: Float,
    val radiusPx: Float,
    val dps: Int,
    var remainingDurationMs: Long
)

data class AcidCrawler(
    var x: Float,
    var y: Float,
    val speedPxPerMs: Float,
    var health: Int,
    var isDead: Boolean = false
)

object AcidCrawlerPuddleHazard {

    fun updateCrawlerMovement(crawler: AcidCrawler, targetX: Float, targetY: Float, deltaMs: Long) {
        if (crawler.isDead) return

        val dx = targetX - crawler.x
        val dy = targetY - crawler.y
        val dist = hypot(dx, dy)

        if (dist > 0.1f) {
            val moveDist = (crawler.speedPxPerMs * deltaMs).coerceAtMost(dist)
            crawler.x += (dx / dist) * moveDist
            crawler.y += (dy / dist) * moveDist
        }
    }

    fun handleCrawlerDeath(crawler: AcidCrawler, puddleDurationMs: Long = 5000L, puddleRadius: Float = 60f, dps: Int = 10): AcidPuddle? {
        if (!crawler.isDead && crawler.health <= 0) {
            crawler.isDead = true
            return AcidPuddle(
                x = crawler.x,
                y = crawler.y,
                radiusPx = puddleRadius,
                dps = dps,
                remainingDurationMs = puddleDurationMs
            )
        }
        return null
    }

    fun updatePuddles(puddles: MutableList<AcidPuddle>, deltaMs: Long) {
        val iterator = puddles.iterator()
        while (iterator.hasNext()) {
            val puddle = iterator.next()
            puddle.remainingDurationMs -= deltaMs
            if (puddle.remainingDurationMs <= 0) {
                iterator.remove()
            }
        }
    }

    fun calculatePuddleDamageToPlayer(playerX: Float, playerY: Float, puddles: List<AcidPuddle>, deltaMs: Long): Int {
        var totalDamage = 0f
        for (puddle in puddles) {
            val dist = hypot(playerX - puddle.x, playerY - puddle.y)
            if (dist <= puddle.radiusPx) {
                totalDamage += (puddle.dps.toFloat() * (deltaMs.toFloat() / 1000f))
            }
        }
        return totalDamage.toInt()
    }
}
