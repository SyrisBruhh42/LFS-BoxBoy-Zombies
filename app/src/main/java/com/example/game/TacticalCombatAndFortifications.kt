package com.example.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * PHASE 3: Hybrid Combat, Ranged/Elemental Archetypes (Calcified Stalker Archers & Abyssal Weaver Mages),
 * Siege Fabricator Waves, and 1-Tap Contextual Auto-Drop Fortifications.
 */
enum class FortificationType(
    val displayName: String,
    val shortCode: String,
    val costPoints: Int,
    val maxHp: Int,
    val colorHex: Long
) {
    RED_BARREL("Explosive Red Barrel", "BARREL", 150, 40, 0xFFFF2A54),
    BARRICADE_WALL("Reinforced Choke Barricade", "WALL", 175, 360, 0xFF00FF66),
    CRYO_MINE("Cryo-Stasis Proximity Mine", "CRYO-MINE", 200, 90, 0xFF00E5FF),
    AUTO_TURRET_BEACON("Pulse Sentry Beacon", "TURRET", 350, 260, 0xFFFFD166)
}

data class FortificationEntity(
    val id: Int,
    val kind: FortificationType,
    val x: Float,
    val y: Float,
    val roomIndex: Int,
    var hp: Int = kind.maxHp,
    val maxHp: Int = kind.maxHp,
    var fireCooldownMs: Long = 0L,
    var ttlMs: Long = 45_000L
)

/**
 * Unshadowed additive laser telegraph projected by Calcified Stalker (Archer) before firing.
 */
data class LaserTelegraph(
    val sourceZombieId: Int,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val aimAngleRad: Float,
    var chargeRemainingMs: Long = 850L,
    val totalChargeMs: Long = 850L,
    val colorHex: Long = 0xFFFF2A54
) {
    val chargeFraction: Float
        get() = (1f - (chargeRemainingMs.toFloat() / totalChargeMs.coerceAtLeast(1L).toFloat())).coerceIn(0f, 1f)
}

/**
 * Enemy ranged projectile fired by Calcified Stalkers (Bone Shards) or Abyssal Weavers (Elemental Orbs).
 */
data class EnemyProjectile(
    val id: Int,
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val damage: Int,
    val radius: Float = 8f,
    val colorHex: Long,
    val spawnsPuddleOnImpact: Boolean = false,
    val elementKind: ElementalAffinity = ElementalAffinity.KINETIC,
    var ttlMs: Long = 2600L
)

/**
 * Glowing elemental hazard puddle created by Abyssal Weaver orbs or Cryo Mines.
 */
data class ElementalPuddle(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float = 62f,
    val elementKind: ElementalAffinity,
    val colorHex: Long = elementKind.colorHex,
    val hitsPlayers: Boolean = true,
    val hitsZombies: Boolean = false,
    var ttlMs: Long = 5500L,
    var tickTimerMs: Long = 0L
)

data class SiegeFabricatorState(
    val isActive: Boolean = false,
    val roomIndex: Int = 0,
    val remainingMs: Long = 0L,
    val totalDurationMs: Long = 20_000L,
    val fabricationsCompleted: Int = 0
) {
    val progressFraction: Float
        get() = if (!isActive || totalDurationMs <= 0L) 0f
        else (1f - (remainingMs.toFloat() / totalDurationMs.toFloat())).coerceIn(0f, 1f)
}

object TacticalCombatCoordinator {

    /**
     * Steps ranged enemy archetypes (Calcified Stalkers & Abyssal Weavers), active laser telegraphs,
     * enemy projectiles, elemental puddles, and player-deployed fortifications (Barricade Walls,
     * Cryo Mines, and Pulse Sentry Beacons).
     */
    fun stepPhase3Combat(
        deltaMs: Long,
        dtSec: Float,
        playerX: Float,
        playerY: Float,
        zombies: MutableList<ZombieEntity>,
        telegraphs: MutableList<LaserTelegraph>,
        enemyProjectiles: MutableList<EnemyProjectile>,
        elementalPuddles: MutableList<ElementalPuddle>,
        fortifications: MutableList<FortificationEntity>,
        tracers: MutableList<BulletTracer>,
        particles: MutableList<ParticleBurst>,
        banners: MutableList<FloatingBanner>,
        nextEntityIdProvider: () -> Int
    ): Pair<Int, Float> {
        var totalPlayerDamageTaken = 0
        var playerSlowFactor = 1.0f

        // 1. Step Calcified Stalker (Archer) & Abyssal Weaver (Mage) ranged attacks
        val activeStalkerIdsWithTelegraph = telegraphs.map { it.sourceZombieId }.toSet()
        for (z in zombies) {
            val distToPlayer = hypot(playerX - z.x, playerY - z.y)
            when (z.archetype) {
                ZombieArchetype.CALCIFIED_STALKER -> {
                    z.attackCooldownMs = (z.attackCooldownMs - deltaMs).coerceAtLeast(0L)
                    if (distToPlayer in 150f..480f && z.attackCooldownMs == 0L && z.id !in activeStalkerIdsWithTelegraph) {
                        z.attackCooldownMs = 2400L
                        val ang = atan2(playerY - z.y, playerX - z.x)
                        telegraphs.add(
                            LaserTelegraph(
                                sourceZombieId = z.id,
                                startX = z.x,
                                startY = z.y,
                                endX = z.x + cos(ang) * 440f,
                                endY = z.y + sin(ang) * 440f,
                                aimAngleRad = ang,
                                chargeRemainingMs = 820L,
                                totalChargeMs = 820L,
                                colorHex = 0xFFFF2A54
                            )
                        )
                    }
                }
                ZombieArchetype.ABYSSAL_WEAVER -> {
                    z.attackCooldownMs = (z.attackCooldownMs - deltaMs).coerceAtLeast(0L)
                    if (distToPlayer in 140f..460f && z.attackCooldownMs == 0L) {
                        z.attackCooldownMs = 2900L
                        val ang = atan2(playerY - z.y, playerX - z.x)
                        val elem = if (Random.nextBoolean()) ElementalAffinity.CRYO else ElementalAffinity.CORROSIVE
                        val speed = 185f
                        enemyProjectiles.add(
                            EnemyProjectile(
                                id = nextEntityIdProvider(),
                                x = z.x,
                                y = z.y,
                                vx = cos(ang) * speed,
                                vy = sin(ang) * speed,
                                damage = 14,
                                radius = 11f,
                                colorHex = elem.colorHex,
                                spawnsPuddleOnImpact = true,
                                elementKind = elem,
                                ttlMs = 2600L
                            )
                        )
                    }
                }
                else -> Unit
            }
        }

        // 2. Step Laser Telegraphs -> fire high-velocity Bone Shard when charge completes
        val updatedTelegraphs = mutableListOf<LaserTelegraph>()
        for (tg in telegraphs) {
            val shooter = zombies.find { it.id == tg.sourceZombieId && it.hp > 0 }
            if (shooter == null) continue
            val rem = tg.chargeRemainingMs - deltaMs
            if (rem <= 0L) {
                val speed = 440f
                enemyProjectiles.add(
                    EnemyProjectile(
                        id = nextEntityIdProvider(),
                        x = shooter.x,
                        y = shooter.y,
                        vx = cos(tg.aimAngleRad) * speed,
                        vy = sin(tg.aimAngleRad) * speed,
                        damage = 22,
                        radius = 7f,
                        colorHex = 0xFFFF2A54,
                        spawnsPuddleOnImpact = false,
                        elementKind = ElementalAffinity.KINETIC,
                        ttlMs = 1500L
                    )
                )
            } else {
                updatedTelegraphs.add(
                    tg.copy(
                        startX = shooter.x,
                        startY = shooter.y,
                        endX = shooter.x + cos(tg.aimAngleRad) * 440f,
                        endY = shooter.y + sin(tg.aimAngleRad) * 440f,
                        chargeRemainingMs = rem
                    )
                )
            }
        }
        telegraphs.clear()
        telegraphs.addAll(updatedTelegraphs)

        // 3. Step Enemy Projectiles (check TTL expiration first, then collision with Barricade Walls & Player)
        val survivingProjectiles = mutableListOf<EnemyProjectile>()
        for (proj in enemyProjectiles) {
            val nx = proj.x + proj.vx * dtSec
            val ny = proj.y + proj.vy * dtSec
            val rem = proj.ttlMs - deltaMs

            if (rem <= 0L) {
                if (proj.spawnsPuddleOnImpact) {
                    elementalPuddles.add(
                        ElementalPuddle(
                            id = nextEntityIdProvider(),
                            x = nx,
                            y = ny,
                            radius = 58f,
                            elementKind = proj.elementKind,
                            hitsPlayers = true,
                            hitsZombies = false
                        )
                    )
                }
                continue
            }

            // Check barricade wall interception
            val hitFort = fortifications.firstOrNull {
                it.kind == FortificationType.BARRICADE_WALL && it.hp > 0 && hypot(nx - it.x, ny - it.y) <= 28f
            }
            if (hitFort != null) {
                hitFort.hp = (hitFort.hp - proj.damage).coerceAtLeast(0)
                banners.add(FloatingBanner("BLOCKED!", hitFort.x, hitFort.y - 12f, 0xFF00FF66, 500L))
                continue
            }

            if (hypot(nx - playerX, ny - playerY) <= proj.radius + 16f) {
                totalPlayerDamageTaken += proj.damage
                banners.add(FloatingBanner("-${proj.damage} (${proj.elementKind.shortTag})", playerX, playerY - 22f, proj.colorHex, 700L))
                if (proj.spawnsPuddleOnImpact) {
                    elementalPuddles.add(
                        ElementalPuddle(
                            id = nextEntityIdProvider(),
                            x = nx,
                            y = ny,
                            radius = 58f,
                            elementKind = proj.elementKind,
                            hitsPlayers = true,
                            hitsZombies = false
                        )
                    )
                }
                continue
            }

            survivingProjectiles.add(proj.copy(x = nx, y = ny, ttlMs = rem))
        }
        enemyProjectiles.clear()
        enemyProjectiles.addAll(survivingProjectiles)

        // 4. Step Elemental Puddles (slows / damages entities inside radius)
        val survivingPuddles = mutableListOf<ElementalPuddle>()
        for (puddle in elementalPuddles) {
            val rem = puddle.ttlMs - deltaMs
            if (rem <= 0L) continue
            var tickTimer = (puddle.tickTimerMs - deltaMs).coerceAtLeast(0L)
            val triggerTick = tickTimer == 0L
            if (triggerTick) tickTimer = 600L

            if (puddle.hitsPlayers && hypot(playerX - puddle.x, playerY - puddle.y) <= puddle.radius) {
                if (puddle.elementKind == ElementalAffinity.CRYO) {
                    playerSlowFactor = 0.65f
                }
                if (triggerTick && puddle.elementKind == ElementalAffinity.CORROSIVE) {
                    totalPlayerDamageTaken += 6
                    banners.add(FloatingBanner("-6 ACID", playerX, playerY - 18f, puddle.colorHex, 450L))
                }
            }

            if (puddle.hitsZombies && triggerTick) {
                zombies.forEach { z ->
                    if (hypot(z.x - puddle.x, z.y - puddle.y) <= puddle.radius + 12f) {
                        z.hp -= 28
                    }
                }
            }

            survivingPuddles.add(puddle.copy(ttlMs = rem, tickTimerMs = tickTimer))
        }
        elementalPuddles.clear()
        elementalPuddles.addAll(survivingPuddles)

        // 5. Step Deployed Fortifications (Cryo Mines & Auto-Turret Beacons)
        val survivingForts = mutableListOf<FortificationEntity>()
        for (fort in fortifications) {
            val rem = fort.ttlMs - deltaMs
            if (rem <= 0L || fort.hp <= 0) continue

            when (fort.kind) {
                FortificationType.CRYO_MINE -> {
                    val triggeredByZombie = zombies.any { hypot(it.x - fort.x, it.y - fort.y) <= 68f }
                    if (triggeredByZombie) {
                        // Detonate in freezing AoE + spawn player-friendly Cryo Puddle
                        zombies.forEach { z ->
                            if (hypot(z.x - fort.x, z.y - fort.y) <= 125f) {
                                z.hp -= 110
                            }
                        }
                        elementalPuddles.add(
                            ElementalPuddle(
                                id = nextEntityIdProvider(),
                                x = fort.x,
                                y = fort.y,
                                radius = 92f,
                                elementKind = ElementalAffinity.CRYO,
                                hitsPlayers = false,
                                hitsZombies = true,
                                ttlMs = 7000L
                            )
                        )
                        banners.add(FloatingBanner("CRYO SHATTER!", fort.x, fort.y - 16f, 0xFF00E5FF, 900L))
                        repeat(12) {
                            particles.add(
                                ParticleBurst(
                                    x = fort.x,
                                    y = fort.y,
                                    vx = Random.nextInt(-180, 180).toFloat(),
                                    vy = Random.nextInt(-180, 180).toFloat(),
                                    colorHex = 0xFF00E5FF,
                                    ttlMs = 420L
                                )
                            )
                        }
                        continue // Mine consumed
                    }
                }
                FortificationType.AUTO_TURRET_BEACON -> {
                    val cd = (fort.fireCooldownMs - deltaMs).coerceAtLeast(0L)
                    if (cd == 0L) {
                        val target = zombies
                            .filter { hypot(it.x - fort.x, it.y - fort.y) <= 320f && it.hp > 0 }
                            .minByOrNull { hypot(it.x - fort.x, it.y - fort.y) }
                        if (target != null) {
                            target.hp -= 34
                            tracers.add(
                                BulletTracer(
                                    startX = fort.x,
                                    startY = fort.y,
                                    endX = target.x,
                                    endY = target.y,
                                    colorHex = 0xFFFFD166,
                                    ttlMs = 95L
                                )
                            )
                            fort.fireCooldownMs = 310L
                        } else {
                            fort.fireCooldownMs = 120L
                        }
                    } else {
                        fort.fireCooldownMs = cd
                    }
                }
                else -> Unit
            }

            survivingForts.add(fort.copy(ttlMs = rem))
        }
        fortifications.clear()
        fortifications.addAll(survivingForts)

        return Pair(totalPlayerDamageTaken, playerSlowFactor)
    }
}
