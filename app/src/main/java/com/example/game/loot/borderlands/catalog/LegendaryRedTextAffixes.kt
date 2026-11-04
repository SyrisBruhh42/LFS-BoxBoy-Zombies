package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep

@Keep
enum class LegendaryRedTextAffix {
    INFINITY,
    CONFERENCE_CALL,
    BEE,
    SHAM,
    THUMPER,
    LYUDA,
    SAND_HAWK,
    NORFLEET,
    HERALD,
    FIBBER,
    BITCH,
    BABY_MAKER,
    MAGGIE,
    SLAGGA,
    HELLFIRE,
    VOLCANO,
    PIMPERNEL,
    FLAKKER,
    BUTCHER,
    HAWK_EYE
}

@Keep
class GearStateSnapshot {
    var isBroken: Boolean = false
    var timeSinceBrokenMs: Float = 0f
    var shotsFiredContinuous: Int = 0
    var bonusAccumulator: Float = 0f

    fun toMap(): Map<String, Any> = mapOf(
        "isBroken" to isBroken,
        "timeSinceBrokenMs" to timeSinceBrokenMs,
        "shotsFiredContinuous" to shotsFiredContinuous,
        "bonusAccumulator" to bonusAccumulator
    )

    fun fromMap(map: Map<String, Any>) {
        isBroken = map["isBroken"] as? Boolean ?: false
        timeSinceBrokenMs = (map["timeSinceBrokenMs"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
        shotsFiredContinuous = (map["shotsFiredContinuous"] as? Number)?.toInt() ?: 0
        bonusAccumulator = (map["bonusAccumulator"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
    }
}

@Keep
class GearConfig {
    var baseDamage: Float = 0f
    var baseFireRate: Float = 0f
    var baseSpread: Float = 0f
    var baseMagazineSize: Int = 0
    
    fun toMap(): Map<String, Any> = mapOf(
        "baseDamage" to baseDamage,
        "baseFireRate" to baseFireRate,
        "baseSpread" to baseSpread,
        "baseMagazineSize" to baseMagazineSize
    )

    fun fromMap(map: Map<String, Any>) {
        baseDamage = (map["baseDamage"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
        baseFireRate = (map["baseFireRate"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
        baseSpread = (map["baseSpread"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
        baseMagazineSize = (map["baseMagazineSize"] as? Number)?.toInt() ?: 0
    }
}

@Keep
class OverriddenGearStats {
    var actualDamage: Float = 0f
    var actualFireRate: Float = 0f
    var actualSpread: Float = 0f
    var actualMagazineSize: Int = 0
    var additionalProjectiles: Int = 0
    var isInfiniteAmmo: Boolean = false
    var shieldCapacityMult: Float = 1.0f
    var shieldRechargeRateMult: Float = 1.0f
    var ricochetCount: Int = 0

    fun toMap(): Map<String, Any> = mapOf(
        "actualDamage" to actualDamage,
        "actualFireRate" to actualFireRate,
        "actualSpread" to actualSpread,
        "actualMagazineSize" to actualMagazineSize,
        "additionalProjectiles" to additionalProjectiles,
        "isInfiniteAmmo" to isInfiniteAmmo,
        "shieldCapacityMult" to shieldCapacityMult,
        "shieldRechargeRateMult" to shieldRechargeRateMult,
        "ricochetCount" to ricochetCount
    )

    fun fromMap(map: Map<String, Any>) {
        actualDamage = (map["actualDamage"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
        actualFireRate = (map["actualFireRate"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
        actualSpread = (map["actualSpread"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 0f
        actualMagazineSize = (map["actualMagazineSize"] as? Number)?.toInt() ?: 0
        additionalProjectiles = (map["additionalProjectiles"] as? Number)?.toInt() ?: 0
        isInfiniteAmmo = map["isInfiniteAmmo"] as? Boolean ?: false
        shieldCapacityMult = (map["shieldCapacityMult"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 1.0f
        shieldRechargeRateMult = (map["shieldRechargeRateMult"] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: 1.0f
        ricochetCount = (map["ricochetCount"] as? Number)?.toInt() ?: 0
    }
}

object LegendaryRedTextEngine {

    fun applyAffixInPlace(
        config: GearConfig,
        state: GearStateSnapshot,
        affix: LegendaryRedTextAffix,
        outStats: OverriddenGearStats
    ) {
        // Enforce finite values and handle NaNs/Infinities
        val safeBaseDamage = config.baseDamage.takeIf { it.isFinite() } ?: 0f
        val safeBaseFireRate = config.baseFireRate.takeIf { it.isFinite() } ?: 0.1f
        val safeBaseSpread = config.baseSpread.takeIf { it.isFinite() } ?: 0f
        val safeBaseMagazineSize = config.baseMagazineSize.coerceAtLeast(1)
        
        var damage = safeBaseDamage
        var fireRate = safeBaseFireRate
        var spread = safeBaseSpread
        var magazineSize = safeBaseMagazineSize
        var additionalProjectiles = 0
        var isInfiniteAmmo = false
        var shieldCapacityMult = 1.0f
        var shieldRechargeRateMult = 1.0f
        var ricochetCount = 0

        when (affix) {
            LegendaryRedTextAffix.INFINITY -> {
                isInfiniteAmmo = true
                magazineSize = 1
                spread = (safeBaseSpread * 0.5f).coerceIn(0f, 1000f)
            }
            LegendaryRedTextAffix.CONFERENCE_CALL -> {
                additionalProjectiles = 5
                damage = safeBaseDamage * 0.8f
            }
            LegendaryRedTextAffix.BEE -> {
                if (!state.isBroken) {
                    damage += 500f // Amp damage
                }
                shieldCapacityMult = 0.3f
                shieldRechargeRateMult = 0.2f
            }
            LegendaryRedTextAffix.SHAM -> {
                shieldCapacityMult = 0.2f // low capacity, 94% absorb (handled outside)
            }
            LegendaryRedTextAffix.THUMPER -> {
                shieldRechargeRateMult = 0.1f // huge recharge delay
            }
            LegendaryRedTextAffix.LYUDA -> {
                additionalProjectiles = 2
                fireRate = safeBaseFireRate * 1.5f
                magazineSize = (safeBaseMagazineSize * 1.25f).toInt().coerceAtLeast(1)
            }
            LegendaryRedTextAffix.SAND_HAWK -> {
                additionalProjectiles = 7
                damage = safeBaseDamage * 0.6f
            }
            LegendaryRedTextAffix.NORFLEET -> {
                additionalProjectiles = 2
                damage = safeBaseDamage * 3.0f
                magazineSize = 3
            }
            LegendaryRedTextAffix.HERALD -> {
                additionalProjectiles = 6
                damage = safeBaseDamage * 1.5f
            }
            LegendaryRedTextAffix.FIBBER -> {
                damage = safeBaseDamage * 4.0f
                ricochetCount = 1
            }
            LegendaryRedTextAffix.BITCH -> {
                spread = safeBaseSpread * 0.2f
                damage = safeBaseDamage * 1.15f // bonus crit handled outside
            }
            LegendaryRedTextAffix.BABY_MAKER -> {
                magazineSize = (safeBaseMagazineSize * 1.5f).toInt().coerceAtLeast(1)
                // reload throw handled outside
            }
            LegendaryRedTextAffix.MAGGIE -> {
                additionalProjectiles = 5
                damage = safeBaseDamage * 0.4f
            }
            LegendaryRedTextAffix.SLAGGA -> {
                additionalProjectiles = 2
                magazineSize = (safeBaseMagazineSize * 2.0f).toInt().coerceAtLeast(1)
            }
            LegendaryRedTextAffix.HELLFIRE -> {
                damage = safeBaseDamage * 1.2f
                // splash & fire dot handled outside
            }
            LegendaryRedTextAffix.VOLCANO -> {
                damage = safeBaseDamage * 0.9f
                // splash & fire dot handled outside
            }
            LegendaryRedTextAffix.PIMPERNEL -> {
                damage = safeBaseDamage * 0.8f
                // bloom proj handled outside
            }
            LegendaryRedTextAffix.FLAKKER -> {
                additionalProjectiles = 15
                damage = safeBaseDamage * 0.5f
                fireRate = safeBaseFireRate * 0.5f
            }
            LegendaryRedTextAffix.BUTCHER -> {
                fireRate = safeBaseFireRate * 2.0f
                // ammo regen handled via state
            }
            LegendaryRedTextAffix.HAWK_EYE -> {
                damage = safeBaseDamage * 0.6f // big crit
                spread = 0f
            }
        }

        outStats.actualDamage = damage.takeIf { it.isFinite() } ?: 0f
        outStats.actualFireRate = fireRate.takeIf { it.isFinite() } ?: 0.1f
        outStats.actualSpread = spread.takeIf { it.isFinite() } ?: 0f
        outStats.actualMagazineSize = magazineSize.coerceAtLeast(1)
        outStats.additionalProjectiles = additionalProjectiles
        outStats.isInfiniteAmmo = isInfiniteAmmo
        outStats.shieldCapacityMult = shieldCapacityMult.takeIf { it.isFinite() } ?: 1.0f
        outStats.shieldRechargeRateMult = shieldRechargeRateMult.takeIf { it.isFinite() } ?: 1.0f
        outStats.ricochetCount = ricochetCount
    }

    fun stepInPlace(state: GearStateSnapshot, dtSeconds: Float) {
        val dt = dtSeconds.takeIf { it.isFinite() }?.coerceIn(0.001f, 0.100f) ?: 0.016f
        var timeSinceBrokenMs = state.timeSinceBrokenMs.takeIf { it.isFinite() } ?: 0f
        if (state.isBroken) {
            timeSinceBrokenMs += dt * 1000f
        } else {
            timeSinceBrokenMs = 0f
        }

        state.timeSinceBrokenMs = timeSinceBrokenMs.takeIf { it.isFinite() } ?: 0f
    }

    fun computeChecksum(state: GearStateSnapshot, config: GearConfig, affix: LegendaryRedTextAffix): Int {
        var hash = 2166136261.toInt()
        hash = (hash * 16777619) xor state.isBroken.hashCode()
        hash = (hash * 16777619) xor state.timeSinceBrokenMs.toBits()
        hash = (hash * 16777619) xor state.shotsFiredContinuous
        hash = (hash * 16777619) xor state.bonusAccumulator.toBits()
        hash = (hash * 16777619) xor config.baseDamage.toBits()
        hash = (hash * 16777619) xor config.baseFireRate.toBits()
        hash = (hash * 16777619) xor config.baseSpread.toBits()
        hash = (hash * 16777619) xor config.baseMagazineSize
        hash = (hash * 16777619) xor affix.hashCode()
        return hash
    }
}


fun evaluateLegendaryRedTextAffixesLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "LegendaryRedTextAffixes") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
