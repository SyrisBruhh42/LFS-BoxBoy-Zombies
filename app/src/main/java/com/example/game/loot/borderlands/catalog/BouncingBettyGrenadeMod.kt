package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import kotlin.math.cos
import kotlin.math.sin

@Keep
enum class BettyPhase {
    THROWN,
    FUSE,
    LEAPING,
    SPRAYING,
    DETONATED
}

@Keep
data class BouncingBettyConfig(
    val fuseTimeSeconds: Float = 1.0f,
    val leapVelocityZ: Float = 400f,
    val leapApexZ: Float = 68f, // Orthogonal wall height matching game engine
    val sprayDurationSeconds: Float = 2.0f,
    val sprayIntervalSeconds: Float = 0.1f,
    val pelletsPerInterval: Int = 4,
    val pelletSpeed: Float = 600f,
    val baseDamage: Float = 50f,
    val gravity: Float = -980f,
    val bounciness: Float = 0.4f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "fuseTimeSeconds" to fuseTimeSeconds,
        "leapVelocityZ" to leapVelocityZ,
        "leapApexZ" to leapApexZ,
        "sprayDurationSeconds" to sprayDurationSeconds,
        "sprayIntervalSeconds" to sprayIntervalSeconds,
        "pelletsPerInterval" to pelletsPerInterval,
        "pelletSpeed" to pelletSpeed,
        "baseDamage" to baseDamage,
        "gravity" to gravity,
        "bounciness" to bounciness
    )

    companion object {
        fun fromMap(map: Map<String, Any>): BouncingBettyConfig {
            return BouncingBettyConfig(
                fuseTimeSeconds = (map["fuseTimeSeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 1.0f,
                leapVelocityZ = (map["leapVelocityZ"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 400f,
                leapApexZ = (map["leapApexZ"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 68f,
                sprayDurationSeconds = (map["sprayDurationSeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 2.0f,
                sprayIntervalSeconds = (map["sprayIntervalSeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0.1f,
                pelletsPerInterval = (map["pelletsPerInterval"] as? Number)?.toInt() ?: 4,
                pelletSpeed = (map["pelletSpeed"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 600f,
                baseDamage = (map["baseDamage"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 50f,
                gravity = (map["gravity"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: -980f,
                bounciness = (map["bounciness"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0.4f
            )
        }
    }
}

@Keep
data class BettyPellet(
    val x: Float,
    val y: Float,
    val z: Float,
    val vx: Float,
    val vy: Float,
    val damage: Float
) {
    fun toMap(): Map<String, Any> = mapOf(
        "x" to x,
        "y" to y,
        "z" to z,
        "vx" to vx,
        "vy" to vy,
        "damage" to damage
    )

    companion object {
        fun fromMap(map: Map<String, Any>): BettyPellet {
            return BettyPellet(
                x = (map["x"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                y = (map["y"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                z = (map["z"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                vx = (map["vx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                vy = (map["vy"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                damage = (map["damage"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            )
        }
    }
}

@Keep
data class BouncingBettyState(
    var id: Int,
    var phase: BettyPhase = BettyPhase.THROWN,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var timerSeconds: Float = 0f,
    var sprayAngle: Float = 0f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "phase" to phase.name,
        "x" to x,
        "y" to y,
        "z" to z,
        "vx" to vx,
        "vy" to vy,
        "vz" to vz,
        "timerSeconds" to timerSeconds,
        "sprayAngle" to sprayAngle
    )

    fun computeChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor id.toLong()) * 16777619L
        hash = (hash xor phase.ordinal.toLong()) * 16777619L
        hash = (hash xor x.toBits().toLong()) * 16777619L
        hash = (hash xor y.toBits().toLong()) * 16777619L
        hash = (hash xor z.toBits().toLong()) * 16777619L
        hash = (hash xor vx.toBits().toLong()) * 16777619L
        hash = (hash xor vy.toBits().toLong()) * 16777619L
        hash = (hash xor vz.toBits().toLong()) * 16777619L
        hash = (hash xor timerSeconds.toBits().toLong()) * 16777619L
        hash = (hash xor sprayAngle.toBits().toLong()) * 16777619L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): BouncingBettyState {
            val phaseName = map["phase"] as? String ?: BettyPhase.THROWN.name
            return BouncingBettyState(
                id = (map["id"] as? Number)?.toInt() ?: 0,
                phase = try { BettyPhase.valueOf(phaseName) } catch (e: Exception) { BettyPhase.THROWN },
                x = (map["x"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                y = (map["y"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                z = (map["z"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                vx = (map["vx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                vy = (map["vy"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                vz = (map["vz"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                timerSeconds = (map["timerSeconds"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f,
                sprayAngle = (map["sprayAngle"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            )
        }
    }
}

@Keep
data class BettyTickResult(
    val checksum: Long,
    val stateMutated: BouncingBettyState, // By ref mutated state returned for pure functional style
    val spawnedPellets: List<BettyPellet>
)

object BouncingBettyGrenadeEngine {
    
    fun tick(
        config: BouncingBettyConfig,
        state: BouncingBettyState,
        dtSecondsRaw: Float
    ): BettyTickResult {
        // Enforce frame-rate independent dt stepping bounds and protect against NaN/Infinity
        val dtSeconds = if (dtSecondsRaw.isFinite()) {
            dtSecondsRaw.coerceIn(0.001f, 0.100f)
        } else {
            0.016f // Default 60Hz loop step
        }

        // Clean potentially corrupted inputs
        if (!state.x.isFinite()) state.x = 0f
        if (!state.y.isFinite()) state.y = 0f
        if (!state.z.isFinite()) state.z = 0f
        if (!state.vx.isFinite()) state.vx = 0f
        if (!state.vy.isFinite()) state.vy = 0f
        if (!state.vz.isFinite()) state.vz = 0f
        if (!state.timerSeconds.isFinite()) state.timerSeconds = 0f
        if (!state.sprayAngle.isFinite()) state.sprayAngle = 0f

        val generatedPellets = mutableListOf<BettyPellet>()

        when (state.phase) {
            BettyPhase.THROWN -> {
                val preGravityVz = state.vz
                state.vz += config.gravity * dtSeconds
                state.x += state.vx * dtSeconds
                state.y += state.vy * dtSeconds
                state.z += state.vz * dtSeconds

                // Ground collision at Z = 0
                if (state.z <= 0f) {
                    state.z = 0f
                    // Bounce handling
                    val speedSq = state.vx * state.vx + state.vy * state.vy + state.vz * state.vz
                    if (speedSq > 2500f && preGravityVz < -50f) {
                        state.vz = -state.vz * config.bounciness
                        state.vx *= 0.8f // Friction
                        state.vy *= 0.8f // Friction
                    } else {
                        // Come to rest and begin fuse
                        state.vz = 0f
                        state.vx = 0f
                        state.vy = 0f
                        state.phase = BettyPhase.FUSE
                        state.timerSeconds = config.fuseTimeSeconds
                    }
                }
            }
            BettyPhase.FUSE -> {
                state.timerSeconds -= dtSeconds
                if (state.timerSeconds <= 0f) {
                    state.timerSeconds = 0f
                    state.phase = BettyPhase.LEAPING
                    state.vz = config.leapVelocityZ
                }
            }
            BettyPhase.LEAPING -> {
                state.z += state.vz * dtSeconds
                // Simple constant velocity leap up to apex or until velocity is checked by gravity
                state.vz += config.gravity * dtSeconds
                
                if (state.z >= config.leapApexZ || state.vz <= 0f) {
                    state.z = state.z.coerceAtMost(config.leapApexZ)
                    state.vz = 0f
                    state.phase = BettyPhase.SPRAYING
                    state.timerSeconds = 0f
                    state.sprayAngle = 0f // Reset angle
                }
            }
            BettyPhase.SPRAYING -> {
                state.timerSeconds += dtSeconds
                
                // Freeze mid-air during spraying
                state.vz = 0f
                state.vx = 0f
                state.vy = 0f
                
                // Deterministic interval emission
                // Example: timer = 0.25, interval = 0.1 -> total emitted so far should be 2 (at 0.1, 0.2)
                // We use sprayAngle as a secondary state to keep track of rotation.
                // Alternatively, we can calculate how many bursts *should* have happened.
                val prevBursts = ((state.timerSeconds - dtSeconds) / config.sprayIntervalSeconds).toInt().coerceAtLeast(0)
                val currBursts = (state.timerSeconds / config.sprayIntervalSeconds).toInt()
                
                if (currBursts > prevBursts && config.pelletsPerInterval > 0) {
                    val burstsToFire = currBursts - prevBursts
                    
                    for (b in 0 until burstsToFire) {
                        val angleStep = (2 * Math.PI / config.pelletsPerInterval).toFloat()
                        for (i in 0 until config.pelletsPerInterval) {
                            val angle = state.sprayAngle + i * angleStep
                            val vx = kotlin.math.cos(angle) * config.pelletSpeed
                            val vy = kotlin.math.sin(angle) * config.pelletSpeed
                            
                            generatedPellets.add(BettyPellet(
                                x = state.x,
                                y = state.y,
                                z = state.z,
                                vx = vx,
                                vy = vy,
                                damage = config.baseDamage
                            ))
                        }
                        // Rotate spray slightly for next burst
                        state.sprayAngle += 0.314f // ~18 degrees
                    }
                }
                
                if (state.timerSeconds >= config.sprayDurationSeconds) {
                    state.phase = BettyPhase.DETONATED
                }
            }
            BettyPhase.DETONATED -> {
                // Done. Just gravity drop back down if needed, or leave it to disappear
                state.vz += config.gravity * dtSeconds
                state.z += state.vz * dtSeconds
                if (state.z <= 0f) {
                    state.z = 0f
                    state.vz = 0f
                }
            }
        }

        return BettyTickResult(
            checksum = state.computeChecksum(),
            stateMutated = state,
            spawnedPellets = generatedPellets
        )
    }
}


fun evaluateBouncingBettyGrenadeModLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "BouncingBettyGrenadeMod") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
