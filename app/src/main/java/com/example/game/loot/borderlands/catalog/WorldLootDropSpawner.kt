package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import com.example.game.ArmorPiece
import com.example.game.LootDropEntity
import com.example.game.LootPayloadKind
import com.example.game.LootRarity
import com.example.game.ModularWeaponGenerator
import com.example.game.ModularWeaponInstance
import com.example.game.loot.borderlands.EquippedOnlyLoadout
import com.example.game.loot.borderlands.SwapComparisonCard
import com.example.game.loot.borderlands.SwapOnSpotLoadoutManager
import kotlin.math.hypot

/**
 * BB-T05-LOOT-020 Track 05 Borderlands Swap Loot Spawner
 */
@Keep
data class WorldLootDropConfig(
    val dropLifetimeMs: Float = 30000f,
    val interactionRadiusPx: Float = 100f,
    val maxActiveDrops: Int = 100
)

@Keep
data class WorldLootDropState(
    val entity: LootDropEntity,
    var timeAliveMs: Float = 0f,
    var isPickedUp: Boolean = false,
    var checksum: Long = 0L,
    val seed: Long = 0L
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "entityId" to entity.id,
            "x" to entity.x,
            "y" to entity.y,
            "rarity" to entity.rarity.name,
            "payloadKind" to entity.payloadKind.name,
            "timeAliveMs" to timeAliveMs,
            "isPickedUp" to isPickedUp,
            "spawnedAtWave" to entity.spawnedAtWave,
            "checksum" to checksum,
            "seed" to seed
        )

        val w = entity.weaponPayload
        if (w != null) {
            map["weapon_instanceId"] = w.instanceId
        }

        val a = entity.armorPayload
        if (a != null) {
            map["armor_id"] = a.id
        }

        return map
    }

    companion object {
        fun fromMap(map: Map<String, Any>): WorldLootDropState {
            val id = (map["entityId"] as? Number)?.toInt() ?: 0
            val x = (map["x"] as? Number)?.toFloat() ?: 0f
            val y = (map["y"] as? Number)?.toFloat() ?: 0f
            val rarityStr = map["rarity"] as? String ?: LootRarity.COMMON.name
            val kindStr = map["payloadKind"] as? String ?: LootPayloadKind.MODULAR_WEAPON.name
            val timeAliveMs = (map["timeAliveMs"] as? Number)?.toFloat() ?: 0f
            val isPickedUp = map["isPickedUp"] as? Boolean ?: false
            val spawnedAtWave = (map["spawnedAtWave"] as? Number)?.toInt() ?: 1
            val checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            val seed = (map["seed"] as? Number)?.toLong() ?: 0L

            val weaponPayload = if (kindStr == LootPayloadKind.MODULAR_WEAPON.name) {
                ModularWeaponGenerator.generateProceduralWeapon(seed, spawnedAtWave, LootRarity.valueOf(rarityStr))
            } else null

            val entity = LootDropEntity(
                id = id,
                x = x,
                y = y,
                rarity = LootRarity.valueOf(rarityStr),
                payloadKind = LootPayloadKind.valueOf(kindStr),
                weaponPayload = weaponPayload,
                armorPayload = null,
                spawnedAtWave = spawnedAtWave
            )
            return WorldLootDropState(entity, timeAliveMs, isPickedUp, checksum, seed)
        }
    }
}

object WorldLootDropSpawner {

    fun spawnWeaponDrop(
        id: Int,
        x: Float,
        y: Float,
        seed: Long,
        wave: Int,
        guaranteedMinRarity: LootRarity? = null,
        preferredBaseWeaponId: String? = null
    ): WorldLootDropState {
        require(x.isFinite() && y.isFinite()) { "Coordinates must be finite" }
        require(wave > 0) { "Wave must be positive" }

        val weapon = ModularWeaponGenerator.generateProceduralWeapon(
            seed = seed,
            wave = wave,
            guaranteedMinRarity = guaranteedMinRarity,
            preferredBaseWeaponId = preferredBaseWeaponId
        )

        val entity = LootDropEntity(
            id = id,
            x = x,
            y = y,
            rarity = weapon.rarity,
            payloadKind = LootPayloadKind.MODULAR_WEAPON,
            weaponPayload = weapon,
            armorPayload = null,
            spawnedAtWave = wave
        )

        val checksum = computeChecksum(entity, 0f, false)
        return WorldLootDropState(entity, 0f, false, checksum, seed)
    }

    fun spawnArmorDrop(
        id: Int,
        x: Float,
        y: Float,
        armorPiece: ArmorPiece,
        wave: Int
    ): WorldLootDropState {
        require(x.isFinite() && y.isFinite()) { "Coordinates must be finite" }
        require(wave > 0) { "Wave must be positive" }

        val entity = LootDropEntity(
            id = id,
            x = x,
            y = y,
            rarity = armorPiece.rarity,
            payloadKind = LootPayloadKind.RPG_ARMOR_PIECE,
            weaponPayload = null,
            armorPayload = armorPiece,
            spawnedAtWave = wave
        )

        val checksum = computeChecksum(entity, 0f, false)
        return WorldLootDropState(entity, 0f, false, checksum, 0L)
    }

    fun tickDropsInPlace(
        drops: MutableList<WorldLootDropState>,
        dtSeconds: Float,
        config: WorldLootDropConfig
    ) {
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)
        val dtMs = dt * 1000f

        var i = 0
        while (i < drops.size) {
            val drop = drops[i]
            val newTimeAliveMs = drop.timeAliveMs + dtMs

            if (drop.isPickedUp || newTimeAliveMs >= config.dropLifetimeMs) {
                val lastIdx = drops.size - 1
                drops[i] = drops[lastIdx]
                drops.removeAt(lastIdx)
                continue
            }

            drop.timeAliveMs = newTimeAliveMs
            drop.checksum = computeChecksum(drop.entity, newTimeAliveMs, drop.isPickedUp)

            i++
        }

        while (drops.size > config.maxActiveDrops) {
            val lastIdx = drops.size - 1
            drops.removeAt(lastIdx) // Removing from end is faster
        }
    }

    fun tickDrops(
        drops: List<WorldLootDropState>,
        dtSeconds: Float,
        config: WorldLootDropConfig
    ): List<WorldLootDropState> {
        val dt = dtSeconds.coerceIn(0.001f, 0.100f)
        val dtMs = dt * 1000f

        val result = ArrayList<WorldLootDropState>(drops.size)
        var count = 0

        for (drop in drops) {
            if (drop.isPickedUp) continue
            val newTimeAliveMs = drop.timeAliveMs + dtMs
            if (newTimeAliveMs >= config.dropLifetimeMs) continue
            if (count >= config.maxActiveDrops) break

            val checksum = computeChecksum(drop.entity, newTimeAliveMs, drop.isPickedUp)
            result.add(WorldLootDropState(drop.entity, newTimeAliveMs, drop.isPickedUp, checksum, drop.seed))
            count++
        }

        return result
    }

    fun getLootSwapComparisonCard(
        drop: WorldLootDropState,
        playerLoadout: EquippedOnlyLoadout,
        playerX: Float,
        playerY: Float,
        config: WorldLootDropConfig
    ): SwapComparisonCard? {
        if (drop.isPickedUp || drop.timeAliveMs >= config.dropLifetimeMs) return null

        val dist = hypot(drop.entity.x - playerX, drop.entity.y - playerY)
        if (dist > config.interactionRadiusPx) return null

        val weaponPayload = drop.entity.weaponPayload ?: return null
        return SwapOnSpotLoadoutManager.buildWeaponComparisonCard(playerLoadout.activeWeapon, weaponPayload)
    }

    fun processLootSwapInteraction(
        drop: WorldLootDropState,
        playerLoadout: EquippedOnlyLoadout,
        playerX: Float,
        playerY: Float,
        config: WorldLootDropConfig
    ): Pair<WorldLootDropState, Pair<EquippedOnlyLoadout, WorldLootDropState?>> {
        if (drop.isPickedUp || drop.timeAliveMs >= config.dropLifetimeMs) {
            return drop to (playerLoadout to null)
        }

        val dist = hypot(drop.entity.x - playerX, drop.entity.y - playerY)
        if (dist > config.interactionRadiusPx) {
            return drop to (playerLoadout to null)
        }

        return when (drop.entity.payloadKind) {
            LootPayloadKind.MODULAR_WEAPON -> {
                val weapon = drop.entity.weaponPayload
                if (weapon != null) {
                    val (newLoadout, displacedWeapon) = SwapOnSpotLoadoutManager.swapWeaponOnSpot(playerLoadout, weapon)

                    drop.isPickedUp = true
                    drop.checksum = computeChecksum(drop.entity, drop.timeAliveMs, true)

                    val displacedDrop = if (displacedWeapon != null) {
                        val displacedEntity = LootDropEntity(
                            id = drop.entity.id + 10000,
                            x = playerX,
                            y = playerY,
                            rarity = displacedWeapon.rarity,
                            payloadKind = LootPayloadKind.MODULAR_WEAPON,
                            weaponPayload = displacedWeapon,
                            armorPayload = null,
                            spawnedAtWave = drop.entity.spawnedAtWave
                        )
                        WorldLootDropState(displacedEntity, 0f, false, computeChecksum(displacedEntity, 0f, false), drop.seed)
                    } else null

                    drop to (newLoadout to displacedDrop)
                } else {
                    drop to (playerLoadout to null)
                }
            }
            LootPayloadKind.RPG_ARMOR_PIECE -> {
                val armor = drop.entity.armorPayload
                if (armor != null) {
                    val (newLoadout, displacedArmor) = SwapOnSpotLoadoutManager.swapShieldArmorOnSpot(playerLoadout, armor)

                    drop.isPickedUp = true
                    drop.checksum = computeChecksum(drop.entity, drop.timeAliveMs, true)

                    val displacedDrop = if (displacedArmor != null) {
                        val displacedEntity = LootDropEntity(
                            id = drop.entity.id + 10000,
                            x = playerX,
                            y = playerY,
                            rarity = displacedArmor.rarity,
                            payloadKind = LootPayloadKind.RPG_ARMOR_PIECE,
                            weaponPayload = null,
                            armorPayload = displacedArmor,
                            spawnedAtWave = drop.entity.spawnedAtWave
                        )
                        WorldLootDropState(displacedEntity, 0f, false, computeChecksum(displacedEntity, 0f, false), drop.seed)
                    } else null

                    drop to (newLoadout to displacedDrop)
                } else {
                    drop to (playerLoadout to null)
                }
            }
        }
    }

    private fun computeChecksum(entity: LootDropEntity, timeAliveMs: Float, isPickedUp: Boolean): Long {
        var hash = -2128831035L
        val prime = 16777619L

        fun update(value: Int) {
            hash = hash xor (value.toLong() and 0xFFFFFFFFL)
            hash *= prime
        }

        update(entity.id)
        update(entity.x.toBits())
        update(entity.y.toBits())
        update(entity.rarity.ordinal)
        update(entity.payloadKind.ordinal)
        update(timeAliveMs.toBits())
        update(if (isPickedUp) 1 else 0)
        update(entity.spawnedAtWave)

        return hash
    }
}
