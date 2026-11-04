package com.example.game

import com.example.data.DEFAULT_WEAPONS
import com.example.data.OasisBotPersona
import com.example.data.PerkType
import com.example.data.WeaponSpec
import com.example.game.combat.BarrelNode
import com.example.game.combat.BoxheadComboAndPhysicsRegistry
import com.example.game.combat.BoxheadComboState
import com.example.game.economy.SingleCurrencyVendingEngine
import com.example.game.economy.VendingStockSnapshot
import com.example.game.relics.PassiveRelicKind
import com.example.game.relics.PassiveRelicStackState
import com.example.game.relics.Ror2PassiveRelicSystem
import com.example.game.world.elona.AssembledSectionReceipt
import com.example.game.world.elona.ElonaSocketFrontierEngine
import com.example.game.world.elona.SparseWorldStateCache
import com.example.network.CoopPeerState
import com.example.ui.render.PersistentSurfaceDecal
import com.example.ui.render.SurfaceDecalKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.system.measureNanoTime

fun Ror2PassiveRelicSystem.computeMoveSpeedMultiplier(state: PassiveRelicStackState): Float =
    state.moveSpeedMultiplier

fun Ror2PassiveRelicSystem.computeAttackSpeedMultiplier(state: PassiveRelicStackState): Float =
    state.attackCadenceMultiplier

fun Ror2PassiveRelicSystem.computeCrowbarMultiplier(
    targetHp: Int,
    targetMaxHp: Int,
    state: PassiveRelicStackState
): Float {
    if (targetMaxHp <= 0) return 1.0f
    return if (targetHp.toFloat() / targetMaxHp.toFloat() >= 0.90f) {
        state.openingStrikeMultiplier
    } else {
        1.0f
    }
}

fun Ror2PassiveRelicSystem.computeCritChanceBonus(state: PassiveRelicStackState): Float =
    (0.05f * state.countOf(PassiveRelicKind.HARVESTERS_SCYTHE)).coerceAtMost(0.65f)

fun DeterministicParticlePool.step(dtSeconds: Float) {
    step((dtSeconds * 1000f).toLong().coerceAtLeast(1L))
}

enum class ZombieArchetype(
    val label: String,
    val asciiChar: Char,
    val speedMultiplier: Float,
    val hpMultiplier: Float,
    val radius: Float,
    val rewardPoints: Int,
    val colorHex: Long
) {
    SHAMBLER("Rotting Husk", 'z', 0.85f, 1.0f, 16f, 70, 0xFF7AE582),
    SPRINTER("Hell Runner", 's', 1.20f, 0.75f, 14f, 90, 0xFFFF7B00),
    CRAWLER("Acid Crawler", 'c', 0.95f, 0.65f, 12f, 80, 0xFF00E5FF),
    CALCIFIED_STALKER("Calcified Stalker (Archer)", 'A', 0.78f, 1.35f, 15f, 130, 0xFFFF5252),
    ABYSSAL_WEAVER("Abyssal Weaver (Mage)", 'W', 0.72f, 1.65f, 17f, 160, 0xFFB388FF),
    DEVIL_BOSS("Boxhead Devil Anomaly", 'D', 0.74f, 4.8f, 25f, 450, 0xFFFF2A54)
}

data class ZombieEntity(
    val id: Int,
    val archetype: ZombieArchetype,
    var x: Float,
    var y: Float,
    var hp: Int,
    val maxHp: Int,
    var attackCooldownMs: Long = 0L
)

data class BulletTracer(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val colorHex: Long,
    var ttlMs: Long = 110L
)

data class ParticleBurst(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val colorHex: Long,
    var ttlMs: Long = 320L
)

data class FloatingBanner(
    val text: String,
    var x: Float,
    var y: Float,
    val colorHex: Long,
    var ttlMs: Long = 900L
)

/**
 * Dual-mode Speed Controller:
 * - clockMultiplier scales overall simulation time (affecting every mechanic equally)
 * - playerMoveMultiplier scales only player movement speed
 * - zombieSpeedMultiplier scales only zombie movement speed
 * - fireReloadMultiplier scales weapon fire rate & reload speed
 */
data class SpeedConfig(
    val presetName: String = "Classic 1.0x",
    val clockMultiplier: Float = 1.0f,
    val playerMoveMultiplier: Float = 1.0f,
    val zombieSpeedMultiplier: Float = 1.0f,
    val fireReloadMultiplier: Float = 1.0f
)

data class AmmoTelemetry(
    val shotsFired: Int = 0,
    val shotsHit: Int = 0,
    val reloadsCompleted: Int = 0,
    val currentReloadTotalMs: Long = 0L,
    val lightLockCrits: Int = 0
) {
    val accuracyPercent: Int
        get() = if (shotsFired <= 0) 100 else ((shotsHit * 100) / shotsFired).coerceIn(0, 100)
}

data class BenchmarkReport(
    val ticksSimulated: Int,
    val zombieCountPeak: Int,
    val totalElapsedMs: Double,
    val avgTickMicros: Double,
    val estimatedMaxFps: Int,
    val killsDuringTest: Int
)

data class GameSessionState(
    val mapSeed: Long = 19960227L,
    val map: ProceduralMap = ProceduralMapEngine.generateMap(mapSeed, ProceduralMapEngine.DEFAULT_UNLOCKED_ROOMS),
    val unlockedRooms: Set<Int> = ProceduralMapEngine.DEFAULT_UNLOCKED_ROOMS,
    val playerX: Float = ProceduralMapEngine.DEFAULT_SPAWN_X,
    val playerY: Float = ProceduralMapEngine.DEFAULT_SPAWN_Y,
    val playerVelX: Float = 0f,
    val playerVelY: Float = 0f,
    val aimAngleRad: Float = 0f,
    val hp: Int = 100,
    val maxHp: Int = 100,
    val shield: Int = 60,
    val maxShield: Int = 116,
    val wave: Int = 1,
    val zombiesRemainingInWave: Int = 8,
    val points: Int = 600,
    val totalKills: Int = 0,
    val comboCount: Int = 0,
    val comboTimerMs: Long = 0L,
    val unlockedWeaponIds: List<String> = listOf("pistol"),
    val weaponLevels: Map<String, Int> = mapOf("pistol" to 1),
    val currentWeaponIndex: Int = 0,
    val currentMagAmmo: Map<String, Int> = DEFAULT_WEAPONS.associate { it.id to it.magazineSize },
    val reloadRemainingMs: Long = 0L,
    val ammoTelemetry: AmmoTelemetry = AmmoTelemetry(),
    val speedConfig: SpeedConfig = SpeedConfig(),
    val aiBotEnabled: Boolean = false,
    val activePerks: Set<PerkType> = emptySet(),
    val zombies: List<ZombieEntity> = emptyList(),
    val tracers: List<BulletTracer> = emptyList(),
    val particles: List<ParticleBurst> = emptyList(),
    val floatingBanners: List<FloatingBanner> = emptyList(),
    val nearbyStation: InteractiveStation? = null,
    // Phase 1: Abyssal Bio-Noir Lighting & Camera Tension
    val cameraTension: CameraTensionState = CameraTensionState(),
    // Phase 2: Non-Euclidean 7-Node Ring Buffer & Chunk Entropy (reuses `map` instead of regenerating)
    val mazeState: NonEuclideanMazeState = MazeDirector.createInitialMazeState(
        mapSeed,
        map
    ),
    // Phase 3: Hybrid Combat, Archetypes & Auto-Drop Fortifications
    val selectedFortificationType: FortificationType = FortificationType.RED_BARREL,
    val fortifications: List<FortificationEntity> = emptyList(),
    val laserTelegraphs: List<LaserTelegraph> = emptyList(),
    val enemyProjectiles: List<EnemyProjectile> = emptyList(),
    val elementalPuddles: List<ElementalPuddle> = emptyList(),
    val siegeFabricator: SiegeFabricatorState = SiegeFabricatorState(),
    // Phase 4: Borderlands 4-Part Modular Weapons, Light-Lock & Time-Freeze Inspect
    val equippedModularWeapon: ModularWeaponInstance = ModularWeaponGenerator.createStarterModularWeapon("pistol"),
    val modularWeaponInventory: Map<String, ModularWeaponInstance> = mapOf(
        "pistol" to ModularWeaponGenerator.createStarterModularWeapon("pistol")
    ),
    val lootDrops: List<LootDropEntity> = emptyList(),
    val nearbyLootDrop: LootDropEntity? = null,
    val inspectedLootDrop: LootDropEntity? = null,
    val lightLockTargetId: Int? = null,
    // Phase 5: 7-Slot RPG Armor Rig, Palette Shader, Upgrade Anvil & Depth Escrow
    val armorRig: ArmorRigState = ArmorRigState(),
    val depthEscrow: DepthEscrowState = DepthEscrowState(),
    // Phase 6 (ARGON 4): 4-Player Co-Op Squad, Downed/Revive State & Async 600px Chunk Streamer
    val coopPeers: List<CoopPeerState> = emptyList(),
    val isPlayerDowned: Boolean = false,
    val playerBleedoutRemainingMs: Long = 30_000L,
    val playerReviveProgress: Float = 0f,
    val chunkSnapshot: ChunkStreamSnapshot = ChunkStreamSnapshot(),
    val passiveRelics: PassiveRelicStackState = PassiveRelicStackState(),
    val elonaWorldReceipt: AssembledSectionReceipt = ElonaSocketFrontierEngine.generateInitialWorldReceipt(mapSeed),
    val clockMs: Long = 0L,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val statusToast: String = "OPEN CITY ONLINE -- WASD Move | Click/Drag or Arrows to Aim & Fire!",
    val surfaceDecals: List<PersistentSurfaceDecal> = emptyList(),
    val particlePool: DeterministicParticlePool = DeterministicParticlePool(capacity = 256),
    val boxheadCombo: BoxheadComboState = BoxheadComboState(),
    val vendingStock: VendingStockSnapshot = SingleCurrencyVendingEngine.generateWaveVendingStock(1, mapSeed),
    val worldStateCache: SparseWorldStateCache = SparseWorldStateCache()
) {
    val currentWeapon: WeaponSpec
        get() {
            val id = unlockedWeaponIds.getOrNull(currentWeaponIndex) ?: "pistol"
            return DEFAULT_WEAPONS.find { it.id == id } ?: DEFAULT_WEAPONS.first()
        }

    val currentWeaponLevel: Int
        get() = weaponLevels[currentWeapon.id] ?: 1

    val activeModularWeaponForCurrentSlot: ModularWeaponInstance
        get() = modularWeaponInventory[currentWeapon.id] ?: equippedModularWeapon

    val currentMaxMagSize: Int
        get() {
            val modMag = activeModularWeaponForCurrentSlot.effectiveMagSize
            val tierBonus = WeaponUpgradeProgression.getTierStats(currentWeaponLevel).bonusMagCapacity
            return modMag + tierBonus
        }

    val currentAmmoCount: Int
        get() = currentMagAmmo[currentWeapon.id] ?: currentMaxMagSize

    val reloadProgressFraction: Float
        get() {
            if (reloadRemainingMs <= 0L) return 1f
            val total = ammoTelemetry.currentReloadTotalMs.coerceAtLeast(1L)
            return (1f - (reloadRemainingMs.toFloat() / total.toFloat())).coerceIn(0f, 1f)
        }

    val emulatorSpeedMultiplier: Float
        get() = speedConfig.clockMultiplier

    /**
     * Time-Freeze Loot Inspection pauses combat simulation automatically while inspecting a drop.
     */
    val isSimulationFrozen: Boolean
        get() = isPaused || isGameOver || inspectedLootDrop != null
}

/**
 * Pure, deterministic 60 FPS Boxhead + BO2 Zombies + Borderlands Modular Loot + Non-Euclidean Maze engine.
 * Can be stepped both from Jetpack Compose UI and headlessly via Konsole TCP port 48900.
 */
class BoxBoyGameEngine {
    private var nextEntityId = 1
    private var lastFireTimestampMs = -10_000L
    private var lastSpawnTimestampMs = 0L
    private var lastEntropyBreachSpawnMs = 0L
    private var lastRelicAuraTickMs = -10_000L
    private var clockMs = 0L
    val flowFieldServer = FlowFieldServer()
    val deterministicParticlePool = DeterministicParticlePool(capacity = 256)

    fun stepFrame(
        state: GameSessionState,
        deltaMsRaw: Long,
        moveXInput: Float,
        moveYInput: Float,
        isFiringInput: Boolean,
        isAltBarrelDropInput: Boolean,
        isInteractPressedInput: Boolean,
        coopPeerX: Float?,
        coopPeerY: Float?,
        aimAngleOverrideRad: Float? = null
    ): GameSessionState {
        if (state.isSimulationFrozen) return state

        val speedCfg = state.speedConfig
        val scaledDeltaMs = (deltaMsRaw.coerceIn(4L, 100L) * speedCfg.clockMultiplier).toLong().coerceAtLeast(1L)
        clockMs = max(clockMs, state.clockMs) + scaledDeltaMs

        var moveX = moveXInput
        var moveY = moveYInput
        var isFiring = isFiringInput
        var isAltBarrelDrop = isAltBarrelDropInput
        var isInteractPressed = isInteractPressedInput

        // Optional Autonomous AI / LLM Bot override for hands-free stress-testing
        if (state.aiBotEnabled && hypot(moveX, moveY) < 0.05f && !isFiring) {
            val nearest = state.zombies.minByOrNull { hypot(it.x - state.playerX, it.y - state.playerY) }
            val primedBarrel = state.map.barrels.firstOrNull { barrel ->
                !barrel.isDetonated &&
                    barrel.roomIndex in state.unlockedRooms &&
                    hypot(barrel.x - state.playerX, barrel.y - state.playerY) in 95f..420f &&
                    state.zombies.any { z -> hypot(z.x - barrel.x, z.y - barrel.y) <= 90f }
            }

            if (primedBarrel != null) {
                val angToBarrel = atan2(primedBarrel.y - state.playerY, primedBarrel.x - state.playerX)
                isFiring = true
                moveX = -sin(angToBarrel) * 0.75f
                moveY = cos(angToBarrel) * 0.75f
            } else if (nearest != null) {
                val dist = hypot(nearest.x - state.playerX, nearest.y - state.playerY)
                val angToZ = atan2(nearest.y - state.playerY, nearest.x - state.playerX)
                isFiring = dist < 460f
                if (dist < 145f) {
                    moveX = -cos(angToZ + 0.55f)
                    moveY = -sin(angToZ + 0.55f)
                } else if (dist > 260f) {
                    moveX = cos(angToZ) * 0.7f
                    moveY = sin(angToZ) * 0.7f
                } else {
                    moveX = -sin(angToZ) * 0.8f
                    moveY = cos(angToZ) * 0.8f
                }
            }
            if (state.nearbyStation != null && state.points >= state.nearbyStation.cost &&
                state.nearbyStation.kind != StationKind.UPGRADE_ANVIL
            ) {
                isInteractPressed = true
            }
        }

        var hp = state.hp
        var maxHp = if (PerkType.JUGGERNOG in state.activePerks) 200 else 100
        val maxShield = 50 + state.armorRig.totalBonusMaxShield
        var shield = state.shield.coerceAtMost(maxShield)
        var points = state.points
        var totalKills = state.totalKills
        var comboCount = state.comboCount
        var comboTimerMs = (state.comboTimerMs - scaledDeltaMs).coerceAtLeast(0L)
        if (comboTimerMs == 0L) comboCount = 0
        var boxheadCombo = BoxheadComboAndPhysicsRegistry.stepComboTimer(state.boxheadCombo, scaledDeltaMs)
        var depthEscrow = state.depthEscrow
        var elonaWorldReceipt = state.elonaWorldReceipt
        var vendingStock = state.vendingStock

        val zombies = state.zombies.map { it.copy() }.toMutableList()
        val tracers = state.tracers.mapNotNull {
            val rem = it.ttlMs - scaledDeltaMs
            if (rem > 0) it.copy(ttlMs = rem) else null
        }.toMutableList()
        val particles = state.particles.mapNotNull {
            val rem = it.ttlMs - scaledDeltaMs
            val dt = scaledDeltaMs / 1000f
            if (rem > 0) it.copy(x = it.x + it.vx * dt, y = it.y + it.vy * dt, ttlMs = rem) else null
        }.toMutableList()
        val banners = state.floatingBanners.mapNotNull {
            val rem = it.ttlMs - scaledDeltaMs
            val dt = scaledDeltaMs / 1000f
            if (rem > 0) it.copy(y = it.y - 22f * dt, ttlMs = rem) else null
        }.toMutableList()
        val barrels = state.map.barrels.toMutableList()
        val stations = state.map.stations.toMutableList()
        val fortifications = state.fortifications.map { it.copy() }.toMutableList()
        val laserTelegraphs = state.laserTelegraphs.map { it.copy() }.toMutableList()
        val enemyProjectiles = state.enemyProjectiles.map { it.copy() }.toMutableList()
        val elementalPuddles = state.elementalPuddles.map { it.copy() }.toMutableList()
        val lootDrops = state.lootDrops.toMutableList()
        val surfaceDecals = state.surfaceDecals.toMutableList()
        var passiveRelics = state.passiveRelics

        val dtSec = scaledDeltaMs / 1000f
        deterministicParticlePool.step(dtSec)
        state.particlePool.step(dtSec)

        // PHASE 3: Step Ranged Stalkers, Elemental Weavers, Projectiles, Puddles & Fortifications
        val (phase3Damage, puddleSlowFactor) = TacticalCombatCoordinator.stepPhase3Combat(
            deltaMs = scaledDeltaMs,
            dtSec = dtSec,
            playerX = state.playerX,
            playerY = state.playerY,
            zombies = zombies,
            telegraphs = laserTelegraphs,
            enemyProjectiles = enemyProjectiles,
            elementalPuddles = elementalPuddles,
            fortifications = fortifications,
            tracers = tracers,
            particles = particles,
            banners = banners,
            nextEntityIdProvider = { nextEntityId++ }
        )

        if (phase3Damage > 0) {
            val mitigated = (phase3Damage * (1f - state.armorRig.damageReductionFraction)).roundToInt().coerceAtLeast(1)
            val absorbedByShield = min(shield, mitigated)
            shield -= absorbedByShield
            hp = (hp - (mitigated - absorbedByShield)).coerceAtLeast(0)
        }

        val striderImmuneToSlow = state.armorRig.countForSet(ArmorSetArchetype.OVERCLOCK_STRIDER) >= 4
        val effectivePuddleSlow = if (striderImmuneToSlow) 1.0f else puddleSlowFactor

        // 1. Player Movement & Room Bounds Collision (with Velocity Inertia + 2x Physics Sub-Stepping + Smooth Corner-Slide)
        val perkSpeedBoost = if (PerkType.STAMIN_UP in state.activePerks) 1.25f else 1.0f
        val armorSpeedBoost = state.armorRig.totalMoveSpeedMultiplier
        val relicMoveBoost = Ror2PassiveRelicSystem.computeMoveSpeedMultiplier(passiveRelics)
        val moveSpeedPxPerSec = 232f * perkSpeedBoost * armorSpeedBoost * relicMoveBoost * effectivePuddleSlow * speedCfg.playerMoveMultiplier

        val targetVelX = moveX * moveSpeedPxPerSec
        val targetVelY = moveY * moveSpeedPxPerSec
        val hasMoveInput = hypot(moveX, moveY) > 0.03f
        val accelRate = if (hasMoveInput) 36f else 28f
        val velAlpha = (dtSec * accelRate).coerceIn(0.35f, 1f)
        var curVelX = state.playerVelX + (targetVelX - state.playerVelX) * velAlpha
        var curVelY = state.playerVelY + (targetVelY - state.playerVelY) * velAlpha
        if (!hasMoveInput && hypot(curVelX, curVelY) < 4f) {
            curVelX = 0f
            curVelY = 0f
        }

        // 2x Physics Sub-Stepping prevents tunneling and makes sliding along jagged borders & buildings silky-smooth
        var nx = state.playerX
        var ny = state.playerY
        val subSteps = 2
        val subDt = dtSec / subSteps.toFloat()
        repeat(subSteps) {
            val stepDx = curVelX * subDt
            val stepDy = curVelY * subDt
            val candX = nx + stepDx
            val candY = ny + stepDy

            if (isWalkable(candX, candY, state.map, state.unlockedRooms)) {
                nx = candX
                ny = candY
            } else {
                // Separate X and Y axis sliding with diagonal corner-nudge assistance
                val slideNudge = hypot(stepDx, stepDy) * 0.72f
                val canMoveX = isWalkable(candX, ny, state.map, state.unlockedRooms)
                val canMoveY = isWalkable(nx, candY, state.map, state.unlockedRooms)
                if (canMoveX) {
                    nx = candX
                } else if (abs(moveY) < 0.4f) {
                    if (isWalkable(candX, ny - 15f, state.map, state.unlockedRooms)) {
                        ny -= slideNudge
                    } else if (isWalkable(candX, ny + 15f, state.map, state.unlockedRooms)) {
                        ny += slideNudge
                    }
                }
                if (canMoveY) {
                    ny = candY
                } else if (abs(moveX) < 0.4f) {
                    if (isWalkable(nx - 15f, candY, state.map, state.unlockedRooms)) {
                        nx -= slideNudge
                    } else if (isWalkable(nx + 15f, candY, state.map, state.unlockedRooms)) {
                        nx += slideNudge
                    }
                }
            }
        }

        // PHASE 2: Step Non-Euclidean 7-Node Ring Buffer Maze Director & 10 Hz Dijkstra Flow-Field Server
        val (updatedMazeState, mazeSyncedMap) = MazeDirector.stepMazeDirector(
            mazeState = state.mazeState,
            map = state.map.copy(stations = stations, barrels = barrels),
            unlockedRooms = state.unlockedRooms,
            playerX = nx,
            playerY = ny,
            deltaMs = scaledDeltaMs
        )

        if (updatedMazeState.totalDoorwaysCrossed + 1 > depthEscrow.deepestMazeDepthReached) {
            depthEscrow = depthEscrow.copy(deepestMazeDepthReached = updatedMazeState.totalDoorwaysCrossed + 1)
        }

        val activeSquadTargets = state.coopPeers
            .filter { !it.isDowned && it.hp > 0 }
            .map { Pair(it.x, it.y) }

        flowFieldServer.updateIfDue(
            clockMs = clockMs,
            map = mazeSyncedMap,
            unlockedRooms = state.unlockedRooms,
            fortifications = fortifications,
            playerX = nx,
            playerY = ny,
            coopPeerX = coopPeerX,
            coopPeerY = coopPeerY,
            extraTargets = activeSquadTargets
        )

        // PHASE 4: Light-Lock Auto-Aim & Lantern Illumination Synergy + Responsive Turning
        val modWeapon = state.activeModularWeaponForCurrentSlot
        val totalLanternRange = 430f + state.armorRig.totalLanternRangeBonusPx + modWeapon.optic.lanternRangeBonusPx
        val totalConeSpread = 0.56f + modWeapon.optic.lightLockConeBonusRad

        var aimAngle = state.aimAngleRad
        if (aimAngleOverrideRad != null) {
            // Direct mouse/touch world pointer or Arrow-Key twin-stick aim: turn immediately & accurately
            var diff = aimAngleOverrideRad - aimAngle
            while (diff > Math.PI) diff -= (2.0 * Math.PI).toFloat()
            while (diff < -Math.PI) diff += (2.0 * Math.PI).toFloat()
            val aimLerp = (dtSec * 36f).coerceIn(0.62f, 1f)
            aimAngle += diff * aimLerp
        } else if (hypot(moveX, moveY) > 0.08f) {
            // Single-stick / WASD movement turning: turn crisply toward movement vector
            val desiredAngle = atan2(moveY, moveX)
            var diff = desiredAngle - aimAngle
            while (diff > Math.PI) diff -= (2.0 * Math.PI).toFloat()
            while (diff < -Math.PI) diff += (2.0 * Math.PI).toFloat()
            val turnLerp = (dtSec * 28f).coerceIn(0.45f, 1f)
            aimAngle += diff * turnLerp
        }

        val (lockTarget, isIlluminatedLock) = ModularWeaponGenerator.findLightLockTarget(
            zombies = zombies,
            playerX = nx,
            playerY = ny,
            currentAimRad = aimAngle,
            lanternRange = totalLanternRange,
            coneHalfSpreadRad = totalConeSpread
        )
        val lightLockTargetId = if (isIlluminatedLock) lockTarget?.id else null
        // Gentle front-cone magnetism ONLY when no manual aim override is active and target is within a narrow 22 deg front arc
        if (aimAngleOverrideRad == null && lockTarget != null && isFiring) {
            val desiredAim = atan2(lockTarget.y - ny, lockTarget.x - nx)
            var diff = desiredAim - aimAngle
            while (diff > Math.PI) diff -= (2.0 * Math.PI).toFloat()
            while (diff < -Math.PI) diff += (2.0 * Math.PI).toFloat()
            if (abs(diff) <= 0.38f) {
                val lockLerp = (dtSec * 14f).coerceIn(0.15f, 0.38f)
                aimAngle += diff * lockLerp
            }
        }

        // 2. Reload & Modular Weapon Firing + Ammo Telemetry
        val wasReloading = state.reloadRemainingMs > 0L
        var reloadRemainingMs = (state.reloadRemainingMs - scaledDeltaMs).coerceAtLeast(0L)
        val magAmmo = state.currentMagAmmo.toMutableMap()
        val weapon = state.currentWeapon
        val weaponLvl = state.currentWeaponLevel
        val effectiveMagSize = state.currentMaxMagSize
        var telemetry = state.ammoTelemetry

        if (reloadRemainingMs == 0L && (magAmmo[weapon.id] ?: 0) <= 0) {
            magAmmo[weapon.id] = effectiveMagSize
            if (wasReloading) {
                telemetry = telemetry.copy(reloadsCompleted = telemetry.reloadsCompleted + 1)
                banners.add(FloatingBanner("MAG FULL ($effectiveMagSize)", nx, ny - 24f, 0xFF00E5FF, 650L))
            }
        }

        val perkFireRateFactor = if (PerkType.DOUBLE_TAP in state.activePerks) 0.68f else 1.0f
        val relicAttackSpeedBoost = Ror2PassiveRelicSystem.computeAttackSpeedMultiplier(passiveRelics)
        val tierStats = WeaponUpgradeProgression.getTierStats(weaponLvl)
        val effectiveCooldown = (
            (modWeapon.effectiveFireCooldownMs * perkFireRateFactor * tierStats.fireCooldownMultiplier) /
                (speedCfg.fireReloadMultiplier * relicAttackSpeedBoost).coerceAtLeast(0.25f)
            ).toLong().coerceAtLeast(35L)

        var didFireThisTick = false
        if (isFiring && reloadRemainingMs == 0L && clockMs - lastFireTimestampMs >= effectiveCooldown) {
            lastFireTimestampMs = clockMs
            didFireThisTick = true
            val currentBullets = magAmmo[weapon.id] ?: effectiveMagSize
            val papMultiplier = tierStats.damageMultiplier
            val baseShotDamage = (modWeapon.effectiveDamage * papMultiplier).roundToInt()
            var anyPelletHit = false
            var anyCritHit = false

            repeat(modWeapon.effectivePelletCount) {
                val spreadScale = if (isIlluminatedLock) 0.65f else 1.0f
                val spreadRad = Math.toRadians(
                    ((Random.nextFloat() - 0.5f) * modWeapon.effectiveSpreadDegrees * spreadScale).toDouble()
                ).toFloat()
                val shotAngle = aimAngle + spreadRad
                val maxRange = 540f + modWeapon.barrel.rangeBonusPx
                var endX = nx + cos(shotAngle) * maxRange
                var endY = ny + sin(shotAngle) * maxRange

                // Check barrel hits with BoxheadComboAndPhysicsRegistry chain reaction & Blast-Surf Thrusters
                barrels.forEachIndexed { bIdx, barrel ->
                    if (!barrel.isDetonated && barrel.roomIndex in state.unlockedRooms) {
                        val distToRay = pointToSegmentDistance(barrel.x, barrel.y, nx, ny, endX, endY)
                        if (distToRay <= 24f) {
                            anyPelletHit = true
                            val barrelNodes = barrels.mapIndexedNotNull { idx, b ->
                                if (b.roomIndex in state.unlockedRooms) {
                                    BarrelNode(id = idx, x = b.x, y = b.y, detonated = b.isDetonated)
                                } else null
                            }
                            val surfStacks = max(
                                passiveRelics.countOf(PassiveRelicKind.BLAST_SURF_THRUSTERS),
                                1 // Default Blast-Surf safety boots or explicit relic stacks
                            )
                            val chainRes = BoxheadComboAndPhysicsRegistry.resolveBarrelChainReaction(
                                initialBarrelId = bIdx,
                                barrels = barrelNodes,
                                playerX = nx,
                                playerY = ny,
                                blastSurfBootsStacks = surfStacks
                            )
                            curVelX += chainRes.blastSurfImpulseVx * 0.35f
                            curVelY += chainRes.blastSurfImpulseVy * 0.35f
                            var chainKills = 0
                            var chainPts = 0
                            for (detIdx in chainRes.detonatedBarrelIds) {
                                if (detIdx in barrels.indices && !barrels[detIdx].isDetonated) {
                                    val db = barrels[detIdx]
                                    barrels[detIdx] = db.copy(isDetonated = true)
                                    val (kills, pts) = triggerBarrelExplosion(db.x, db.y, zombies, particles, banners)
                                    chainKills += kills
                                    chainPts += pts
                                    surfaceDecals.add(
                                        PersistentSurfaceDecal(
                                            decalId = "scorch_${clockMs}_$detIdx",
                                            worldX = db.x,
                                            worldY = db.y,
                                            radiusPx = 42f,
                                            kind = SurfaceDecalKind.BARREL_SCORCH_CRATER,
                                            colorArgb = 0xAA1B1F2A,
                                            createdAtTick = clockMs
                                        )
                                    )
                                }
                            }
                            if (chainKills > 0) {
                                totalKills += chainKills
                                points += chainPts
                                comboCount += chainKills
                                comboTimerMs = 4500L
                                boxheadCombo = BoxheadComboAndPhysicsRegistry.registerKillStreak(boxheadCombo, chainKills).first
                            }
                        }
                    }
                }

                // Check zombie hits along ray
                var pierced = 0
                val totalAllowedPierce = modWeapon.effectivePierceCount + tierStats.bonusPierceCount
                val hitCandidates = zombies
                    .filter { pointToSegmentDistance(it.x, it.y, nx, ny, endX, endY) <= it.archetype.radius + 8f }
                    .sortedBy { hypot(it.x - nx, it.y - ny) }

                for (z in hitCandidates) {
                    if (pierced >= totalAllowedPierce) break
                    anyPelletHit = true

                    val targetIlluminated = AbyssalChiaroscuroLightingEngine.isPointIlluminatedByPlayer(
                        targetX = z.x,
                        targetY = z.y,
                        playerX = nx,
                        playerY = ny,
                        aimAngleRad = aimAngle,
                        lanternRange = totalLanternRange,
                        coneHalfSpreadRad = totalConeSpread
                    )
                    val scytheCritBonus = Ror2PassiveRelicSystem.computeCritChanceBonus(passiveRelics)
                    val isScytheCrit = scytheCritBonus > 0f &&
                        (((clockMs + z.id * 37L) % 100L) < (scytheCritBonus * 100f).toLong() || scytheCritBonus >= 0.05f)
                    val isCritHit = targetIlluminated || isScytheCrit
                    val crowbarMult = Ror2PassiveRelicSystem.computeCrowbarMultiplier(z.hp, z.maxHp, passiveRelics)
                    val rawCritDamage = if (isCritHit) {
                        anyCritHit = true
                        baseShotDamage * modWeapon.illuminatedCritMultiplier
                    } else {
                        baseShotDamage.toFloat()
                    }
                    val finalHitDamage = (rawCritDamage * crowbarMult).roundToInt().coerceAtLeast(1)

                    if (isCritHit && passiveRelics.critHealAmount > 0) {
                        hp = (hp + passiveRelics.critHealAmount).coerceAtMost(maxHp)
                    }

                    z.hp -= finalHitDamage
                    pierced++
                    if (pierced == totalAllowedPierce) {
                        endX = z.x
                        endY = z.y
                    }

                    // Relic: Ukulele Arc Lightning on-hit chain
                    val ukuleleStacks = passiveRelics.countOf(PassiveRelicKind.UKULELE_ARC_LIGHTNING)
                    if (ukuleleStacks > 0) {
                        val arcDamage = (finalHitDamage * (0.40f + 0.15f * ukuleleStacks)).roundToInt().coerceAtLeast(8)
                        val arcTargets = zombies
                            .filter { it.id != z.id && it.hp > 0 && hypot(it.x - z.x, it.y - z.y) <= 195f }
                            .take(passiveRelics.arcLightningTargets)
                        for (arcZ in arcTargets) {
                            arcZ.hp -= arcDamage
                            tracers.add(
                                BulletTracer(
                                    startX = z.x,
                                    startY = z.y,
                                    endX = arcZ.x,
                                    endY = arcZ.y,
                                    colorHex = PassiveRelicKind.UKULELE_ARC_LIGHTNING.colorHex
                                )
                            )
                        }
                    }

                    // Relic: AtG Plasma Missile Mk.1 on-hit homing strike
                    val atgStacks = passiveRelics.countOf(PassiveRelicKind.ATG_PLASMA_MISSILE)
                    if (atgStacks > 0) {
                        val missileTarget = zombies
                            .filter { it.hp > 0 && hypot(it.x - z.x, it.y - z.y) <= 420f }
                            .maxByOrNull { it.hp } ?: z
                        val missileDmg = (finalHitDamage * (0.75f + 0.45f * atgStacks)).roundToInt().coerceAtLeast(15)
                        missileTarget.hp -= missileDmg
                        tracers.add(
                            BulletTracer(
                                startX = nx,
                                startY = ny,
                                endX = missileTarget.x,
                                endY = missileTarget.y,
                                colorHex = PassiveRelicKind.ATG_PLASMA_MISSILE.colorHex
                            )
                        )
                    }

                    if (weapon.isExplosive) {
                        triggerBarrelExplosion(z.x, z.y, zombies, particles, banners, radius = 95f, damage = finalHitDamage)
                    }
                    if (particles.size < 120) {
                        deterministicParticlePool.spawnBurst(
                            x = z.x,
                            y = z.y,
                            count = 2,
                            speedMin = -120,
                            speedMax = 120,
                            colorHex = if (z.hp <= 0) 0xFFFF2A54 else modWeapon.magazine.elementalAffinity.colorHex,
                            ttlMs = 320L
                        )
                        repeat(2) {
                            val vx = deterministicParticlePool.nextDeterministicInt(-120, 120).toFloat()
                            val vy = deterministicParticlePool.nextDeterministicInt(-120, 120).toFloat()
                            particles.add(
                                ParticleBurst(
                                    x = z.x,
                                    y = z.y,
                                    vx = vx,
                                    vy = vy,
                                    colorHex = if (z.hp <= 0) 0xFFFF2A54 else modWeapon.magazine.elementalAffinity.colorHex
                                )
                            )
                        }
                    }
                    if (z.hp <= 0) {
                        comboCount++
                        comboTimerMs = 4500L
                        boxheadCombo = BoxheadComboAndPhysicsRegistry.registerKillStreak(boxheadCombo, 1).first
                        val mult = (1 + comboCount / 5).coerceAtMost(8)
                        val earned = z.archetype.rewardPoints * mult
                        points += earned
                        totalKills++
                        depthEscrow = depthEscrow.copy(abyssalScrap = depthEscrow.abyssalScrap + 2)
                        if (state.armorRig.countForSet(ArmorSetArchetype.VOID_ALCHEMIST) >= 4) {
                            shield = (shield + 5).coerceAtMost(maxShield)
                        }
                        surfaceDecals.add(
                            PersistentSurfaceDecal(
                                decalId = "ichor_${clockMs}_${z.id}",
                                worldX = z.x,
                                worldY = z.y,
                                radiusPx = 18f,
                                kind = SurfaceDecalKind.ZOMBIE_ICHOR_SPLATTER,
                                colorArgb = 0x993A0CA3,
                                createdAtTick = clockMs
                            )
                        )
                        // Relic: Will-o'-the-Wisp Lava Pillar Detonation on kill
                        val wispStacks = passiveRelics.countOf(PassiveRelicKind.WILL_O_WISP_JAR)
                        if (wispStacks > 0) {
                            val wispRadius = 95f + 22f * wispStacks
                            val wispDmg = 45 + 30 * wispStacks
                            for (otherZ in zombies) {
                                if (otherZ.id != z.id && otherZ.hp > 0 && hypot(otherZ.x - z.x, otherZ.y - z.y) <= wispRadius) {
                                    otherZ.hp -= wispDmg
                                }
                            }
                            surfaceDecals.add(
                                PersistentSurfaceDecal(
                                    decalId = "wisp_${clockMs}_${z.id}",
                                    worldX = z.x,
                                    worldY = z.y,
                                    radiusPx = 32f,
                                    kind = SurfaceDecalKind.BARREL_SCORCH_CRATER,
                                    colorArgb = 0xAAFF595E,
                                    createdAtTick = clockMs
                                )
                            )
                            banners.add(
                                FloatingBanner(
                                    text = "WISP PILLAR!",
                                    x = z.x,
                                    y = z.y - 36f,
                                    colorHex = PassiveRelicKind.WILL_O_WISP_JAR.colorHex
                                )
                            )
                        }
                        banners.add(
                            FloatingBanner(
                                text = "+$earned ${if (isCritHit) "CRIT!" else ""}",
                                x = z.x,
                                y = z.y - 14f,
                                colorHex = if (isCritHit) 0xFF00E5FF else 0xFF00FF66
                            )
                        )
                        // Phase 4 & 5: Roll procedural Modular Weapon or 7-Slot Armor drop
                        maybeSpawnLootDrop(
                            zombie = z,
                            wave = state.wave,
                            lootDrops = lootDrops,
                            banners = banners
                        )
                        // Track 6: Risk of Rain 2 Drop-Only Passive Skill Relic auto-stacking on Devil Boss or Combo Milestone
                        val droppedRelic = Ror2PassiveRelicSystem.rollRareRelicDrop(
                            wave = state.wave,
                            isBossKill = z.archetype == ZombieArchetype.DEVIL_BOSS,
                            isComboMilestone = comboCount > 0 && comboCount % 8 == 0,
                            seed = state.mapSeed xor (totalKills.toLong() * 31L) xor clockMs
                        )
                        if (droppedRelic != null) {
                            passiveRelics = Ror2PassiveRelicSystem.collectRelicDrop(passiveRelics, droppedRelic, 1)
                            val newCount = passiveRelics.countOf(droppedRelic)
                            banners.add(
                                FloatingBanner(
                                    text = "RELIC: ${droppedRelic.displayName} (x$newCount)",
                                    x = z.x,
                                    y = z.y - 28f,
                                    colorHex = droppedRelic.colorHex
                                )
                            )
                        }
                    }
                }

                tracers.add(
                    BulletTracer(
                        startX = nx + cos(shotAngle) * 16f,
                        startY = ny + sin(shotAngle) * 16f,
                        endX = endX,
                        endY = endY,
                        colorHex = if (weaponLvl >= 2) 0xFF00E5FF else modWeapon.magazine.elementalAffinity.colorHex
                    )
                )
            }

            zombies.removeAll { it.hp <= 0 }
            telemetry = telemetry.copy(
                shotsFired = telemetry.shotsFired + 1,
                shotsHit = telemetry.shotsHit + if (anyPelletHit) 1 else 0,
                lightLockCrits = telemetry.lightLockCrits + if (anyCritHit) 1 else 0
            )

            val nextAmmo = currentBullets - 1
            if (nextAmmo <= 0) {
                val perkReloadFactor = if (PerkType.SPEED_COLA in state.activePerks) 0.5f else 1.0f
                val armorReloadBoost = state.armorRig.totalReloadSpeedMultiplier
                val totalReloadMs = (
                    (modWeapon.effectiveReloadMs * perkReloadFactor * tierStats.reloadTimeMultiplier) /
                        (speedCfg.fireReloadMultiplier * armorReloadBoost * relicAttackSpeedBoost).coerceAtLeast(0.25f)
                    ).toLong().coerceAtLeast(180L)
                reloadRemainingMs = totalReloadMs
                telemetry = telemetry.copy(currentReloadTotalMs = totalReloadMs)
                magAmmo[weapon.id] = 0
            } else {
                magAmmo[weapon.id] = nextAmmo
            }
        }

        // Passive Relic Auras: Frost Relic Icicle & Tesla Coil Beacon
        val frostStacks = passiveRelics.countOf(PassiveRelicKind.FROST_RELIC_ICICLE)
        val teslaStacks = passiveRelics.countOf(PassiveRelicKind.TESLA_COIL_BEACON)
        if ((frostStacks > 0 || teslaStacks > 0) && clockMs - lastRelicAuraTickMs >= 250L) {
            lastRelicAuraTickMs = clockMs
            if (frostStacks > 0) {
                val frostRadius = 140f + 22f * frostStacks
                val frostDmg = 8 + 6 * frostStacks
                for (z in zombies) {
                    if (z.hp > 0 && hypot(z.x - nx, z.y - ny) <= frostRadius) {
                        z.hp -= frostDmg
                    }
                }
            }
            if (teslaStacks > 0) {
                val teslaRadius = 195f + 20f * teslaStacks
                val teslaDmg = 14 + 10 * teslaStacks
                val zapped = zombies
                    .filter { it.hp > 0 && hypot(it.x - nx, it.y - ny) <= teslaRadius }
                    .take(2 + teslaStacks)
                for (z in zapped) {
                    z.hp -= teslaDmg
                    tracers.add(
                        BulletTracer(
                            startX = nx,
                            startY = ny,
                            endX = z.x,
                            endY = z.y,
                            colorHex = PassiveRelicKind.TESLA_COIL_BEACON.colorHex
                        )
                    )
                }
            }
        }

        // Remove any zombies killed by Cryo Mines, Puddles, Auto-Turret Beacons, or Relic Auras
        val secondaryKills = zombies.filter { it.hp <= 0 }
        for (zk in secondaryKills) {
            totalKills++
            points += zk.archetype.rewardPoints
            boxheadCombo = BoxheadComboAndPhysicsRegistry.registerKillStreak(boxheadCombo, 1).first
            surfaceDecals.add(
                PersistentSurfaceDecal(
                    decalId = "ichor_${clockMs}_${zk.id}",
                    worldX = zk.x,
                    worldY = zk.y,
                    radiusPx = 18f,
                    kind = SurfaceDecalKind.ZOMBIE_ICHOR_SPLATTER,
                    colorArgb = 0x993A0CA3,
                    createdAtTick = clockMs
                )
            )
            if (passiveRelics.countOf(PassiveRelicKind.WILL_O_WISP_JAR) > 0) {
                surfaceDecals.add(
                    PersistentSurfaceDecal(
                        decalId = "wisp_${clockMs}_${zk.id}",
                        worldX = zk.x,
                        worldY = zk.y,
                        radiusPx = 32f,
                        kind = SurfaceDecalKind.BARREL_SCORCH_CRATER,
                        colorArgb = 0xAAFF595E,
                        createdAtTick = clockMs
                    )
                )
            }
            maybeSpawnLootDrop(zk, state.wave, lootDrops, banners)
        }
        zombies.removeAll { it.hp <= 0 }

        if (surfaceDecals.size > 128) {
            surfaceDecals.subList(0, surfaceDecals.size - 128).clear()
        }

        // 3. Phase 3 Contextual 1-Tap Fortification Placement (Barrel / Barricade Wall / Cryo Mine / Auto-Turret)
        var statusToast = state.statusToast
        if (isAltBarrelDrop) {
            val fortType = state.selectedFortificationType
            if (fortType == FortificationType.RED_BARREL) {
                if (points >= fortType.costPoints && barrels.count { !it.isDetonated } < 18) {
                    points -= fortType.costPoints
                    barrels.add(
                        ExplosiveBarrel(
                            id = nextEntityId++,
                            x = nx,
                            y = ny,
                            roomIndex = updatedMazeState.activeRoomSector
                        )
                    )
                    banners.add(FloatingBanner("BARREL PLACED!", nx, ny - 20f, 0xFFFF2A54))
                }
            } else if (points >= fortType.costPoints && fortifications.size < 14) {
                points -= fortType.costPoints
                fortifications.add(
                    FortificationEntity(
                        id = nextEntityId++,
                        kind = fortType,
                        x = nx,
                        y = ny,
                        roomIndex = updatedMazeState.activeRoomSector
                    )
                )
                banners.add(FloatingBanner("${fortType.shortCode} DEPLOYED!", nx, ny - 20f, fortType.colorHex))
                statusToast = "DEPLOYED ${fortType.displayName.uppercase()} (-${fortType.costPoints} PTS)"
            }
        }

        // Step Siege Fabricator Timer (Phase 3)
        var siegeState = state.siegeFabricator
        if (siegeState.isActive) {
            val remSiege = (siegeState.remainingMs - scaledDeltaMs).coerceAtLeast(0L)
            if (remSiege == 0L) {
                siegeState = siegeState.copy(
                    isActive = false,
                    remainingMs = 0L,
                    fabricationsCompleted = siegeState.fabricationsCompleted + 1
                )
                // Spawn guaranteed Epic/Legendary Modular Weapon + Armor Cache + Crystals
                val dropSeed = clockMs xor state.mapSeed
                val rewardWpn = ModularWeaponGenerator.generateProceduralWeapon(
                    seed = dropSeed,
                    wave = state.wave + 2,
                    guaranteedMinRarity = LootRarity.EPIC
                )
                val rewardArmor = RpgArmorGenerator.generateProceduralArmorPiece(
                    seed = dropSeed xor 0x99AAFFL,
                    wave = state.wave + 2,
                    minRarity = LootRarity.RARE
                )
                lootDrops.add(
                    LootDropEntity(
                        id = nextEntityId++,
                        x = nx + 36f,
                        y = ny,
                        rarity = rewardWpn.rarity,
                        payloadKind = LootPayloadKind.MODULAR_WEAPON,
                        weaponPayload = rewardWpn,
                        spawnedAtWave = state.wave
                    )
                )
                lootDrops.add(
                    LootDropEntity(
                        id = nextEntityId++,
                        x = nx - 36f,
                        y = ny,
                        rarity = rewardArmor.rarity,
                        payloadKind = LootPayloadKind.RPG_ARMOR_PIECE,
                        armorPayload = rewardArmor,
                        spawnedAtWave = state.wave
                    )
                )
                depthEscrow = depthEscrow.copy(
                    abyssalScrap = depthEscrow.abyssalScrap + 65,
                    abyssalCrystals = depthEscrow.abyssalCrystals + 2
                )
                points += 850
                banners.add(FloatingBanner("SIEGE COMPLETE! +LOOT CACHE", nx, ny - 32f, 0xFFFFD166, 1600L))
                statusToast = "SIEGE FABRICATION COMPLETE -- Epic Weapon & Armor Cache Dropped!"
            } else {
                siegeState = siegeState.copy(remainingMs = remSiege)
            }
        }

        // Phase 2 Chunk Entropy Anti-Turtling Breacher Spawn (>180s in same room)
        val activeEntropyStage = updatedMazeState.currentRoomNode?.entropyStage ?: RoomEntropyStage.STABLE
        if (activeEntropyStage == RoomEntropyStage.CRITICAL_BREACH &&
            clockMs - lastEntropyBreachSpawnMs >= 4200L &&
            zombies.size < 36
        ) {
            lastEntropyBreachSpawnMs = clockMs
            val arch = if (Random.nextBoolean()) ZombieArchetype.CALCIFIED_STALKER else ZombieArchetype.ABYSSAL_WEAVER
            val zHp = ((65 + state.wave * 26) * arch.hpMultiplier).toInt()
            zombies.add(
                ZombieEntity(
                    id = nextEntityId++,
                    archetype = arch,
                    x = (nx + Random.nextInt(-220, 220)).coerceIn(110f, 4660f),
                    y = (ny + Random.nextInt(-220, 220)).coerceIn(110f, 2620f),
                    hp = zHp,
                    maxHp = zHp
                )
            )
            banners.add(FloatingBanner("ENTROPY BREACH!", nx, ny - 30f, 0xFFFF2A54, 1100L))
        }

        // 4. Spawn Zombies from Active Sector Windows (measured tactical cadence)
        var remainingToSpawn = state.zombiesRemainingInWave
        var wave = state.wave
        val maxConcurrentZombies = (8 + wave * 2).coerceAtMost(32)
        val baseSpawnInterval = (2450L - wave * 75L).coerceAtLeast(850L)
        val spawnIntervalMs = if (siegeState.isActive) (baseSpawnInterval * 0.72f).toLong() else baseSpawnInterval

        if ((remainingToSpawn > 0 || siegeState.isActive) &&
            zombies.size < maxConcurrentZombies &&
            clockMs - lastSpawnTimestampMs >= spawnIntervalMs
        ) {
            lastSpawnTimestampMs = clockMs
            val activeWindows = stations.filter {
                it.kind == StationKind.BARRICADE_WINDOW && it.roomIndex in state.unlockedRooms
            }
            val spawnWin = activeWindows.randomOrNull()
            if (spawnWin != null) {
                val winIdx = stations.indexOfFirst { it.id == spawnWin.id }
                if (winIdx >= 0 && stations[winIdx].barricadePlanks > 0 && Random.nextFloat() < 0.45f) {
                    stations[winIdx] = stations[winIdx].copy(barricadePlanks = stations[winIdx].barricadePlanks - 1)
                } else {
                    val roll = Random.nextFloat()
                    val arch = when {
                        wave % 4 == 0 && remainingToSpawn == 1 -> ZombieArchetype.DEVIL_BOSS
                        (wave >= 2 || siegeState.isActive) && roll < 0.20f -> ZombieArchetype.CALCIFIED_STALKER
                        (wave >= 3 || siegeState.isActive) && roll < 0.36f -> ZombieArchetype.ABYSSAL_WEAVER
                        wave >= 3 && roll < 0.56f -> ZombieArchetype.SPRINTER
                        wave >= 2 && roll < 0.72f -> ZombieArchetype.CRAWLER
                        else -> ZombieArchetype.SHAMBLER
                    }
                    val baseHp = (55 + wave * 24)
                    val zHp = (baseHp * arch.hpMultiplier).toInt()
                    zombies.add(
                        ZombieEntity(
                            id = nextEntityId++,
                            archetype = arch,
                            x = spawnWin.x + Random.nextInt(-22, 22),
                            y = spawnWin.y + Random.nextInt(-22, 22),
                            hp = zHp,
                            maxHp = zHp
                        )
                    )
                    if (remainingToSpawn > 0) remainingToSpawn--
                }
            }
        }

        // Advance Wave when all zombies cleared
        if (remainingToSpawn <= 0 && zombies.isEmpty() && !siegeState.isActive) {
            wave++
            remainingToSpawn = 7 + wave * 3
            hp = maxHp
            shield = maxShield
            for (i in barrels.indices) {
                barrels[i] = barrels[i].copy(isDetonated = false)
            }
            vendingStock = SingleCurrencyVendingEngine.generateWaveVendingStock(wave, state.mapSeed)
            depthEscrow = depthEscrow.copy(abyssalScrap = depthEscrow.abyssalScrap + 25)
            statusToast = "WAVE $wave INCOMING -- Shield Recharged & +25 Abyssal Scrap!"
            banners.add(FloatingBanner("WAVE $wave!", nx, ny - 30f, 0xFFFFD166, ttlMs = 1500L))
        }

        // 5. Phase 2 & ARGON 4: Multi-Player Squad Bot AI, Dynamic Aggro, Flow-Field Steering & Co-Op Revive
        var squadPeers = state.coopPeers.map { it.copy() }.toMutableList()

        // Step Autonomous AI Squad Bots (@willow, @cinder, @mote)
        val botKillsAndPoints = stepSquadBots(
            squadPeers = squadPeers,
            deltaMs = scaledDeltaMs,
            dtSec = dtSec,
            playerX = nx,
            playerY = ny,
            isPlayerDowned = state.isPlayerDowned,
            wave = wave,
            map = mazeSyncedMap,
            unlockedRooms = state.unlockedRooms,
            zombies = zombies,
            barrels = barrels,
            stations = stations,
            tracers = tracers,
            particles = particles,
            banners = banners,
            lootDrops = lootDrops
        )
        totalKills += botKillsAndPoints.first
        points += botKillsAndPoints.second

        val zBaseSpeed = (36f + wave * 2.4f).coerceAtMost(82f) * speedCfg.zombieSpeedMultiplier
        val zombieCount = zombies.size
        HordeSteeringEngine.rebuildSpatialGrid(zombies)
        for (zi in 0 until zombieCount) {
            val z = zombies[zi]
            // Evaluate distance to player (if not downed) and all living squad members
            val dPlayer = if (!state.isPlayerDowned || squadPeers.none { !it.isDowned && it.hp > 0 }) {
                hypot(nx - z.x, ny - z.y)
            } else {
                Float.MAX_VALUE
            }
            val dLegacyPeer = if (coopPeerX != null && coopPeerY != null) {
                hypot(coopPeerX - z.x, coopPeerY - z.y)
            } else {
                Float.MAX_VALUE
            }

            var bestTx = if (dLegacyPeer < dPlayer && coopPeerX != null) coopPeerX else nx
            var bestTy = if (dLegacyPeer < dPlayer && coopPeerY != null) coopPeerY else ny
            var minDist = min(dPlayer, dLegacyPeer)
            var targetedPeerIndex = -1

            for (pi in 0 until squadPeers.size) {
                val peer = squadPeers[pi]
                if (!peer.isDowned && peer.hp > 0) {
                    val dp = hypot(peer.x - z.x, peer.y - z.y)
                    if (dp < minDist) {
                        minDist = dp
                        bestTx = peer.x
                        bestTy = peer.y
                        targetedPeerIndex = pi
                    }
                }
            }
            val tx = bestTx
            val ty = bestTy
            val dist = minDist.coerceAtLeast(0.001f)

            // Check if a player Barricade Wall is blocking this enemy
            val blockingWall = fortifications.firstOrNull {
                it.kind == FortificationType.BARRICADE_WALL &&
                    it.hp > 0 &&
                    hypot(it.x - z.x, it.y - z.y) <= z.archetype.radius + 24f
            }
            if (blockingWall != null) {
                z.attackCooldownMs = (z.attackCooldownMs - scaledDeltaMs).coerceAtLeast(0L)
                if (z.attackCooldownMs == 0L) {
                    z.attackCooldownMs = 950L
                    blockingWall.hp = (blockingWall.hp - 20).coerceAtLeast(0)
                }
                continue
            }

            // Ranged archetypes (Calcified Stalker & Abyssal Weaver) hold standoff distance when within 220..360px
            val holdsStandoff = (z.archetype == ZombieArchetype.CALCIFIED_STALKER ||
                z.archetype == ZombieArchetype.ABYSSAL_WEAVER) && dist in 220f..360f

            if (!holdsStandoff && dist > z.archetype.radius + 14f) {
                val (flowVx, flowVy) = flowFieldServer.sampleFlowVector(z.x, z.y, tx, ty)
                val steeringResult = HordeSteeringEngine.computeHordeSteering(
                    zombieIndex = zi,
                    zombies = zombies,
                    targetX = tx,
                    targetY = ty,
                    flowVx = flowVx,
                    flowVy = flowVy
                )
                val steerX = steeringResult.steerX
                val steerY = steeringResult.steerY

                val directVx = (tx - z.x) / dist
                val directVy = (ty - z.y) / dist

                val step = zBaseSpeed * z.archetype.speedMultiplier * dtSec
                val candX = z.x + steerX * step
                val candY = z.y + steerY * step
                if (isWalkable(candX, candY, mazeSyncedMap, state.unlockedRooms)) {
                    z.x = candX
                    z.y = candY
                } else if (isWalkable(candX, z.y, mazeSyncedMap, state.unlockedRooms)) {
                    z.x = candX
                } else if (isWalkable(z.x, candY, mazeSyncedMap, state.unlockedRooms)) {
                    z.y = candY
                } else {
                    z.x += directVx * step * 0.6f
                    z.y += directVy * step * 0.6f
                }
            } else if (targetedPeerIndex >= 0 && dist <= z.archetype.radius + 18f) {
                z.attackCooldownMs = (z.attackCooldownMs - scaledDeltaMs).coerceAtLeast(0L)
                if (z.attackCooldownMs == 0L) {
                    z.attackCooldownMs = 1050L
                    val rawDmg = if (z.archetype == ZombieArchetype.DEVIL_BOSS) 22 else 12
                    val peer = squadPeers[targetedPeerIndex]
                    val absorbed = min(peer.shield, rawDmg)
                    val newShield = peer.shield - absorbed
                    val newHp = (peer.hp - (rawDmg - absorbed)).coerceAtLeast(0)
                    val becameDowned = newHp == 0 && !peer.isDowned
                    squadPeers[targetedPeerIndex] = peer.copy(
                        hp = newHp,
                        shield = newShield,
                        isDowned = peer.isDowned || becameDowned,
                        bleedoutRemainingMs = if (becameDowned) 30_000L else peer.bleedoutRemainingMs,
                        reviveProgress = if (becameDowned) 0f else peer.reviveProgress,
                        radioCalloutText = if (becameDowned) "MAN DOWN! Need revive!" else peer.radioCalloutText,
                        radioCalloutTtlMs = if (becameDowned) 3500L else peer.radioCalloutTtlMs
                    )
                    if (becameDowned) {
                        banners.add(FloatingBanner("${peer.playerName} DOWNED!", peer.x, peer.y - 22f, 0xFFFF2A54, 1400L))
                    }
                }
            } else if (dPlayer <= z.archetype.radius + 18f && !state.isPlayerDowned) {
                z.attackCooldownMs = (z.attackCooldownMs - scaledDeltaMs).coerceAtLeast(0L)
                if (z.attackCooldownMs == 0L) {
                    z.attackCooldownMs = 1050L
                    val rawDmg = if (z.archetype == ZombieArchetype.DEVIL_BOSS) 26 else 14
                    val mitigated = (rawDmg * (1f - state.armorRig.damageReductionFraction)).roundToInt().coerceAtLeast(2)
                    val absorbed = min(shield, mitigated)
                    shield -= absorbed
                    val hpLoss = mitigated - absorbed
                    hp = (hp - hpLoss).coerceAtLeast(0)
                    banners.add(FloatingBanner("-$mitigated", nx, ny - 18f, 0xFFFF2A54))
                }
            }
        }

        // Step Co-Op Downed Bleedout & Proximity Revive (90f radius, 3.0s standard / 1.5s Quick Revive)
        var activePerks = state.activePerks
        var isPlayerDowned = state.isPlayerDowned
        var playerBleedoutMs = state.playerBleedoutRemainingMs
        var playerReviveProg = state.playerReviveProgress
        var isGameOver = false
        val reviveDurationMs = if (PerkType.QUICK_REVIVE in activePerks) 1500f else 3000f

        if (hp <= 0 && !isPlayerDowned) {
            if (PerkType.QUICK_REVIVE in activePerks || state.aiBotEnabled) {
                activePerks = activePerks - PerkType.QUICK_REVIVE
                hp = maxHp
                shield = maxShield
                zombies.removeIf { hypot(it.x - nx, it.y - ny) < 180f }
                statusToast = "QUICK REVIVE ACTIVATED -- Shockwave cleared nearby undead!"
                banners.add(FloatingBanner("SELF-REVIVED!", nx, ny - 28f, 0xFF00E5FF, 1500L))
            } else if (squadPeers.any { !it.isDowned && it.hp > 0 } || (coopPeerX != null && coopPeerY != null)) {
                isPlayerDowned = true
                playerBleedoutMs = 30_000L
                playerReviveProg = 0f
                statusToast = "DOWNED (30s BLEEDOUT) -- Teammate within 90px can revive you!"
                banners.add(FloatingBanner("DOWNED! AWAITING REVIVE", nx, ny - 28f, 0xFFFFD166, 1500L))
            } else {
                isGameOver = true
                statusToast = "SURVIVOR DOWN AT WAVE $wave -- Tap Replay Seed or Load Save State"
            }
        }

        if (isPlayerDowned) {
            playerBleedoutMs = (playerBleedoutMs - scaledDeltaMs).coerceAtLeast(0L)
            val reviverNearPlayer = squadPeers.any { !it.isDowned && it.hp > 0 && hypot(it.x - nx, it.y - ny) <= 90f } ||
                (coopPeerX != null && coopPeerY != null && hypot(coopPeerX - nx, coopPeerY - ny) <= 90f)

            if (reviverNearPlayer) {
                playerReviveProg = (playerReviveProg + (scaledDeltaMs.toFloat() / reviveDurationMs)).coerceAtMost(1f)
                if (playerReviveProg >= 1f) {
                    isPlayerDowned = false
                    playerReviveProg = 0f
                    playerBleedoutMs = 30_000L
                    hp = 100
                    shield = 50.coerceAtMost(maxShield)
                    points += 250
                    banners.add(FloatingBanner("+250 CO-OP REVIVE!", nx, ny - 30f, 0xFF00FF66, 1500L))
                    statusToast = "REVIVED BY SQUADMATE -- Restored 100 HP + 50 Shield!"
                }
            } else {
                playerReviveProg = (playerReviveProg - (scaledDeltaMs.toFloat() / 6000f)).coerceAtLeast(0f)
            }

            val anyLivingTeammate = squadPeers.any { !it.isDowned && it.hp > 0 } || (coopPeerX != null && coopPeerY != null)
            if (playerBleedoutMs <= 0L || !anyLivingTeammate) {
                isGameOver = true
                statusToast = "SQUAD WIPED AT WAVE $wave -- Tap Replay Seed or Load Save State"
            }
        }

        // Step proximity revives for any downed squadPeers
        for (pi in 0 until squadPeers.size) {
            val peer = squadPeers[pi]
            if (peer.isDowned) {
                val remBleed = (peer.bleedoutRemainingMs - scaledDeltaMs).coerceAtLeast(0L)
                val playerNearPeer = !isPlayerDowned && hp > 0 && hypot(nx - peer.x, ny - peer.y) <= 90f
                val otherBotNearPeer = squadPeers.any { other ->
                    other.peerId != peer.peerId && !other.isDowned && other.hp > 0 && hypot(other.x - peer.x, other.y - peer.y) <= 90f
                }
                if (playerNearPeer || otherBotNearPeer) {
                    val nextProg = (peer.reviveProgress + (scaledDeltaMs.toFloat() / reviveDurationMs)).coerceAtMost(1f)
                    if (nextProg >= 1f) {
                        squadPeers[pi] = peer.copy(
                            hp = 100,
                            shield = 50,
                            isDowned = false,
                            bleedoutRemainingMs = 30_000L,
                            reviveProgress = 0f,
                            radioCalloutText = "Back on my feet! Thanks squad!",
                            radioCalloutTtlMs = 2800L
                        )
                        points += 250
                        banners.add(FloatingBanner("+250 CO-OP REVIVE (${peer.playerName})!", peer.x, peer.y - 26f, 0xFF00FF66, 1500L))
                        statusToast = "REVIVED ${peer.playerName.uppercase()} (+250 PTS)!"
                    } else {
                        squadPeers[pi] = peer.copy(bleedoutRemainingMs = remBleed, reviveProgress = nextProg)
                    }
                } else if (remBleed == 0L) {
                    // Auto-recover AI bot at next wave or keep downed until revived
                    squadPeers[pi] = peer.copy(bleedoutRemainingMs = 0L, reviveProgress = 0f)
                } else {
                    val decayed = (peer.reviveProgress - (scaledDeltaMs.toFloat() / 6000f)).coerceAtLeast(0f)
                    squadPeers[pi] = peer.copy(bleedoutRemainingMs = remBleed, reviveProgress = decayed)
                }
            }
        }

        // 6. Nearby Loot Drop Detection (Phase 4 Time-Freeze Inspection) & Interactive Stations
        var unlockedRooms = state.unlockedRooms
        var unlockedWeapons = state.unlockedWeaponIds
        var weaponLevels = state.weaponLevels
        var currentWeaponIdx = state.currentWeaponIndex
        var inspectedLootDrop: LootDropEntity? = state.inspectedLootDrop
        var modularInventory = state.modularWeaponInventory
        var equippedModular = state.equippedModularWeapon

        val nearbyLoot = lootDrops
            .minByOrNull { hypot(it.x - nx, it.y - ny) }
            ?.takeIf { hypot(it.x - nx, it.y - ny) <= 58f }

        val nearby = stations
            .filter {
                it.roomIndex in unlockedRooms &&
                    (it.kind != StationKind.DEBRIS_DOOR || it.targetRoomIndex !in unlockedRooms)
            }
            .minByOrNull { hypot(it.x - nx, it.y - ny) }
            ?.takeIf { hypot(it.x - nx, it.y - ny) <= 72f }

        if (isInteractPressed) {
            if (nearbyLoot != null && !state.aiBotEnabled) {
                // Phase 4: Trigger Time-Freeze Loot Inspection Card
                inspectedLootDrop = nearbyLoot
                statusToast = "TIME-FREEZE LOOT INSPECT: ${nearbyLoot.displayTitle}"
            } else if (nearby != null) {
                when (nearby.kind) {
                    StationKind.DEBRIS_DOOR -> {
                        if (points >= nearby.cost && nearby.targetRoomIndex !in unlockedRooms) {
                            points -= nearby.cost
                            unlockedRooms = unlockedRooms + nearby.targetRoomIndex
                            stations.removeAll { it.id == nearby.id }
                            elonaWorldReceipt = ElonaSocketFrontierEngine.unlockFrontierDoorAndExpand(
                                currentReceipt = elonaWorldReceipt,
                                frontierId = "door_sector_${nearby.roomIndex}_to_${nearby.targetRoomIndex}",
                                stateCache = state.worldStateCache
                            )
                            WorldChunkStreamer.invalidateChunks()
                            statusToast = "UNLOCKED ${mazeSyncedMap.rooms.getOrNull(nearby.targetRoomIndex)?.name ?: "NEW SECTOR"}!"
                            banners.add(FloatingBanner("SECTOR UNLOCKED!", nearby.x, nearby.y, 0xFFFFD166))
                        }
                    }
                    StationKind.WALL_WEAPON -> {
                        if (points >= nearby.cost) {
                            points -= nearby.cost
                            if (nearby.weaponId !in unlockedWeapons) {
                                unlockedWeapons = unlockedWeapons + nearby.weaponId
                            }
                            currentWeaponIdx = unlockedWeapons.indexOf(nearby.weaponId).coerceAtLeast(0)
                            val rolledMod = ModularWeaponGenerator.generateProceduralWeapon(
                                seed = clockMs xor state.mapSeed,
                                wave = wave,
                                preferredBaseWeaponId = nearby.weaponId
                            )
                            modularInventory = modularInventory + (nearby.weaponId to rolledMod)
                            equippedModular = rolledMod
                            magAmmo[nearby.weaponId] = rolledMod.effectiveMagSize
                            statusToast = "EQUIPPED MODULAR ${rolledMod.customTitle}!"
                        }
                    }
                    StationKind.MYSTERY_BOX -> {
                        if (points >= nearby.cost) {
                            points -= nearby.cost
                            val rolledMod = ModularWeaponGenerator.generateProceduralWeapon(
                                seed = clockMs xor (points * 31L),
                                wave = wave + 1,
                                guaranteedMinRarity = LootRarity.RARE
                            )
                            if (rolledMod.baseWeaponId !in unlockedWeapons) {
                                unlockedWeapons = unlockedWeapons + rolledMod.baseWeaponId
                            }
                            currentWeaponIdx = unlockedWeapons.indexOf(rolledMod.baseWeaponId).coerceAtLeast(0)
                            modularInventory = modularInventory + (rolledMod.baseWeaponId to rolledMod)
                            equippedModular = rolledMod
                            magAmmo[rolledMod.baseWeaponId] = rolledMod.effectiveMagSize
                            statusToast = "MYSTERY BOX ROLLED [${rolledMod.rarity.shortBadge}]: ${rolledMod.customTitle}!"
                            banners.add(FloatingBanner("ROLLED ${rolledMod.rarity.shortBadge}!", nearby.x, nearby.y, rolledMod.rarity.colorHex))
                        }
                    }
                    StationKind.PACK_A_PUNCH -> {
                        val curWpn = state.currentWeapon
                        val curLv = weaponLevels[curWpn.id] ?: 1
                        val tierUpgradeCost = if (curLv == 1 && nearby.cost in 1..1500) {
                            nearby.cost
                        } else {
                            SingleCurrencyVendingEngine.computePackAPunchCost(curLv)
                        }
                        if (points >= tierUpgradeCost && curLv < 5) {
                            points -= tierUpgradeCost
                            val nextLv = curLv + 1
                            weaponLevels = weaponLevels + (curWpn.id to nextLv)
                            magAmmo[curWpn.id] = state.currentMaxMagSize + 4
                            val sIdx = stations.indexOfFirst { it.id == nearby.id }
                            if (sIdx >= 0) {
                                stations[sIdx] = stations[sIdx].copy(
                                    cost = SingleCurrencyVendingEngine.computePackAPunchCost(nextLv)
                                )
                            }
                            statusToast = "PACK-A-PUNCHED ${curWpn.name} TO TIER $nextLv!"
                            banners.add(FloatingBanner("${curWpn.shortCode} MK-$nextLv!", nearby.x, nearby.y, 0xFFB388FF))
                        }
                    }
                    StationKind.PERK_MACHINE -> {
                        val perk = nearby.perkType
                        if (perk != null && perk !in activePerks && points >= nearby.cost) {
                            points -= nearby.cost
                            activePerks = activePerks + perk
                            if (perk == PerkType.JUGGERNOG) {
                                maxHp = 200
                                hp = 200
                            }
                            statusToast = "PERK ACQUIRED: ${perk.displayName}!"
                            banners.add(FloatingBanner("+${perk.shortTag}", nearby.x, nearby.y, perk.colorHex))
                        }
                    }
                    StationKind.BARRICADE_WINDOW -> {
                        val idx = stations.indexOfFirst { it.id == nearby.id }
                        if (idx >= 0 && stations[idx].barricadePlanks < stations[idx].maxPlanks) {
                            stations[idx] = stations[idx].copy(barricadePlanks = stations[idx].barricadePlanks + 1)
                            points += 20
                            banners.add(FloatingBanner("+20 REPAIR", nearby.x, nearby.y, 0xFF00FF66))
                        }
                    }
                    StationKind.SIEGE_FABRICATOR -> {
                        if (!siegeState.isActive && points >= nearby.cost) {
                            points -= nearby.cost
                            siegeState = siegeState.copy(
                                isActive = true,
                                roomIndex = nearby.roomIndex,
                                remainingMs = 20_000L,
                                totalDurationMs = 20_000L
                            )
                            statusToast = "SIEGE FABRICATOR ACTIVATED (20s) -- Camera framing widened to 1.1x!"
                            banners.add(FloatingBanner("SIEGE STARTED!", nearby.x, nearby.y, 0xFFFF2A54, 1500L))
                        }
                    }
                    StationKind.UPGRADE_ANVIL -> {
                        if (points >= nearby.cost || depthEscrow.abyssalScrap >= 30) {
                            if (depthEscrow.abyssalScrap >= 30) {
                                depthEscrow = depthEscrow.copy(
                                    abyssalScrap = depthEscrow.abyssalScrap - 30,
                                    totalAnvilUpgrades = depthEscrow.totalAnvilUpgrades + 1
                                )
                            } else {
                                points -= nearby.cost
                            }
                            val tempered = equippedModular.copy(anvilUpgradeTier = equippedModular.anvilUpgradeTier + 1)
                            equippedModular = tempered
                            modularInventory = modularInventory + (tempered.baseWeaponId to tempered)
                            statusToast = "ANVIL TEMPERED ${tempered.customTitle} TO +${tempered.anvilUpgradeTier}!"
                            banners.add(FloatingBanner("ANVIL +${tempered.anvilUpgradeTier}!", nearby.x, nearby.y, 0xFFFFD166))
                        }
                    }
                }
            }
        }

        // PHASE 1: Step Dynamic Camera Tension Controller (2.2x Corridor <-> 1.1x Siege/Boss Cubic Tween)
        val hasBossAnomaly = zombies.any { it.archetype == ZombieArchetype.DEVIL_BOSS }
        val nearbyHostilesCount = zombies.count { hypot(it.x - nx, it.y - ny) <= 360f }
        val updatedCameraTension = CameraTensionComponent.stepCameraTension(
            current = state.cameraTension,
            deltaMs = scaledDeltaMs,
            isSiegeActive = siegeState.isActive,
            hasBossAnomaly = hasBossAnomaly,
            nearbyHostileCount = nearbyHostilesCount,
            didFireThisTick = didFireThisTick
        )

        val updatedRooms = if (unlockedRooms != state.unlockedRooms) {
            mazeSyncedMap.rooms.map { it.copy(isUnlocked = it.index in unlockedRooms) }
        } else {
            mazeSyncedMap.rooms
        }

        val updatedMap = mazeSyncedMap.copy(
            rooms = updatedRooms,
            stations = stations,
            barrels = barrels
        )

        val chunkSnapshot = WorldChunkStreamer.queryAndStreamSquadChunks(
            map = updatedMap,
            playerX = nx,
            playerY = ny,
            coopPeers = squadPeers,
            nowMs = clockMs
        )

        return state.copy(
            map = updatedMap,
            unlockedRooms = unlockedRooms,
            playerX = nx,
            playerY = ny,
            playerVelX = curVelX,
            playerVelY = curVelY,
            aimAngleRad = aimAngle,
            hp = hp,
            maxHp = maxHp,
            shield = shield,
            maxShield = maxShield,
            wave = wave,
            zombiesRemainingInWave = remainingToSpawn,
            points = points,
            totalKills = totalKills,
            comboCount = comboCount,
            comboTimerMs = comboTimerMs,
            unlockedWeaponIds = unlockedWeapons,
            weaponLevels = weaponLevels,
            currentWeaponIndex = currentWeaponIdx,
            currentMagAmmo = magAmmo,
            reloadRemainingMs = reloadRemainingMs,
            ammoTelemetry = telemetry,
            activePerks = activePerks,
            zombies = zombies,
            tracers = tracers,
            particles = particles,
            floatingBanners = banners,
            nearbyStation = nearby,
            cameraTension = updatedCameraTension,
            mazeState = updatedMazeState,
            fortifications = fortifications,
            laserTelegraphs = laserTelegraphs,
            enemyProjectiles = enemyProjectiles,
            elementalPuddles = elementalPuddles,
            siegeFabricator = siegeState,
            equippedModularWeapon = equippedModular,
            modularWeaponInventory = modularInventory,
            lootDrops = lootDrops,
            nearbyLootDrop = nearbyLoot,
            inspectedLootDrop = inspectedLootDrop,
            lightLockTargetId = lightLockTargetId,
            depthEscrow = depthEscrow,
            coopPeers = squadPeers,
            isPlayerDowned = isPlayerDowned,
            playerBleedoutRemainingMs = playerBleedoutMs,
            playerReviveProgress = playerReviveProg,
            chunkSnapshot = chunkSnapshot,
            passiveRelics = passiveRelics,
            elonaWorldReceipt = elonaWorldReceipt,
            clockMs = clockMs,
            isGameOver = isGameOver,
            statusToast = statusToast,
            surfaceDecals = surfaceDecals,
            boxheadCombo = boxheadCombo,
            vendingStock = vendingStock
        )
    }

    /**
     * Spawns or toggles an Oasis AI Squad Bot (@willow, @cinder, @mote) in the 4-player squad roster.
     */
    fun spawnOrToggleSquadBot(state: GameSessionState, persona: OasisBotPersona): GameSessionState {
        val currentPeers = state.coopPeers.toMutableList()
        val existingIdx = currentPeers.indexOfFirst { it.botPersonaId == persona.id }
        return if (existingIdx >= 0) {
            val removed = currentPeers.removeAt(existingIdx)
            state.copy(
                coopPeers = currentPeers,
                statusToast = "REMOVED SQUAD BOT ${removed.playerName} (${currentPeers.size + 1}/4 SQUAD)"
            )
        } else {
            if (currentPeers.size >= 3) {
                currentPeers.removeFirst()
            }
            val angleOffset = (currentPeers.size + 1) * 1.15f
            val spawnX = (state.playerX + cos(angleOffset) * 62f).coerceIn(140f, 4620f)
            val spawnY = (state.playerY + sin(angleOffset) * 62f).coerceIn(140f, 2600f)
            val newBot = CoopPeerState(
                peerId = "bot_${persona.id}",
                playerName = persona.displayName,
                x = spawnX,
                y = spawnY,
                vx = 0f,
                vy = 0f,
                aimAngleRad = 0f,
                hp = 100,
                maxHp = 100,
                shield = 60,
                weaponId = persona.preferredWeaponId,
                weaponLevel = (state.wave / 2 + 1).coerceIn(1, 5),
                isFiring = false,
                isDowned = false,
                bleedoutRemainingMs = 30_000L,
                reviveProgress = 0f,
                score = state.points,
                wave = state.wave,
                isAiBot = true,
                botPersonaId = persona.id,
                visorColorHex = persona.visorColorHex,
                radioCalloutText = "${persona.displayName} online -- ${persona.roleSummary}!",
                radioCalloutTtlMs = 3200L,
                updatedAtMs = System.currentTimeMillis()
            )
            currentPeers.add(newBot)
            state.copy(
                coopPeers = currentPeers,
                statusToast = "DEPLOYED ${persona.displayName.uppercase()} (${currentPeers.size + 1}/4 SQUAD)"
            )
        }
    }

    /**
     * Fills remaining open squad slots up to 4/4 with @willow, @cinder, and @mote.
     */
    fun fillSquadWithBots(state: GameSessionState): GameSessionState {
        var nextState = state
        for (persona in OasisBotPersona.entries) {
            if (nextState.coopPeers.size >= 3) break
            if (nextState.coopPeers.none { it.botPersonaId == persona.id }) {
                nextState = spawnOrToggleSquadBot(nextState, persona)
            }
        }
        return nextState.copy(
            statusToast = "SQUAD FILLED (4/4): @willow, @cinder & @mote Tactical Bots Active!"
        )
    }

    private fun stepSquadBots(
        squadPeers: MutableList<CoopPeerState>,
        deltaMs: Long,
        dtSec: Float,
        playerX: Float,
        playerY: Float,
        isPlayerDowned: Boolean,
        wave: Int,
        map: ProceduralMap,
        unlockedRooms: Set<Int>,
        zombies: MutableList<ZombieEntity>,
        barrels: MutableList<ExplosiveBarrel>,
        stations: MutableList<InteractiveStation>,
        tracers: MutableList<BulletTracer>,
        particles: MutableList<ParticleBurst>,
        banners: MutableList<FloatingBanner>,
        lootDrops: MutableList<LootDropEntity>
    ): Pair<Int, Int> {
        var killsAdded = 0
        var pointsAdded = 0

        for (i in 0 until squadPeers.size) {
            val bot = squadPeers[i]
            if (!bot.isAiBot || bot.isDowned || bot.hp <= 0) continue

            val persona = OasisBotPersona.entries.firstOrNull { it.id == bot.botPersonaId } ?: OasisBotPersona.WILLOW
            var callout = bot.radioCalloutText
            var calloutTtl = (bot.radioCalloutTtlMs - deltaMs).coerceAtLeast(0L)
            if (calloutTtl == 0L) callout = ""

            // 1. Determine movement goal based on Persona
            var goalX = playerX + cos((i + 1) * 2.09f) * 85f
            var goalY = playerY + sin((i + 1) * 2.09f) * 85f

            val downedAlly: Pair<Float, Float>? = if (isPlayerDowned) {
                Pair(playerX, playerY)
            } else {
                squadPeers.firstOrNull { it.peerId != bot.peerId && it.isDowned }?.let { Pair(it.x, it.y) }
            }

            when (persona) {
                OasisBotPersona.WILLOW -> {
                    // Medic & Barricade Engineer: rush to downed ally first, else repair damaged window, else flank player
                    if (downedAlly != null) {
                        goalX = downedAlly.first
                        goalY = downedAlly.second
                        if (callout.isBlank()) {
                            callout = "Holding revive on downed teammate!"
                            calloutTtl = 2400L
                        }
                    } else {
                        val damagedWindow = stations.firstOrNull {
                            it.kind == StationKind.BARRICADE_WINDOW &&
                                it.roomIndex in unlockedRooms &&
                                it.barricadePlanks < it.maxPlanks &&
                                hypot(it.x - bot.x, it.y - bot.y) <= 260f
                        }
                        if (damagedWindow != null) {
                            goalX = damagedWindow.x
                            goalY = damagedWindow.y
                            if (hypot(damagedWindow.x - bot.x, damagedWindow.y - bot.y) <= 56f && (clockMs % 600L) < deltaMs * 2) {
                                val winIdx = stations.indexOfFirst { it.id == damagedWindow.id }
                                if (winIdx >= 0) {
                                    stations[winIdx] = stations[winIdx].copy(
                                        barricadePlanks = (stations[winIdx].barricadePlanks + 1).coerceAtMost(stations[winIdx].maxPlanks)
                                    )
                                    callout = "Barricade reinforced!"
                                    calloutTtl = 2000L
                                }
                            }
                        }
                    }
                }
                OasisBotPersona.CINDER -> {
                    // Aggressive Crowd-Control: kite near undetonated barrels or orbit horde at 165px
                    if (downedAlly != null && squadPeers.none { it.botPersonaId == OasisBotPersona.WILLOW.id && !it.isDowned }) {
                        goalX = downedAlly.first
                        goalY = downedAlly.second
                    } else {
                        val nearestZ = zombies.minByOrNull { hypot(it.x - bot.x, it.y - bot.y) }
                        if (nearestZ != null) {
                            val ang = atan2(bot.y - nearestZ.y, bot.x - nearestZ.x)
                            goalX = nearestZ.x + cos(ang + 0.45f) * 165f
                            goalY = nearestZ.y + sin(ang + 0.45f) * 165f
                        }
                    }
                }
                OasisBotPersona.MOTE -> {
                    // High-Precision Rail/Crit Scout: maintain 250px standoff from priority targets
                    if (downedAlly != null && hypot(downedAlly.first - bot.x, downedAlly.second - bot.y) < 180f) {
                        goalX = downedAlly.first
                        goalY = downedAlly.second
                    } else {
                        val priorityTarget = zombies.firstOrNull {
                            it.archetype == ZombieArchetype.DEVIL_BOSS ||
                                it.archetype == ZombieArchetype.ABYSSAL_WEAVER ||
                                it.archetype == ZombieArchetype.CALCIFIED_STALKER
                        } ?: zombies.minByOrNull { hypot(it.x - bot.x, it.y - bot.y) }
                        if (priorityTarget != null) {
                            val ang = atan2(bot.y - priorityTarget.y, bot.x - priorityTarget.x)
                            goalX = priorityTarget.x + cos(ang) * 245f
                            goalY = priorityTarget.y + sin(ang) * 245f
                        }
                    }
                }
            }

            // Move bot smoothly toward (goalX, goalY)
            val dxGoal = goalX - bot.x
            val dyGoal = goalY - bot.y
            val distGoal = hypot(dxGoal, dyGoal)
            val botSpeed = 195f
            var nextX = bot.x
            var nextY = bot.y
            var vx = 0f
            var vy = 0f
            if (distGoal > 18f) {
                vx = (dxGoal / distGoal) * botSpeed
                vy = (dyGoal / distGoal) * botSpeed
                val candX = bot.x + vx * dtSec
                val candY = bot.y + vy * dtSec
                if (isWalkable(candX, candY, map, unlockedRooms)) {
                    nextX = candX
                    nextY = candY
                } else if (isWalkable(candX, bot.y, map, unlockedRooms)) {
                    nextX = candX
                } else if (isWalkable(bot.x, candY, map, unlockedRooms)) {
                    nextY = candY
                }
            }

            // 2. Bot Weapon Firing & Barrel Detonation
            var aimRad = bot.aimAngleRad
            var isFiring = false
            val fireIntervalMs = when (persona) {
                OasisBotPersona.WILLOW -> 420L
                OasisBotPersona.CINDER -> 260L
                OasisBotPersona.MOTE -> 480L
            }
            val botPhaseOffset = (i + 1) * 130L
            val canFireTick = ((clockMs + botPhaseOffset) % fireIntervalMs) < deltaMs.coerceAtLeast(16L)

            // Check if Cinder can detonate a barrel with zombies nearby
            val primedBarrel = if (persona == OasisBotPersona.CINDER) {
                barrels.firstOrNull { b ->
                    !b.isDetonated &&
                        b.roomIndex in unlockedRooms &&
                        hypot(b.x - nextX, b.y - nextY) in 95f..410f &&
                        zombies.any { z -> hypot(z.x - b.x, z.y - b.y) <= 115f }
                }
            } else null

            if (primedBarrel != null && canFireTick) {
                aimRad = atan2(primedBarrel.y - nextY, primedBarrel.x - nextX)
                isFiring = true
                val bIdx = barrels.indexOfFirst { it.id == primedBarrel.id }
                if (bIdx >= 0) {
                    barrels[bIdx] = primedBarrel.copy(isDetonated = true)
                    val (bk, bp) = triggerBarrelExplosion(primedBarrel.x, primedBarrel.y, zombies, particles, banners)
                    killsAdded += bk
                    pointsAdded += bp
                    callout = "BARREL PRIMED -- KABOOM! (+$bk)"
                    calloutTtl = 2600L
                }
                tracers.add(
                    BulletTracer(
                        startX = nextX,
                        startY = nextY,
                        endX = primedBarrel.x,
                        endY = primedBarrel.y,
                        colorHex = persona.visorColorHex
                    )
                )
            } else {
                val targetZombie = when (persona) {
                    OasisBotPersona.MOTE -> {
                        zombies.filter { hypot(it.x - nextX, it.y - nextY) <= 520f }
                            .maxByOrNull {
                                when (it.archetype) {
                                    ZombieArchetype.DEVIL_BOSS -> 1000f
                                    ZombieArchetype.ABYSSAL_WEAVER -> 600f
                                    ZombieArchetype.CALCIFIED_STALKER -> 500f
                                    else -> 300f - hypot(it.x - nextX, it.y - nextY) * 0.4f
                                }
                            }
                    }
                    else -> zombies.minByOrNull { hypot(it.x - nextX, it.y - nextY) }
                        ?.takeIf { hypot(it.x - nextX, it.y - nextY) <= 420f }
                }

                if (targetZombie != null) {
                    aimRad = atan2(targetZombie.y - nextY, targetZombie.x - nextX)
                    isFiring = true
                    if (canFireTick) {
                        val shotDamage = when (persona) {
                            OasisBotPersona.WILLOW -> 34 + wave * 4
                            OasisBotPersona.CINDER -> 26 + wave * 3
                            OasisBotPersona.MOTE -> 54 + wave * 6
                        }
                        targetZombie.hp -= shotDamage
                        tracers.add(
                            BulletTracer(
                                startX = nextX + cos(aimRad) * 14f,
                                startY = nextY + sin(aimRad) * 14f,
                                endX = targetZombie.x,
                                endY = targetZombie.y,
                                colorHex = persona.visorColorHex
                            )
                        )
                        if (targetZombie.hp <= 0) {
                            killsAdded++
                            pointsAdded += targetZombie.archetype.rewardPoints
                            maybeSpawnLootDrop(targetZombie, wave, lootDrops, banners)
                            if (callout.isBlank() && Random.nextFloat() < 0.35f) {
                                callout = when (persona) {
                                    OasisBotPersona.WILLOW -> "Target neutralized! Squad perimeter clear."
                                    OasisBotPersona.CINDER -> "Scorched another husk!"
                                    OasisBotPersona.MOTE -> "Rail crit confirmed on ${targetZombie.archetype.label}!"
                                }
                                calloutTtl = 2200L
                            }
                        }
                    }
                }
            }

            zombies.removeAll { it.hp <= 0 }

            squadPeers[i] = bot.copy(
                x = nextX,
                y = nextY,
                vx = vx,
                vy = vy,
                aimAngleRad = aimRad,
                isFiring = isFiring,
                wave = wave,
                radioCalloutText = callout,
                radioCalloutTtlMs = calloutTtl,
                updatedAtMs = System.currentTimeMillis()
            )
        }

        return Pair(killsAdded, pointsAdded)
    }

    private fun maybeSpawnLootDrop(
        zombie: ZombieEntity,
        wave: Int,
        lootDrops: MutableList<LootDropEntity>,
        banners: MutableList<FloatingBanner>
    ) {
        if (lootDrops.size >= 10) return
        val dropChance = when (zombie.archetype) {
            ZombieArchetype.DEVIL_BOSS -> 1.0f
            ZombieArchetype.ABYSSAL_WEAVER, ZombieArchetype.CALCIFIED_STALKER -> 0.28f
            else -> 0.08f
        }
        if (Random.nextFloat() <= dropChance) {
            val seed = clockMs xor (zombie.id * 7919L)
            val minRarity = if (zombie.archetype == ZombieArchetype.DEVIL_BOSS) LootRarity.EPIC else null
            if (Random.nextBoolean()) {
                val wpn = ModularWeaponGenerator.generateProceduralWeapon(seed, wave, minRarity)
                lootDrops.add(
                    LootDropEntity(
                        id = nextEntityId++,
                        x = zombie.x,
                        y = zombie.y,
                        rarity = wpn.rarity,
                        payloadKind = LootPayloadKind.MODULAR_WEAPON,
                        weaponPayload = wpn,
                        spawnedAtWave = wave
                    )
                )
                banners.add(FloatingBanner("[${wpn.rarity.shortBadge}] WEAPON DROP!", zombie.x, zombie.y - 22f, wpn.rarity.colorHex, 1300L))
            } else {
                val arm = RpgArmorGenerator.generateProceduralArmorPiece(seed, wave, minRarity = minRarity)
                lootDrops.add(
                    LootDropEntity(
                        id = nextEntityId++,
                        x = zombie.x,
                        y = zombie.y,
                        rarity = arm.rarity,
                        payloadKind = LootPayloadKind.RPG_ARMOR_PIECE,
                        armorPayload = arm,
                        spawnedAtWave = wave
                    )
                )
                banners.add(FloatingBanner("[${arm.rarity.shortBadge}] ARMOR DROP!", zombie.x, zombie.y - 22f, arm.rarity.colorHex, 1300L))
            }
        }
    }

    fun spawnStressZombies(
        state: GameSessionState,
        count: Int,
        archetype: ZombieArchetype = ZombieArchetype.SHAMBLER
    ): GameSessionState {
        val current = state.zombies.toMutableList()
        val baseHp = (55 + state.wave * 24)
        val zHp = (baseHp * archetype.hpMultiplier).toInt()
        repeat(count.coerceIn(1, 250)) { i ->
            val angle = (i * 0.35f)
            val dist = 180f + (i % 6) * 42f
            val zx = (state.playerX + cos(angle) * dist).coerceIn(120f, 4660f)
            val zy = (state.playerY + sin(angle) * dist).coerceIn(120f, 2620f)
            current.add(
                ZombieEntity(
                    id = nextEntityId++,
                    archetype = archetype,
                    x = zx,
                    y = zy,
                    hp = zHp,
                    maxHp = zHp
                )
            )
        }
        return state.copy(
            zombies = current,
            statusToast = "KONSOLE SPAWNED $count ${archetype.label.uppercase()}S (ACTIVE: ${current.size})"
        )
    }

    fun runHeadlessBenchmark(
        initialState: GameSessionState,
        spawnZombieCount: Int,
        ticks: Int
    ): Pair<GameSessionState, BenchmarkReport> {
        val safeTicks = ticks.coerceIn(10, 2000)
        var simState = spawnStressZombies(
            initialState.copy(aiBotEnabled = true, isPaused = false, isGameOver = false, inspectedLootDrop = null),
            spawnZombieCount.coerceIn(1, 250),
            ZombieArchetype.SPRINTER
        )
        val startKills = simState.totalKills
        var peakZombies = simState.zombies.size

        val elapsedNanos = measureNanoTime {
            repeat(safeTicks) {
                simState = stepFrame(
                    state = simState,
                    deltaMsRaw = 16L,
                    moveXInput = 0f,
                    moveYInput = 0f,
                    isFiringInput = false,
                    isAltBarrelDropInput = false,
                    isInteractPressedInput = false,
                    coopPeerX = null,
                    coopPeerY = null
                )
                if (simState.zombies.size > peakZombies) {
                    peakZombies = simState.zombies.size
                }
            }
        }
        val elapsedMs = elapsedNanos / 1_000_000.0
        val avgMicros = (elapsedNanos / 1_000.0) / safeTicks
        val maxFps = if (avgMicros > 0) (1_000_000.0 / avgMicros).toInt().coerceAtMost(9999) else 9999
        val report = BenchmarkReport(
            ticksSimulated = safeTicks,
            zombieCountPeak = peakZombies,
            totalElapsedMs = elapsedMs,
            avgTickMicros = avgMicros,
            estimatedMaxFps = maxFps,
            killsDuringTest = simState.totalKills - startKills
        )
        return simState.copy(
            statusToast = "STRESS BENCHMARK: $safeTicks ticks in ${"%.1f".format(elapsedMs)}ms (${"%.1f".format(avgMicros)}us/tick, ~$maxFps FPS cap)"
        ) to report
    }

    private fun triggerBarrelExplosion(
        bx: Float,
        by: Float,
        zombies: MutableList<ZombieEntity>,
        particles: MutableList<ParticleBurst>,
        banners: MutableList<FloatingBanner>,
        radius: Float = 140f,
        damage: Int = 260
    ): Pair<Int, Int> {
        var kills = 0
        var pts = 0
        if (particles.size < 140) {
            repeat(12) {
                val vx = deterministicParticlePool.nextDeterministicInt(-220, 220).toFloat()
                val vy = deterministicParticlePool.nextDeterministicInt(-220, 220).toFloat()
                val color = if (it % 2 == 0) 0xFFFF2A54 else 0xFFFFD166
                deterministicParticlePool.spawn(bx, by, vx, vy, color, ttlMs = 420L)
                particles.add(
                    ParticleBurst(
                        x = bx,
                        y = by,
                        vx = vx,
                        vy = vy,
                        colorHex = color,
                        ttlMs = 420L
                    )
                )
            }
        }
        banners.add(FloatingBanner("KABOOM!", bx, by - 20f, 0xFFFFD166))
        for (z in zombies) {
            if (hypot(z.x - bx, z.y - by) <= radius) {
                z.hp -= damage
                if (z.hp <= 0) {
                    kills++
                    pts += z.archetype.rewardPoints
                }
            }
        }
        return kills to pts
    }

    private fun isWalkable(x: Float, y: Float, map: ProceduralMap, unlockedRooms: Set<Int>): Boolean {
        return WorldChunkStreamer.isWalkableInChunkedWorld(x, y, map, unlockedRooms)
    }

    private fun pointToSegmentDistance(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot(px - projX, py - projY)
    }
}
