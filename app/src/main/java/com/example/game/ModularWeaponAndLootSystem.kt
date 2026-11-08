package com.example.game

import com.example.data.DEFAULT_WEAPONS
import com.example.data.WeaponSpec
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * PHASE 4: Borderlands-Style 4-Part Modular Weapons, Light-Lock Auto-Aim & Time-Freeze Loot Inspection
 */
enum class LootRarity(
    val displayName: String,
    val shortBadge: String,
    val statMultiplier: Float,
    val colorHex: Long,
    val salvageScrapValue: Int
) {
    COMMON("Standard Issue", "CMN", 1.0f, 0xFFB0BEC5, 15),
    UNCOMMON("Tactical Forged", "UNC", 1.22f, 0xFF00FF66, 35),
    RARE("Cryo-Tempered", "RARE", 1.48f, 0xFF00E5FF, 70),
    EPIC("Abyssal Relic", "EPIC", 1.82f, 0xFFB388FF, 140),
    ABYSSAL_LEGENDARY("Bio-Noir Ascendant", "LGND", 2.35f, 0xFFFFD166, 300)
}

enum class ElementalAffinity(
    val displayName: String,
    val shortTag: String,
    val bonusDamageMultiplier: Float,
    val colorHex: Long
) {
    KINETIC("High-Impact Kinetic", "KIN", 1.0f, 0xFFFFD166),
    INCENDIARY("Thermite Incendiary", "FIRE", 1.18f, 0xFFFF5722),
    CRYO("Cryo-Stasis Frost", "CRYO", 1.12f, 0xFF00E5FF),
    CORROSIVE("Bio-Acid Corrosive", "ACID", 1.20f, 0xFF00FF66),
    VOLTAIC("Arc-Voltaic Shock", "VOLT", 1.22f, 0xFFB388FF)
}

enum class WeaponManufacturer(
    val brandName: String,
    val traitSummary: String,
    val primaryColorHex: Long
) {
    KORSAKOV_ARMORIES("Korsakov Armories", "+25% Base Damage & Heavy Knockback", 0xFFFF2A54),
    CRYO_VEX("Cryo-Vex Labs", "+30% Light-Lock Crit & Elemental Status", 0xFF00E5FF),
    HYPERION_VOLT("Hyperion-Volt", "Stabilized Recoil & +25% Fire Rate", 0xFFFFD166),
    BIO_HAZE("Bio-Haze Syndicate", "+1 Pierce & Corrosive/Bio Rounds", 0xFF00FF66),
    JAKOBS_ABYSS("Jakobs-Abyss", "+45% Illuminated Lantern Crit Damage", 0xFFB388FF)
}

data class WeaponReceiverPart(
    val name: String,
    val manufacturer: WeaponManufacturer,
    val baseDamageBonus: Float,
    val fireRateBonus: Float
)

data class WeaponBarrelPart(
    val name: String,
    val spreadModifier: Float,
    val bonusPierce: Int,
    val bonusPellets: Int,
    val rangeBonusPx: Float
) {
    fun toCatalogBarrelConfig(seed: Int = name.hashCode()): com.example.game.loot.borderlands.catalog.WeaponBarrelConfig {
        val barrelType = when {
            bonusPellets > 0 -> com.example.game.loot.borderlands.catalog.BarrelPartType.TWIN_BARREL
            bonusPierce > 0 -> com.example.game.loot.borderlands.catalog.BarrelPartType.FLUTED
            spreadModifier > 1.05f -> com.example.game.loot.borderlands.catalog.BarrelPartType.SAWED_OFF
            rangeBonusPx >= 40f -> com.example.game.loot.borderlands.catalog.BarrelPartType.LONG_BARREL
            else -> com.example.game.loot.borderlands.catalog.BarrelPartType.COMPENSATOR
        }
        return com.example.game.loot.borderlands.catalog.WeaponBarrelPartCatalog.generateProceduralBarrel(barrelType, seed)
    }
}

data class WeaponMagazinePart(
    val name: String,
    val magSizeBonus: Int,
    val reloadSpeedMultiplier: Float,
    val elementalAffinity: ElementalAffinity
) {
    fun toCatalogMagazinePart(partId: Int = 1): com.example.game.loot.borderlands.catalog.WeaponMagazinePart {
        val catAffinity = when (elementalAffinity) {
            ElementalAffinity.KINETIC -> com.example.game.loot.borderlands.catalog.MagazineElementalAffinity.NONE
            ElementalAffinity.INCENDIARY -> com.example.game.loot.borderlands.catalog.MagazineElementalAffinity.INCENDIARY
            ElementalAffinity.VOLTAIC -> com.example.game.loot.borderlands.catalog.MagazineElementalAffinity.VOLTAIC
            ElementalAffinity.CORROSIVE -> com.example.game.loot.borderlands.catalog.MagazineElementalAffinity.CORROSIVE
            ElementalAffinity.CRYO -> com.example.game.loot.borderlands.catalog.MagazineElementalAffinity.CRYO
        }
        return com.example.game.loot.borderlands.catalog.WeaponMagazinePart(
            partId = partId,
            name = name,
            capacityMultiplier = (1.0f + magSizeBonus * 0.05f).coerceIn(0.5f, 2.5f),
            reloadSpeedMultiplier = reloadSpeedMultiplier.coerceIn(0.4f, 2.5f),
            elementalAffinity = catAffinity,
            elementalChance = if (elementalAffinity == ElementalAffinity.KINETIC) 0.0f else 0.18f
        )
    }
}

data class WeaponOpticPart(
    val name: String,
    val lightLockCritMultiplier: Float,
    val lanternRangeBonusPx: Float,
    val lightLockConeBonusRad: Float
) {
    fun toCatalogOpticPart(id: String = "OPTIC_BRIDGE"): com.example.game.loot.borderlands.catalog.WeaponOpticPart {
        val deg = Math.toDegrees(lightLockConeBonusRad.toDouble()).toFloat().coerceIn(5f, 180f)
        return com.example.game.loot.borderlands.catalog.WeaponOpticPart(
            id = id,
            displayName = name,
            lanternRangePx = (240f + lanternRangeBonusPx).coerceIn(50f, 1500f),
            lightLockConeDegrees = deg,
            critMultiplier = lightLockCritMultiplier.coerceIn(1.0f, 8.0f)
        )
    }
}

fun com.example.game.loot.borderlands.catalog.WeaponMagazinePart.toGameMagazinePart(): WeaponMagazinePart {
    val affinity = when (elementalAffinity) {
        com.example.game.loot.borderlands.catalog.MagazineElementalAffinity.NONE,
        com.example.game.loot.borderlands.catalog.MagazineElementalAffinity.EXPLOSIVE -> ElementalAffinity.KINETIC
        com.example.game.loot.borderlands.catalog.MagazineElementalAffinity.INCENDIARY -> ElementalAffinity.INCENDIARY
        com.example.game.loot.borderlands.catalog.MagazineElementalAffinity.VOLTAIC -> ElementalAffinity.VOLTAIC
        com.example.game.loot.borderlands.catalog.MagazineElementalAffinity.CORROSIVE -> ElementalAffinity.CORROSIVE
        com.example.game.loot.borderlands.catalog.MagazineElementalAffinity.CRYO -> ElementalAffinity.CRYO
    }
    return WeaponMagazinePart(
        name = name,
        magSizeBonus = ((capacityMultiplier - 1.0f) * 12f).roundToInt(),
        reloadSpeedMultiplier = reloadSpeedMultiplier,
        elementalAffinity = affinity
    )
}

fun com.example.game.loot.borderlands.catalog.WeaponOpticPart.toGameOpticPart(): WeaponOpticPart {
    return WeaponOpticPart(
        name = displayName,
        lightLockCritMultiplier = critMultiplier,
        lanternRangeBonusPx = (lanternRangePx - 220f).coerceAtLeast(10f),
        lightLockConeBonusRad = Math.toRadians(lightLockConeDegrees.toDouble()).toFloat().coerceIn(0.03f, 0.45f)
    )
}

/**
 * Complete 4-part procedural weapon instance that wraps/extends a base WeaponSpec
 * without breaking existing weapon switching or Pack-a-Punch progression.
 */
data class ModularWeaponInstance(
    val instanceId: String,
    val baseWeaponId: String,
    val customTitle: String,
    val rarity: LootRarity,
    val itemLevel: Int,
    val anvilUpgradeTier: Int = 0,
    val receiver: WeaponReceiverPart,
    val barrel: WeaponBarrelPart,
    val magazine: WeaponMagazinePart,
    val optic: WeaponOpticPart
) {
    val baseSpec: WeaponSpec
        get() = DEFAULT_WEAPONS.find { it.id == baseWeaponId } ?: DEFAULT_WEAPONS.first()

    val effectiveDamage: Int
        get() {
            val multiplier = max(0f, 1f + (itemLevel - 1) * 0.14f + anvilUpgradeTier * 0.18f)
            val raw = baseSpec.baseDamage *
                rarity.statMultiplier *
                max(0f, receiver.baseDamageBonus) *
                magazine.elementalAffinity.bonusDamageMultiplier *
                multiplier
            return raw.roundToInt().coerceAtLeast(10)
        }

    val effectiveFireCooldownMs: Long
        get() {
            val denominator = max(0.01f, receiver.fireRateBonus * (1f + anvilUpgradeTier * 0.05f))
            val raw = baseSpec.fireCooldownMs / denominator
            return raw.toLong().coerceAtLeast(40L)
        }

    val shotsPerSecond: Float
        get() = (1000f / effectiveFireCooldownMs.toFloat())

    val effectiveMagSize: Int
        get() = (baseSpec.magazineSize + magazine.magSizeBonus + anvilUpgradeTier * 3).coerceAtLeast(4)

    val effectiveReloadMs: Long
        get() = (baseSpec.reloadMs / magazine.reloadSpeedMultiplier.coerceAtLeast(0.5f)).toLong().coerceAtLeast(220L)

    val effectivePelletCount: Int
        get() = (baseSpec.pelletCount + barrel.bonusPellets).coerceAtLeast(1)

    val effectivePierceCount: Int
        get() = (baseSpec.pierceCount + barrel.bonusPierce).coerceAtLeast(1)

    val effectiveSpreadDegrees: Float
        get() = (baseSpec.spreadDegrees * barrel.spreadModifier).coerceIn(0.5f, 32f)

    val illuminatedCritMultiplier: Float
        get() = optic.lightLockCritMultiplier + (if (receiver.manufacturer == WeaponManufacturer.JAKOBS_ABYSS) 0.25f else 0f)
}

enum class LootPayloadKind {
    MODULAR_WEAPON,
    RPG_ARMOR_PIECE
}

data class LootDropEntity(
    val id: Int,
    val x: Float,
    val y: Float,
    val rarity: LootRarity,
    val payloadKind: LootPayloadKind,
    val weaponPayload: ModularWeaponInstance? = null,
    val armorPayload: ArmorPiece? = null,
    val spawnedAtWave: Int = 1
) {
    val displayTitle: String
        get() = weaponPayload?.customTitle ?: armorPayload?.displayName ?: "Abyssal Cache"
}

data class WeaponStatComparison(
    val candidate: ModularWeaponInstance,
    val equipped: ModularWeaponInstance,
    val damageDelta: Int = candidate.effectiveDamage - equipped.effectiveDamage,
    val rofDelta: Float = candidate.shotsPerSecond - equipped.shotsPerSecond,
    val magDelta: Int = candidate.effectiveMagSize - equipped.effectiveMagSize,
    val reloadMsDelta: Long = candidate.effectiveReloadMs - equipped.effectiveReloadMs,
    val critPercentDelta: Int = ((candidate.illuminatedCritMultiplier - equipped.illuminatedCritMultiplier) * 100f).roundToInt()
)

object ModularWeaponGenerator {

    private val receiverPrefixes = listOf(
        "Abyssal", "Vortex", "Chiaroscuro", "Ferro", "Cryo-Locked", "Bio-Forged", "Null-Sec", "Overclocked"
    )

    private val barrelNames = listOf(
        "Rifled Void Longbarrel",
        "Sawed-Off Choke Shroud",
        "Twin-Bore Splitter Barrel",
        "Tungsten Rail Fluted Barrel",
        "Suppressor Vent Assembly"
    )

    private val magazineNames = listOf(
        "Helical Drum Cell",
        "Quick-Eject Magwell",
        "Cryo-Core Capacitor",
        "Bio-Acid Pressurized Clip",
        "Voltaic Overcharge Mag"
    )

    private val opticNames = listOf(
        "Chiaroscuro Light-Lock Reflex",
        "Abyssal Thermal Scope 2.4x",
        "Phosphor Laser Aperture",
        "Holosight Beacon Tracker",
        "Pinpoint Lantern Collimator"
    )

    fun createStarterModularWeapon(baseWeaponId: String = "pistol"): ModularWeaponInstance {
        val spec = DEFAULT_WEAPONS.find { it.id == baseWeaponId } ?: DEFAULT_WEAPONS.first()
        return ModularWeaponInstance(
            instanceId = "starter_${spec.id}",
            baseWeaponId = spec.id,
            customTitle = "Issued ${spec.name}",
            rarity = LootRarity.COMMON,
            itemLevel = 1,
            receiver = WeaponReceiverPart(
                name = "Standard Korsakov Receiver",
                manufacturer = WeaponManufacturer.KORSAKOV_ARMORIES,
                baseDamageBonus = 1.0f,
                fireRateBonus = 1.0f
            ),
            barrel = WeaponBarrelPart(
                name = "Factory Tactical Barrel",
                spreadModifier = 1.0f,
                bonusPierce = 0,
                bonusPellets = 0,
                rangeBonusPx = 0f
            ),
            magazine = WeaponMagazinePart(
                name = "Standard Issue Magazine",
                magSizeBonus = 0,
                reloadSpeedMultiplier = 1.0f,
                elementalAffinity = ElementalAffinity.KINETIC
            ),
            optic = WeaponOpticPart(
                name = "Iron Lantern Sight",
                lightLockCritMultiplier = 1.25f,
                lanternRangeBonusPx = 20f,
                lightLockConeBonusRad = 0.05f
            )
        )
    }

    fun generateProceduralWeapon(
        seed: Long,
        wave: Int,
        guaranteedMinRarity: LootRarity? = null,
        preferredBaseWeaponId: String? = null
    ): ModularWeaponInstance {
        val rng = Random(seed)
        val baseSpec = if (preferredBaseWeaponId != null) {
            DEFAULT_WEAPONS.find { it.id == preferredBaseWeaponId } ?: DEFAULT_WEAPONS.random(rng)
        } else {
            DEFAULT_WEAPONS.random(rng)
        }

        val rolledRarity = rollRarity(rng, wave, guaranteedMinRarity)
        val manufacturer = WeaponManufacturer.entries.random(rng)
        val prefix = receiverPrefixes.random(rng)

        val receiver = WeaponReceiverPart(
            name = "${manufacturer.brandName} Mk-${(wave % 5) + 1} Receiver",
            manufacturer = manufacturer,
            baseDamageBonus = when (manufacturer) {
                WeaponManufacturer.KORSAKOV_ARMORIES -> 1.25f
                WeaponManufacturer.JAKOBS_ABYSS -> 1.18f
                else -> 1.08f
            },
            fireRateBonus = when (manufacturer) {
                WeaponManufacturer.HYPERION_VOLT -> 1.26f
                WeaponManufacturer.CRYO_VEX -> 1.12f
                else -> 1.04f
            }
        )

        val barrelIdx = rng.nextInt(barrelNames.size)
        val barrel = WeaponBarrelPart(
            name = barrelNames[barrelIdx],
            spreadModifier = if (barrelIdx == 2) 1.25f else 0.82f,
            bonusPierce = if (manufacturer == WeaponManufacturer.BIO_HAZE || barrelIdx == 3) 1 else 0,
            bonusPellets = if (barrelIdx == 2) 1 else 0,
            rangeBonusPx = (barrelIdx * 25f)
        )

        val element = if (rolledRarity == LootRarity.COMMON && rng.nextFloat() < 0.65f) {
            ElementalAffinity.KINETIC
        } else {
            ElementalAffinity.entries.random(rng)
        }
        val magIdx = rng.nextInt(magazineNames.size)
        val magazine = WeaponMagazinePart(
            name = magazineNames[magIdx],
            magSizeBonus = 2 + rng.nextInt(0, 8) + rolledRarity.ordinal * 2,
            reloadSpeedMultiplier = 1.05f + rolledRarity.ordinal * 0.08f,
            elementalAffinity = element
        )

        val opticIdx = rng.nextInt(opticNames.size)
        val optic = WeaponOpticPart(
            name = opticNames[opticIdx],
            lightLockCritMultiplier = 1.28f + rolledRarity.ordinal * 0.12f,
            lanternRangeBonusPx = 25f + rolledRarity.ordinal * 20f,
            lightLockConeBonusRad = 0.06f + rolledRarity.ordinal * 0.03f
        )

        val elemPrefix = if (element != ElementalAffinity.KINETIC) "${element.shortTag}-" else ""
        val customTitle = "$prefix $elemPrefix${baseSpec.shortCode} [${manufacturer.brandName.substringBefore(" ")}]"

        return ModularWeaponInstance(
            instanceId = "wpn_${seed}_${rng.nextInt(1000, 9999)}",
            baseWeaponId = baseSpec.id,
            customTitle = customTitle,
            rarity = rolledRarity,
            itemLevel = wave.coerceAtLeast(1),
            receiver = receiver,
            barrel = barrel,
            magazine = magazine,
            optic = optic
        )
    }

    fun rollRarity(rng: Random, wave: Int, minRarity: LootRarity? = null): LootRarity {
        val roll = rng.nextFloat() + (wave * 0.025f).coerceAtMost(0.35f)
        val raw = when {
            roll >= 0.96f -> LootRarity.ABYSSAL_LEGENDARY
            roll >= 0.82f -> LootRarity.EPIC
            roll >= 0.58f -> LootRarity.RARE
            roll >= 0.30f -> LootRarity.UNCOMMON
            else -> LootRarity.COMMON
        }
        return if (minRarity != null && raw.ordinal < minRarity.ordinal) minRarity else raw
    }

    /**
     * Evaluates Light-Lock Auto-Aim: prioritizes enemies inside the player's directional
     * lantern cone first, then falls back to closest proximity enemy.
     */
    fun findLightLockTarget(
        zombies: List<ZombieEntity>,
        playerX: Float,
        playerY: Float,
        currentAimRad: Float,
        lanternRange: Float,
        coneHalfSpreadRad: Float
    ): Pair<ZombieEntity?, Boolean> {
        if (zombies.isEmpty()) return Pair(null, false)

        // First search for enemies illuminated inside the player's lantern cone
        val illuminatedTarget = zombies
            .filter { z ->
                AbyssalChiaroscuroLightingEngine.isPointIlluminatedByPlayer(
                    targetX = z.x,
                    targetY = z.y,
                    playerX = playerX,
                    playerY = playerY,
                    aimAngleRad = currentAimRad,
                    lanternRange = lanternRange,
                    coneHalfSpreadRad = coneHalfSpreadRad
                )
            }
            .minByOrNull { hypot(it.x - playerX, it.y - playerY) }

        if (illuminatedTarget != null) {
            return Pair(illuminatedTarget, true)
        }

        val fallback = zombies
            .filter { hypot(it.x - playerX, it.y - playerY) <= 420f }
            .minByOrNull { hypot(it.x - playerX, it.y - playerY) }

        return Pair(fallback, false)
    }
}
