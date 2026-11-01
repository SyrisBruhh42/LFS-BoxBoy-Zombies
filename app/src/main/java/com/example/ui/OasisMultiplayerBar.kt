package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FriendlyGuideCardState
import com.example.data.OasisImagineeringTranslator
import com.example.data.OasisWingDestination
import com.example.data.RmblynChatMessage
import com.example.data.SanctuaryStorytellerState
import com.example.data.Zt9ComfortOverridesState
import com.example.network.OasisSessionStatus
import com.example.ui.theme.AetherCyan
import com.example.ui.theme.AstralObsidian
import com.example.ui.theme.AstralSurface
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CryoCyan
import com.example.ui.theme.HandheldChassis
import com.example.ui.theme.HandheldObsidian
import com.example.ui.theme.HighContrastBlack
import com.example.ui.theme.HighContrastYellow
import com.example.ui.theme.LocalAccessibilityTheme
import com.example.ui.theme.MysteryGold
import com.example.ui.theme.PhosphorGreen

/**
 * Uninterrupted Top Shell Ribbon (`OasisMultiplayerBar`) that remains mounted across
 * Title Hub, active gameplay, modals, and Wing switches without ever dropping the active
 * party room (`OASIS-XXXX`) or 16-Channel Rmblyn Family Chat (`@willow`, `@cinder`, `@mote`).
 *
 * Includes the compact AudioBookshelf & OpenAudible "Sanctuary Storyteller" pill (`cb64f9e7`)
 * with Play/Pause, Chapter title, and 15m/30m/45m Starlight Sleep-Fade timer.
 */
@Composable
fun OasisMultiplayerBar(
    oasisStatus: OasisSessionStatus,
    storytellerState: SanctuaryStorytellerState,
    comfortState: Zt9ComfortOverridesState,
    activeWing: OasisWingDestination,
    isOverlayOpen: Boolean,
    onToggleOverlay: () -> Unit,
    onToggleStorytellerPlay: () -> Unit,
    onCycleStorytellerChapter: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onOpenWingSwitcher: () -> Unit,
    onOpenFamilyRoomModal: () -> Unit
) {
    val a11y = LocalAccessibilityTheme.current
    val partyCode = OasisImagineeringTranslator.formatPartyRoomBadge(oasisStatus.roomCode)
    val hearthBadge = oasisStatus.tailscaleHandshake.tailscaleTunnelBadge

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (a11y.highContrastMode) HighContrastBlack else AstralObsidian)
            .border(
                width = 1.dp,
                color = if (a11y.highContrastMode) HighContrastYellow else ChampagneGold.copy(alpha = 0.45f)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .horizontalScroll(rememberScrollState())
            .testTag("oasis_multiplayer_shell_ribbon"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Active Party Room & Hearth Key (Instant Family Pass) Pill
        Row(
            modifier = Modifier
                .background(AstralSurface, RoundedCornerShape(8.dp))
                .border(1.dp, PhosphorGreen.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenFamilyRoomModal)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("oasis_ribbon_party_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WifiTethering,
                contentDescription = "Family Party Room",
                tint = PhosphorGreen,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "$partyCode | Hearth Key $hearthBadge",
                style = MaterialTheme.typography.labelSmall,
                color = PhosphorGreen,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "(${oasisStatus.hostMode.label})",
                style = MaterialTheme.typography.labelSmall,
                color = AetherCyan,
                fontSize = 10.sp
            )
        }

        // 2. Sanctuary Storyteller Audio Pill (cb64f9e7) with Play/Pause, Chapter & 15m/30m/45m Starlight Sleep-Fade
        Row(
            modifier = Modifier
                .background(AstralSurface, RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = if (storytellerState.isPlaying) ChampagneGold else CryoCyan.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 7.dp, vertical = 3.dp)
                .testTag("sanctuary_storyteller_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onToggleStorytellerPlay)
                    .padding(horizontal = 2.dp, vertical = 1.dp)
                    .testTag("storyteller_play_pause_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (storytellerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (storytellerState.isPlaying) "Pause Sanctuary Storyteller" else "Play Sanctuary Storyteller",
                    tint = ChampagneGold,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Sanctuary Storyteller: ${storytellerState.chapterTitle}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChampagneGold,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .background(HandheldObsidian, RoundedCornerShape(5.dp))
                    .border(1.dp, CryoCyan.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                    .clickable(onClick = onCycleStorytellerChapter)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .testTag("storyteller_next_chapter_button")
            ) {
                Text(
                    text = "CH+",
                    style = MaterialTheme.typography.labelSmall,
                    color = CryoCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val sleepLabel = if (storytellerState.sleepTimerMinutes > 0) {
                val remMin = (storytellerState.sleepRemainingSeconds / 60).coerceAtLeast(1)
                "Sleep ${storytellerState.sleepTimerMinutes}m (${remMin}m)"
            } else {
                "Sleep: Off"
            }
            Box(
                modifier = Modifier
                    .background(HandheldObsidian, RoundedCornerShape(5.dp))
                    .border(
                        1.dp,
                        if (storytellerState.sleepTimerMinutes > 0) PhosphorGreen else ChampagneGold.copy(alpha = 0.5f),
                        RoundedCornerShape(5.dp)
                    )
                    .clickable(onClick = onCycleSleepTimer)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .testTag("storyteller_sleep_timer_button")
            ) {
                Text(
                    text = sleepLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (storytellerState.sleepTimerMinutes > 0) PhosphorGreen else ChampagneGold,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 3. 1-Click Switch Wing / Return to TrashArcade Hall Selector
        Row(
            modifier = Modifier
                .background(AstralSurface, RoundedCornerShape(8.dp))
                .border(1.dp, CryoCyan.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenWingSwitcher)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("oasis_switch_wing_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = "Switch Wing or Return to TrashArcade Hall",
                tint = CryoCyan,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "Wing: ${activeWing.shortBadge}",
                style = MaterialTheme.typography.labelSmall,
                color = CryoCyan,
                fontWeight = FontWeight.Bold
            )
        }

        // 4. Shift+Tab / Ctrl+K Overlay & 16-Channel Rmblyn Family Chat Toggle
        Row(
            modifier = Modifier
                .background(
                    if (isOverlayOpen) ChampagneGold else AstralSurface,
                    RoundedCornerShape(8.dp)
                )
                .border(1.dp, ChampagneGold, RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleOverlay)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("oasis_overlay_toggle_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Toggle Oasis Overlay (Shift+Tab)",
                tint = if (isOverlayOpen) HandheldObsidian else ChampagneGold,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (isOverlayOpen) "Overlay Open [Shift+Tab]" else "Family Chat & Wings [Shift+Tab]",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOverlayOpen) HandheldObsidian else ChampagneGold,
                fontWeight = FontWeight.Bold
            )
        }

        // 5. Family-Friendly Arcade Mode Comfort Badge
        if (comfortState.isEffectiveFamilyArcade) {
            Box(
                modifier = Modifier
                    .background(HandheldObsidian, RoundedCornerShape(6.dp))
                    .border(1.dp, PhosphorGreen.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleOverlay)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Family Arcade [SAFE]",
                    style = MaterialTheme.typography.labelSmall,
                    color = PhosphorGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Friendly Guide Card (`ZT-6` replacement) structured in the 4 required Disney Imagineering steps:
 * 1. What Happened
 * 2. Why
 * 3. Your Progress Is Safe
 * 4. One-Click Next Step
 */
@Composable
fun FriendlyGuideCard(
    guideState: FriendlyGuideCardState,
    onNextStepClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (guideState.isWarning) MysteryGold else PhosphorGreen
    Card(
        colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .testTag("friendly_guide_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = borderColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = guideState.title.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = borderColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onNextStepClicked,
                    modifier = Modifier.testTag("friendly_guide_next_step_button")
                ) {
                    Text(guideState.oneClickNextStepLabel)
                }
            }

            Text(
                text = "1. What Happened: ${guideState.whatHappened}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            Text(
                text = "2. Why: ${guideState.whyItHappened}",
                style = MaterialTheme.typography.bodySmall,
                color = CryoCyan
            )
            Text(
                text = "3. Your Progress Is Safe: ${guideState.yourProgressIsSafe}",
                style = MaterialTheme.typography.bodySmall,
                color = PhosphorGreen
            )
            Text(
                text = "4. One-Click Next Step: Tap '${guideState.oneClickNextStepLabel}' anytime to continue.",
                style = MaterialTheme.typography.labelSmall,
                color = MysteryGold
            )
        }
    }
}

/**
 * `Shift+Tab` / `Ctrl+K` Oasis Universal Overlay & Wing Switcher Dialog.
 * Pauses single-player action physics cleanly while keeping the active party room (`OASIS-XXXX`),
 * 16-Channel Rmblyn Family Chat (`@willow`, `@cinder`, `@mote`), Wing Switcher, and
 * Sanctuary Storyteller (`cb64f9e7`) responsive.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OasisUniversalOverlayDialog(
    oasisStatus: OasisSessionStatus,
    storytellerState: SanctuaryStorytellerState,
    comfortState: Zt9ComfortOverridesState,
    friendlyGuideCard: FriendlyGuideCardState,
    activeWing: OasisWingDestination,
    rmblynMessages: List<RmblynChatMessage>,
    onSelectWing: (OasisWingDestination) -> Unit,
    onToggleFamilyArcadeMode: (Boolean) -> Unit,
    onSendRmblynChat: (String) -> Unit,
    onToggleStorytellerPlay: () -> Unit,
    onSelectStorytellerTrack: (String) -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onRefreshStorytellerLibrary: () -> Unit,
    onProbeHearthKeyHandshake: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var chatDraft by remember { mutableStateOf("") }
    val partyCode = OasisImagineeringTranslator.formatPartyRoomBadge(oasisStatus.roomCode)
    val sso = oasisStatus.tailscaleHandshake

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 720.dp)
                .border(1.5.dp, ChampagneGold, RoundedCornerShape(16.dp))
                .testTag("oasis_universal_overlay_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = AstralObsidian
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(listOf(AstralObsidian, AstralSurface, AstralObsidian))
                    )
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "OASIS SANCTUARY OVERLAY & WING SWITCHER",
                            style = MaterialTheme.typography.titleLarge,
                            color = ChampagneGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Party Room: $partyCode | ${OasisImagineeringTranslator.HEARTH_KEY_LABEL}: ${sso.stableNodeId} (${sso.tailscaleTunnelBadge}) | Single-Player Physics Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = AetherCyan
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PhosphorGreen,
                            contentColor = HandheldObsidian
                        ),
                        modifier = Modifier.testTag("resume_from_oasis_overlay_button")
                    ) {
                        Text("RESUME PLAY", fontWeight = FontWeight.Bold)
                    }
                }

                // 1. Switch Wing / Return to TrashArcade Hall Selector
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CryoCyan.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SWITCH WING OR RETURN TO TRASHARCADE HALL (PARTY ROOM STAYS CONNECTED)",
                            style = MaterialTheme.typography.titleMedium,
                            color = CryoCyan,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OasisWingDestination.entries.forEach { wing ->
                                FilterChip(
                                    selected = activeWing == wing,
                                    onClick = { onSelectWing(wing) },
                                    label = { Text(wing.displayName) },
                                    modifier = Modifier.testTag("wing_chip_${wing.wingId}")
                                )
                            }
                        }

                        // In-App Wing Preview + 1-Click Oasis Hub Deep-Link Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HandheldChassis, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeWing.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ChampagneGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = activeWing.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "Deep-Link: ${activeWing.deepLinkUri}?oasis_room_code=${oasisStatus.roomCode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AetherCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onSelectWing(activeWing)
                                    runCatching {
                                        val uri = Uri.parse(
                                            "${activeWing.deepLinkUri}?oasis_room_code=${oasisStatus.roomCode}&oasis_subuser_id=${sso.subuserId}"
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            putExtra("oasis_room_code", oasisStatus.roomCode)
                                            putExtra("oasis_subuser_id", sso.subuserId)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ChampagneGold,
                                    contentColor = HandheldObsidian
                                ),
                                modifier = Modifier.testTag("open_wing_deeplink_button")
                            ) {
                                Text("OPEN IN OASIS HUB")
                            }
                        }
                    }
                }

                // 2. AudioBookshelf & OpenAudible Sanctuary Storyteller (cb64f9e7)
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ChampagneGold.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AUDIOBOOKSHELF & OPENAUDIBLE SANCTUARY STORYTELLER (cb64f9e7)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ChampagneGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${storytellerState.bookTitle} | ${storytellerState.chapterTitle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                                Text(
                                    text = storytellerState.sourceModeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PhosphorGreen
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = onRefreshStorytellerLibrary) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Library", modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SYNC LIBRARY")
                                }
                                Button(
                                    onClick = onToggleStorytellerPlay,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (storytellerState.isPlaying) MysteryGold else PhosphorGreen,
                                        contentColor = HandheldObsidian
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (storytellerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (storytellerState.isPlaying) "PAUSE" else "PLAY STORY")
                                }
                            }
                        }

                        Text(
                            text = "Select Chapter (/api/lfs/openaudible/stream/[key]):",
                            style = MaterialTheme.typography.labelSmall,
                            color = AetherCyan
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            storytellerState.availableTracks.forEach { track ->
                                FilterChip(
                                    selected = storytellerState.activeTrackKey == track.key,
                                    onClick = { onSelectStorytellerTrack(track.key) },
                                    label = { Text(track.chapterTitle) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Starlight Sleep-Fade Timer (15m / 30m / 45m):",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneGold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0 to "Off", 15 to "15m", 30 to "30m", 45 to "45m").forEach { (mins, label) ->
                                    FilterChip(
                                        selected = storytellerState.sleepTimerMinutes == mins,
                                        onClick = { onSetSleepTimerMinutes(mins) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 16-Channel Rmblyn Family Chat (@willow, @cinder, @mote)
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PhosphorGreen.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "16-CHANNEL RMBLYN FAMILY CHAT (ARCADE-LOUNGE | @willow @cinder @mote)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PhosphorGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HandheldChassis, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rmblynMessages.takeLast(5).forEach { msg ->
                                Text(
                                    text = "[${msg.senderHandle}]: ${msg.messageText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (msg.isCompanionBot) AetherCyan else ChampagneGold
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = chatDraft,
                                onValueChange = { chatDraft = it },
                                label = { Text("Send message to Family Room & @willow / @cinder / @mote") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (chatDraft.isNotBlank()) {
                                        onSendRmblynChat(chatDraft)
                                        chatDraft = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PhosphorGreen,
                                    contentColor = HandheldObsidian
                                )
                            ) {
                                Text("SEND")
                            }
                        }
                    }
                }

                // 4. Family-Friendly Arcade Mode (zt9_comfort_overrides) & Starlight Quiet Hours
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MysteryGold.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FAMILY-FRIENDLY ARCADE MODE (zt9_comfort_overrides)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MysteryGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Frames opponents as Cardboard Critters & Neon Arcade Bots with Confetti KOs and Tag-Team Rescue for Ring 1 (Child) through Ring 3 (Owner).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                            Switch(
                                checked = comfortState.isEffectiveFamilyArcade,
                                onCheckedChange = onToggleFamilyArcadeMode,
                                modifier = Modifier.testTag("family_arcade_mode_switch")
                            )
                        }
                        Text(
                            text = "${OasisImagineeringTranslator.STARLIGHT_QUIET_HOURS_LABEL}: Active Ring ${sso.trustRing} (${sso.persona}) -- ${sso.abacPerimeterBadge}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AetherCyan
                        )
                    }
                }

                // 5. Friendly Guide Card (ZT-6 replacement)
                FriendlyGuideCard(
                    guideState = friendlyGuideCard,
                    onNextStepClicked = onProbeHearthKeyHandshake
                )
            }
        }
    }
}
