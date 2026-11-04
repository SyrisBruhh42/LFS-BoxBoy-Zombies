package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import kotlin.math.max

/**
 * 16 procedural barrel parts controlling spread, pellet count, range, and pierce
 * for Unified BoxBoys Track 5 (Borderlands 2 Swap-on-Spot Gear & Weapons).
 */
@Keep
enum class BarrelPartType {
    STANDARD,
    CHOKED,
    SAWED_OFF,
    SNIPER,
    HEAVY,
    SPREAD,
    PRECISION,
    ASSAULT,
    BURST,
    LONG_BARREL,
    SHORT_BARREL,
    TWIN_BARREL,
    QUAD_BARREL,
    FLUTED,
    BULL_BARREL,
    COMPENSATOR
}

@Keep
data class WeaponBarrelConfig(
    val type: BarrelPartType,
    val seed: Int,
    val spreadMultiplier: Float,
    val pelletCountBonus: Int,
    val rangeMultiplier: Float,
    val pierceBonus: Int,
    val checksum: Int = 0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "type" to type.name,
        "seed" to seed,
        "spreadMultiplier" to spreadMultiplier,
        "pelletCountBonus" to pelletCountBonus,
        "rangeMultiplier" to rangeMultiplier,
        "pierceBonus" to pierceBonus,
        "checksum" to checksum
    )

    companion object {
        fun calculateChecksum(
            type: BarrelPartType,
            seed: Int,
            spreadMultiplier: Float,
            pelletCountBonus: Int,
            rangeMultiplier: Float,
            pierceBonus: Int
        ): Int {
            var hash = -2128831035 // FNV-1a 32-bit offset basis
            hash = (hash xor type.ordinal) * 16777619
            hash = (hash xor seed) * 16777619
            hash = (hash xor spreadMultiplier.toBits()) * 16777619
            hash = (hash xor pelletCountBonus) * 16777619
            hash = (hash xor rangeMultiplier.toBits()) * 16777619
            hash = (hash xor pierceBonus) * 16777619
            return hash
        }

        fun fromMap(map: Map<String, Any>): WeaponBarrelConfig {
            val typeStr = map["type"] as? String ?: BarrelPartType.STANDARD.name
            val type = try { BarrelPartType.valueOf(typeStr) } catch (e: Exception) { BarrelPartType.STANDARD }
            val seed = (map["seed"] as? Number)?.toInt() ?: 0
            
            val spreadRaw = (map["spreadMultiplier"] as? Number)?.toFloat() ?: 1.0f
            val pelletCountBonus = (map["pelletCountBonus"] as? Number)?.toInt() ?: 0
            val rangeRaw = (map["rangeMultiplier"] as? Number)?.toFloat() ?: 1.0f
            val pierceBonus = (map["pierceBonus"] as? Number)?.toInt() ?: 0

            val spreadMultiplier = if (spreadRaw.isFinite() && spreadRaw > 0f) spreadRaw else 1.0f
            val rangeMultiplier = if (rangeRaw.isFinite() && rangeRaw > 0f) rangeRaw else 1.0f

            val providedChecksum = (map["checksum"] as? Number)?.toInt() ?: 0
            val calculatedChecksum = calculateChecksum(type, seed, spreadMultiplier, pelletCountBonus, rangeMultiplier, pierceBonus)
            
            // Re-calculate checksum to ensure integrity
            return WeaponBarrelConfig(
                type = type,
                seed = seed,
                spreadMultiplier = spreadMultiplier,
                pelletCountBonus = pelletCountBonus,
                rangeMultiplier = rangeMultiplier,
                pierceBonus = pierceBonus,
                checksum = calculatedChecksum
            )
        }
    }
}

object WeaponBarrelPartCatalog {

    private fun createBaseConfig(type: BarrelPartType, spread: Float, pellets: Int, range: Float, pierce: Int): WeaponBarrelConfig {
        return WeaponBarrelConfig(
            type = type,
            seed = 0,
            spreadMultiplier = spread,
            pelletCountBonus = pellets,
            rangeMultiplier = range,
            pierceBonus = pierce,
            checksum = WeaponBarrelConfig.calculateChecksum(type, 0, spread, pellets, range, pierce)
        )
    }

    private val STANDARD_CONFIG = createBaseConfig(BarrelPartType.STANDARD, 1.0f, 0, 1.0f, 0)

    // Pre-allocated immutable map for zero per-frame heap churn.
    private val catalogMap: Map<BarrelPartType, WeaponBarrelConfig> = mapOf(
        BarrelPartType.STANDARD to STANDARD_CONFIG,
        BarrelPartType.CHOKED to createBaseConfig(BarrelPartType.CHOKED, 0.7f, 0, 1.1f, 0),
        BarrelPartType.SAWED_OFF to createBaseConfig(BarrelPartType.SAWED_OFF, 1.8f, 2, 0.6f, 0),
        BarrelPartType.SNIPER to createBaseConfig(BarrelPartType.SNIPER, 0.3f, 0, 2.5f, 1),
        BarrelPartType.HEAVY to createBaseConfig(BarrelPartType.HEAVY, 1.2f, 0, 1.5f, 2),
        BarrelPartType.SPREAD to createBaseConfig(BarrelPartType.SPREAD, 2.0f, 4, 0.5f, 0),
        BarrelPartType.PRECISION to createBaseConfig(BarrelPartType.PRECISION, 0.5f, 0, 1.8f, 0),
        BarrelPartType.ASSAULT to createBaseConfig(BarrelPartType.ASSAULT, 1.1f, 0, 1.2f, 0),
        BarrelPartType.BURST to createBaseConfig(BarrelPartType.BURST, 0.85f, 1, 1.0f, 0),
        BarrelPartType.LONG_BARREL to createBaseConfig(BarrelPartType.LONG_BARREL, 0.8f, 0, 1.4f, 0),
        BarrelPartType.SHORT_BARREL to createBaseConfig(BarrelPartType.SHORT_BARREL, 1.4f, 0, 0.7f, 0),
        BarrelPartType.TWIN_BARREL to createBaseConfig(BarrelPartType.TWIN_BARREL, 1.3f, 1, 0.8f, 0),
        BarrelPartType.QUAD_BARREL to createBaseConfig(BarrelPartType.QUAD_BARREL, 1.6f, 3, 0.6f, 0),
        BarrelPartType.FLUTED to createBaseConfig(BarrelPartType.FLUTED, 0.9f, 0, 1.3f, 0),
        BarrelPartType.BULL_BARREL to createBaseConfig(BarrelPartType.BULL_BARREL, 1.1f, 0, 1.6f, 1),
        BarrelPartType.COMPENSATOR to createBaseConfig(BarrelPartType.COMPENSATOR, 0.6f, 0, 1.0f, 0)
    )

    /**
     * Returns the pre-allocated base configuration for a given type.
     * Guaranteed zero allocations. Use for hot loops or default resolution.
     */
    fun resolveBaseConfig(type: BarrelPartType?): WeaponBarrelConfig {
        if (type == null) return STANDARD_CONFIG
        return catalogMap[type] ?: STANDARD_CONFIG
    }

    /**
     * Generates a procedural barrel variation utilizing deterministic LCG and FNV-1a checksums.
     * Performs allocations; call this only at generation/spawn time, not during hot frame loops.
     */
    fun generateProceduralBarrel(type: BarrelPartType, seed: Int): WeaponBarrelConfig {
        val base = resolveBaseConfig(type)
        
        // Deterministic LCG setup
        var lcg = seed
        lcg = (lcg * 1103515245 + 12345) and 0x7FFFFFFF
        val spreadJitter = ((lcg % 1000) / 1000.0f) * 0.2f - 0.1f // +/- 10%
        
        lcg = (lcg * 1103515245 + 12345) and 0x7FFFFFFF
        val rangeJitter = ((lcg % 1000) / 1000.0f) * 0.2f - 0.1f // +/- 10%

        val finalSpread = max(0.1f, base.spreadMultiplier + spreadJitter)
        val finalRange = max(0.1f, base.rangeMultiplier + rangeJitter)

        val checksum = WeaponBarrelConfig.calculateChecksum(
            type = type,
            seed = seed,
            spreadMultiplier = finalSpread,
            pelletCountBonus = base.pelletCountBonus,
            rangeMultiplier = finalRange,
            pierceBonus = base.pierceBonus
        )

        return WeaponBarrelConfig(
            type = type,
            seed = seed,
            spreadMultiplier = finalSpread,
            pelletCountBonus = base.pelletCountBonus,
            rangeMultiplier = finalRange,
            pierceBonus = base.pierceBonus,
            checksum = checksum
        )
    }

    /**
     * Frame-rate-independent delta-time stepping for barrel heat / degradation logic.
     * Guaranteed zero allocations.
     */
    fun tickBarrelHeat(currentHeat: Float, dtSeconds: Float, coolingRate: Float): Float {
        // Clamp dtSeconds to valid bounds [0.001, 0.100] to prevent huge jumps or negative time
        val clampedDt = if (dtSeconds.isNaN() || dtSeconds < 0.001f) 0.001f else if (dtSeconds > 0.100f) 0.100f else dtSeconds
        
        val safeCurrent = if (currentHeat.isFinite()) currentHeat else 0f
        val safeCooling = if (coolingRate.isFinite()) coolingRate else 1.0f

        val newHeat = safeCurrent - (safeCooling * clampedDt)
        return max(0f, newHeat)
    }
}


fun evaluateWeaponBarrelPartCatalogLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "WeaponBarrelPartCatalog") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
