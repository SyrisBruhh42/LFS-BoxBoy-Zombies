package com.example.game.relics.items

import androidx.annotation.Keep
import kotlin.math.sqrt

@Keep
data class VoltaicUkuleleConfig(
    val procChance: Float = 0.25f,
    val damageMultiplier: Float = 0.80f,
    val baseTargets: Int = 3,
    val targetsPerStack: Int = 2,
    val baseRadius: Float = 60f,
    val radiusPerStack: Float = 60f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "procChance" to procChance,
        "damageMultiplier" to damageMultiplier,
        "baseTargets" to baseTargets,
        "targetsPerStack" to targetsPerStack,
        "baseRadius" to baseRadius,
        "radiusPerStack" to radiusPerStack
    )

    companion object {
        fun fromMap(map: Map<String, Any>): VoltaicUkuleleConfig {
            return VoltaicUkuleleConfig(
                procChance = (map["procChance"] as? Number)?.toFloat() ?: 0.25f,
                damageMultiplier = (map["damageMultiplier"] as? Number)?.toFloat() ?: 0.80f,
                baseTargets = (map["baseTargets"] as? Number)?.toInt() ?: 3,
                targetsPerStack = (map["targetsPerStack"] as? Number)?.toInt() ?: 2,
                baseRadius = (map["baseRadius"] as? Number)?.toFloat() ?: 60f,
                radiusPerStack = (map["radiusPerStack"] as? Number)?.toFloat() ?: 60f
            )
        }
    }
}

@Keep
data class ActiveArcLightning(
    val arcId: Long,
    val x: Float,
    val y: Float,
    val damage: Float,
    val remainingBounces: Int,
    val bounceRadius: Float,
    val visitedEntityIds: List<Long>,
    val timeToNextBounce: Float = 0.05f
) {
    val checksum: Long
        get() {
            var hash = 2166136261L
            hash = (hash xor arcId) * 16777619L
            hash = (hash xor x.toBits().toLong()) * 16777619L
            hash = (hash xor y.toBits().toLong()) * 16777619L
            hash = (hash xor damage.toBits().toLong()) * 16777619L
            hash = (hash xor remainingBounces.toLong()) * 16777619L
            hash = (hash xor bounceRadius.toBits().toLong()) * 16777619L
            for (id in visitedEntityIds) {
                hash = (hash xor id) * 16777619L
            }
            hash = (hash xor timeToNextBounce.toBits().toLong()) * 16777619L
            return hash
        }

    fun toMap(): Map<String, Any> = mapOf(
        "arcId" to arcId,
        "x" to x,
        "y" to y,
        "damage" to damage,
        "remainingBounces" to remainingBounces,
        "bounceRadius" to bounceRadius,
        "visitedEntityIds" to visitedEntityIds,
        "timeToNextBounce" to timeToNextBounce,
        "checksum" to checksum
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any>): ActiveArcLightning {
            return ActiveArcLightning(
                arcId = (map["arcId"] as? Number)?.toLong() ?: 0L,
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                damage = (map["damage"] as? Number)?.toFloat() ?: 0f,
                remainingBounces = (map["remainingBounces"] as? Number)?.toInt() ?: 0,
                bounceRadius = (map["bounceRadius"] as? Number)?.toFloat() ?: 0f,
                visitedEntityIds = (map["visitedEntityIds"] as? List<Number>)?.map { it.toLong() } ?: emptyList(),
                timeToNextBounce = (map["timeToNextBounce"] as? Number)?.toFloat() ?: 0.05f
            )
        }
    }
}

@Keep
data class VoltaicUkuleleSnapshot(
    val activeArcs: List<ActiveArcLightning> = emptyList()
) {
    val checksum: Long
        get() {
            var hash = 2166136261L
            for (arc in activeArcs) {
                hash = (hash xor arc.checksum) * 16777619L
            }
            return hash
        }

    fun toMap(): Map<String, Any> = mapOf(
        "activeArcs" to activeArcs.map { it.toMap() },
        "checksum" to checksum
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any>): VoltaicUkuleleSnapshot {
            val arcsList = (map["activeArcs"] as? List<Map<String, Any>>)?.map { ActiveArcLightning.fromMap(it) } ?: emptyList()
            return VoltaicUkuleleSnapshot(activeArcs = arcsList)
        }
    }
}

object Relic01VoltaicUkuleleEngine {
    const val CAN_BE_PURCHASED_AT_VENDING = false

    private fun lcg(seed: Long): Float {
        val nextSeed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        return (nextSeed.toFloat() / 0x7fffffffL)
    }

    fun tryProc(
        stackCount: Int,
        baseDamage: Float,
        hitX: Float,
        hitY: Float,
        hitEntityId: Long,
        seed: Long,
        config: VoltaicUkuleleConfig = VoltaicUkuleleConfig()
    ): ActiveArcLightning? {
        if (stackCount <= 0) return null
        
        if (!hitX.isFinite() || !hitY.isFinite() || !baseDamage.isFinite()) return null

        val rand = lcg(seed)
        if (rand > config.procChance) return null

        val totalTargets = config.baseTargets + (stackCount - 1) * config.targetsPerStack
        // The radius is +60px per stack.
        // Stack 1 => 60, Stack 2 => 120, etc.
        val totalRadius = config.baseRadius + (stackCount - 1) * config.radiusPerStack
        val damage = baseDamage * config.damageMultiplier

        // Bounces = targets - 1 (since the first hit doesn't count as a bounce, but wait...
        // The first hit procs it. Then it chains to N more targets.
        // Let's say totalTargets is the number of targets it can hit after the initial proc.
        // The tooltip says "+2 targets". So at stack 1, it hits 3 targets. The initial hit procced it, so we want 3 total targets (including the initial? Or 3 extra bounces?).
        // Usually, 3 targets means it bounces to 3 other targets. We will use `totalTargets` as `remainingBounces`.
        val remainingBounces = totalTargets

        return ActiveArcLightning(
            arcId = seed xor hitEntityId xor 0x12345678L,
            x = hitX,
            y = hitY,
            damage = damage,
            remainingBounces = remainingBounces,
            bounceRadius = totalRadius,
            visitedEntityIds = listOf(hitEntityId),
            timeToNextBounce = 0.05f
        )
    }

    fun tickArcs(
        snapshot: VoltaicUkuleleSnapshot,
        dtSeconds: Float,
        entityIds: LongArray,
        entityXs: FloatArray,
        entityYs: FloatArray,
        entityHps: FloatArray
    ): Pair<VoltaicUkuleleSnapshot, FloatArray> {
        if (snapshot.activeArcs.isEmpty()) return Pair(snapshot, entityHps)

        val dt = dtSeconds.coerceIn(0.001f, 0.100f)
        if (!dt.isFinite()) return Pair(snapshot, entityHps)

        val newArcs = mutableListOf<ActiveArcLightning>()
        val modifiedHps = entityHps.copyOf()

        val entityCount = entityIds.size

        for (arc in snapshot.activeArcs) {
            if (arc.remainingBounces <= 0) continue

            var timeToNext = arc.timeToNextBounce - dt
            if (timeToNext > 0f) {
                newArcs.add(arc.copy(timeToNextBounce = timeToNext))
                continue
            }

            // Time to bounce!
            var closestDistSq = Float.MAX_VALUE
            var closestIdx = -1
            val maxDistSq = arc.bounceRadius * arc.bounceRadius

            for (i in 0 until entityCount) {
                if (modifiedHps[i] <= 0f) continue
                if (arc.visitedEntityIds.contains(entityIds[i])) continue

                val dx = entityXs[i] - arc.x
                val dy = entityYs[i] - arc.y
                if (!dx.isFinite() || !dy.isFinite()) continue

                val distSq = dx * dx + dy * dy
                if (distSq <= maxDistSq && distSq < closestDistSq) {
                    closestDistSq = distSq
                    closestIdx = i
                }
            }

            if (closestIdx != -1) {
                // Found a target
                modifiedHps[closestIdx] -= arc.damage
                val hitEntityId = entityIds[closestIdx]

                val newVisited = ArrayList<Long>(arc.visitedEntityIds.size + 1)
                newVisited.addAll(arc.visitedEntityIds)
                newVisited.add(hitEntityId)

                if (arc.remainingBounces - 1 > 0) {
                    newArcs.add(
                        ActiveArcLightning(
                            arcId = arc.arcId xor hitEntityId,
                            x = entityXs[closestIdx],
                            y = entityYs[closestIdx],
                            damage = arc.damage,
                            remainingBounces = arc.remainingBounces - 1,
                            bounceRadius = arc.bounceRadius,
                            visitedEntityIds = newVisited,
                            timeToNextBounce = 0.05f
                        )
                    )
                }
            }
            // If no target found, the arc just fizzles out (not added to newArcs)
        }

        return Pair(VoltaicUkuleleSnapshot(newArcs), modifiedHps)
    }
}


fun evaluateRelic01VoltaicUkuleleLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "Relic01VoltaicUkulele") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
