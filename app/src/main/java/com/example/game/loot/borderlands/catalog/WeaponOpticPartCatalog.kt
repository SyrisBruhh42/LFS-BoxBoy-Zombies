package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep

@Keep
data class WeaponOpticPart(
    val id: String,
    val displayName: String,
    val lanternRangePx: Float,
    val lightLockConeDegrees: Float,
    val critMultiplier: Float
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "displayName" to displayName,
        "lanternRangePx" to lanternRangePx,
        "lightLockConeDegrees" to lightLockConeDegrees,
        "critMultiplier" to critMultiplier
    )

    fun computeChecksum(): Long {
        var hash = -3750763034362895579L
        val prime = 1099511628211L
        
        hash = (hash xor id.hashCode().toLong()) * prime
        hash = (hash xor displayName.hashCode().toLong()) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(lanternRangePx).toLong()) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(lightLockConeDegrees).toLong()) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(critMultiplier).toLong()) * prime
        
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): WeaponOpticPart {
            val id = map["id"]?.toString() ?: "OPTIC_DEFAULT"
            val displayName = map["displayName"]?.toString() ?: "Default Iron Sights"
            val lanternRangePx = (map["lanternRangePx"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 200f
            val lightLockConeDegrees = (map["lightLockConeDegrees"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 45f
            val critMultiplier = (map["critMultiplier"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 1.0f

            return WeaponOpticPart(
                id = id,
                displayName = displayName,
                lanternRangePx = lanternRangePx.coerceIn(10f, 1500f),
                lightLockConeDegrees = lightLockConeDegrees.coerceIn(5f, 360f),
                critMultiplier = critMultiplier.coerceIn(0.1f, 10.0f)
            )
        }
    }
}

@Keep
data class OpticSimulationState(
    var entityId: Int,
    var currentCritMultiplier: Float,
    var activeLanternRangePx: Float,
    var activeLightLockConeDegrees: Float
) {
    fun toMap(): Map<String, Any> = mapOf(
        "entityId" to entityId,
        "currentCritMultiplier" to currentCritMultiplier,
        "activeLanternRangePx" to activeLanternRangePx,
        "activeLightLockConeDegrees" to activeLightLockConeDegrees
    )

    fun computeChecksum(): Long {
        var hash = -3750763034362895579L
        val prime = 1099511628211L
        hash = (hash xor entityId.toLong()) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(currentCritMultiplier).toLong()) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(activeLanternRangePx).toLong()) * prime
        hash = (hash xor java.lang.Float.floatToIntBits(activeLightLockConeDegrees).toLong()) * prime
        return hash
    }

    companion object {
        fun fromMap(map: Map<String, Any>): OpticSimulationState {
            return OpticSimulationState(
                entityId = (map["entityId"] as? Number)?.toInt() ?: -1,
                currentCritMultiplier = (map["currentCritMultiplier"] as? Number)?.toFloat()?.takeIf { it.isFinite() }?.coerceIn(0.1f, 10f) ?: 1f,
                activeLanternRangePx = (map["activeLanternRangePx"] as? Number)?.toFloat()?.takeIf { it.isFinite() }?.coerceIn(10f, 1500f) ?: 200f,
                activeLightLockConeDegrees = (map["activeLightLockConeDegrees"] as? Number)?.toFloat()?.takeIf { it.isFinite() }?.coerceIn(5f, 360f) ?: 45f
            )
        }
    }
}

object WeaponOpticPartCatalog {
    val CATALOG = listOf(
        WeaponOpticPart("OPTIC_01", "Standard Iron Sights", 250f, 60f, 1.2f),
        WeaponOpticPart("OPTIC_02", "Red Dot Sight", 300f, 45f, 1.4f),
        WeaponOpticPart("OPTIC_03", "Holo Sight", 350f, 50f, 1.35f),
        WeaponOpticPart("OPTIC_04", "ACOG Scope", 500f, 25f, 2.0f),
        WeaponOpticPart("OPTIC_05", "Thermal Scope", 450f, 20f, 1.8f),
        WeaponOpticPart("OPTIC_06", "Sniper Scope 8x", 800f, 10f, 3.5f),
        WeaponOpticPart("OPTIC_07", "Sniper Scope 16x", 1200f, 5f, 5.0f),
        WeaponOpticPart("OPTIC_08", "Reflex Sight", 320f, 55f, 1.3f),
        WeaponOpticPart("OPTIC_09", "Night Vision Optic", 400f, 40f, 1.6f),
        WeaponOpticPart("OPTIC_10", "Laser Pointer Sight", 600f, 15f, 1.7f),
        WeaponOpticPart("OPTIC_11", "Compact Sight", 280f, 65f, 1.25f),
        WeaponOpticPart("OPTIC_12", "Variable Zoom Scope", 900f, 8f, 4.0f),
        WeaponOpticPart("OPTIC_13", "Quickdraw Sight", 270f, 70f, 1.15f),
        WeaponOpticPart("OPTIC_14", "Heavy Optic", 480f, 30f, 1.9f),
        WeaponOpticPart("OPTIC_15", "Recon Scope", 700f, 12f, 2.5f),
        WeaponOpticPart("OPTIC_16", "Abyssal Lens", 1000f, 60f, 4.5f)
    )

    fun getOpticById(id: String): WeaponOpticPart {
        for (i in CATALOG.indices) {
            if (CATALOG[i].id == id) {
                return CATALOG[i]
            }
        }
        return CATALOG[0] // fallback
    }
    
    fun getRandomOptic(seed: Long): WeaponOpticPart {
        val lcg = (seed * 6364136223846793005L + 1442695040888963407L)
        val index = ((lcg.ushr(1)) % CATALOG.size).toInt()
        return CATALOG[index]
    }

    /**
     * Delta-time stepping logic for dynamic optics adjustments under stress.
     * Uses primitive types and in-place mutation to avoid heap churn.
     */
    fun processOpticStates(
        states: Array<OpticSimulationState>,
        activeCount: Int,
        dtSeconds: Float
    ) {
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)
        for (i in 0 until activeCount) {
            val state = states[i]
            // Simple logic simulating dynamic optic adjustments based on time 
            // e.g. focusing mechanics, range swaying
            
            // Adding small deterministic noise/decay using dt
            var nextRange = state.activeLanternRangePx - (dt * 5f)
            if (!nextRange.isFinite()) nextRange = 200f
            state.activeLanternRangePx = nextRange.coerceIn(10f, 1500f)

            var nextCone = state.activeLightLockConeDegrees + (dt * 1f)
            if (!nextCone.isFinite()) nextCone = 45f
            state.activeLightLockConeDegrees = nextCone.coerceIn(5f, 360f)
            
            var nextCrit = state.currentCritMultiplier
            if (!nextCrit.isFinite()) nextCrit = 1f
            state.currentCritMultiplier = nextCrit.coerceIn(0.1f, 10.0f)
        }
    }
}


fun evaluateWeaponOpticPartCatalogLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "WeaponOpticPartCatalog") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
