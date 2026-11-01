package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.CareerDossier
import com.example.data.DailyDirective
import com.example.data.GoogleDriveSyncUiState
import com.example.game.GameSessionState
import com.example.network.NetworkHealthSnapshot
import com.example.ui.theme.AetherCyan
import com.example.ui.theme.AstralObsidian
import com.example.ui.theme.AstralSlate
import com.example.ui.theme.AstralSurface
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CinzelDisplayFamily
import com.example.ui.theme.CryoCyan
import com.example.ui.theme.HighContrastBlack
import com.example.ui.theme.HighContrastYellow
import com.example.ui.theme.LimitCrimson
import com.example.ui.theme.LocalAccessibilityTheme
import com.example.ui.theme.MateriaViolet
import com.example.ui.theme.TextCrisp
import com.example.ui.theme.TextMuted

/**
 * Final Fantasy XVI / VII Remake Inspired Crystalline RPG Entry Hub (`TitleHub`).
 *
 * Features a custom Aetherial Crystal Crest logo, 'Start Survival' entry, Google Drive
 * Chronicle Save/Load sync bar, 'Career Dossier', and a hidden 'Developer Tools' overlay toggle
 * for live debugging and telemetry metrics.
 */
@Composable
fun TitleHub(
    gameState: GameSessionState,
    careerDossier: CareerDossier,
    dailyDirectives: List<DailyDirective>,
    driveSyncState: GoogleDriveSyncUiState,
    networkHealth: NetworkHealthSnapshot,
    isCloudAuthenticated: Boolean,
    developerModeEnabled: Boolean,
    onStartSurvival: () -> Unit,
    onNewAstralSeed: () -> Unit,
    onQuickSyncToGoogleDrive: () -> Unit,
    onOpenSaveAndDriveModal: () -> Unit,
    onOpenCareerDossier: () -> Unit,
    onOpenCoopLobby: () -> Unit,
    onOpenArmory: () -> Unit,
    onOpenFieldManual: () -> Unit,
    onOpenSettingsPrivacy: () -> Unit,
    onToggleDeveloperTools: (Boolean) -> Unit,
    onToggleAiStressBot: () -> Unit,
    onOpenKonsoleDebugger: () -> Unit
) {
    val a11y = LocalAccessibilityTheme.current
    var devTapCount by remember { mutableIntStateOf(0) }
    var isDevOverlayExpanded by remember(developerModeEnabled) { mutableStateOf(developerModeEnabled) }
    val readyContracts = dailyDirectives.count { it.isCompleted && !it.isClaimed }
    val activeContracts = dailyDirectives.count { !it.isClaimed }

    Dialog(
        onDismissRequest = onStartSurvival,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 680.dp)
                .border(
                    width = if (a11y.highContrastMode) 3.dp else 1.5.dp,
                    brush = Brush.linearGradient(
                        if (a11y.highContrastMode) {
                            listOf(HighContrastYellow, HighContrastYellow)
                        } else {
                            listOf(ChampagneGold, AetherCyan, ChampagneGold)
                        }
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                .testTag("title_hub_root"),
            shape = RoundedCornerShape(18.dp),
            color = if (a11y.highContrastMode) HighContrastBlack else AstralObsidian
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            if (a11y.highContrastMode) {
                                listOf(HighContrastBlack, HighContrastBlack)
                            } else {
                                listOf(AstralObsidian, AstralSurface, AstralObsidian)
                            }
                        )
                    )
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Professional Final Fantasy Inspired Crest Logo & Hero Artwork
                Card(
                    colors = CardDefaults.cardColors(containerColor = AstralSurface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ChampagneGold.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_ff_crest_banner_1793566253875),
                            contentDescription = "BoxBoy Zombies Crystalline Citadel Emblem",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        // Subtle dark gradient scrim for crisp engraved Cinzel logo legibility
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            AstralObsidian.copy(alpha = 0.25f),
                                            AstralObsidian.copy(alpha = 0.72f),
                                            AstralObsidian.copy(alpha = 0.94f)
                                        )
                                    )
                                )
                                .padding(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = careerDossier.careerRankTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ChampagneGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (driveSyncState.isDriveConnected || isCloudAuthenticated) {
                                            "GOOGLE DRIVE & CLOUD SYNCED"
                                        } else {
                                            "LOCAL CHRONICLE VAULT"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AetherCyan
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Custom Crystalline Aether Emblem + Engraved Game Logo
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.clickable {
                                        devTapCount++
                                        if (devTapCount >= 5) {
                                            val next = !isDevOverlayExpanded
                                            isDevOverlayExpanded = next
                                            onToggleDeveloperTools(next)
                                            devTapCount = 0
                                        }
                                    }
                                ) {
                                    AetherCrystalLogoCrest(modifier = Modifier.size(34.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "BOXBOY ZOMBIES",
                                            fontFamily = CinzelDisplayFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 24.sp,
                                            letterSpacing = 2.sp,
                                            color = TextCrisp,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "AETHERIAL CITADEL CHRONICLES",
                                            fontFamily = CinzelDisplayFamily,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 11.sp,
                                            letterSpacing = 3.sp,
                                            color = ChampagneGold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    AetherCrystalLogoCrest(modifier = Modifier.size(34.dp))
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                // Filigree Gold Rule
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .height(1.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color.Transparent, ChampagneGold, AetherCyan, ChampagneGold, Color.Transparent)
                                            )
                                        )
                                )
                            }
                        }
                    }
                }

                // 2. Primary Entry: 'Start Survival' & 'New Astral Seed'
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartSurvival,
                        modifier = Modifier
                            .weight(1.45f)
                            .height(58.dp)
                            .border(1.dp, ChampagneGold, RoundedCornerShape(12.dp))
                            .testTag("start_survival_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AetherCyan,
                            contentColor = AstralObsidian
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start Survival")
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "START SURVIVAL",
                                fontFamily = CinzelDisplayFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Wave ${gameState.wave} • ${gameState.points} Gil/PTS • ${gameState.currentWeapon.shortCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AstralObsidian.copy(alpha = 0.85f)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onNewAstralSeed,
                        modifier = Modifier
                            .weight(0.95f)
                            .height(58.dp)
                            .border(1.dp, ChampagneGold.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .testTag("title_hub_new_seed_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "NEW REALM",
                                fontFamily = CinzelDisplayFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ChampagneGold
                            )
                            Text(
                                text = "Seed #${gameState.mapSeed.toString().takeLast(5)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                // 3. Google Drive Save & Load Chronicle Sync Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = AstralSlate),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AetherCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Google Drive Chronicles",
                                    tint = AetherCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "GOOGLE DRIVE CLOUD CHRONICLES",
                                        fontFamily = CinzelDisplayFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = ChampagneGold
                                    )
                                    Text(
                                        text = driveSyncState.statusMessage,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextCrisp
                                    )
                                }
                            }
                            Text(
                                text = "${driveSyncState.driveFiles.size} Saved",
                                style = MaterialTheme.typography.labelLarge,
                                color = AetherCyan
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onQuickSyncToGoogleDrive,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ChampagneGold,
                                    contentColor = AstralObsidian
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("title_hub_quick_drive_backup_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SAVE TO DRIVE", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onOpenSaveAndDriveModal,
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, AetherCyan, RoundedCornerShape(8.dp))
                                    .testTag("title_hub_drive_sync_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = AetherCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("LOAD CHRONICLE", color = AetherCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 4. Primary Navigation Grid: Career Dossier, 4-Player Co-Op, Armory & Forge, Field Manual & Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CrystallineHubCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.MilitaryTech,
                        title = "CAREER DOSSIER",
                        subtitle = if (readyContracts > 0) "$readyContracts Bounties Ready!" else "$activeContracts Daily Directives",
                        accent = ChampagneGold,
                        tag = "title_hub_career_dossier_button",
                        onClick = onOpenCareerDossier
                    )
                    CrystallineHubCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.WifiTethering,
                        title = "ASTRAL CO-OP",
                        subtitle = "4-Player Squad & AI Companions",
                        accent = AetherCyan,
                        tag = "title_hub_coop_button",
                        onClick = onOpenCoopLobby
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CrystallineHubCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AutoAwesome,
                        title = "CELESTIAL ARMORY",
                        subtitle = "${gameState.unlockedWeaponIds.size}/7 Arms • 7-Slot Armor",
                        accent = MateriaViolet,
                        tag = "title_hub_armory_button",
                        onClick = onOpenArmory
                    )
                    CrystallineHubCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Settings,
                        title = "CONFIG & PRIVACY",
                        subtitle = "Audio, Haptics & Account",
                        accent = CryoCyan,
                        tag = "title_hub_settings_button",
                        onClick = onOpenSettingsPrivacy
                    )
                }

                // 5. Footer Strip: Tactical Codex + Hidden Developer Tools Overlay Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onOpenFieldManual,
                        modifier = Modifier.testTag("title_hub_manual_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TACTICAL CODEX", color = ChampagneGold)
                    }

                    // Hidden / Discrete Developer Tools Overlay Toggle
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDevOverlayExpanded) MateriaViolet.copy(alpha = 0.2f) else AstralSurface)
                            .border(
                                1.dp,
                                if (isDevOverlayExpanded) MateriaViolet else TextMuted.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                val next = !isDevOverlayExpanded
                                isDevOverlayExpanded = next
                                onToggleDeveloperTools(next)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("title_hub_dev_tools_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Toggle Developer Tools Overlay",
                            tint = if (isDevOverlayExpanded) MateriaViolet else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isDevOverlayExpanded) "DEV TELEMETRY: ACTIVE" else "DEV TOOLS",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDevOverlayExpanded) MateriaViolet else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 6. Collapsible Live Developer Tools & Telemetry Metrics Overlay
                AnimatedVisibility(visible = isDevOverlayExpanded) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AstralObsidian),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MateriaViolet, RoundedCornerShape(12.dp))
                            .testTag("title_hub_dev_tools_overlay")
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
                                Text(
                                    text = "LIVE DEVELOPER TELEMETRY & DIAGNOSTICS",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MateriaViolet,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Clock ${gameState.speedConfig.clockMultiplier}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneGold
                                )
                            }
                            HorizontalDivider(color = MateriaViolet.copy(alpha = 0.3f))
                            Text(
                                text = "• Entities: ${gameState.zombies.size} Hostiles | ${gameState.fortifications.size} Fortifications | ${gameState.coopPeers.size} Squad Peers",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextCrisp
                            )
                            Text(
                                text = "• Chunk Streamer: ${gameState.chunkSnapshot.activeChunkCount} Active Chunks | Mid-LOD Cached: ${gameState.chunkSnapshot.cachedMidLodCount} / ${gameState.chunkSnapshot.totalChunksInWorld}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AetherCyan
                            )
                            Text(
                                text = "• Network Mesh: ${networkHealth.statusHeadline} | Private/VPN: ${networkHealth.isPrivateNetworkDetected}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onToggleAiStressBot,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dev_overlay_toggle_bot_button")
                                ) {
                                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (gameState.aiBotEnabled) "AI BOT: ON" else "AI BOT: OFF")
                                }
                                Button(
                                    onClick = onOpenKonsoleDebugger,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MateriaViolet,
                                        contentColor = AstralObsidian
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dev_overlay_open_konsole_button")
                                ) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("KONSOLE CLI", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AetherCrystalLogoCrest(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Outer Champagne Gold Filigree Diamond
        val outerPath = Path().apply {
            moveTo(cx, 2f)
            lineTo(w - 4f, cy)
            lineTo(cx, h - 2f)
            lineTo(4f, cy)
            close()
        }
        drawPath(
            path = outerPath,
            color = ChampagneGold.copy(alpha = 0.85f),
            style = Stroke(width = 2.2f)
        )

        // Inner Luminous Aether Crystal Facet
        val innerPath = Path().apply {
            moveTo(cx, h * 0.16f)
            lineTo(w * 0.76f, cy)
            lineTo(cx, h * 0.84f)
            lineTo(w * 0.24f, cy)
            close()
        }
        drawPath(
            path = innerPath,
            brush = Brush.verticalGradient(
                listOf(CryoCyan, AetherCyan, MateriaViolet)
            )
        )

        // Center Starlight Horizon Line
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(w * 0.12f, cy),
            end = Offset(w * 0.88f, cy),
            strokeWidth = 1.4f
        )
    }
}

@Composable
private fun CrystallineHubCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        colors = CardDefaults.cardColors(containerColor = AstralSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    fontFamily = CinzelDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = accent
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextCrisp
            )
        }
    }
}
