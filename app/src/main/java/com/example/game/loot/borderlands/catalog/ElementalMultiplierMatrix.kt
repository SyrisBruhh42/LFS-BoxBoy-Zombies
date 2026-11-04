package com.example.game.loot.borderlands.catalog

import androidx.annotation.Keep
import com.example.game.ElementalAffinity
import kotlin.math.max

@Keep
enum class TargetDefenseType(val id: Int, val displayName: String, val colorHex: Long) {
    FLESH(0, "Flesh", 0xFFFF5252),
    ARMOR(1, "Armor", 0xFFFFD700),
    SHIELD(2, "Shield", 0xFF448AFF);

    companion object {
        fun fromId(id: Int): TargetDefenseType {
            return entries.firstOrNull { it.id == id } ?: FLESH
        }
    }
}

@Keep
data class ElementalMatrixConfig(
    val version: Int = 1,
    val isEnabled: Boolean = true
) {
    fun toMap(): Map<String, Any> = mapOf(
        "version" to version,
        "isEnabled" to isEnabled
    )

    companion object {
        fun fromMap(map: Map<String, Any>): ElementalMatrixConfig {
            return ElementalMatrixConfig(
                version = (map["version"] as? Number)?.toInt() ?: 1,
                isEnabled = map["isEnabled"] as? Boolean ?: true
            )
        }
    }
}

@Keep
data class ElementalMatrixSnapshot(
    val lastEvaluatedElementId: Int,
    val lastEvaluatedDefenseId: Int,
    val calculatedMultiplier: Float,
    val checksum: Long
) {
    fun toMap(): Map<String, Any> = mapOf(
        "lastEvaluatedElementId" to lastEvaluatedElementId,
        "lastEvaluatedDefenseId" to lastEvaluatedDefenseId,
        "calculatedMultiplier" to calculatedMultiplier,
        "checksum" to checksum
    )

    companion object {
        fun fromMap(map: Map<String, Any>): ElementalMatrixSnapshot {
            return ElementalMatrixSnapshot(
                lastEvaluatedElementId = (map["lastEvaluatedElementId"] as? Number)?.toInt() ?: 0,
                lastEvaluatedDefenseId = (map["lastEvaluatedDefenseId"] as? Number)?.toInt() ?: 0,
                calculatedMultiplier = (map["calculatedMultiplier"] as? Number)?.toFloat() ?: 1.0f,
                checksum = (map["checksum"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

object ElementalMultiplierMatrix {
    fun getMultiplier(
        element: ElementalAffinity,
        defense: TargetDefenseType,
        config: ElementalMatrixConfig = ElementalMatrixConfig()
    ): Float {
        if (!config.isEnabled) return 1.0f

        val rawMultiplier = when (element) {
            ElementalAffinity.KINETIC -> when (defense) {
                TargetDefenseType.FLESH -> 1.0f
                TargetDefenseType.ARMOR -> 0.8f
                TargetDefenseType.SHIELD -> 0.8f
            }
            ElementalAffinity.INCENDIARY -> when (defense) {
                TargetDefenseType.FLESH -> 1.75f
                TargetDefenseType.ARMOR -> 0.5f
                TargetDefenseType.SHIELD -> 0.4f
            }
            ElementalAffinity.CRYO -> when (defense) {
                TargetDefenseType.FLESH -> 1.0f
                TargetDefenseType.ARMOR -> 1.2f
                TargetDefenseType.SHIELD -> 1.0f
            }
            ElementalAffinity.CORROSIVE -> when (defense) {
                TargetDefenseType.FLESH -> 0.75f
                TargetDefenseType.ARMOR -> 1.75f
                TargetDefenseType.SHIELD -> 0.4f
            }
            ElementalAffinity.VOLTAIC -> when (defense) {
                TargetDefenseType.FLESH -> 0.75f
                TargetDefenseType.ARMOR -> 1.0f
                TargetDefenseType.SHIELD -> 2.5f
            }
        }

        return if (rawMultiplier.isFinite() && rawMultiplier >= 0f) rawMultiplier else 1.0f
    }

    fun computeStateChecksum(
        elementId: Int,
        defenseId: Int,
        multiplier: Float
    ): Long {
        var hash = -2128831035146728271L // FNV-1a 64-bit offset basis
        hash = (hash xor elementId.toLong()) * 1099511628211L
        hash = (hash xor defenseId.toLong()) * 1099511628211L
        hash = (hash xor multiplier.toRawBits().toLong()) * 1099511628211L
        return hash
    }

    fun evaluate(element: ElementalAffinity, defense: TargetDefenseType, config: ElementalMatrixConfig): ElementalMatrixSnapshot {
        val multiplier = getMultiplier(element, defense, config)
        val elementId = element.ordinal
        val defenseId = defense.id
        val checksum = computeStateChecksum(elementId, defenseId, multiplier)

        return ElementalMatrixSnapshot(
            lastEvaluatedElementId = elementId,
            lastEvaluatedDefenseId = defenseId,
            calculatedMultiplier = multiplier,
            checksum = checksum
        )
    }
}
