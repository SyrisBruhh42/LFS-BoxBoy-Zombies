package com.example.game.combat.modules

import kotlin.math.hypot
import kotlin.math.max

data class DevilBossState(
    val entityId: Int,
    val hp: Int,
    val maxHp: Int,
    val fireballCooldownMs: Long = 0L,
    val chargeCooldownMs: Long = 0L,
    val isCharging: Boolean = false,
    val chargeDurationRemainingMs: Long = 0L,
    val chargeDirectionX: Float = 0f,
    val chargeDirectionY: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f
) {
    val phase: Int
        get() = when {
            hp <= maxHp * 0.33f -> 3
            hp <= maxHp * 0.66f -> 2
            else -> 1
        }

    val isEnraged: Boolean
        get() = phase >= 2
}

data class DevilBossUpdateResult(
    val updatedBoss: DevilBossState,
    val spawnedFireballs: List<Pair<Float, Float>>, // List of (vx, vy) velocities
    val chargeSlamDamage: Int = 0
)

object BoxheadDevilBossPhase {
    const val CHARGE_COOLDOWN_MS = 6000L
    const val CHARGE_DURATION_MS = 800L
    const val FIREBALL_COOLDOWN_MS = 3000L

    fun applyBarrelDamage(boss: DevilBossState, baseBarrelDamage: Int): Int {
        // Track 3: Barrel immunity scaling based on phase
        val multiplier = when (boss.phase) {
            1 -> 1.0f   // Full damage
            2 -> 0.5f   // 50% damage reduction
            3 -> 0.25f  // 75% damage reduction
            else -> 1.0f
        }
        return (baseBarrelDamage * multiplier).toInt()
    }

    fun stepBoss(
        boss: DevilBossState,
        deltaMs: Long,
        playerX: Float,
        playerY: Float
    ): DevilBossUpdateResult {
        var newBoss = boss
        val spawnedFireballs = mutableListOf<Pair<Float, Float>>()
        var chargeSlamDmg = 0

        if (newBoss.isCharging) {
            val remaining = newBoss.chargeDurationRemainingMs - deltaMs
            if (remaining <= 0) {
                newBoss = newBoss.copy(
                    isCharging = false,
                    chargeDurationRemainingMs = 0L,
                    chargeCooldownMs = CHARGE_COOLDOWN_MS
                )
                // End of charge triggers an AOE slam
                chargeSlamDmg = 150 + (newBoss.phase * 50)
            } else {
                newBoss = newBoss.copy(chargeDurationRemainingMs = remaining)
            }
        } else {
            // Update cooldowns
            val chargeCd = max(0L, newBoss.chargeCooldownMs - deltaMs)
            val fireballCd = max(0L, newBoss.fireballCooldownMs - deltaMs)

            newBoss = newBoss.copy(chargeCooldownMs = chargeCd, fireballCooldownMs = fireballCd)

            // Logic priority: Charge if phase >= 2 and cooldown ready
            if (newBoss.isEnraged && newBoss.chargeCooldownMs <= 0L) {
                val dx = playerX - newBoss.x
                val dy = playerY - newBoss.y
                val dist = hypot(dx, dy)

                if (dist > 0.1f) {
                    newBoss = newBoss.copy(
                        isCharging = true,
                        chargeDurationRemainingMs = CHARGE_DURATION_MS,
                        chargeDirectionX = dx / dist,
                        chargeDirectionY = dy / dist
                    )
                }
            }
            // Else Fireball Volley if cooldown ready
            else if (newBoss.fireballCooldownMs <= 0L) {
                val dx = playerX - newBoss.x
                val dy = playerY - newBoss.y
                val dist = hypot(dx, dy)

                if (dist > 0.1f) {
                    val dirX = dx / dist
                    val dirY = dy / dist

                    val numFireballs = when (newBoss.phase) {
                        1 -> 1
                        2 -> 3
                        3 -> 5
                        else -> 1
                    }

                    // Simple spread logic
                    val baseAngle = kotlin.math.atan2(dirY, dirX)
                    val spreadAngle = 0.2f // radians

                    val startIndex = -(numFireballs / 2)
                    for (i in 0 until numFireballs) {
                        val angle = baseAngle + (startIndex + i) * spreadAngle
                        val vx = kotlin.math.cos(angle) * 300f
                        val vy = kotlin.math.sin(angle) * 300f
                        spawnedFireballs.add(Pair(vx, vy))
                    }

                    newBoss = newBoss.copy(fireballCooldownMs = FIREBALL_COOLDOWN_MS)
                }
            }
        }

        return DevilBossUpdateResult(newBoss, spawnedFireballs, chargeSlamDmg)
    }
}
