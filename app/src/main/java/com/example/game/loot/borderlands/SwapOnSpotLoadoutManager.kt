package com.example.game.loot.borderlands

import com.example.game.ArmorPiece
import com.example.game.ElementalAffinity
import com.example.game.LootRarity
import com.example.game.ModularWeaponInstance
import kotlin.math.roundToInt

/**
 * Track 5: Simple Borderlands 2 Equipped-Only Inventory & Swap-on-the-Spot Loadout Manager.
 * - Zero backpack / no hauling junk loot around.
 * - Players hold only their active equipped slots (`primaryWeapon`, `secondaryWeapon`, `equippedShieldArmor`).
 * - Approaching any world drop or Vending Machine generates a Borderlands 2 floating
 *   `SwapComparisonCard` with green/red stat deltas for instant 1-tap swap-equipping.
 */
data class StatDeltaLine(
    val label: String,
    val equippedFormatted: String,
    val candidateFormatted: String,
    val numericDelta: Float,
    val higherIsBetter: Boolean = true
) {
    val isUpgrade: Boolean
        get() = if (higherIsBetter) numericDelta > 0.01f else numericDelta < -0.01f

    val isDowngrade: Boolean
        get() = if (higherIsBetter) numericDelta < -0.01f else numericDelta > 0.01f

    val arrowBadge: String
        get() = when {
            isUpgrade -> "▲"
            isDowngrade -> "▼"
            else -> "═"
        }
}

data class SwapComparisonCard(
    val candidateId: String,
    val candidateTitle: String,
    val candidateRarity: LootRarity,
    val manufacturerName: String,
    val elementalAffinity: ElementalAffinity,
    val statDeltas: List<StatDeltaLine>,
    val traitSummary: String
)

data class EquippedOnlyLoadout(
    val primaryWeapon: ModularWeaponInstance,
    val secondaryWeapon: ModularWeaponInstance? = null,
    val activeSlotIndex: Int = 0, // 0 = primary, 1 = secondary
    val equippedShieldArmor: ArmorPiece? = null
) {
    val activeWeapon: ModularWeaponInstance
        get() = if (activeSlotIndex == 1 && secondaryWeapon != null) secondaryWeapon else primaryWeapon
}

object SwapOnSpotLoadoutManager {
    const val HAS_BACKPACK_STORAGE: Boolean = false

    fun buildWeaponComparisonCard(
        equipped: ModularWeaponInstance,
        candidate: ModularWeaponInstance
    ): SwapComparisonCard {
        val dmgDelta = (candidate.effectiveDamage - equipped.effectiveDamage).toFloat()
        val rofDelta = candidate.shotsPerSecond - equipped.shotsPerSecond
        val magDelta = (candidate.effectiveMagSize - equipped.effectiveMagSize).toFloat()
        val critDelta = (candidate.optic.lightLockCritMultiplier - equipped.optic.lightLockCritMultiplier) * 100f

        return SwapComparisonCard(
            candidateId = candidate.instanceId,
            candidateTitle = candidate.customTitle,
            candidateRarity = candidate.rarity,
            manufacturerName = candidate.receiver.manufacturer.brandName,
            elementalAffinity = candidate.magazine.elementalAffinity,
            statDeltas = listOf(
                StatDeltaLine(
                    label = "DAMAGE",
                    equippedFormatted = "${equipped.effectiveDamage}",
                    candidateFormatted = "${candidate.effectiveDamage}",
                    numericDelta = dmgDelta
                ),
                StatDeltaLine(
                    label = "FIRE RATE",
                    equippedFormatted = "${(equipped.shotsPerSecond * 10f).roundToInt() / 10f}/s",
                    candidateFormatted = "${(candidate.shotsPerSecond * 10f).roundToInt() / 10f}/s",
                    numericDelta = rofDelta
                ),
                StatDeltaLine(
                    label = "MAG SIZE",
                    equippedFormatted = "${equipped.effectiveMagSize}",
                    candidateFormatted = "${candidate.effectiveMagSize}",
                    numericDelta = magDelta
                ),
                StatDeltaLine(
                    label = "CRIT MULT",
                    equippedFormatted = "${(equipped.optic.lightLockCritMultiplier * 100f).roundToInt()}%",
                    candidateFormatted = "${(candidate.optic.lightLockCritMultiplier * 100f).roundToInt()}%",
                    numericDelta = critDelta
                )
            ),
            traitSummary = "${candidate.receiver.manufacturer.traitSummary} • ${candidate.magazine.elementalAffinity.displayName}"
        )
    }

    /**
     * Swaps the candidate weapon directly into the active equipped slot and returns the displaced
     * weapon (which drops onto the ground at the player's feet rather than filling a backpack).
     */
    fun swapWeaponOnSpot(
        currentLoadout: EquippedOnlyLoadout,
        newWeapon: ModularWeaponInstance
    ): Pair<EquippedOnlyLoadout, ModularWeaponInstance?> {
        return if (currentLoadout.secondaryWeapon == null) {
            currentLoadout.copy(
                secondaryWeapon = newWeapon,
                activeSlotIndex = 1
            ) to null
        } else if (currentLoadout.activeSlotIndex == 1) {
            val displaced = currentLoadout.secondaryWeapon
            currentLoadout.copy(secondaryWeapon = newWeapon) to displaced
        } else {
            val displaced = currentLoadout.primaryWeapon
            currentLoadout.copy(primaryWeapon = newWeapon) to displaced
        }
    }

    fun swapShieldArmorOnSpot(
        currentLoadout: EquippedOnlyLoadout,
        newShieldArmor: ArmorPiece
    ): Pair<EquippedOnlyLoadout, ArmorPiece?> {
        val displaced = currentLoadout.equippedShieldArmor
        return currentLoadout.copy(equippedShieldArmor = newShieldArmor) to displaced
    }
}
