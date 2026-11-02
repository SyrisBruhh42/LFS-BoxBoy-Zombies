package com.example.ui.hud

import com.example.game.economy.SingleCurrencyVendingEngine
import com.example.game.loot.borderlands.SwapComparisonCard
import com.example.game.relics.PassiveRelicKind
import com.example.game.relics.PassiveRelicStackState
import com.example.network.p2p.OfflineHostTransportKind

/**
 * Track 10: Unified BoxBoys Mobile HUD State Presenter.
 * - Enforces Single-Currency display (XP bar benched/hidden).
 * - Formats Risk of Rain 2 Passive Relic Ribbon badges (`x1`, `x2`, `x5`...).
 * - Presents Borderlands 2 Swap-on-the-Spot comparison cards and P2P Transport pills.
 */
data class RelicRibbonChip(
    val relicKind: PassiveRelicKind,
    val stackBadge: String,
    val tooltipText: String,
    val colorHex: Long
)

data class UnifiedHudViewModelState(
    val currencyFormatted: String,
    val showXpBar: Boolean = !SingleCurrencyVendingEngine.XP_SYSTEM_BENCHED,
    val comboStreakBadge: String,
    val activeTransportBadge: String,
    val relicChips: List<RelicRibbonChip>,
    val activeSwapCard: SwapComparisonCard? = null
)

object SwapCardAndRelicRibbonHud {

    fun buildHudViewState(
        currencyPoints: Int,
        comboStreak: Int,
        comboMultiplier: Float,
        relicState: PassiveRelicStackState,
        transport: OfflineHostTransportKind,
        activeSwapCard: SwapComparisonCard? = null
    ): UnifiedHudViewModelState {
        val chips = relicState.stacks.entries
            .filter { it.value > 0 }
            .map { (kind, count) ->
                RelicRibbonChip(
                    relicKind = kind,
                    stackBadge = "${kind.displayName} x$count",
                    tooltipText = "${kind.baseEffectDescription} (${kind.perStackScalingDescription})",
                    colorHex = kind.colorHex
                )
            }

        val comboText = if (comboStreak >= 2) {
            "COMBO ${comboStreak}x (${String.format("%.2f", comboMultiplier)}x CASH)"
        } else {
            "COMBO READY"
        }

        return UnifiedHudViewModelState(
            currencyFormatted = "$currencyPoints PTS",
            showXpBar = false,
            comboStreakBadge = comboText,
            activeTransportBadge = "${transport.badgeCode} (${transport.defaultTickHz}Hz)",
            relicChips = chips,
            activeSwapCard = activeSwapCard
        )
    }
}
