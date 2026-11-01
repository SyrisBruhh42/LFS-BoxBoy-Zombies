package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.game.ArmorSlot
import com.example.game.FortificationType
import com.example.game.GameSessionState
import com.example.game.LootDropEntity
import com.example.game.LootPayloadKind
import com.example.game.RoomEntropyStage
import com.example.game.WeaponStatComparison
import com.example.ui.theme.CryoCyan
import com.example.ui.theme.HandheldChassis
import com.example.ui.theme.HandheldObsidian
import com.example.ui.theme.MysteryGold
import com.example.ui.theme.PackPurple
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.TextCrisp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.ZombieCrimson
import java.util.Locale

/**
 * Compact HUD strip surfacing Phase 1 (Chiaroscuro Lighting & Camera Tension),
 * Phase 2 (7-Node Non-Euclidean Maze & 180s Entropy Timer),
 * Phase 3 (Fortification Cycler & Siege Fabricator),
 * Phase 4 (4-Part Weapon & Loot Inspect), and Phase 5 (7-Slot Armor & Upgrade Anvil).
 */
@Composable
fun AbyssalTacticalHudStrip(
    state: GameSessionState,
    onCycleLightingMode: () -> Unit,
    onCycleCameraFraming: () -> Unit,
    onCycleFortification: () -> Unit,
    onInspectNearbyLoot: () -> Unit,
    onOpenRpgAnvilModal: () -> Unit
) {
    val activeNode = state.mazeState.currentRoomNode
    val entropySec = activeNode?.timeSpentSeconds ?: 0
    val entropyStage = activeNode?.entropyStage ?: RoomEntropyStage.STABLE
    val modWpn = state.activeModularWeaponForCurrentSlot

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HandheldObsidian)
            .border(1.dp, HandheldChassis)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Phase 1: Chiaroscuro Lighting & Camera Tension Pill
        TacticalPillChip(
            label = "CAM ${String.format(Locale.US, "%.1fx", state.cameraTension.currentZoom)} | ${
                state.cameraTension.lightingMode.name.substringBefore("_")
            }",
            accentColor = CryoCyan,
            tag = "hud_phase1_camera_light_chip",
            onClick = onCycleLightingMode
        )

        TacticalPillChip(
            label = if (state.cameraTension.autoTensionEnabled) "TENSION: AUTO" else "FRAME: ${state.cameraTension.framingState.name.substringBefore("_")}",
            accentColor = PhosphorGreen,
            tag = "hud_phase1_tension_mode_chip",
            onClick = onCycleCameraFraming
        )

        // 2. Phase 2: 7-Node Ring Buffer & 180s Chunk Entropy Pill
        TacticalPillChip(
            label = "MAZE ${state.mazeState.ringBufferNodes.size}/7 | ENTROPY ${entropySec}s/180s",
            accentColor = Color(entropyStage.colorHex),
            tag = "hud_phase2_maze_entropy_chip",
            onClick = onOpenRpgAnvilModal
        )

        // 3. Phase 3: Contextual 1-Tap Fortification Selector Pill
        val fort = state.selectedFortificationType
        TacticalPillChip(
            label = "FORTIFY [X]: ${fort.shortCode} (${fort.costPoints})",
            accentColor = Color(fort.colorHex),
            tag = "hud_phase3_fortify_chip",
            onClick = onCycleFortification
        )

        // 4. Phase 4: Nearby Loot Drop Time-Freeze Inspect Trigger (if any drop is on field)
        val inspectTarget = state.nearbyLootDrop ?: state.lootDrops.firstOrNull()
        if (inspectTarget != null) {
            TacticalPillChip(
                label = "INSPECT [${inspectTarget.rarity.shortBadge}] ${inspectTarget.displayTitle.take(14)}",
                accentColor = Color(inspectTarget.rarity.colorHex),
                tag = "hud_phase4_loot_inspect_chip",
                onClick = onInspectNearbyLoot
            )
        }

        // 5. Phase 4 & 5: 4-Part Modular Weapon + 7-Slot Armor & Upgrade Anvil Button
        TacticalPillChip(
            label = "ANVIL & 7-ARMOR [${modWpn.rarity.shortBadge} +${modWpn.anvilUpgradeTier} | DEF ${state.armorRig.totalDefenseRating} | SCRAP ${state.depthEscrow.abyssalScrap}]",
            accentColor = MysteryGold,
            tag = "hud_phase5_anvil_armor_chip",
            onClick = onOpenRpgAnvilModal
        )
    }
}

@Composable
private fun TacticalPillChip(
    label: String,
    accentColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.75f), RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

/**
 * PHASE 4: Borderlands-Style Time-Freeze Loot Inspection Card Overlay.
 * Automatically freezes the 60 FPS combat loop while the player compares stat deltas.
 */
@Composable
fun TimeFreezeLootInspectOverlay(
    state: GameSessionState,
    drop: LootDropEntity,
    onEquip: () -> Unit,
    onSalvage: () -> Unit,
    onDismiss: () -> Unit
) {
    val rarityColor = Color(drop.rarity.colorHex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HandheldObsidian.copy(alpha = 0.88f))
            .padding(12.dp)
            .testTag("time_freeze_loot_inspect_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldChassis),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, rarityColor, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TIME-FREEZE TACTICAL LOOT INSPECTION",
                        style = MaterialTheme.typography.labelMedium,
                        color = CryoCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "[${drop.rarity.displayName.uppercase()}]",
                        style = MaterialTheme.typography.labelMedium,
                        color = rarityColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = drop.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = rarityColor,
                    fontWeight = FontWeight.Bold
                )

                if (drop.payloadKind == LootPayloadKind.MODULAR_WEAPON && drop.weaponPayload != null) {
                    val cand = drop.weaponPayload
                    val eq = state.activeModularWeaponForCurrentSlot
                    val cmp = WeaponStatComparison(candidate = cand, equipped = eq)

                    Text(
                        text = "Manufacturer: ${cand.receiver.manufacturer.brandName} (${cand.receiver.manufacturer.traitSummary})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextCrisp
                    )

                    // Side-by-Side Stat Delta Grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(HandheldObsidian)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatDeltaColumn("DAMAGE", "${cand.effectiveDamage}", cmp.damageDelta.toFloat(), higherIsBetter = true)
                        StatDeltaColumn("FIRE RATE", String.format(Locale.US, "%.1f/s", cand.shotsPerSecond), cmp.rofDelta, higherIsBetter = true)
                        StatDeltaColumn("MAG SIZE", "${cand.effectiveMagSize}", cmp.magDelta.toFloat(), higherIsBetter = true)
                        StatDeltaColumn("LIGHT-CRIT", "${(cand.illuminatedCritMultiplier * 100).toInt()}%", cmp.critPercentDelta.toFloat(), higherIsBetter = true)
                    }

                    // 4-Part Modular Breakdown
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(HandheldObsidian)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("4-PART MODULAR ARCHITECTURE:", style = MaterialTheme.typography.labelSmall, color = MysteryGold, fontWeight = FontWeight.Bold)
                        Text("| Receiver: ${cand.receiver.name}", style = MaterialTheme.typography.bodySmall, color = TextCrisp)
                        Text("| Barrel: ${cand.barrel.name} (Pierce: ${cand.effectivePierceCount}, Pellets: ${cand.effectivePelletCount})", style = MaterialTheme.typography.bodySmall, color = TextCrisp)
                        Text("| Magazine: ${cand.magazine.name} [${cand.magazine.elementalAffinity.displayName}]", style = MaterialTheme.typography.bodySmall, color = Color(cand.magazine.elementalAffinity.colorHex))
                        Text("| Optic: ${cand.optic.name} (+${cand.optic.lanternRangeBonusPx.toInt()}px Lantern Beam)", style = MaterialTheme.typography.bodySmall, color = CryoCyan)
                    }
                } else if (drop.payloadKind == LootPayloadKind.RPG_ARMOR_PIECE && drop.armorPayload != null) {
                    val piece = drop.armorPayload
                    val equippedPiece = state.armorRig.equippedSlots[piece.slot]
                    val defDelta = piece.effectiveDefense - (equippedPiece?.effectiveDefense ?: 0)
                    val shdDelta = piece.effectiveShieldBonus - (equippedPiece?.effectiveShieldBonus ?: 0)

                    Text(
                        text = "Slot: ${piece.slot.displayName} | Set: ${piece.setArchetype.setName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextCrisp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(HandheldObsidian)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatDeltaColumn("DEFENSE", "${piece.effectiveDefense}", defDelta.toFloat(), higherIsBetter = true)
                        StatDeltaColumn("MAX SHIELD", "+${piece.effectiveShieldBonus}", shdDelta.toFloat(), higherIsBetter = true)
                        StatDeltaColumn("MOVE SPD", "+${piece.moveSpeedBonusPct}%", piece.moveSpeedBonusPct.toFloat(), higherIsBetter = true)
                    }
                    Text(
                        text = "${piece.setArchetype.twoPieceBonusDesc}\n${piece.setArchetype.fourPieceBonusDesc}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PhosphorGreen
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onEquip,
                        colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("loot_inspect_equip_button")
                    ) {
                        Text("EQUIP NOW", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onSalvage,
                        colors = ButtonDefaults.buttonColors(containerColor = MysteryGold, contentColor = HandheldObsidian),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("loot_inspect_salvage_button")
                    ) {
                        Text("SALVAGE (+${drop.rarity.salvageScrapValue} SCRAP)", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("loot_inspect_close_button")
                    ) {
                        Text("RESUME")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatDeltaColumn(
    label: String,
    valueText: String,
    delta: Float,
    higherIsBetter: Boolean
) {
    val isPositive = if (higherIsBetter) delta > 0.05f else delta < -0.05f
    val isNegative = if (higherIsBetter) delta < -0.05f else delta > 0.05f
    val deltaColor = when {
        isPositive -> PhosphorGreen
        isNegative -> ZombieCrimson
        else -> TextMuted
    }
    val prefix = if (delta > 0.05f) "+" else ""

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
        Text(valueText, style = MaterialTheme.typography.titleMedium, color = TextCrisp, fontWeight = FontWeight.Bold)
        Text(
            text = if (kotlin.math.abs(delta) < 0.05f) "(=)" else "($prefix${String.format(Locale.US, "%.0f", delta)})",
            style = MaterialTheme.typography.labelSmall,
            color = deltaColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * PHASE 4 & PHASE 5: Comprehensive Modal Dialog for 4-Part Modular Weapon Forging,
 * 7-Slot RPG Armor Rig & Palette Shader, Upgrade Anvil Tempering, Non-Euclidean Maze Telemetry,
 * and Depth Escrow Meta-Progression Vault.
 */
@Composable
fun AbyssalRpgAnvilAndArmorDialog(
    state: GameSessionState,
    onTemperWeapon: () -> Unit,
    onReforgeElement: () -> Unit,
    onRollNewModularWeapon: () -> Unit,
    onUpgradeArmorSlot: (ArmorSlot) -> Unit,
    onCraftArmorSlot: (ArmorSlot) -> Unit,
    onSelectFortification: (FortificationType) -> Unit,
    onTriggerSiegeNow: () -> Unit,
    onBankEscrowPoints: () -> Unit,
    onWithdrawEscrowPoints: () -> Unit,
    onDismiss: () -> Unit
) {
    val modWpn = state.activeModularWeaponForCurrentSlot
    val rig = state.armorRig
    val palette = rig.paletteProfile
    val escrow = state.depthEscrow

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = HandheldChassis,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
                .border(2.dp, MysteryGold, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ABYSSAL ANVIL, 4-PART WEAPONS & 7-SLOT RPG ARMOR",
                            style = MaterialTheme.typography.titleMedium,
                            color = MysteryGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Scrap: ${escrow.abyssalScrap} | Crystals: ${escrow.abyssalCrystals} | Escrow Vault: ${escrow.bankedEscrowPoints} PTS",
                            style = MaterialTheme.typography.labelMedium,
                            color = CryoCyan
                        )
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.testTag("close_anvil_dialog_button")) {
                        Text("CLOSE")
                    }
                }

                // SECTION 1: Phase 4 Active 4-Part Modular Weapon & Upgrade Anvil
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(modWpn.rarity.colorHex), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PHASE 4: 4-PART MODULAR WEAPON (+${modWpn.anvilUpgradeTier} ANVIL)",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(modWpn.rarity.colorHex),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${modWpn.customTitle} | DMG ${modWpn.effectiveDamage} | MAG ${modWpn.effectiveMagSize} | LIGHT-CRIT ${(modWpn.illuminatedCritMultiplier * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextCrisp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1. Receiver: ${modWpn.receiver.name}\n" +
                                "2. Barrel: ${modWpn.barrel.name}\n" +
                                "3. Magazine: ${modWpn.magazine.name} (${modWpn.magazine.elementalAffinity.displayName})\n" +
                                "4. Optic: ${modWpn.optic.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = onTemperWeapon,
                                colors = ButtonDefaults.buttonColors(containerColor = MysteryGold, contentColor = HandheldObsidian),
                                modifier = Modifier.weight(1f).testTag("anvil_temper_weapon_btn")
                            ) {
                                Text("TEMPER +1", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onReforgeElement,
                                colors = ButtonDefaults.buttonColors(containerColor = CryoCyan, contentColor = HandheldObsidian),
                                modifier = Modifier.weight(1f).testTag("anvil_reforge_element_btn")
                            ) {
                                Text("REFORGE ELEM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onRollNewModularWeapon,
                                colors = ButtonDefaults.buttonColors(containerColor = PackPurple, contentColor = HandheldObsidian),
                                modifier = Modifier.weight(1f).testTag("anvil_forge_weapon_btn")
                            ) {
                                Text("FORGE 4-PART", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // SECTION 2: Phase 5 7-Slot RPG Armor Rig & Dynamic Palette Shader Preview
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(palette.trimColorHex), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PHASE 5: 7-SLOT RPG ARMOR RIG & PALETTE SHADER",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PhosphorGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Total DEF: ${rig.totalDefenseRating} (${(rig.damageReductionFraction * 100).toInt()}% Mitigation) | Shield: ${state.shield}/${state.maxShield} | Active Set: ${palette.activeSetName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextCrisp
                                )
                            }
                            // Live Palette Swatch Preview
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(palette.chassisColorHex)).border(1.dp, TextCrisp, CircleShape))
                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(palette.trimColorHex)).border(1.dp, TextCrisp, CircleShape))
                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(palette.visorColorHex)).border(1.dp, TextCrisp, CircleShape))
                            }
                        }

                        ArmorSlot.entries.forEach { slot ->
                            val piece = rig.equippedSlots[slot]
                            if (piece != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(HandheldChassis)
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${slot.shortCode}: ${piece.displayName} (+${piece.anvilTier})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(piece.rarity.colorHex),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "DEF ${piece.effectiveDefense} | SHD +${piece.effectiveShieldBonus} | Set: ${piece.setArchetype.setName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "[+ANVIL]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MysteryGold,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { onUpgradeArmorSlot(slot) }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                        Text(
                                            text = "[RE-CRAFT]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CryoCyan,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { onCraftArmorSlot(slot) }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 3: Phase 2 Non-Euclidean 7-Node Ring Buffer & Phase 3 Siege / Fortifications
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CryoCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PHASE 2 & 3: NON-EUCLIDEAN 7-NODE RING BUFFER & SIEGE FABRICATOR",
                            style = MaterialTheme.typography.labelLarge,
                            color = CryoCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ring Buffer Nodes: ${state.mazeState.ringBufferNodes.size}/7 | Non-Euclidean Rewrites: ${state.mazeState.nonEuclideanRewritesCount} | Last Event: ${state.mazeState.lastTopologicalEvent}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextCrisp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FortificationType.entries.forEach { ft ->
                                val selected = state.selectedFortificationType == ft
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) Color(ft.colorHex).copy(alpha = 0.25f) else HandheldChassis)
                                        .border(1.dp, Color(ft.colorHex), RoundedCornerShape(6.dp))
                                        .clickable { onSelectFortification(ft) }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "${ft.shortCode} (${ft.costPoints})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(ft.colorHex),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (state.siegeFabricator.isActive) {
                            LinearProgressIndicator(
                                progress = { state.siegeFabricator.progressFraction },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = ZombieCrimson,
                                trackColor = HandheldChassis
                            )
                        } else {
                            Button(
                                onClick = onTriggerSiegeNow,
                                colors = ButtonDefaults.buttonColors(containerColor = ZombieCrimson, contentColor = TextCrisp),
                                modifier = Modifier.fillMaxWidth().testTag("trigger_siege_fabricator_btn")
                            ) {
                                Text("ACTIVATE 20s SIEGE FABRICATOR WAVE (GUARANTEED EPIC LOOT CACHE)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // SECTION 4: Phase 5 Depth Escrow Extraction Vault
                HorizontalDivider(color = MysteryGold.copy(alpha = 0.3f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBankEscrowPoints,
                        colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian),
                        modifier = Modifier.weight(1f).testTag("escrow_bank_points_btn")
                    ) {
                        Text("BANK 500 PTS TO ESCROW", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Button(
                        onClick = onWithdrawEscrowPoints,
                        colors = ButtonDefaults.buttonColors(containerColor = CryoCyan, contentColor = HandheldObsidian),
                        modifier = Modifier.weight(1f).testTag("escrow_withdraw_points_btn")
                    ) {
                        Text("EXTRACT ESCROW (${escrow.bankedEscrowPoints} PTS)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
