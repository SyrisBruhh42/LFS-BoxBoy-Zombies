package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.game.ElementalAffinity
import com.example.game.GameSessionState
import com.example.game.WeaponUpgradeProgression
import com.example.game.economy.SingleCurrencyVendingEngine
import kotlin.random.Random

@Keep
sealed class PackAPunchPurchaseResult {
    @Keep
    data class Success(
        val newState: GameSessionState,
        val message: String,
        val cost: Int
    ) : PackAPunchPurchaseResult()

    @Keep
    data class Error(val reason: String) : PackAPunchPurchaseResult()
}

object PackAPunchTier1To5Forge {

    fun processUpgradePurchase(state: GameSessionState, targetWeaponId: String): PackAPunchPurchaseResult {
        val weapon = state.modularWeaponInventory[targetWeaponId]
            ?: return PackAPunchPurchaseResult.Error("Weapon not found in inventory")

        val currentTier = weapon.anvilUpgradeTier
        if (currentTier >= WeaponUpgradeProgression.MAX_UPGRADE_LEVEL) {
            return PackAPunchPurchaseResult.Error("Weapon is already at maximum Pack-a-Punch tier")
        }

        val nextTier = currentTier + 1
        val cost = SingleCurrencyVendingEngine.computePackAPunchCost(nextTier)

        if (state.points < cost) {
            return PackAPunchPurchaseResult.Error("Insufficient Currency Points")
        }

        // Base upgrade
        var upgradedWeapon = weapon.copy(anvilUpgradeTier = nextTier)

        // Tier 4: Elemental Overcharge deterministic elemental assignment if Kinetic
        if (nextTier == 4 && upgradedWeapon.magazine.elementalAffinity == ElementalAffinity.KINETIC) {
            val seed = state.mapSeed + targetWeaponId.hashCode() + nextTier
            val rnd = Random(seed)
            val elementalOptions = listOf(
                ElementalAffinity.INCENDIARY,
                ElementalAffinity.CRYO,
                ElementalAffinity.CORROSIVE,
                ElementalAffinity.VOLTAIC
            )
            val selectedElement = elementalOptions.random(rnd)
            val newMag = upgradedWeapon.magazine.copy(elementalAffinity = selectedElement)
            upgradedWeapon = upgradedWeapon.copy(magazine = newMag)
        }

        // Apply state updates
        val newPoints = state.points - cost
        
        val newModularWeaponInventory = state.modularWeaponInventory.toMutableMap()
        newModularWeaponInventory[targetWeaponId] = upgradedWeapon

        val newWeaponLevels = state.weaponLevels.toMutableMap()
        newWeaponLevels[targetWeaponId] = nextTier

        val newCurrentMagAmmo = state.currentMagAmmo.toMutableMap()
        newCurrentMagAmmo[targetWeaponId] = upgradedWeapon.effectiveMagSize

        val newEquippedWeapon = if (state.equippedModularWeapon.baseWeaponId == targetWeaponId) {
            upgradedWeapon
        } else {
            state.equippedModularWeapon
        }

        val tierStats = WeaponUpgradeProgression.getTierStats(nextTier)
        val message = "Upgraded to ${tierStats.tierTitle}: ${tierStats.milestonePerk}"

        val newState = state.copy(
            points = newPoints,
            modularWeaponInventory = newModularWeaponInventory,
            weaponLevels = newWeaponLevels,
            currentMagAmmo = newCurrentMagAmmo,
            equippedModularWeapon = newEquippedWeapon
        )

        return PackAPunchPurchaseResult.Success(newState, message, cost)
    }
}


fun evaluatePackAPunchTier1To5ForgeLiveMetric(wave: Int, tick: Long, seed: Long = 42L): Double {
    val safeWave = wave.coerceAtLeast(1)
    val safeTick = tick.coerceAtLeast(0L)
    var h = -3750763034362895579L xor seed
    for (ch in "PackAPunchTier1To5Forge") {
        h = (h xor ch.code.toLong()) * 1099511628211L
    }
    h = (h xor safeWave.toLong()) * 1099511628211L
    h = (h xor safeTick) * 1099511628211L
    val normalized = ((h ushr 1) % 10000L).toDouble() / 10000.0
    return (1.0 + safeWave * 0.05) + normalized * 0.25
}
