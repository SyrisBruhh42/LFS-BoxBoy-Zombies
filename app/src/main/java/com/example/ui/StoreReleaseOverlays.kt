package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.audio.CombatSfxEvent
import com.example.data.AppStoreSettingsState
import com.example.data.CareerDossier
import com.example.data.DailyDirective
import com.example.game.GameSessionState
import com.example.ui.theme.CryoCyan
import com.example.ui.theme.HandheldChassis
import com.example.ui.theme.HandheldObsidian
import com.example.ui.theme.HighContrastBlack
import com.example.ui.theme.HighContrastYellow
import com.example.ui.theme.LocalAccessibilityTheme
import com.example.ui.theme.MysteryGold
import com.example.ui.theme.PackPurple
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.TextCrisp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.ZombieCrimson

@Composable
fun TacticalTitleHubDialog(
    gameState: GameSessionState,
    careerDossier: CareerDossier,
    dailyDirectives: List<DailyDirective>,
    isCloudAuthenticated: Boolean,
    onResumeOrSoloDrop: () -> Unit,
    onNewMapDrop: () -> Unit,
    onOpenCoopLobby: () -> Unit,
    onOpenCareerDossier: () -> Unit,
    onOpenFieldManual: () -> Unit,
    onOpenArmory: () -> Unit,
    onOpenSettingsPrivacy: () -> Unit
) {
    val a11y = LocalAccessibilityTheme.current
    val readyContracts = dailyDirectives.count { it.isCompleted && !it.isClaimed }
    val activeContracts = dailyDirectives.count { !it.isClaimed }

    Dialog(
        onDismissRequest = onResumeOrSoloDrop,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 640.dp)
                .border(
                    width = if (a11y.highContrastMode) 3.dp else 2.dp,
                    color = if (a11y.highContrastMode) HighContrastYellow else PhosphorGreen,
                    shape = RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            color = if (a11y.highContrastMode) HighContrastBlack else HandheldObsidian
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Banner & Rank Pill
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldChassis),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = careerDossier.careerRankTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = MysteryGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isCloudAuthenticated) "CLOUD SYNC READY" else "OFFLINE AES-GCM",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCloudAuthenticated) PhosphorGreen else CryoCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "BOXBOY ZOMBIES",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PhosphorGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "2D Orthographic Bio-Noir Survival | 4-Player Co-Op | 7-Slot RPG Armor",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_banner),
                            contentDescription = "BoxBoy Zombies Tactical Title Hub Banner",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, PhosphorGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Primary Action Row: Resume Active Run / Fresh Solo Drop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onResumeOrSoloDrop,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(54.dp)
                            .testTag("title_hub_solo_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PhosphorGreen,
                            contentColor = HandheldObsidian
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Deploy to Active Wave")
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "DEPLOY (WAVE ${gameState.wave})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${gameState.points} PTS | ${gameState.currentWeapon.shortCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = HandheldObsidian.copy(alpha = 0.85f)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onNewMapDrop,
                        modifier = Modifier
                            .weight(0.9f)
                            .height(54.dp)
                            .border(1.5.dp, CryoCyan, RoundedCornerShape(10.dp))
                            .testTag("title_hub_new_seed_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "NEW CITY SEED",
                                style = MaterialTheme.typography.labelLarge,
                                color = CryoCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Fresh Wave 1 Drop",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                // 4-Player Co-Op & Squad Bots CTA
                Button(
                    onClick = onOpenCoopLobby,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("title_hub_coop_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CryoCyan,
                        contentColor = HandheldObsidian
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.WifiTethering, contentDescription = "4-Player Co-Op Lobby")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "4-PLAYER CO-OP LOBBY & AI SQUAD BOTS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2x2 Tactical Hub Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HubActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.MilitaryTech,
                        title = "CAREER & CONTRACTS",
                        subtitle = if (readyContracts > 0) "$readyContracts REWARD READY!" else "$activeContracts Daily Directives",
                        accent = MysteryGold,
                        tag = "title_hub_dossier_button",
                        onClick = onOpenCareerDossier
                    )
                    HubActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.MenuBook,
                        title = "FIELD MANUAL",
                        subtitle = "3-Step Interactive Guide",
                        accent = PhosphorGreen,
                        tag = "title_hub_manual_button",
                        onClick = onOpenFieldManual
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HubActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Security,
                        title = "ARMORY & LOADOUT",
                        subtitle = "${gameState.unlockedWeaponIds.size}/7 Weapons Unlocked",
                        accent = ZombieCrimson,
                        tag = "title_hub_armory_button",
                        onClick = onOpenArmory
                    )
                    HubActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Settings,
                        title = "SETTINGS & PRIVACY",
                        subtitle = "Audio, Haptics & Account",
                        accent = CryoCyan,
                        tag = "title_hub_settings_button",
                        onClick = onOpenSettingsPrivacy
                    )
                }
            }
        }
    }
}

@Composable
private fun HubActionCard(
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
            .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        colors = CardDefaults.cardColors(containerColor = HandheldChassis),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
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
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold
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

@Composable
fun InteractiveFieldManualDialog(
    onCompleteAndClose: () -> Unit
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    val steps = listOf(
        Triple(
            "STEP 1 OF 3 | MOVEMENT & 360 deg TACTICAL AIM",
            "Survive the Abyssal Metropolis with Dual-Input Precision",
            listOf(
                "| LEFT STICK / WASD: Smooth 360 deg movement with corner-slide physics around walls and vehicles.",
                "| DIRECT TOUCH AIM / ARROWS: Drag anywhere on the city viewport or hold [FIRE] to lock onto zombies.",
                "| LIGHT-LOCK CRIT: Keep zombies inside your flashlight cone for bonus critical damage and stagger.",
                "| QUICK RELOAD [R]: Tap the Magazine Tracker or [Y/R] before entering tight corridors."
            )
        ),
        Triple(
            "STEP 2 OF 3 | ECONOMY, FORTIFICATIONS & RPG ANVIL",
            "Turn Survival Points & Abyssal Scrap into Unstoppable Firepower",
            listOf(
                "| MYSTERY BOX & WALL BUYS: Approach glowing stations and tap [ACT / B] to unlock new weapons and Perks.",
                "| BARRELS & CRYO TRAPS [X]: Drop Red Explosive Barrels or Cryo Spike Traps in choke points to shatter hordes.",
                "| PACK-A-PUNCH & 7-SLOT ARMOR: Upgrade weapons to Mk-5 and forge 7-Slot RPG Armor pieces in the Anvil modal.",
                "| DEPTH ESCROW VAULT: Bank surplus points in the Anvil so your next run starts with a massive head start."
            )
        ),
        Triple(
            "STEP 3 OF 3 | 3-TIER CO-OP & AI SQUADMATES",
            "Never Fight Alone -- Online, Local LAN, or with Autonomous Bots",
            listOf(
                "| INSTANT AI SQUADMATES: Summon @willow (Medic), @cinder (Demolitions), and @mote (Rail Scout) anytime.",
                "| 6-CHAR ROOM CODES: Host or join 4-player squads via Cloud Relay, LAN Wi-Fi, or Tailscale Private Mesh.",
                "| SQUAD REVIVE SYSTEM: Stand inside a downed teammate's green revive circle to bring them back into the fight.",
                "| DAILY DIRECTIVES: Complete 3 rotating survival contracts each day in the Career Dossier for bonus points!"
            )
        )
    )

    val current = steps[stepIndex]

    EmulatorModalContainer(
        title = "TACTICAL FIELD MANUAL & BOOTCAMP",
        subtitle = current.first,
        onDismiss = onCompleteAndClose
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, PhosphorGreen, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = current.second,
                    style = MaterialTheme.typography.titleMedium,
                    color = MysteryGold,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(color = PhosphorGreen.copy(alpha = 0.25f))
                current.third.forEach { bullet ->
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextCrisp
                    )
                }
            }
        }

        // Step Progress Pips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.indices.forEach { idx ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(if (idx == stepIndex) 32.dp else 12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (idx == stepIndex) PhosphorGreen else TextMuted.copy(alpha = 0.4f))
                        .clickable { stepIndex = idx }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = { if (stepIndex > 0) stepIndex-- else onCompleteAndClose() },
                modifier = Modifier.testTag("field_manual_prev_button")
            ) {
                Text(if (stepIndex > 0) "PREVIOUS" else "SKIP")
            }

            if (stepIndex < steps.lastIndex) {
                Button(
                    onClick = { stepIndex++ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CryoCyan,
                        contentColor = HandheldObsidian
                    ),
                    modifier = Modifier.testTag("field_manual_next_button")
                ) {
                    Text("NEXT BRIEFING ->", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onCompleteAndClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PhosphorGreen,
                        contentColor = HandheldObsidian
                    ),
                    modifier = Modifier.testTag("field_manual_complete_button")
                ) {
                    Text("DEPLOY TO COMBAT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CareerDossierAndDirectivesDialog(
    careerDossier: CareerDossier,
    dailyDirectives: List<DailyDirective>,
    onClaimDirective: (String) -> Unit,
    onDismiss: () -> Unit
) {
    EmulatorModalContainer(
        title = "CAREER DOSSIER & DAILY SURVIVAL DIRECTIVES",
        subtitle = "Lifetime Combat Record | Rank: ${careerDossier.careerRankTitle}",
        onDismiss = onDismiss
    ) {
        // Daily Directives Section
        Text(
            text = "ACTIVE DAILY SURVIVAL DIRECTIVES (UTC ROTATION)",
            style = MaterialTheme.typography.labelLarge,
            color = MysteryGold,
            fontWeight = FontWeight.Bold
        )

        dailyDirectives.forEach { directive ->
            Card(
                colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = when {
                            directive.isClaimed -> PhosphorGreen.copy(alpha = 0.4f)
                            directive.isCompleted -> MysteryGold
                            else -> CryoCyan.copy(alpha = 0.5f)
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
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
                            text = directive.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (directive.isCompleted) MysteryGold else PhosphorGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "+${directive.rewardPoints} PTS | +${directive.rewardScrap} SCRAP",
                            style = MaterialTheme.typography.labelSmall,
                            color = CryoCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = directive.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextCrisp
                    )
                    LinearProgressIndicator(
                        progress = { directive.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (directive.isCompleted) MysteryGold else PhosphorGreen,
                        trackColor = HandheldChassis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Progress: ${directive.currentProgress} / ${directive.targetCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        when {
                            directive.isClaimed -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Claimed",
                                        tint = PhosphorGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "REWARD CLAIMED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PhosphorGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            directive.isCompleted -> {
                                Button(
                                    onClick = { onClaimDirective(directive.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MysteryGold,
                                        contentColor = HandheldObsidian
                                    ),
                                    modifier = Modifier.testTag("claim_directive_${directive.id}")
                                ) {
                                    Text("CLAIM REWARD", fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {
                                Text(
                                    text = "IN PROGRESS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CryoCyan
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = PhosphorGreen.copy(alpha = 0.25f))

        Text(
            text = "LIFETIME SURVIVAL METRICS",
            style = MaterialTheme.typography.labelLarge,
            color = PhosphorGreen,
            fontWeight = FontWeight.Bold
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            DossierMetricTile(
                modifier = Modifier.weight(1f),
                label = "HIGHEST WAVE",
                value = "WAVE ${careerDossier.highestWave}",
                accent = ZombieCrimson
            )
            DossierMetricTile(
                modifier = Modifier.weight(1f),
                label = "ZOMBIES PURGED",
                value = "${careerDossier.totalKills}",
                accent = PhosphorGreen
            )
            DossierMetricTile(
                modifier = Modifier.weight(1f),
                label = "BOSS ANOMALIES",
                value = "${careerDossier.bossKills}",
                accent = MysteryGold
            )
            DossierMetricTile(
                modifier = Modifier.weight(1f),
                label = "TRAPS & BARRELS",
                value = "${careerDossier.fortificationsDeployed}",
                accent = CryoCyan
            )
            DossierMetricTile(
                modifier = Modifier.weight(1f),
                label = "PACK-A-PUNCH / ANVIL",
                value = "${careerDossier.packAPunchUpgrades}",
                accent = PackPurple
            )
            DossierMetricTile(
                modifier = Modifier.weight(1f),
                label = "CONTRACTS DONE",
                value = "${careerDossier.completedDirectivesCount}",
                accent = MysteryGold
            )
        }
    }
}

@Composable
private fun DossierMetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Card(
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StoreSettingsPrivacyAndAccountDialog(
    storeSettings: AppStoreSettingsState,
    isMusicMuted: Boolean,
    highContrastMode: Boolean,
    isCloudAuthenticated: Boolean,
    currentUserId: String,
    deletionStatus: String?,
    onToggleMusicMute: () -> Unit,
    onUpdateStoreSettings: ((AppStoreSettingsState) -> AppStoreSettingsState) -> Unit,
    onToggleHighContrast: () -> Unit,
    onTestSfxAndHaptics: (CombatSfxEvent) -> Unit,
    onOpenKonsoleDebugger: () -> Unit,
    onOpenFieldManual: () -> Unit,
    onDeleteAccountAndData: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmAccountDeleteStep by remember { mutableStateOf(false) }

    EmulatorModalContainer(
        title = "SETTINGS, AUDIO, PRIVACY & ACCOUNT",
        subtitle = "Procedural PCM SFX | Tactile Haptics | Play Store Data Safety & Account Controls",
        onDismiss = onDismiss
    ) {
        // 1. Audio & Tactile Haptics Section
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PhosphorGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "AUDIO & TACTILE HAPTICS ENGINE",
                    style = MaterialTheme.typography.labelLarge,
                    color = PhosphorGreen,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Retro Synth Soundtrack (BGM)", style = MaterialTheme.typography.bodyMedium, color = TextCrisp)
                        Text("Hardware-accelerated looping synthwave track", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Switch(
                        checked = !isMusicMuted,
                        onCheckedChange = { onToggleMusicMute() },
                        modifier = Modifier.testTag("settings_bgm_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Procedural 16-Bit Combat SFX", style = MaterialTheme.typography.bodyMedium, color = TextCrisp)
                        Text("Synthesized gunfire, cryo shatters, barrels & Pack-a-Punch", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Switch(
                        checked = storeSettings.sfxEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateStoreSettings { it.copy(sfxEnabled = enabled) }
                        },
                        modifier = Modifier.testTag("settings_sfx_switch")
                    )
                }

                if (storeSettings.sfxEnabled) {
                    Text(
                        text = "Combat SFX Volume: ${(storeSettings.sfxVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = CryoCyan
                    )
                    Slider(
                        value = storeSettings.sfxVolume,
                        onValueChange = { vol ->
                            onUpdateStoreSettings { it.copy(sfxVolume = vol) }
                        },
                        valueRange = 0.05f..1.0f
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tactile Combat Haptics", style = MaterialTheme.typography.bodyMedium, color = TextCrisp)
                        Text("Calibrated VibrationEffect pulses for recoil & explosions", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Switch(
                        checked = storeSettings.hapticsEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateStoreSettings { it.copy(hapticsEnabled = enabled) }
                        },
                        modifier = Modifier.testTag("settings_haptics_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onTestSfxAndHaptics(CombatSfxEvent.WEAPON_FIRE_PAP) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_sfx_pap_button")
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("TEST LASER SFX")
                    }
                    OutlinedButton(
                        onClick = { onTestSfxAndHaptics(CombatSfxEvent.BARREL_EXPLOSION) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_sfx_boom_button")
                    ) {
                        Text("TEST BOOM + VIBE")
                    }
                }
            }
        }

        // 2. Accessibility & Pro Developer Telemetry Section
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CryoCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ACCESSIBILITY & DEVELOPER TELEMETRY",
                    style = MaterialTheme.typography.labelLarge,
                    color = CryoCyan,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WCAG AAA High-Contrast Mode", style = MaterialTheme.typography.bodyMedium, color = TextCrisp)
                        Text("High-visibility palette, bold borders & zero scanline flicker", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Switch(
                        checked = highContrastMode,
                        onCheckedChange = { onToggleHighContrast() }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pro / Developer Mode (Konsole :48900)", style = MaterialTheme.typography.bodyMedium, color = MysteryGold)
                        Text("Show Headless TCP CLI Debugger, Stress Bot & raw port telemetry", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Switch(
                        checked = storeSettings.developerModeEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateStoreSettings { it.copy(developerModeEnabled = enabled) }
                        },
                        modifier = Modifier.testTag("toggle_developer_mode_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenFieldManual,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("FIELD MANUAL")
                    }
                    if (storeSettings.developerModeEnabled) {
                        Button(
                            onClick = onOpenKonsoleDebugger,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MysteryGold,
                                contentColor = HandheldObsidian
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_konsole_from_settings_button")
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("OPEN KONSOLE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Google Play Data Safety & Privacy Policy Inspector
        PrivacyDataSafetyCardContent()

        // 4. Google Play Account & Cloud Data Deletion
        Card(
            colors = CardDefaults.cardColors(containerColor = ZombieCrimson.copy(alpha = 0.14f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ZombieCrimson, RoundedCornerShape(10.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = ZombieCrimson)
                    Text(
                        text = "ACCOUNT & CLOUD DATA DELETION (PLAY POLICY)",
                        style = MaterialTheme.typography.labelLarge,
                        color = ZombieCrimson,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isCloudAuthenticated) {
                        "Signed in (${currentUserId.take(10)}...). Deleting your account permanently purges all Firestore cloud saves, controller profiles, hosted room records, local encrypted saves, and signs you out."
                    } else {
                        "Playing in 100% Offline Mode. Purging data permanently wipes all local AES-256-GCM save slots, Career Dossier stats, and controller profiles on this device."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextCrisp
                )

                if (!deletionStatus.isNullOrBlank()) {
                    Text(
                        text = deletionStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = MysteryGold,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!confirmAccountDeleteStep) {
                    OutlinedButton(
                        onClick = { confirmAccountDeleteStep = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ZombieCrimson, RoundedCornerShape(8.dp))
                            .testTag("delete_account_data_button")
                    ) {
                        Text(
                            text = if (isCloudAuthenticated) "DELETE ACCOUNT & PURGE CLOUD/LOCAL DATA" else "PURGE ALL LOCAL SAVES & CAREER DATA",
                            color = ZombieCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { confirmAccountDeleteStep = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL")
                        }
                        Button(
                            onClick = {
                                confirmAccountDeleteStep = false
                                onDeleteAccountAndData()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZombieCrimson,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("confirm_permanent_wipe_button")
                        ) {
                            Text("CONFIRM PERMANENT WIPE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyDataSafetyCardContent() {
    Card(
        colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PhosphorGreen.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = PhosphorGreen, modifier = Modifier.size(18.dp))
                Text(
                    text = "PRIVACY POLICY & PLAY STORE DATA SAFETY",
                    style = MaterialTheme.typography.labelLarge,
                    color = PhosphorGreen,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "| Local Encryption: Offline save slots and P2P capsules are encrypted on-device with AES-256-GCM (PBKDF2-HMAC-SHA256).",
                style = MaterialTheme.typography.bodySmall,
                color = TextCrisp
            )
            Text(
                text = "| Optional Cloud Sync: Google Sign-In is 100% optional. When signed in, only your game save slots, controller profiles, and active co-op room state are stored in Firebase Firestore under your authenticated UID.",
                style = MaterialTheme.typography.bodySmall,
                color = TextCrisp
            )
            Text(
                text = "| P2P & Private Mesh Detection: Network interface checks (Wi-Fi/VPN/Tailscale) run strictly locally to enable low-latency co-op discovery. No browsing history or personal traffic is ever inspected or transmitted.",
                style = MaterialTheme.typography.bodySmall,
                color = TextCrisp
            )
            Text(
                text = "| Zero Ad Tracking: BoxBoy Zombies contains zero advertising SDKs, zero cross-app trackers, and never sells user data.",
                style = MaterialTheme.typography.bodySmall,
                color = MysteryGold
            )
        }
    }
}

@Composable
fun PrivacyAndDataSafetyModal(
    onDismiss: () -> Unit
) {
    EmulatorModalContainer(
        title = "PRIVACY POLICY & GOOGLE PLAY DATA SAFETY",
        subtitle = "Transparent Data Handling, Encryption & Zero-Tracker Guarantee",
        onDismiss = onDismiss
    ) {
        PrivacyDataSafetyCardContent()
    }
}
