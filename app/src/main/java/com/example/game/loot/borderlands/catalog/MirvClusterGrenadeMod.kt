package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep

@Keep
data class MirvClusterConfig(
    val initialExplosionRadiusPx: Float = 150f,
    val childExplosionRadiusPx: Float = 80f,
    val initialDamage: Float = 200f,
    val childDamage: Float = 75f,
    val childCount: Int = 6,
    val childSpeed: Float = 300f,
    val childFuseTimeMs: Float = 1500f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "initialExplosionRadiusPx" to initialExplosionRadiusPx,
        "childExplosionRadiusPx" to childExplosionRadiusPx,
        "initialDamage" to initialDamage,
        "childDamage" to childDamage,
        "childCount" to childCount,
        "childSpeed" to childSpeed,
        "childFuseTimeMs" to childFuseTimeMs
    )

    companion object {
        fun fromMap(map: Map<String, Any>): MirvClusterConfig {
            val initialExplosionRadiusPx = (map["initialExplosionRadiusPx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 150f
            val childExplosionRadiusPx = (map["childExplosionRadiusPx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 80f
            val initialDamage = (map["initialDamage"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 200f
            val childDamage = (map["childDamage"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 75f
            val childCount = (map["childCount"] as? Number)?.toInt() ?: 6
            val childSpeed = (map["childSpeed"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 300f
            val childFuseTimeMs = (map["childFuseTimeMs"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 1500f

            return MirvClusterConfig(
                initialExplosionRadiusPx = initialExplosionRadiusPx.coerceIn(10f, 1000f),
                childExplosionRadiusPx = childExplosionRadiusPx.coerceIn(10f, 1000f),
                initialDamage = initialDamage.coerceIn(0f, 10000f),
                childDamage = childDamage.coerceIn(0f, 10000f),
                childCount = childCount.coerceIn(0, 20),
                childSpeed = childSpeed.coerceIn(0f, 2000f),
                childFuseTimeMs = childFuseTimeMs.coerceIn(0f, 10000f)
            )
        }
    }
}

@Keep
data class MirvGrenade(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var fuseTimerMs: Float,
    var isChild: Boolean = false,
    var isActive: Boolean = true
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "x" to x,
        "y" to y,
        "vx" to vx,
        "vy" to vy,
        "fuseTimerMs" to fuseTimerMs,
        "isChild" to isChild,
        "isActive" to isActive
    )

    fun computeChecksum(): Long {
        var hash = 2166136261L
        hash = (hash xor id.toLong()) * 16777619L
        hash = (hash xor x.toBits().toLong()) * 16777619L
        hash = (hash xor y.toBits().toLong()) * 16777619L
        hash = (hash xor vx.toBits().toLong()) * 16777619L
        hash = (hash xor vy.toBits().toLong()) * 16777619L
        hash = (hash xor fuseTimerMs.toBits().toLong()) * 16777619L
        hash = (hash xor (if (isChild) 1L else 0L)) * 16777619L
        hash = (hash xor (if (isActive) 1L else 0L)) * 16777619L
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): MirvGrenade {
            val id = (map["id"] as? Number)?.toInt() ?: 0
            val x = (map["x"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            val y = (map["y"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            val vx = (map["vx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            val vy = (map["vy"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            val fuseTimerMs = (map["fuseTimerMs"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
            val isChild = map["isChild"] as? Boolean ?: false
            val isActive = map["isActive"] as? Boolean ?: true

            return MirvGrenade(id, x, y, vx, vy, fuseTimerMs, isChild, isActive)
        }
    }
}

@Keep
data class MirvExplosion(
    val x: Float,
    val y: Float,
    val radius: Float,
    val damage: Float,
    val isChild: Boolean
)

@Keep
data class MirvClusterResult(
    val stateChecksum: Long,
    val updatedGrenades: List<MirvGrenade>,
    val explosions: List<MirvExplosion>,
    val nextId: Int
)

object MirvClusterGrenadeMod {

    fun step(
        dtSecondsRaw: Float,
        grenades: List<MirvGrenade>,
        config: MirvClusterConfig,
        currentNextId: Int
    ): MirvClusterResult {
        val dtSeconds = if (dtSecondsRaw.isFinite()) {
            dtSecondsRaw.coerceIn(0.001f, 0.100f)
        } else {
            0.016f // 60Hz default fallback
        }

        val dtMs = dtSeconds * 1000f

        // Use exact required size for array lists or rough estimate to minimize resizing
        val updatedGrenades = ArrayList<MirvGrenade>(grenades.size + 10)
        val explosions = ArrayList<MirvExplosion>(4)

        var checksumAccumulator = -2128831035L // FNV-1a basis
        var nextIdTracker = currentNextId

        for (i in grenades.indices) {
            val g = grenades[i]

            if (!g.isActive) {
                // Pass through inactive
                val newG = MirvGrenade(g.id, g.x, g.y, g.vx, g.vy, g.fuseTimerMs, g.isChild, false)
                updatedGrenades.add(newG)
                checksumAccumulator = (checksumAccumulator xor newG.computeChecksum()) * 16777619L
                continue
            }

            var px = if (g.x.isFinite()) g.x else 0f
            var py = if (g.y.isFinite()) g.y else 0f
            val pvx = if (g.vx.isFinite()) g.vx else 0f
            val pvy = if (g.vy.isFinite()) g.vy else 0f
            var fuse = if (g.fuseTimerMs.isFinite()) g.fuseTimerMs else 0f

            // Movement integration
            px += pvx * dtSeconds
            py += pvy * dtSeconds

            // Fuse tick
            fuse -= dtMs

            if (fuse <= 0f) {
                // Detonation
                if (!g.isChild) {
                    explosions.add(MirvExplosion(px, py, config.initialExplosionRadiusPx, config.initialDamage, false))

                    // Spawn children
                    if (config.childCount > 0) {
                        val angleStep = (2.0 * Math.PI) / config.childCount
                        for (c in 0 until config.childCount) {
                            val angle = c * angleStep
                            val cvx = (kotlin.math.cos(angle) * config.childSpeed).toFloat()
                            val cvy = (kotlin.math.sin(angle) * config.childSpeed).toFloat()

                            val childGrenade = MirvGrenade(
                                id = nextIdTracker++,
                                x = px,
                                y = py,
                                vx = cvx,
                                vy = cvy,
                                fuseTimerMs = config.childFuseTimeMs,
                                isChild = true,
                                isActive = true
                            )
                            updatedGrenades.add(childGrenade)
                            checksumAccumulator = (checksumAccumulator xor childGrenade.computeChecksum()) * 16777619L
                        }
                    }
                } else {
                    explosions.add(MirvExplosion(px, py, config.childExplosionRadiusPx, config.childDamage, true))
                }

                // Add parent as inactive
                val deadG = MirvGrenade(g.id, px, py, pvx, pvy, 0f, g.isChild, false)
                updatedGrenades.add(deadG)
                checksumAccumulator = (checksumAccumulator xor deadG.computeChecksum()) * 16777619L
            } else {
                val newG = MirvGrenade(g.id, px, py, pvx, pvy, fuse, g.isChild, true)
                updatedGrenades.add(newG)
                checksumAccumulator = (checksumAccumulator xor newG.computeChecksum()) * 16777619L
            }
        }

        return MirvClusterResult(
            stateChecksum = checksumAccumulator,
            updatedGrenades = updatedGrenades,
            explosions = explosions,
            nextId = nextIdTracker
        )
    }
}
