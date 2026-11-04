package com.example.game.economy.vendors

import androidx.annotation.Keep
import com.example.data.PerkType
import com.example.game.GameSessionState
import com.example.network.CoopPeerState
import kotlin.math.max
import kotlin.math.min

@Keep
data class QuickReviveState(
    val soloRevivesUsed: Int = 0,
    val isAvailable: Boolean = true
) {
    fun toJsonMap(): Map<String, Any> = mapOf(
        "soloRevivesUsed" to soloRevivesUsed,
        "isAvailable" to isAvailable
    )

    companion object {
        fun fromJsonMap(map: Map<String, Any>): QuickReviveState {
            return QuickReviveState(
                soloRevivesUsed = (map["soloRevivesUsed"] as? Number)?.toInt() ?: 0,
                isAvailable = (map["isAvailable"] as? Boolean) ?: true
            )
        }
    }
}

object PerkQuickReviveMachine {
    const val MAX_SOLO_QUICK_REVIVES = 3
    const val DEFAULT_REVIVE_COST = 1000

    data class PurchaseResult(
        val success: Boolean,
        val message: String,
        val updatedState: GameSessionState,
        val updatedVendorState: QuickReviveState
    )

    fun attemptPurchase(
        state: GameSessionState,
        vendorState: QuickReviveState,
        cost: Int = DEFAULT_REVIVE_COST
    ): PurchaseResult {
        val isSolo = state.coopPeers.isEmpty()

        if (!vendorState.isAvailable) {
            return PurchaseResult(false, "Machine out of stock or powered down.", state, vendorState)
        }

        if (state.points < cost) {
            return PurchaseResult(false, "Insufficient points.", state, vendorState)
        }

        if (isSolo) {
            if (vendorState.soloRevivesUsed >= MAX_SOLO_QUICK_REVIVES) {
                return PurchaseResult(
                    false,
                    "Max solo revives ($MAX_SOLO_QUICK_REVIVES) reached. Machine inactive.",
                    state,
                    vendorState.copy(isAvailable = false)
                )
            }
            if (state.activePerks.contains(PerkType.QUICK_REVIVE)) {
                return PurchaseResult(false, "Already holding Quick Revive.", state, vendorState)
            }
        } else {
            // Co-op mode
            if (state.activePerks.contains(PerkType.QUICK_REVIVE)) {
                return PurchaseResult(false, "Already holding Quick Revive.", state, vendorState)
            }
        }

        val newPoints = state.points - cost
        val newPerks = state.activePerks + PerkType.QUICK_REVIVE
        val newState = state.copy(points = newPoints, activePerks = newPerks)
        
        return PurchaseResult(true, "Purchased Quick Revive!", newState, vendorState)
    }

    data class SoloReviveResult(
        val success: Boolean,
        val message: String,
        val updatedState: GameSessionState,
        val updatedVendorState: QuickReviveState
    )

    /**
     * Call this when a solo player is downed, to consume the auto-revive.
     */
    fun attemptSoloAutoRevive(
        state: GameSessionState,
        vendorState: QuickReviveState
    ): SoloReviveResult {
        if (!state.isPlayerDowned) {
            return SoloReviveResult(false, "Player is not downed.", state, vendorState)
        }
        
        if (state.coopPeers.isNotEmpty()) {
            return SoloReviveResult(false, "Auto-revive is Solo only.", state, vendorState)
        }

        if (!state.activePerks.contains(PerkType.QUICK_REVIVE)) {
            return SoloReviveResult(false, "Does not have Quick Revive perk.", state, vendorState)
        }

        val newSoloRevivesUsed = vendorState.soloRevivesUsed + 1
        val newIsAvailable = newSoloRevivesUsed < MAX_SOLO_QUICK_REVIVES

        // Remove the perk and stand the player back up
        val newPerks = state.activePerks - PerkType.QUICK_REVIVE
        val newState = state.copy(
            isPlayerDowned = false,
            playerReviveProgress = 0f,
            activePerks = newPerks,
            hp = state.maxHp, // Restore health upon revive
            playerBleedoutRemainingMs = 30_000L
        )
        val newVendorState = vendorState.copy(
            soloRevivesUsed = newSoloRevivesUsed,
            isAvailable = newIsAvailable
        )

        return SoloReviveResult(true, "Auto-revived!", newState, newVendorState)
    }

    /**
     * Stepping the revive progress of downed peers in co-op mode.
     * dtSeconds should be clamped between 0.001 and 0.100.
     */
    fun computeReviveProgress(
        state: GameSessionState,
        dtSeconds: Float,
        peerId: String,
        baseReviveSpeedPerSec: Float = 0.333f // 3 seconds to revive by default
    ): GameSessionState {
        val dt = if (dtSeconds.isNaN() || dtSeconds.isInfinite()) 0.001f else max(0.001f, min(0.100f, dtSeconds))

        // Check if player has quick revive
        val hasQuickRevive = state.activePerks.contains(PerkType.QUICK_REVIVE)
        val speedMultiplier = if (hasQuickRevive) 2.0f else 1.0f

        val increment = baseReviveSpeedPerSec * speedMultiplier * dt

        val newPeers = state.coopPeers.map { peer ->
            if (peer.peerId == peerId && peer.isDowned) {
                val newProgress = min(1.0f, peer.reviveProgress + increment)
                if (newProgress >= 1.0f) {
                    peer.copy(isDowned = false, reviveProgress = 0f, hp = peer.maxHp)
                } else {
                    peer.copy(reviveProgress = newProgress)
                }
            } else {
                peer
            }
        }

        return state.copy(coopPeers = newPeers)
    }
}
