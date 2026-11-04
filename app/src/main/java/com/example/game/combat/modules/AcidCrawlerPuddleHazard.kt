package com.example.game.combat.modules

import kotlin.math.hypot

data class AcidPuddle(
    val id: Int = 0,
    val x: Float,
    val y: Float,
    val radiusPx: Float = 45f,
    val damagePerSecond: Float = 25f,
    var remainingLifetimeMs: Long = 6000L,
    val dps: Int = damagePerSecond.toInt(),
    var remainingDurationMs: Long = remainingLifetimeMs
)

data class AcidCrawler(
    var x: Float,
    var y: Float,
    val speedPxPerMs: Float,
    var health: Int,
    var isDead: Boolean = false
)

object AcidCrawlerPuddleHazardEngine {
    fun computeCrawlerSpeedMultiplier(currentHp: Int, maxHp: Int): Float {
        val ratio = currentHp.toFloat() / maxHp.coerceAtLeast(1).toFloat()
        return when {
            ratio <= 0.33f -> 1.75f
            ratio <= 0.66f -> 1.35f
            else -> 1.0f
        }
    }

    fun spawnPuddleOnDeath(x: Float, y: Float, id: Int = 0): AcidPuddle {
        return AcidPuddle(
            id = id,
            x = x,
            y = y,
            radiusPx = 45f,
            damagePerSecond = 25f,
            remainingLifetimeMs = 6000L
        )
    }

    fun stepPuddles(
        puddles: List<AcidPuddle>,
        playerX: Float,
        playerY: Float,
        deltaMs: Long
    ): Pair<List<AcidPuddle>, Float> {
        val active = mutableListOf<AcidPuddle>()
        var damageReceived = 0f
        for (puddle in puddles) {
            val dist = hypot(playerX - puddle.x, playerY - puddle.y)
            if (dist <= puddle.radiusPx) {
                damageReceived += puddle.damagePerSecond * (deltaMs.toFloat() / 1000f)
            }
            val nextRemaining = puddle.remainingLifetimeMs - deltaMs
            if (nextRemaining > 0L) {
                active.add(
                    puddle.copy(
                        remainingLifetimeMs = nextRemaining,
                        remainingDurationMs = nextRemaining
                    )
                )
            }
        }
        return active to damageReceived
    }
}

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
                damagePerSecond = dps.toFloat(),
                remainingLifetimeMs = puddleDurationMs,
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
            puddle.remainingLifetimeMs = puddle.remainingDurationMs
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


/**
 * Deterministic 60Hz subsystem telemetry & FNV-1a verification receipt for [AcidCrawlerPuddleHazard].
 */
data class AcidCrawlerPuddleHazardTelemetryReceipt(
    val moduleName: String = "AcidCrawlerPuddleHazard",
    val wave: Int,
    val tick: Long,
    val metricScore: Double,
    val checksum: Long
)

fun computeAcidCrawlerPuddleHazardTelemetryReceipt(wave: Int, tick: Long, seed: Long = 42L): AcidCrawlerPuddleHazardTelemetryReceipt {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var hash = -3750763034362895579L xor seed
    for (ch in "AcidCrawlerPuddleHazard") {
        hash = (hash xor ch.code.toLong()) * 1099511628211L
    }
    hash = (hash xor safeWave.toLong()) * 1099511628211L
    hash = (hash xor safeTick) * 1099511628211L
    val score = (1.0 + safeWave * 0.12) * (1.0 + (safeTick % 60L) * 0.005)
    return AcidCrawlerPuddleHazardTelemetryReceipt(
        wave = safeWave,
        tick = safeTick,
        metricScore = score,
        checksum = hash
    )
}


fun evaluateAcidCrawlerPuddleHazardLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "AcidCrawlerPuddleHazard") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
