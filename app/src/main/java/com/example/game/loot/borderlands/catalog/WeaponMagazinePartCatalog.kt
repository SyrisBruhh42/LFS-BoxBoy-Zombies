package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep

@Keep
enum class MagazineElementalAffinity(val id: Int) {
    NONE(0),
    INCENDIARY(1),
    VOLTAIC(2),
    CORROSIVE(3),
    CRYO(4),
    EXPLOSIVE(5);

    companion object {
        fun fromId(id: Int): MagazineElementalAffinity {
            return values().firstOrNull { it.id == id } ?: NONE
        }
    }
}

@Keep
data class WeaponMagazinePart(
    val partId: Int,
    val name: String,
    val capacityMultiplier: Float,
    val reloadSpeedMultiplier: Float,
    val elementalAffinity: MagazineElementalAffinity,
    val elementalChance: Float
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "partId" to partId,
            "name" to name,
            "capacityMultiplier" to capacityMultiplier,
            "reloadSpeedMultiplier" to reloadSpeedMultiplier,
            "elementalAffinity" to elementalAffinity.id,
            "elementalChance" to elementalChance
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): WeaponMagazinePart {
            return WeaponMagazinePart(
                partId = (map["partId"] as? Number)?.toInt() ?: 0,
                name = map["name"] as? String ?: "Unknown",
                capacityMultiplier = (map["capacityMultiplier"] as? Number)?.toFloat() ?: 1.0f,
                reloadSpeedMultiplier = (map["reloadSpeedMultiplier"] as? Number)?.toFloat() ?: 1.0f,
                elementalAffinity = MagazineElementalAffinity.fromId((map["elementalAffinity"] as? Number)?.toInt() ?: 0),
                elementalChance = (map["elementalChance"] as? Number)?.toFloat() ?: 0.0f
            )
        }
    }
}

@Keep
data class MagazineReloadState(
    var entityId: Int = 0,
    var x: Float = 0f,
    var y: Float = 0f,
    var reloadProgress: Float = 0f,
    var reloadDuration: Float = 1.0f,
    var isReloading: Boolean = false,
    var partId: Int = 0
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "entityId" to entityId,
            "x" to x,
            "y" to y,
            "reloadProgress" to reloadProgress,
            "reloadDuration" to reloadDuration,
            "isReloading" to isReloading,
            "partId" to partId
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): MagazineReloadState {
            return MagazineReloadState(
                entityId = (map["entityId"] as? Number)?.toInt() ?: 0,
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f,
                reloadProgress = (map["reloadProgress"] as? Number)?.toFloat() ?: 0f,
                reloadDuration = (map["reloadDuration"] as? Number)?.toFloat() ?: 1.0f,
                isReloading = map["isReloading"] as? Boolean ?: false,
                partId = (map["partId"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

object WeaponMagazinePartCatalog {
    val PARTS = arrayOf(
        WeaponMagazinePart(1, "Standard Mag", 1.0f, 1.0f, MagazineElementalAffinity.NONE, 0.0f),
        WeaponMagazinePart(2, "Extended Mag", 1.5f, 0.8f, MagazineElementalAffinity.NONE, 0.0f),
        WeaponMagazinePart(3, "Quick Mag", 0.8f, 1.5f, MagazineElementalAffinity.NONE, 0.0f),
        WeaponMagazinePart(4, "Incendiary Standard", 1.0f, 1.0f, MagazineElementalAffinity.INCENDIARY, 0.15f),
        WeaponMagazinePart(5, "Incendiary Extended", 1.5f, 0.8f, MagazineElementalAffinity.INCENDIARY, 0.15f),
        WeaponMagazinePart(6, "Incendiary Quick", 0.8f, 1.5f, MagazineElementalAffinity.INCENDIARY, 0.15f),
        WeaponMagazinePart(7, "Voltaic Standard", 1.0f, 1.0f, MagazineElementalAffinity.VOLTAIC, 0.15f),
        WeaponMagazinePart(8, "Voltaic Extended", 1.5f, 0.8f, MagazineElementalAffinity.VOLTAIC, 0.15f),
        WeaponMagazinePart(9, "Voltaic Quick", 0.8f, 1.5f, MagazineElementalAffinity.VOLTAIC, 0.15f),
        WeaponMagazinePart(10, "Corrosive Standard", 1.0f, 1.0f, MagazineElementalAffinity.CORROSIVE, 0.15f),
        WeaponMagazinePart(11, "Corrosive Extended", 1.5f, 0.8f, MagazineElementalAffinity.CORROSIVE, 0.15f),
        WeaponMagazinePart(12, "Corrosive Quick", 0.8f, 1.5f, MagazineElementalAffinity.CORROSIVE, 0.15f),
        WeaponMagazinePart(13, "Cryo Standard", 1.0f, 1.0f, MagazineElementalAffinity.CRYO, 0.15f),
        WeaponMagazinePart(14, "Cryo Extended", 1.5f, 0.8f, MagazineElementalAffinity.CRYO, 0.15f),
        WeaponMagazinePart(15, "Cryo Quick", 0.8f, 1.5f, MagazineElementalAffinity.CRYO, 0.15f),
        WeaponMagazinePart(16, "Explosive Drum", 2.0f, 0.6f, MagazineElementalAffinity.EXPLOSIVE, 0.10f)
    )

    fun getPart(partId: Int): WeaponMagazinePart {
        return PARTS.firstOrNull { it.partId == partId } ?: PARTS[0]
    }

    fun generatePartProcedurally(seed: Long, itemLevel: Int): WeaponMagazinePart {
        var hash = seed
        hash = (hash xor itemLevel.toLong()) * 1099511628211L
        // Mix to ensure bit dispersion
        hash = hash xor (hash ushr 16)
        hash = hash * 1099511628211L
        val index = ((hash and 0x7FFFFFFF) % 16).toInt()
        return PARTS[index]
    }

    const val MAX_RELOAD_STATES = 256
    const val MIN_DT_SECONDS = 0.001f
    const val MAX_DT_SECONDS = 0.100f

    private val reloadStates = arrayOfNulls<MagazineReloadState>(MAX_RELOAD_STATES)
    private var activeCount = 0

    fun startReload(entityId: Int, x: Float, y: Float, partId: Int, baseReloadDuration: Float): Boolean {
        val safeX = if (x.isNaN() || x.isInfinite()) 0f else x
        val safeY = if (y.isNaN() || y.isInfinite()) 0f else y
        val safeDuration = if (baseReloadDuration.isNaN() || baseReloadDuration.isInfinite() || baseReloadDuration <= 0f) 1.0f else baseReloadDuration

        val part = getPart(partId)
        val finalDuration = safeDuration / part.reloadSpeedMultiplier

        for (i in 0 until activeCount) {
            val s = reloadStates[i]
            if (s != null && s.entityId == entityId) {
                s.x = safeX
                s.y = safeY
                s.reloadProgress = 0f
                s.reloadDuration = finalDuration
                s.isReloading = true
                s.partId = partId
                return true
            }
        }

        if (activeCount >= MAX_RELOAD_STATES) {
            // graceful degradation under extreme load
            // Evict oldest or simply reject. Reject is safer for zero heap churn.
            return false
        }

        val newState = MagazineReloadState(
            entityId = entityId,
            x = safeX,
            y = safeY,
            reloadProgress = 0f,
            reloadDuration = finalDuration,
            isReloading = true,
            partId = partId
        )
        reloadStates[activeCount++] = newState
        return true
    }

    fun stepReloads(dtSeconds: Float, playerX: Float, playerY: Float, maxActiveDistance: Float = 2400f) {
        val dt = if (dtSeconds.isNaN() || dtSeconds.isInfinite()) {
            MIN_DT_SECONDS
        } else {
            dtSeconds.coerceIn(MIN_DT_SECONDS, MAX_DT_SECONDS)
        }

        val maxDistSq = maxActiveDistance * maxActiveDistance
        var writeIdx = 0

        for (i in 0 until activeCount) {
            val state = reloadStates[i] ?: continue

            val dx = state.x - playerX
            val dy = state.y - playerY
            val distSq = dx * dx + dy * dy

            if (distSq <= maxDistSq && state.isReloading) {
                state.reloadProgress += dt
                if (state.reloadProgress >= state.reloadDuration) {
                    state.reloadProgress = state.reloadDuration
                    state.isReloading = false
                }
            }

            if (state.isReloading) {
                reloadStates[writeIdx++] = state
            }
        }

        for (i in writeIdx until activeCount) {
            reloadStates[i] = null
        }
        activeCount = writeIdx
    }

    fun getReloadState(entityId: Int): MagazineReloadState? {
        for (i in 0 until activeCount) {
            if (reloadStates[i]?.entityId == entityId) {
                return reloadStates[i]
            }
        }
        return null
    }

    fun clearReloadStates() {
        for (i in 0 until activeCount) {
            reloadStates[i] = null
        }
        activeCount = 0
    }

    fun computeStateChecksum(): Long {
        var checksumSum = 0L
        for (i in 0 until activeCount) {
            val state = reloadStates[i] ?: continue
            var h = -2128831035146330053L
            h = (h xor state.entityId.toLong()) * 1099511628211L
            h = (h xor state.x.toRawBits().toLong()) * 1099511628211L
            h = (h xor state.y.toRawBits().toLong()) * 1099511628211L
            h = (h xor state.reloadProgress.toRawBits().toLong()) * 1099511628211L
            h = (h xor state.reloadDuration.toRawBits().toLong()) * 1099511628211L
            h = (h xor (if (state.isReloading) 1L else 0L)) * 1099511628211L
            h = (h xor state.partId.toLong()) * 1099511628211L
            checksumSum = checksumSum xor h
        }
        return checksumSum
    }
}
