package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import com.example.game.LootDropEntity
import com.example.game.LootPayloadKind
import com.example.game.ModularWeaponInstance
import kotlin.math.hypot
import kotlin.math.roundToInt

@Keep
enum class StatDeltaIndicator {
    UP_GREEN,
    DOWN_RED,
    EQUAL_GRAY
}

@Keep
data class WeaponStatsCard(
    val damage: Int,
    val fireRatePerSecond: Float,
    val magSize: Int,
    val reloadMs: Long,
    val critMultiplier: Float
) {
    fun toMap(): Map<String, Any> = mapOf(
        "damage" to damage,
        "fireRatePerSecond" to fireRatePerSecond,
        "magSize" to magSize,
        "reloadMs" to reloadMs,
        "critMultiplier" to critMultiplier
    )

    companion object {
        fun fromMap(map: Map<String, Any>): WeaponStatsCard {
            return WeaponStatsCard(
                damage = (map["damage"] as? Number)?.toInt() ?: 0,
                fireRatePerSecond = (map["fireRatePerSecond"] as? Number)?.toFloat() ?: 0f,
                magSize = (map["magSize"] as? Number)?.toInt() ?: 0,
                reloadMs = (map["reloadMs"] as? Number)?.toLong() ?: 0L,
                critMultiplier = (map["critMultiplier"] as? Number)?.toFloat() ?: 0f
            )
        }
    }
}

@Keep
data class SwapOnSpotComparisonResult(
    val damageDelta: Int,
    val damageIndicator: StatDeltaIndicator,
    val fireRateDelta: Float,
    val fireRateIndicator: StatDeltaIndicator,
    val magSizeDelta: Int,
    val magSizeIndicator: StatDeltaIndicator,
    val reloadMsDelta: Long,
    val reloadIndicator: StatDeltaIndicator,
    val critMultiplierDelta: Float,
    val critIndicator: StatDeltaIndicator
) {
    fun toMap(): Map<String, Any> = mapOf(
        "damageDelta" to damageDelta,
        "damageIndicator" to damageIndicator.name,
        "fireRateDelta" to fireRateDelta,
        "fireRateIndicator" to fireRateIndicator.name,
        "magSizeDelta" to magSizeDelta,
        "magSizeIndicator" to magSizeIndicator.name,
        "reloadMsDelta" to reloadMsDelta,
        "reloadIndicator" to reloadIndicator.name,
        "critMultiplierDelta" to critMultiplierDelta,
        "critIndicator" to critIndicator.name
    )

    companion object {
        fun fromMap(map: Map<String, Any>): SwapOnSpotComparisonResult {
            return SwapOnSpotComparisonResult(
                damageDelta = (map["damageDelta"] as? Number)?.toInt() ?: 0,
                damageIndicator = StatDeltaIndicator.valueOf(map["damageIndicator"] as? String ?: StatDeltaIndicator.EQUAL_GRAY.name),
                fireRateDelta = (map["fireRateDelta"] as? Number)?.toFloat() ?: 0f,
                fireRateIndicator = StatDeltaIndicator.valueOf(map["fireRateIndicator"] as? String ?: StatDeltaIndicator.EQUAL_GRAY.name),
                magSizeDelta = (map["magSizeDelta"] as? Number)?.toInt() ?: 0,
                magSizeIndicator = StatDeltaIndicator.valueOf(map["magSizeIndicator"] as? String ?: StatDeltaIndicator.EQUAL_GRAY.name),
                reloadMsDelta = (map["reloadMsDelta"] as? Number)?.toLong() ?: 0L,
                reloadIndicator = StatDeltaIndicator.valueOf(map["reloadIndicator"] as? String ?: StatDeltaIndicator.EQUAL_GRAY.name),
                critMultiplierDelta = (map["critMultiplierDelta"] as? Number)?.toFloat() ?: 0f,
                critIndicator = StatDeltaIndicator.valueOf(map["critIndicator"] as? String ?: StatDeltaIndicator.EQUAL_GRAY.name)
            )
        }
    }
}

@Keep
data class SwapOnSpotEvaluationState(
    val closestLootId: Int?,
    val comparisonResult: SwapOnSpotComparisonResult?,
    val candidateStats: WeaponStatsCard?,
    val checksum: Long
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "closestLootId" to closestLootId,
        "comparisonResult" to comparisonResult?.toMap(),
        "candidateStats" to candidateStats?.toMap(),
        "checksum" to checksum
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): SwapOnSpotEvaluationState {
            return SwapOnSpotEvaluationState(
                closestLootId = (map["closestLootId"] as? Number)?.toInt(),
                comparisonResult = (map["comparisonResult"] as? Map<String, Any>)?.let { SwapOnSpotComparisonResult.fromMap(it) },
                candidateStats = (map["candidateStats"] as? Map<String, Any>)?.let { WeaponStatsCard.fromMap(it) },
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

object SwapOnSpotWeaponEvaluator {

    const val MAX_INTERACTION_RADIUS = 120f

    fun evaluateClosestWeapon(
        playerX: Float,
        playerY: Float,
        activeDrops: List<LootDropEntity>,
        equippedWeapon: ModularWeaponInstance,
        dtSeconds: Float = 0.016f
    ): SwapOnSpotEvaluationState {
        // Enforce dtSeconds clamping [0.001, 0.100]
        val clampedDt = dtSeconds.coerceIn(0.001f, 0.100f)

        // Validate coordinates
        val pX = if (playerX.isFinite()) playerX else 0f
        val pY = if (playerY.isFinite()) playerY else 0f

        var closestDistanceSq = MAX_INTERACTION_RADIUS * MAX_INTERACTION_RADIUS
        var closestWeaponDrop: LootDropEntity? = null
        var closestWeapon: ModularWeaponInstance? = null

        // Iterate through all active drops directly
        for (i in activeDrops.indices) {
            val drop = activeDrops[i]
            if (drop.payloadKind == LootPayloadKind.MODULAR_WEAPON && drop.weaponPayload != null) {
                val dx = drop.x - pX
                val dy = drop.y - pY
                val distSq = dx * dx + dy * dy
                if (distSq <= closestDistanceSq) {
                    closestDistanceSq = distSq
                    closestWeaponDrop = drop
                    closestWeapon = drop.weaponPayload
                }
            }
        }

        if (closestWeaponDrop == null || closestWeapon == null) {
            return SwapOnSpotEvaluationState(
                closestLootId = null,
                comparisonResult = null,
                candidateStats = null,
                checksum = 0L // Base empty checksum
            )
        }

        val equippedStats = WeaponStatsCard(
            damage = equippedWeapon.effectiveDamage,
            fireRatePerSecond = equippedWeapon.shotsPerSecond,
            magSize = equippedWeapon.effectiveMagSize,
            reloadMs = equippedWeapon.effectiveReloadMs,
            critMultiplier = equippedWeapon.illuminatedCritMultiplier
        )

        val candidateStats = WeaponStatsCard(
            damage = closestWeapon.effectiveDamage,
            fireRatePerSecond = closestWeapon.shotsPerSecond,
            magSize = closestWeapon.effectiveMagSize,
            reloadMs = closestWeapon.effectiveReloadMs,
            critMultiplier = closestWeapon.illuminatedCritMultiplier
        )

        val comparisonResult = compareStats(equippedStats, candidateStats)

        val state = SwapOnSpotEvaluationState(
            closestLootId = closestWeaponDrop.id,
            comparisonResult = comparisonResult,
            candidateStats = candidateStats,
            checksum = 0L
        )
        return state.copy(checksum = calculateChecksum(state))
    }

    private fun compareStats(equipped: WeaponStatsCard, candidate: WeaponStatsCard): SwapOnSpotComparisonResult {
        // Damage (Higher is better)
        val dmgDelta = candidate.damage - equipped.damage
        val dmgIndicator = when {
            dmgDelta > 0 -> StatDeltaIndicator.UP_GREEN
            dmgDelta < 0 -> StatDeltaIndicator.DOWN_RED
            else -> StatDeltaIndicator.EQUAL_GRAY
        }

        // Fire Rate (Higher is better)
        val rofDeltaRaw = candidate.fireRatePerSecond - equipped.fireRatePerSecond
        val rofDelta = if (rofDeltaRaw.isFinite()) rofDeltaRaw else 0f
        val rofIndicator = when {
            rofDelta > 0.01f -> StatDeltaIndicator.UP_GREEN
            rofDelta < -0.01f -> StatDeltaIndicator.DOWN_RED
            else -> StatDeltaIndicator.EQUAL_GRAY
        }

        // Mag Size (Higher is better)
        val magDelta = candidate.magSize - equipped.magSize
        val magIndicator = when {
            magDelta > 0 -> StatDeltaIndicator.UP_GREEN
            magDelta < 0 -> StatDeltaIndicator.DOWN_RED
            else -> StatDeltaIndicator.EQUAL_GRAY
        }

        // Reload Time (Lower is better!)
        val reloadDelta = candidate.reloadMs - equipped.reloadMs
        val reloadIndicator = when {
            reloadDelta < 0 -> StatDeltaIndicator.UP_GREEN
            reloadDelta > 0 -> StatDeltaIndicator.DOWN_RED
            else -> StatDeltaIndicator.EQUAL_GRAY
        }

        // Crit Multiplier (Higher is better)
        val critDeltaRaw = candidate.critMultiplier - equipped.critMultiplier
        val critDelta = if (critDeltaRaw.isFinite()) critDeltaRaw else 0f
        val critIndicator = when {
            critDelta > 0.01f -> StatDeltaIndicator.UP_GREEN
            critDelta < -0.01f -> StatDeltaIndicator.DOWN_RED
            else -> StatDeltaIndicator.EQUAL_GRAY
        }

        return SwapOnSpotComparisonResult(
            damageDelta = dmgDelta,
            damageIndicator = dmgIndicator,
            fireRateDelta = rofDelta,
            fireRateIndicator = rofIndicator,
            magSizeDelta = magDelta,
            magSizeIndicator = magIndicator,
            reloadMsDelta = reloadDelta,
            reloadIndicator = reloadIndicator,
            critMultiplierDelta = critDelta,
            critIndicator = critIndicator
        )
    }

    private fun calculateChecksum(state: SwapOnSpotEvaluationState): Long {
        var hash = -2128831035142426939L // FNV offset basis

        fun appendInt(value: Int) {
            hash = hash xor value.toLong()
            hash *= 1099511628211L
        }
        fun appendFloat(value: Float) {
            hash = hash xor value.toRawBits().toLong()
            hash *= 1099511628211L
        }
        fun appendLong(value: Long) {
            hash = hash xor value
            hash *= 1099511628211L
        }

        state.closestLootId?.let { appendInt(it) } ?: appendInt(-1)

        state.comparisonResult?.let {
            appendInt(it.damageDelta)
            appendInt(it.damageIndicator.ordinal)
            appendFloat(it.fireRateDelta)
            appendInt(it.fireRateIndicator.ordinal)
            appendInt(it.magSizeDelta)
            appendInt(it.magSizeIndicator.ordinal)
            appendLong(it.reloadMsDelta)
            appendInt(it.reloadIndicator.ordinal)
            appendFloat(it.critMultiplierDelta)
            appendInt(it.critIndicator.ordinal)
        }

        state.candidateStats?.let {
            appendInt(it.damage)
            appendFloat(it.fireRatePerSecond)
            appendInt(it.magSize)
            appendLong(it.reloadMs)
            appendFloat(it.critMultiplier)
        }

        return hash
    }
}
