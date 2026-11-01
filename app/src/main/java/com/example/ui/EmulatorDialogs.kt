package com.example.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ControllerProfile
import com.example.data.CryptoSyncEngine
import com.example.data.DEFAULT_WEAPONS
import com.example.data.DriveConflictStatus
import com.example.data.DriveEnvelopeFileKind
import com.example.data.DriveSyncManager
import com.example.data.DriveUploadRetentionPolicy
import com.example.data.GoogleDriveSaveFile
import com.example.data.GoogleDriveSyncUiState
import com.example.data.LfsHostMode
import com.example.data.LfsLifecyclePolicy
import com.example.data.LfsServerEndpointConfig
import com.example.data.FriendlyGuideCardState
import com.example.data.OasisBotPersona
import com.example.data.OasisImagineeringTranslator
import com.example.data.SaveSlot
import com.example.data.SyncTransportMode
import com.example.data.TrashArcadeSharedRelic
import com.example.game.GameSessionState
import com.example.game.SpeedConfig
import com.example.game.ZombieArchetype
import com.example.input.ControllerManager
import com.example.input.GamepadAction
import com.example.input.GamepadMapping
import com.example.input.TouchButtonId
import com.example.network.CoopPeerState
import com.example.network.HeadlessKonsoleServer
import com.example.network.KONSOLE_TCP_PORT
import com.example.network.NetworkHealthSnapshot
import com.example.network.OasisHostedServerClient
import com.example.network.OasisSessionStatus
import com.example.network.P2pSessionStatus
import com.example.ui.theme.CryoCyan
import com.example.ui.theme.HandheldChassis
import com.example.ui.theme.HandheldObsidian
import com.example.ui.theme.HighContrastBlack
import com.example.ui.theme.HighContrastCyan
import com.example.ui.theme.HighContrastWhite
import com.example.ui.theme.HighContrastYellow
import com.example.ui.theme.LocalAccessibilityTheme
import com.example.ui.theme.MysteryGold
import com.example.ui.theme.PackPurple
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.ShareTechMonoFamily
import com.example.ui.theme.ZombieCrimson
import com.example.viewmodel.AiDirectorUiState
import com.example.viewmodel.AiDirectiveResult
import com.example.viewmodel.ControllerConfigState
import com.example.viewmodel.DisplayScreenMode
import com.example.viewmodel.OverlayPreset
import com.example.viewmodel.UiState

enum class ActiveEmulatorSheet {
    NONE,
    TITLE_HUB,
    CAREER_DOSSIER,
    FIELD_MANUAL,
    STORE_SETTINGS_PRIVACY,
    SAVE_SLOTS,
    CONTROLLER_MAPPER,
    SPEED_CONTROLLER,
    P2P_MULTIPLAYER_SYNC,
    WEAPON_ARMORY,
    AI_TACTICAL_DIRECTOR,
    KONSOLE_DEBUGGER,
    ABYSSAL_RPG_ANVIL
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedControllerDialog(
    speedConfig: SpeedConfig,
    onUpdateSpeed: ((SpeedConfig) -> SpeedConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(
        SpeedConfig("Chill Tactical", clockMultiplier = 0.85f, playerMoveMultiplier = 1.15f, zombieSpeedMultiplier = 0.70f, fireReloadMultiplier = 1.15f),
        SpeedConfig("Classic 1.0x", clockMultiplier = 1.0f, playerMoveMultiplier = 1.0f, zombieSpeedMultiplier = 1.0f, fireReloadMultiplier = 1.0f),
        SpeedConfig("Arcade Run-N-Gun", clockMultiplier = 1.0f, playerMoveMultiplier = 1.45f, zombieSpeedMultiplier = 1.10f, fireReloadMultiplier = 1.40f),
        SpeedConfig("MyBoy! 1.5x Fast", clockMultiplier = 1.5f, playerMoveMultiplier = 1.0f, zombieSpeedMultiplier = 1.0f, fireReloadMultiplier = 1.0f),
        SpeedConfig("MyBoy! 2.0x Hyper", clockMultiplier = 2.0f, playerMoveMultiplier = 1.0f, zombieSpeedMultiplier = 1.0f, fireReloadMultiplier = 1.0f),
        SpeedConfig("Nightmare Horde", clockMultiplier = 1.15f, playerMoveMultiplier = 1.25f, zombieSpeedMultiplier = 1.65f, fireReloadMultiplier = 1.35f)
    )

    EmulatorModalContainer(
        title = "DUAL-AXIS GAMEPLAY & MECHANICS SPEED CONTROLLER",
        subtitle = "Mix overall Emulator Clock Speed (scales every mechanic) with individual Move, Zombie & Combat multipliers",
        onDismiss = onDismiss
    ) {
        Text("1-TAP SPEED PRESETS", style = MaterialTheme.typography.labelLarge, color = MysteryGold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                FilterChip(
                    selected = speedConfig.presetName == preset.presetName,
                    onClick = { onUpdateSpeed { preset } },
                    label = { Text(preset.presetName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PhosphorGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "1. OVERALL EMULATOR CLOCK SPEED: ${"%.2f".format(speedConfig.clockMultiplier)}x",
                    style = MaterialTheme.typography.titleMedium,
                    color = PhosphorGreen
                )
                Text(
                    "Accelerates or slows down the entire game simulation clock (zombies, bullets, reloads, wave timers, particles).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = speedConfig.clockMultiplier,
                    onValueChange = { v ->
                        onUpdateSpeed { it.copy(presetName = "Custom", clockMultiplier = (v * 20).toInt() / 20f) }
                    },
                    valueRange = 0.5f..2.5f,
                    modifier = Modifier.testTag("clock_speed_slider")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CryoCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "2. INDIVIDUAL MECHANIC MULTIPLIERS",
                    style = MaterialTheme.typography.titleMedium,
                    color = CryoCyan
                )
                Text(
                    "Tune player mobility, zombie sprint speed, and weapon handling independently of the global clock:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Player Movement Speed: ${"%.2f".format(speedConfig.playerMoveMultiplier)}x", style = MaterialTheme.typography.labelLarge, color = PhosphorGreen)
                Slider(
                    value = speedConfig.playerMoveMultiplier,
                    onValueChange = { v ->
                        onUpdateSpeed { it.copy(presetName = "Custom", playerMoveMultiplier = (v * 20).toInt() / 20f) }
                    },
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.testTag("player_move_speed_slider")
                )

                Text("Zombie Horde Speed: ${"%.2f".format(speedConfig.zombieSpeedMultiplier)}x", style = MaterialTheme.typography.labelLarge, color = ZombieCrimson)
                Slider(
                    value = speedConfig.zombieSpeedMultiplier,
                    onValueChange = { v ->
                        onUpdateSpeed { it.copy(presetName = "Custom", zombieSpeedMultiplier = (v * 20).toInt() / 20f) }
                    },
                    valueRange = 0.4f..2.0f,
                    modifier = Modifier.testTag("zombie_speed_slider")
                )

                Text("Weapon Fire Rate & Reload Speed: ${"%.2f".format(speedConfig.fireReloadMultiplier)}x", style = MaterialTheme.typography.labelLarge, color = MysteryGold)
                Slider(
                    value = speedConfig.fireReloadMultiplier,
                    onValueChange = { v ->
                        onUpdateSpeed { it.copy(presetName = "Custom", fireReloadMultiplier = (v * 20).toInt() / 20f) }
                    },
                    valueRange = 0.5f..2.5f,
                    modifier = Modifier.testTag("fire_reload_speed_slider")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveSlotsDialog(
    gameState: GameSessionState,
    isCloudAuthenticated: Boolean,
    offlineSlots: List<CryptoSyncEngine.GameSaveSnapshot>,
    cloudState: UiState<List<SaveSlot>>,
    driveSyncState: GoogleDriveSyncUiState = GoogleDriveSyncUiState(),
    onSaveNewSlot: (String) -> Unit,
    onSyncToGoogleDrive: (String) -> Unit = onSaveNewSlot,
    onRefreshGoogleDrive: () -> Unit = {},
    onReconcileGoogleDrive: () -> Unit = onRefreshGoogleDrive,
    onSelectDriveRetentionPolicy: (DriveUploadRetentionPolicy) -> Unit = {},
    onLoadGoogleDriveSlot: (GoogleDriveSaveFile) -> Unit = {},
    onDeleteGoogleDriveSlot: (String) -> Unit = {},
    onLoadOfflineSlot: (CryptoSyncEngine.GameSaveSnapshot) -> Unit,
    onLoadCloudSlot: (SaveSlot) -> Unit,
    onDeleteCloudSlot: (String) -> Unit,
    onRequestGoogleSignIn: () -> Unit,
    onDismiss: () -> Unit
) {
    var slotTitle by remember { mutableStateOf("Chronicle - Wave ${gameState.wave} (${gameState.currentWeapon.shortCode})") }

    EmulatorModalContainer(
        title = "TIME-MACHINE MEMORY VAULTS | GOOGLE DRIVE & CLOUD SAVE",
        subtitle = "${OasisImagineeringTranslator.AEGIS_SAVE_VAULTS_LABEL} (aegisEnvelopeV1) | Sync SHA-256 verified JSON save envelopes to ${DriveSyncManager.TARGET_FOLDER_PATH}",
        onDismiss = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = slotTitle,
                onValueChange = { slotTitle = it },
                label = { Text("Chronicle Slot Title") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_slot_input")
            )
            Button(
                onClick = { onSaveNewSlot(slotTitle) },
                colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian),
                modifier = Modifier.testTag("save_state_confirm_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save Chronicle")
                Spacer(modifier = Modifier.width(6.dp))
                Text("SAVE ALL", fontWeight = FontWeight.Bold)
            }
        }

        // Google Drive BoxBoyZombies/Saves Vault Section
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, MysteryGold.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
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
                            text = "GOOGLE DRIVE | ${driveSyncState.targetFolderPath} (${driveSyncState.driveFiles.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MysteryGold
                        )
                        Text(
                            text = driveSyncState.statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = CryoCyan
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onReconcileGoogleDrive,
                            modifier = Modifier.testTag("reconcile_google_drive_button")
                        ) {
                            Text("NEWEST-WINS SYNC")
                        }
                        OutlinedButton(
                            onClick = onRefreshGoogleDrive,
                            modifier = Modifier.testTag("refresh_google_drive_button")
                        ) {
                            Text("REFRESH")
                        }
                        Button(
                            onClick = { onSyncToGoogleDrive(slotTitle) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MysteryGold,
                                contentColor = HandheldObsidian
                            ),
                            modifier = Modifier.testTag("save_google_drive_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DRIVE UPLOAD")
                        }
                    }
                }

                // Multi-Tier Retention Policy Selector ("All" modes supported)
                Text(
                    text = "Upload Retention Mode (${driveSyncState.retentionPolicy.subtitle}):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DriveUploadRetentionPolicy.entries.forEach { policy ->
                        FilterChip(
                            selected = driveSyncState.retentionPolicy == policy,
                            onClick = { onSelectDriveRetentionPolicy(policy) },
                            label = { Text(policy.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("drive_policy_${policy.name.lowercase()}")
                        )
                    }
                }

                if (driveSyncState.driveFiles.isEmpty()) {
                    Text(
                        text = "No JSON save envelopes in ${driveSyncState.targetFolderPath} yet. Tap DRIVE UPLOAD or SAVE ALL to write primary, rolling .bak, and archive envelopes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    driveSyncState.driveFiles.forEach { dFile ->
                        val kindColor = when (dFile.fileKind) {
                            DriveEnvelopeFileKind.PRIMARY_SLOT -> MysteryGold
                            DriveEnvelopeFileKind.ROLLING_BACKUP -> CryoCyan
                            DriveEnvelopeFileKind.TIMESTAMPED_ARCHIVE -> PhosphorGreen
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = HandheldChassis),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, kindColor.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "[${dFile.fileKind.shortBadge}]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = kindColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = dFile.slotName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MysteryGold
                                        )
                                    }
                                    Text(
                                        "Wave ${dFile.highestWave} | ${dFile.totalKills} Kills | ${dFile.points} PTS | ${dFile.loadoutSummary}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    val checksumShort = dFile.sha256Checksum.take(8).ifBlank { "verified" }
                                    Text(
                                        text = "${dFile.folderPath}/${dFile.fileName} | ${dFile.conflictStatus.badgeText} | SHA-256: $checksumShort",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (dFile.checksumVerified) PhosphorGreen else ZombieCrimson
                                    )
                                }
                                Button(
                                    onClick = {
                                        onLoadGoogleDriveSlot(dFile)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian),
                                    modifier = Modifier.testTag("load_drive_${dFile.driveFileId}")
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("RESTORE")
                                }
                                IconButton(onClick = { onDeleteGoogleDriveSlot(dFile.driveFileId) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Google Drive Save Envelope", tint = ZombieCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("LOCAL ENCRYPTED CHRONICLES (${offlineSlots.size})", style = MaterialTheme.typography.labelLarge, color = PhosphorGreen)
        if (offlineSlots.isEmpty()) {
            Text(
                "No local offline states saved yet. Tap SAVE above to write an instant encrypted state on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            offlineSlots.forEach { snap ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, PhosphorGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(snap.slotName, style = MaterialTheme.typography.titleMedium, color = PhosphorGreen)
                            Text(
                                "Wave ${snap.highestWave} | ${snap.totalKills} Kills | ${snap.points} PTS | ${snap.unlockedRoomsCount}/6 Rooms",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(
                            onClick = {
                                onLoadOfflineSlot(snap)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MysteryGold, contentColor = HandheldObsidian)
                        ) {
                            Text("LOAD")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = PhosphorGreen.copy(alpha = 0.25f))
        Spacer(modifier = Modifier.height(12.dp))
        Text("CLOUD FIRESTORE SYNCED SLOTS", style = MaterialTheme.typography.labelLarge, color = CryoCyan)

        if (!isCloudAuthenticated) {
            Card(
                colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CryoCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Playing in 100% Offline Cartridge Mode", style = MaterialTheme.typography.titleMedium, color = CryoCyan)
                        Text(
                            "Your local saves & P2P Wi-Fi/USB/Bluetooth sync work without any account. Sign in with Google anytime if you also want Cloud Firestore backup.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onRequestGoogleSignIn) {
                        Text("SIGN IN")
                    }
                }
            }
        } else {
            when (cloudState) {
                is UiState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Syncing cloud save slots...")
                    }
                }
                is UiState.Error -> {
                    Text(cloudState.message, color = ZombieCrimson, style = MaterialTheme.typography.bodyMedium)
                }
                is UiState.Success -> {
                    if (cloudState.data.isEmpty()) {
                        Text(
                            "No cloud save slots yet. Saving a state automatically backs it up to Firestore.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        cloudState.data.forEach { slot ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(1.dp, CryoCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(slot.slotName, style = MaterialTheme.typography.titleMedium, color = CryoCyan)
                                        Text(
                                            "Wave ${slot.highestWave} | ${slot.totalKills} Kills | ${slot.points} PTS",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "Loadout: ${slot.loadoutSummary}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            onLoadCloudSlot(slot)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CryoCyan, contentColor = HandheldObsidian)
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = "Load Cloud Slot", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("LOAD")
                                    }
                                    IconButton(onClick = { onDeleteCloudSlot(slot.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Cloud Slot", tint = ZombieCrimson)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControllerMapperDialog(
    config: ControllerConfigState,
    gamepadMapping: GamepadMapping,
    cloudProfiles: UiState<List<ControllerProfile>>,
    onUpdateConfig: ((ControllerConfigState) -> ControllerConfigState) -> Unit,
    onBindTouchButton: (TouchButtonId, GamepadAction) -> Unit,
    onApplyMappingPreset: (GamepadMapping) -> Unit,
    onUpdateDeadzone: (Float) -> Unit,
    onSaveProfileToCloud: (String) -> Unit,
    onApplyCloudProfile: (ControllerProfile) -> Unit,
    onDeleteCloudProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var profileName by remember { mutableStateOf(config.profileName) }
    var selectedTouchButtonForBind by remember { mutableStateOf<TouchButtonId?>(null) }

    EmulatorModalContainer(
        title = "MYBOY! MAPPABLE CONTROLS & BIG SCREEN / TV CAST",
        subtitle = "Configure Touch Button-to-Action mappings (GamepadMapping), TV / Linux cast view & hardware keycodes",
        onDismiss = onDismiss
    ) {
        // 1. Touch Button-to-Action Mapping Section (GamepadMapping & ControllerManager)
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, PhosphorGreen.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "TOUCH CONTROL BUTTON-TO-ACTION MAPPER (ControllerManager)",
                    style = MaterialTheme.typography.titleMedium,
                    color = PhosphorGreen
                )
                Text(
                    "Active Mapping: ${gamepadMapping.mappingName} | Tap any touch button below to assign a custom gameplay action:",
                    style = MaterialTheme.typography.bodyMedium
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControllerManager.ALL_PRESETS.forEach { presetMap ->
                        FilterChip(
                            selected = gamepadMapping.mappingName == presetMap.mappingName,
                            onClick = { onApplyMappingPreset(presetMap) },
                            label = { Text(presetMap.mappingName) }
                        )
                    }
                }

                TouchButtonId.entries.forEach { btnId ->
                    val currentAction = gamepadMapping.actionForTouchButton(btnId)
                    val isSelecting = selectedTouchButtonForBind == btnId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${btnId.displayName} [${gamepadMapping.labelForTouchButton(btnId)}]",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelecting) MysteryGold else Color.White
                        )
                        OutlinedButton(
                            onClick = {
                                selectedTouchButtonForBind = if (isSelecting) null else btnId
                            },
                            modifier = Modifier.testTag("remap_touch_${btnId.name.lowercase()}")
                        ) {
                            Text(if (isSelecting) "PICK ACTION BELOW..." else currentAction.displayName)
                        }
                    }
                }

                if (selectedTouchButtonForBind != null) {
                    val targetBtn = selectedTouchButtonForBind!!
                    Text(
                        "SELECT ACTION FOR [${targetBtn.displayName}]:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MysteryGold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GamepadAction.entries.forEach { act ->
                            Button(
                                onClick = {
                                    onBindTouchButton(targetBtn, act)
                                    selectedTouchButtonForBind = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (gamepadMapping.actionForTouchButton(targetBtn) == act) MysteryGold else HandheldChassis,
                                    contentColor = if (gamepadMapping.actionForTouchButton(targetBtn) == act) HandheldObsidian else CryoCyan
                                )
                            ) {
                                Text("${act.hudBadge} (${act.displayName})")
                            }
                        }
                    }
                }

                Text(
                    "D-PAD RADIAL DEADZONE: ${(gamepadMapping.dpadDeadzone * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = CryoCyan
                )
                Slider(
                    value = gamepadMapping.dpadDeadzone,
                    onValueChange = onUpdateDeadzone,
                    valueRange = 0.04f..0.35f,
                    modifier = Modifier.testTag("dpad_deadzone_slider")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("SCREEN & EXTERNAL DISPLAY MODE (TV CAST / LINUX / MONITOR)", style = MaterialTheme.typography.labelLarge, color = CryoCyan)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DisplayScreenMode.entries.forEach { mode ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (config.displayMode == mode) HandheldObsidian else HandheldChassis
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (config.displayMode == mode) 2.dp else 1.dp,
                            color = if (config.displayMode == mode) CryoCyan else PhosphorGreen.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.label, style = MaterialTheme.typography.titleMedium, color = CryoCyan)
                            Text(mode.subtitle, style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(
                            onClick = { onUpdateConfig { it.copy(displayMode = mode) } },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (config.displayMode == mode) CryoCyan else HandheldObsidian,
                                contentColor = if (config.displayMode == mode) HandheldObsidian else CryoCyan
                            )
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = mode.label, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (config.displayMode == mode) "ACTIVE" else "SELECT")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("CAMERA FIELD OF VIEW / ZOOM: ${config.cameraZoomPercent}% (Lower = Wider Arena View for TV/Monitors)", style = MaterialTheme.typography.labelLarge, color = MysteryGold)
        Slider(
            value = config.cameraZoomPercent.toFloat(),
            onValueChange = { v -> onUpdateConfig { it.copy(cameraZoomPercent = v.toInt()) } },
            valueRange = 65f..140f,
            modifier = Modifier.testTag("camera_zoom_slider")
        )

        Text("TOUCH OVERLAY LAYOUT PRESET", style = MaterialTheme.typography.labelLarge, color = MysteryGold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OverlayPreset.entries.forEach { preset ->
                FilterChip(
                    selected = config.preset == preset,
                    onClick = { onUpdateConfig { it.copy(preset = preset) } },
                    label = { Text(preset.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("D-PAD & BUTTON SCALE: ${config.dpadScalePercent}%", style = MaterialTheme.typography.labelLarge, color = PhosphorGreen)
        Slider(
            value = config.dpadScalePercent.toFloat(),
            onValueChange = { v -> onUpdateConfig { it.copy(dpadScalePercent = v.toInt()) } },
            valueRange = 60f..160f,
            modifier = Modifier.testTag("dpad_scale_slider")
        )

        Text("GAMEPAD OVERLAY OPACITY: ${config.buttonOpacityPercent}%", style = MaterialTheme.typography.labelLarge, color = PhosphorGreen)
        Slider(
            value = config.buttonOpacityPercent.toFloat(),
            onValueChange = { v -> onUpdateConfig { it.copy(buttonOpacityPercent = v.toInt()) } },
            valueRange = 15f..100f,
            modifier = Modifier.testTag("overlay_opacity_slider")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CRT Scanline & Dot-Matrix Shader", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = config.crtScanlinesEnabled,
                onCheckedChange = { checked -> onUpdateConfig { it.copy(crtScanlinesEnabled = checked) } }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("A-Button Auto-Turbo Fire", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = config.turboFireEnabled,
                onCheckedChange = { checked -> onUpdateConfig { it.copy(turboFireEnabled = checked) } }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (config.highContrastMode) HighContrastBlack else HandheldObsidian
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (config.highContrastMode) 2.5.dp else 1.5.dp,
                    color = if (config.highContrastMode) HighContrastYellow else MysteryGold,
                    shape = RoundedCornerShape(12.dp)
                )
                .testTag("accessibility_settings_card")
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
                            "HIGH CONTRAST & ACCESSIBILITY MODE (WCAG AAA)",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (config.highContrastMode) HighContrastYellow else MysteryGold
                        )
                        Text(
                            "21:1 luminance contrast, 56dp touch targets, geometric colorblind symbols & reduced motion",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (config.highContrastMode) HighContrastWhite else CryoCyan
                        )
                    }
                    Button(
                        onClick = {
                            val enableAll = !(config.highContrastMode && config.largeTouchTargetsAndText)
                            onUpdateConfig {
                                it.copy(
                                    highContrastMode = enableAll,
                                    largeTouchTargetsAndText = enableAll,
                                    colorblindHazardMarkers = enableAll,
                                    reduceMotionAndFlashes = enableAll,
                                    crtScanlinesEnabled = !enableAll,
                                    dpadScalePercent = if (enableAll) 130 else 110,
                                    buttonOpacityPercent = if (enableAll) 100 else 82
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (config.highContrastMode) HighContrastYellow else MysteryGold,
                            contentColor = HighContrastBlack
                        ),
                        modifier = Modifier.testTag("a11y_full_suite_button")
                    ) {
                        Text(if (config.highContrastMode && config.largeTouchTargetsAndText) "RESET A11Y" else "FULL A11Y")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("High Contrast Mode (Pure Black / Yellow / White)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = config.highContrastMode,
                        onCheckedChange = { checked ->
                            onUpdateConfig {
                                it.copy(
                                    highContrastMode = checked,
                                    crtScanlinesEnabled = if (checked) false else it.crtScanlinesEnabled
                                )
                            }
                        },
                        modifier = Modifier.testTag("a11y_high_contrast_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Large Typography & >=56dp Touch Targets", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = config.largeTouchTargetsAndText,
                        onCheckedChange = { checked ->
                            onUpdateConfig {
                                it.copy(
                                    largeTouchTargetsAndText = checked,
                                    dpadScalePercent = if (checked && it.dpadScalePercent < 125) 125 else it.dpadScalePercent
                                )
                            }
                        },
                        modifier = Modifier.testTag("a11y_large_targets_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Colorblind Geometric Hazard Markers (\u25B2 \u25C6 \u2B21 \u271A)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = config.colorblindHazardMarkers,
                        onCheckedChange = { checked ->
                            onUpdateConfig { it.copy(colorblindHazardMarkers = checked) }
                        },
                        modifier = Modifier.testTag("a11y_colorblind_shapes_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reduce Motion, Screen Shake & Strobe Flashes", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = config.reduceMotionAndFlashes,
                        onCheckedChange = { checked ->
                            onUpdateConfig {
                                it.copy(
                                    reduceMotionAndFlashes = checked,
                                    crtScanlinesEnabled = if (checked) false else it.crtScanlinesEnabled
                                )
                            }
                        },
                        modifier = Modifier.testTag("a11y_reduce_motion_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = PhosphorGreen.copy(alpha = 0.25f))
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (config.awaitingKeyBindAction != null) {
                "PRESS ANY HARDWARE GAMEPAD OR KEYBOARD KEY FOR [${config.awaitingKeyBindAction}]..."
            } else {
                "MAPPABLE HARDWARE CONTROLLER & KEYBOARD TOOL (Tap action, then press any button)"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (config.awaitingKeyBindAction != null) ZombieCrimson else CryoCyan
        )

        val bindings = listOf(
            Triple("FIRE", "A / Fire Weapon (or Space)", config.keyFire),
            Triple("INTERACT", "B / Buy Door & Repair (or E)", config.keyInteract),
            Triple("BARREL", "X / Drop Red Barrel (or Q)", config.keyPlaceBarrel),
            Triple("RELOAD", "Y / Tactical Reload (or R)", config.keyReload),
            Triple("PREV", "L1 / Prev Weapon", config.keyPrevWeapon),
            Triple("NEXT", "R1 / Next Weapon", config.keyNextWeapon)
        )
        bindings.forEach { (code, label, keyCode) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = { onUpdateConfig { it.copy(awaitingKeyBindAction = code) } }
                ) {
                    Text(
                        if (config.awaitingKeyBindAction == code) "PRESS KEY NOW..."
                        else KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                label = { Text("Profile Name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onSaveProfileToCloud(profileName) },
                colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian),
                modifier = Modifier.testTag("save_controller_profile_button")
            ) {
                Icon(Icons.Default.Gamepad, contentDescription = "Save Profile")
                Spacer(modifier = Modifier.width(6.dp))
                Text("SAVE PROFILE")
            }
        }

        if (cloudProfiles is UiState.Success && cloudProfiles.data.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("SAVED CLOUD CONTROLLER PROFILES", style = MaterialTheme.typography.labelLarge, color = MysteryGold)
            cloudProfiles.data.forEach { prof ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${prof.profileName} (Scale ${prof.dpadScale}% | Opacity ${prof.buttonOpacity}%)", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { onApplyCloudProfile(prof) }) {
                        Text("APPLY")
                    }
                    IconButton(onClick = { onDeleteCloudProfile(prof.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Profile", tint = ZombieCrimson)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun P2pMultiplayerSyncDialog(
    p2pStatus: P2pSessionStatus,
    passphrase: String,
    exportedCapsule: String,
    onSelectTransport: (SyncTransportMode) -> Unit,
    onUpdatePassphrase: (String) -> Unit,
    onStartHost: () -> Unit,
    onConnectPeer: (String) -> Unit,
    onStopSession: () -> Unit,
    onSendTacticalMessage: (String) -> Unit,
    onRunLoopbackTest: () -> Unit,
    onPushSaveSocket: () -> Unit,
    onExportCapsule: () -> String,
    onImportCapsule: (String) -> Result<CryptoSyncEngine.GameSaveSnapshot>,
    onDismiss: () -> Unit,
    oasisStatus: OasisSessionStatus = OasisSessionStatus(),
    oasisEndpointConfig: LfsServerEndpointConfig = LfsServerEndpointConfig(),
    coopPeers: List<CoopPeerState> = emptyList(),
    networkHealth: NetworkHealthSnapshot = NetworkHealthSnapshot(manualPrivateOverride = true),
    onRefreshNetworkHealth: () -> Unit = {},
    onSetPrivateNetworkOverride: (Boolean) -> Unit = {},
    onUpdateCustomServerUrl: (String) -> Unit = {},
    onLaunchOrJoinLfsRoom: (String, LfsHostMode, LfsLifecyclePolicy) -> Unit = { _, _, _ -> },
    onMigrateHostMode: (LfsHostMode) -> Unit = {},
    onRefreshPublicRooms: () -> Unit = {},
    onDisconnectLfsRoom: () -> Unit = {},
    onToggleSquadBot: (OasisBotPersona) -> Unit = {},
    onFillSquadWithBots: () -> Unit = {},
    currentPoints: Int = 500,
    unlockedTrashArcadeRelics: Set<String> = emptySet(),
    onProbeTailscaleHandshake: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    var targetHostIp by remember {
        mutableStateOf(
            if (p2pStatus.transportMode == SyncTransportMode.USB_ADB) "127.0.0.1"
            else p2pStatus.discoveredPeers.firstOrNull() ?: "192.168.1.100"
        )
    }
    var roomCodeInput by remember(oasisStatus.roomCode) {
        mutableStateOf(
            oasisStatus.roomCode.ifBlank { OasisHostedServerClient.generateRoomCode() }
        )
    }
    var selectedHostMode by remember(oasisStatus.hostMode) { mutableStateOf(oasisStatus.hostMode) }
    var selectedLifecycle by remember(oasisStatus.lifecyclePolicy) { mutableStateOf(oasisStatus.lifecyclePolicy) }
    var customServerUrlInput by remember(oasisEndpointConfig.customServerUrl) {
        mutableStateOf(oasisEndpointConfig.customServerUrl)
    }
    var customPeerMessage by remember { mutableStateOf("COVER ME - RELOADING!") }
    var importText by remember { mutableStateOf("") }
    var syncFeedback by remember { mutableStateOf<String?>(null) }

    EmulatorModalContainer(
        title = "LFS OASIS HOSTED SERVER & P2P CO-OP COMMAND DECK",
        subtitle = "play.lewisfamilysystems.com | 3-Tier Failover | 4-Player Squad & AI Bots",
        onDismiss = onDismiss
    ) {
        // NETWORK HEALTH SERVICE: Tailscale & VPN Private Network Probe Card
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = if (networkHealth.isPrivateNetworkDetected) PhosphorGreen else MysteryGold.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp)
                )
                .testTag("network_health_banner")
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
                            text = if (networkHealth.isPrivateNetworkDetected) {
                                "NETWORK HEALTH: PRIVATE TAILSCALE / VPN DETECTED"
                            } else {
                                "NETWORK HEALTH: PUBLIC / STANDARD LAN"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (networkHealth.isPrivateNetworkDetected) PhosphorGreen else MysteryGold
                        )
                        Text(
                            text = networkHealth.statusHeadline,
                            style = MaterialTheme.typography.bodySmall,
                            color = CryoCyan
                        )
                    }
                    OutlinedButton(
                        onClick = onRefreshNetworkHealth,
                        modifier = Modifier.testTag("network_health_scan_button")
                    ) {
                        Text("SCAN VPN")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Private Network Override (Show LFS Oasis Server Card)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = networkHealth.manualPrivateOverride,
                        onCheckedChange = onSetPrivateNetworkOverride,
                        modifier = Modifier.testTag("network_health_override_switch")
                    )
                }
            }
        }

        // TRASHARCADE SATCHEL & TAILSCALE 4-PILLAR STATUS (CP-12 / lfs-local-abac-v1)
        val ssoState = oasisStatus.tailscaleHandshake
        val rupeeBreakdown = remember(currentPoints) {
            OasisHostedServerClient.decomposeCmyRupees(currentPoints)
        }
        val satchelTier = remember(currentPoints) {
            OasisHostedServerClient.computeSatchelTier(currentPoints)
        }
        val guideCard = remember(ssoState.abacDecision.reason, oasisStatus.isConnected, oasisStatus.roomCode) {
            OasisImagineeringTranslator.buildFriendlyGuideCard(
                rawReason = ssoState.abacDecision.reason,
                isOfflineFallback = !oasisStatus.isConnected,
                roomCode = oasisStatus.roomCode
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, CryoCyan.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                .testTag("trash_arcade_tailscale_status_card")
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
                            text = "${OasisImagineeringTranslator.HEARTH_KEY_LABEL} & ${OasisImagineeringTranslator.STARLIGHT_RUPEES_LABEL}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MysteryGold
                        )
                        Text(
                            text = "Hearth Key (Node.StableID): ${ssoState.stableNodeId} | Subuser: ${ssoState.subuserId} (${ssoState.persona}) | Ring ${ssoState.trustRing} | Epoch #${ssoState.generationEpoch}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CryoCyan
                        )
                        Text(
                            text = "${OasisImagineeringTranslator.STARLIGHT_QUIET_HOURS_LABEL} | ${OasisImagineeringTranslator.FAMILY_ARCADE_SUBTITLE}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PhosphorGreen
                        )
                    }
                    OutlinedButton(
                        onClick = onProbeTailscaleHandshake,
                        modifier = Modifier.testTag("probe_tailscale_sso_button")
                    ) {
                        Text("CHECK HEARTH KEY")
                    }
                }

                // 4 Live Pillar Checks with Machado CVD-Safe [PASS] / [WARN] Badges
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "tailscale_tunnel" to ssoState.tailscaleTunnelBadge,
                        "abac_perimeter" to ssoState.abacPerimeterBadge,
                        "isolated_profile" to ssoState.isolatedProfileBadge,
                        "user_data_backup" to ssoState.userDataBackupBadge
                    ).forEach { (pillar, badge) ->
                        val isPass = badge == "[PASS]"
                        Text(
                            text = "$pillar: $badge",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPass) PhosphorGreen else MysteryGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "32-State ABAC Lattice: bits=0x${ssoState.abacDecision.stateBits.toString(16).uppercase()} (${ssoState.abacDecision.reason}) | Endpoint: ${ssoState.lastProbedEndpoint}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ssoState.abacDecision.permit) PhosphorGreen else ZombieCrimson
                )

                HorizontalDivider(color = CryoCyan.copy(alpha = 0.25f))

                // Starlight Rupees & Wonder Relics Satchel Breakdown & Tier
                val rupeeSummary = if (rupeeBreakdown.isEmpty()) {
                    "0 RUP"
                } else {
                    rupeeBreakdown.joinToString(" + ") { (denom, count) -> "${count}x${denom}R" }
                }
                val totalSprites = rupeeBreakdown.sumOf { it.second }
                Text(
                    text = "STARLIGHT RUPEES SATCHEL [$satchelTier]: $currentPoints RUP -> $rupeeSummary ($totalSprites/24 static sprites)",
                    style = MaterialTheme.typography.labelSmall,
                    color = PhosphorGreen
                )

                // 4 Cross-Game lfs-local-abac-v1 Wonder Relics
                Text(
                    text = "WONDER RELICS (lfs-local-abac-v1):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MysteryGold
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    TrashArcadeSharedRelic.entries.forEach { relic ->
                        val isUnlocked = relic.relicId in unlockedTrashArcadeRelics
                        val badge = if (isUnlocked) "[UNLOCKED]" else "[LOCKED]"
                        Text(
                            text = "$badge ${relic.title} (${relic.relicId}) -> ${relic.crossGamePerk}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isUnlocked) PhosphorGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = CryoCyan.copy(alpha = 0.25f))

                // Friendly Guide Card (Grade <= 8.0 4-Step ZT-6 Replacement)
                FriendlyGuideCard(
                    guideState = guideCard,
                    onNextStepClicked = onProbeTailscaleHandshake
                )
            }
        }

        Text("1. SELECT MULTIPLAYER TRANSPORT TIER", style = MaterialTheme.typography.labelLarge, color = MysteryGold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SyncTransportMode.entries.forEach { mode ->
                FilterChip(
                    selected = p2pStatus.transportMode == mode,
                    onClick = {
                        onSelectTransport(mode)
                        if (mode == SyncTransportMode.USB_ADB) targetHostIp = "127.0.0.1"
                    },
                    label = { Text(mode.label) }
                )
            }
        }

        // CONDITIONALLY DISPLAYED HOSTED LFS SERVER DUAL-MODE COMMAND PANEL
        // Shown when NetworkHealthService detects an active Tailscale interface / VPN profile or manual override
        if (networkHealth.shouldShowOasisServerConfig) {
            Card(
                colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, PhosphorGreen.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .testTag("lfs_oasis_config_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "LFS OASIS DUAL-MODE SERVER HOSTER (U1/U2 :19448)",
                                style = MaterialTheme.typography.titleMedium,
                                color = PhosphorGreen
                            )
                            Text(
                                "Room: ${oasisStatus.roomCode} | Epoch: ${oasisStatus.hostEpoch} | Tier: ${oasisStatus.transportTier}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CryoCyan
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                roomCodeInput = OasisHostedServerClient.generateRoomCode()
                                syncFeedback = "Generated Crockford Base32 code: $roomCodeInput"
                            }
                        ) {
                            Text("NEW CODE")
                        }
                    }

                    Text(
                        oasisStatus.statusHeadline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MysteryGold
                    )

                    // Preset Endpoint Selector + Custom LFS Server Override
                    Text("LFS SERVER ENDPOINT (U1 / U2 / U3 / TAILSCALE):", style = MaterialTheme.typography.labelSmall, color = CryoCyan)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "play.lewisfamilysystems.com" to "",
                            "lfs-prod.tail420c4d.ts.net" to oasisEndpointConfig.tailscaleAnchorHost,
                            "Laputa (:19448)" to oasisEndpointConfig.tailscaleLaputaHosterUrl,
                            "Anchor SSO (:13379)" to oasisEndpointConfig.tailscaleAnchorSsoUrl,
                            "Local Hoster (:19448)" to "http://127.0.0.1:19448"
                        ).forEach { (label, url) ->
                            FilterChip(
                                selected = customServerUrlInput == url,
                                onClick = {
                                    customServerUrlInput = url
                                    onUpdateCustomServerUrl(url)
                                },
                                label = { Text(label) }
                            )
                        }
                        if (networkHealth.tailscaleIp != null) {
                            val tsUrl = "http://${networkHealth.tailscaleIp}:19448"
                            FilterChip(
                                selected = customServerUrlInput == tsUrl,
                                onClick = {
                                    customServerUrlInput = tsUrl
                                    onUpdateCustomServerUrl(tsUrl)
                                },
                                label = { Text("Tailscale (${networkHealth.tailscaleIp}:19448)") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customServerUrlInput,
                        onValueChange = {
                            customServerUrlInput = it
                            onUpdateCustomServerUrl(it)
                        },
                        label = { Text("Custom LFS Server URL / Tailscale IP (Optional Override)") },
                        placeholder = { Text("https://play.lewisfamilysystems.com or http://100.x.y.z:19448") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Dual-Mode Host Selector (HOST-01 vs HOST-02)
                    Text("DUAL-MODE AUTHORITY (HOST-01 / HOST-02):", style = MaterialTheme.typography.labelSmall, color = MysteryGold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LfsHostMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedHostMode == mode,
                                onClick = { selectedHostMode = mode },
                                label = { Text(mode.title) }
                            )
                        }
                    }

                    // Lifecycle Policy Selector (HOST-03)
                    Text("SERVER LIFECYCLE POLICY (HOST-03):", style = MaterialTheme.typography.labelSmall, color = CryoCyan)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LfsLifecyclePolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = selectedLifecycle == policy,
                                onClick = { selectedLifecycle = policy },
                                label = { Text(policy.wireId) }
                            )
                        }
                    }

                    // Room Code Input + Launch/Join + Live Migration (HOST-04)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = roomCodeInput,
                            onValueChange = { roomCodeInput = it.uppercase() },
                            label = { Text("LFS Room Code (LFS-XXXXXX)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(roomCodeInput))
                                syncFeedback = "Copied Room Code $roomCodeInput!"
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Room Code", tint = PhosphorGreen)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onLaunchOrJoinLfsRoom(roomCodeInput, selectedHostMode, selectedLifecycle)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Launch or Join LFS Room")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (oasisStatus.isConnected) "RE-SYNC LFS ROOM" else "LAUNCH / JOIN LFS ROOM")
                        }
                        if (oasisStatus.isConnected) {
                            OutlinedButton(
                                onClick = {
                                    val nextMode = if (oasisStatus.hostMode == LfsHostMode.LAUNCH_U2_HOSTING_SERVICE) {
                                        LfsHostMode.HOST_LOCAL_DEVICE
                                    } else {
                                        LfsHostMode.LAUNCH_U2_HOSTING_SERVICE
                                    }
                                    selectedHostMode = nextMode
                                    onMigrateHostMode(nextMode)
                                }
                            ) {
                                Text("MIGRATE (<300ms)")
                            }
                            OutlinedButton(onClick = onDisconnectLfsRoom) {
                                Text("LEAVE")
                            }
                        }
                    }

                    // Active Public LFS Rooms Browser
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "PUBLIC ROOMS ON SERVER (${oasisStatus.publicRooms.size}):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MysteryGold
                        )
                        OutlinedButton(onClick = onRefreshPublicRooms) {
                            Text("REFRESH ROOMS")
                        }
                    }
                    if (oasisStatus.publicRooms.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            oasisStatus.publicRooms.take(6).forEach { room ->
                                Button(
                                    onClick = {
                                        roomCodeInput = room.roomCode
                                        onLaunchOrJoinLfsRoom(room.roomCode, selectedHostMode, selectedLifecycle)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CryoCyan, contentColor = HandheldObsidian)
                                ) {
                                    Text("JOIN ${room.roomCode} (W${room.wave} | ${room.activePlayers}/${room.maxPlayers})")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MysteryGold.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .testTag("lfs_oasis_hidden_notice")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "LFS OASIS SERVER CONFIG HIDDEN (PUBLIC NETWORK)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MysteryGold
                        )
                        Text(
                            "Connect to Tailscale (100.x.y.z) or an active VPN profile, or toggle Private Network Override above to configure LFS Oasis Hoster.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CryoCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { onSetPrivateNetworkOverride(true) }) {
                        Text("UNLOCK")
                    }
                }
            }
        }

        // 4/4 CO-OP SQUAD ROSTER & AUTONOMOUS AI SQUAD BOTS (@willow, @cinder, @mote)
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, MysteryGold.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "4/4 SQUAD ROSTER & OASIS AI BOTS (${1 + coopPeers.size}/4 ACTIVE)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MysteryGold
                        )
                        Text(
                            "Bots auto-revive downed teammates, kite bosses, and call out tactical actions",
                            style = MaterialTheme.typography.bodySmall,
                            color = CryoCyan
                        )
                    }
                    Button(
                        onClick = onFillSquadWithBots,
                        colors = ButtonDefaults.buttonColors(containerColor = MysteryGold, contentColor = HandheldObsidian)
                    ) {
                        Text("FILL 4/4 SQUAD")
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OasisBotPersona.entries.forEach { persona ->
                        val isSpawned = coopPeers.any { it.isAiBot && it.botPersonaId == persona.id }
                        FilterChip(
                            selected = isSpawned,
                            onClick = { onToggleSquadBot(persona) },
                            label = {
                                Text(if (isSpawned) "${persona.displayName} [ACTIVE]" else "+ ${persona.displayName}")
                            }
                        )
                    }
                }

                if (coopPeers.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HandheldChassis, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        coopPeers.forEach { peer ->
                            val statusTag = if (peer.isDowned) "DOWNED (${peer.bleedoutRemainingMs / 1000}s)" else "HP ${peer.hp}/${peer.maxHp}"
                            Text(
                                text = "| ${peer.callsign} [${if (peer.isAiBot) "AI BOT" else "LIVE PEER"}] -- $statusTag -- ${peer.radioCalloutText}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (peer.isDowned) ZombieCrimson else PhosphorGreen
                            )
                        }
                    }
                }
            }
        }

        // Step-by-Step Visual Guide based on selected transport
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, CryoCyan.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (p2pStatus.transportMode) {
                    SyncTransportMode.LFS_HOSTED_SERVER -> {
                        Text("LFS DEDICATED SERVER (U1/U2) GUIDE", style = MaterialTheme.typography.titleMedium, color = PhosphorGreen)
                        Text("| STEP 1: Tap 'LAUNCH / JOIN LFS ROOM' above with any LFS-XXXXXX code.", style = MaterialTheme.typography.bodyMedium)
                        Text("| STEP 2: Share the 6-character code with up to 3 friends anywhere on Earth -- zero port forwarding needed!", style = MaterialTheme.typography.bodyMedium)
                    }
                    SyncTransportMode.LFS_LOCAL_HOST_TUNNEL -> {
                        Text("HOST ON DEVICE + LFS REVERSE TUNNEL", style = MaterialTheme.typography.titleMedium, color = CryoCyan)
                        Text("| Your phone runs the authoritative 60 FPS simulation and exposes a NAT-free reverse WebSocket tunnel through sync.lewisfamilysystems.com.", style = MaterialTheme.typography.bodyMedium)
                    }
                    SyncTransportMode.FIRESTORE_CLOUD_RELAY -> {
                        Text("CLOUD ROOM RELAY (FIRESTORE) GUIDE", style = MaterialTheme.typography.titleMedium, color = MysteryGold)
                        Text("| Automatic Tier-B internet room relay synchronized by LFS-XXXXXX room code when dedicated WebSocket ports are blocked.", style = MaterialTheme.typography.bodyMedium)
                    }
                    SyncTransportMode.LAN_WIFI -> {
                        Text("WI-FI / HOTSPOT QUICK GUIDE (CRAZY EASY 2 STEPS)", style = MaterialTheme.typography.titleMedium, color = CryoCyan)
                        Text("| STEP 1 (Host): Tap 'START HOSTING ROOM' below. Your phone broadcasts a LAN beacon automatically.", style = MaterialTheme.typography.bodyMedium)
                        Text("| STEP 2 (Friend): Connect to the same Wi-Fi or Phone Hotspot. Your Host IP (${p2pStatus.localIpAddress}) will pop up under 'Discovered Rooms' for 1-tap Join!", style = MaterialTheme.typography.bodyMedium)
                    }
                    SyncTransportMode.USB_ADB -> {
                        Text("USB CABLE LINK GUIDE (ZERO-LATENCY WIRED)", style = MaterialTheme.typography.titleMedium, color = MysteryGold)
                        Text("| STEP 1: Plug your phone into your Linux/PC or second device via USB cable.", style = MaterialTheme.typography.bodyMedium)
                        Text("| STEP 2: Run this 1-line bridge command on your computer terminal:", style = MaterialTheme.typography.bodyMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HandheldChassis, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "adb forward tcp:48899 tcp:48899",
                                style = MaterialTheme.typography.labelLarge,
                                color = PhosphorGreen
                            )
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString("adb forward tcp:48899 tcp:48899"))
                                    syncFeedback = "Copied USB tunnel command!"
                                }
                            ) {
                                Icon(Icons.Default.Usb, contentDescription = "Copy USB command", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("COPY")
                            }
                        }
                        Text("| STEP 3: Tap 'START HOSTING ROOM' on Device 1 and 'JOIN 127.0.0.1' on Device 2!", style = MaterialTheme.typography.bodyMedium)
                    }
                    SyncTransportMode.BLUETOOTH_P2P -> {
                        Text("BLUETOOTH PAN & OFFLINE CAPSULE GUIDE", style = MaterialTheme.typography.titleMedium, color = PackPurple)
                        Text("| FOR LIVE CO-OP OVER BLUETOOTH: Enable 'Bluetooth Tethering' in Android Settings after pairing phones, then tap 'START HOSTING ROOM'!", style = MaterialTheme.typography.bodyMedium)
                        Text("| FOR INSTANT SAVE SYNC: Tap 'GENERATE & COPY CAPSULE' below to create an AES-256-GCM encrypted string you can send via Bluetooth Share, Quick Share, or Clipboard.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Live Direct LAN / USB / Bluetooth Host / Join Controls (Tier C)
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PhosphorGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "DIRECT LAN / USB SOCKET: ${p2pStatus.localIpAddress}:48899",
                            style = MaterialTheme.typography.titleMedium,
                            color = PhosphorGreen
                        )
                        Text(p2pStatus.statusLog, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(p2pStatus.localIpAddress))
                            syncFeedback = "Copied Room IP ${p2pStatus.localIpAddress}!"
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = PhosphorGreen)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStartHost,
                        colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("p2p_host_button")
                    ) {
                        Icon(Icons.Default.WifiTethering, contentDescription = "Host Server")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (p2pStatus.isHosting) "HOSTING ACTIVE..." else "1. START HOSTING ROOM")
                    }
                    if (p2pStatus.isHosting || p2pStatus.isConnected) {
                        OutlinedButton(onClick = onStopSession) {
                            Text("STOP")
                        }
                    }
                }

                // 1-Tap Discovered LAN Peers
                if (p2pStatus.discoveredPeers.isNotEmpty()) {
                    Text("DISCOVERED NEARBY ROOMS (1-TAP JOIN):", style = MaterialTheme.typography.labelLarge, color = MysteryGold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        p2pStatus.discoveredPeers.forEach { peerIp ->
                            Button(
                                onClick = {
                                    targetHostIp = peerIp
                                    onConnectPeer(peerIp)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MysteryGold, contentColor = HandheldObsidian)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Join $peerIp", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("JOIN $peerIp")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = targetHostIp,
                        onValueChange = { targetHostIp = it },
                        label = { Text("Host IP (or 127.0.0.1 for USB)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onConnectPeer(targetHostIp) },
                        colors = ButtonDefaults.buttonColors(containerColor = CryoCyan, contentColor = HandheldObsidian),
                        modifier = Modifier.testTag("p2p_connect_button")
                    ) {
                        Text("2. JOIN ROOM")
                    }
                }

                if (p2pStatus.isConnected || oasisStatus.isConnected) {
                    Button(
                        onClick = onPushSaveSocket,
                        colors = ButtonDefaults.buttonColors(containerColor = MysteryGold, contentColor = HandheldObsidian),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Push Save")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SYNC ENCRYPTED SAVE TO CONNECTED SQUAD NOW")
                    }
                }
            }
        }

        // Real-Time ServerSocket / Socket Message Passing & Co-Op Tactical Radio Console
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CryoCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SERVERSOCKET / SOCKET MESSAGE PASSING",
                        style = MaterialTheme.typography.titleMedium,
                        color = CryoCyan
                    )
                    Text(
                        "TX: ${p2pStatus.packetsSent + oasisStatus.packetsSent} | RX: ${p2pStatus.packetsReceived + oasisStatus.packetsReceived}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MysteryGold
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "COVER ME - RELOADING!",
                        "OPEN EAST ARMORY DOOR!",
                        "DEVIL BOSS INCOMING!",
                        "GROUP BY RED BARRELS!"
                    ).forEach { callout ->
                        FilterChip(
                            selected = customPeerMessage == callout,
                            onClick = {
                                customPeerMessage = callout
                                onSendTacticalMessage(callout)
                            },
                            label = { Text(callout) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customPeerMessage,
                        onValueChange = { customPeerMessage = it },
                        label = { Text("P2P Socket Message / Tactical Ping") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("p2p_message_input")
                    )
                    Button(
                        onClick = { onSendTacticalMessage(customPeerMessage) },
                        colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian),
                        modifier = Modifier.testTag("p2p_send_message_button")
                    ) {
                        Text("SEND MSG")
                    }
                }

                OutlinedButton(
                    onClick = onRunLoopbackTest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("p2p_loopback_test_button")
                ) {
                    Text("VERIFY SERVERSOCKET / SOCKET LOOPBACK (127.0.0.1)")
                }

                if (p2pStatus.messageHistory.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HandheldChassis, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        p2pStatus.messageHistory.takeLast(5).forEach { netMsg ->
                            Text(
                                text = "${if (netMsg.isOutgoing) "->" else "<-"} [${netMsg.senderTag}] ${netMsg.content}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (netMsg.isOutgoing) PhosphorGreen else CryoCyan
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = passphrase,
            onValueChange = onUpdatePassphrase,
            label = { Text("AES-256-GCM Shared Encryption Key (Must match on both devices)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val cap = onExportCapsule()
                    clipboard.setText(AnnotatedString(cap))
                    syncFeedback = "Encrypted AES-256-GCM save capsule copied to clipboard!"
                },
                colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian),
                modifier = Modifier.testTag("export_capsule_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Export Capsule")
                Spacer(modifier = Modifier.width(6.dp))
                Text("GENERATE & COPY SAVE CAPSULE")
            }
        }

        if (exportedCapsule.isNotBlank()) {
            Text(
                text = exportedCapsule.take(88) + "...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                label = { Text("Paste BBZ1:... Save Capsule") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    onImportCapsule(importText).onSuccess { snap ->
                        syncFeedback = "Restored '${snap.slotName}' (Wave ${snap.highestWave})!"
                    }.onFailure { err ->
                        syncFeedback = "Decrypt failed: ${err.localizedMessage}"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MysteryGold, contentColor = HandheldObsidian)
            ) {
                Icon(Icons.Default.Security, contentDescription = "Decrypt Capsule")
                Spacer(modifier = Modifier.width(4.dp))
                Text("RESTORE")
            }
        }

        if (!syncFeedback.isNullOrBlank()) {
            Text(syncFeedback!!, style = MaterialTheme.typography.bodyMedium, color = PhosphorGreen)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KonsoleDebuggerDialog(
    gameState: GameSessionState,
    konsoleLog: List<String>,
    onExecuteCommand: (String) -> String,
    onToggleBot: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var commandInput by remember { mutableStateOf("MAP") }
    var asciiPreview by remember(gameState.wave, gameState.playerX.toInt(), gameState.zombies.size) {
        mutableStateOf(HeadlessKonsoleServer.renderAsciiBunkerMap(gameState))
    }

    EmulatorModalContainer(
        title = "KONSOLE TERMINAL & AI/LLM STRESS-TEST PORT (:$KONSOLE_TCP_PORT)",
        subtitle = "Run, inspect, and stress-test 100% of BoxBoy Zombies via Linux Konsole TCP socket or CLI",
        onDismiss = onDismiss
    ) {
        // Linux Konsole Connection Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MysteryGold, RoundedCornerShape(10.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "BACKGROUND TCP KONSOLE SERVER ACTIVE ON PORT $KONSOLE_TCP_PORT",
                    style = MaterialTheme.typography.titleMedium,
                    color = MysteryGold
                )
                Text(
                    "Connect any Linux Konsole, bash script, or external LLM agent to play & stress-test headlessly:",
                    style = MaterialTheme.typography.bodyMedium
                )
                val shellCmd = "adb forward tcp:$KONSOLE_TCP_PORT tcp:$KONSOLE_TCP_PORT && nc 127.0.0.1 $KONSOLE_TCP_PORT"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HandheldChassis, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(shellCmd, style = MaterialTheme.typography.labelSmall, color = PhosphorGreen, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(shellCmd)) }
                    ) {
                        Text("COPY CMD")
                    }
                }
            }
        }

        // Quick 1-Tap AI/LLM Stress-Test & Bot Actions
        Text("1-TAP AI / LLM STRESS-TEST & DEBUG ACTIONS", style = MaterialTheme.typography.labelLarge, color = CryoCyan)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onToggleBot,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (gameState.aiBotEnabled) PhosphorGreen else HandheldObsidian,
                    contentColor = if (gameState.aiBotEnabled) HandheldObsidian else PhosphorGreen
                ),
                modifier = Modifier
                    .border(1.dp, PhosphorGreen, RoundedCornerShape(8.dp))
                    .testTag("konsole_toggle_bot_button")
            ) {
                Icon(Icons.Default.BugReport, contentDescription = "Toggle AI Bot", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (gameState.aiBotEnabled) "AI BOT: RUNNING" else "AI BOT: OFF")
            }

            OutlinedButton(
                onClick = {
                    onExecuteCommand("STRESS 150 300")
                    asciiPreview = HeadlessKonsoleServer.renderAsciiBunkerMap(gameState)
                },
                modifier = Modifier.testTag("konsole_stress_benchmark_button")
            ) {
                Icon(Icons.Default.Speed, contentDescription = "Stress Test", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("STRESS 150 ZOMBIES (300 TICKS)")
            }

            OutlinedButton(
                onClick = {
                    onExecuteCommand("SPAWN 40 SPRINTER")
                    asciiPreview = HeadlessKonsoleServer.renderAsciiBunkerMap(gameState)
                }
            ) {
                Text("+40 SPRINTERS")
            }

            OutlinedButton(
                onClick = {
                    onExecuteCommand("SPAWN 5 DEVIL_BOSS")
                    asciiPreview = HeadlessKonsoleServer.renderAsciiBunkerMap(gameState)
                }
            ) {
                Text("+5 DEVIL BOSSES")
            }

            OutlinedButton(
                onClick = {
                    onExecuteCommand("UNLOCK_ALL")
                    asciiPreview = HeadlessKonsoleServer.renderAsciiBunkerMap(gameState)
                }
            ) {
                Text("UNLOCK ALL & +25K PTS")
            }

            OutlinedButton(
                onClick = {
                    onExecuteCommand("STEP 250 1 0 1 1")
                    asciiPreview = HeadlessKonsoleServer.renderAsciiBunkerMap(gameState)
                }
            ) {
                Text("STEP 250ms + FIRE")
            }
        }

        // Live ASCII Bunker Viewport (Full Konsole Playability)
        Text("LIVE KONSOLE ASCII BUNKER VIEW (@=You, z/s/D=Zombies, B=Barrel)", style = MaterialTheme.typography.labelLarge, color = PhosphorGreen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF050A07), RoundedCornerShape(8.dp))
                .border(1.dp, PhosphorGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(
                text = asciiPreview,
                fontFamily = ShareTechMonoFamily,
                color = PhosphorGreen,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Interactive Command Line Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                label = { Text("Konsole Command (HELP, MAP, JSON, STEP, STRESS...)") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("konsole_command_input")
            )
            Button(
                onClick = {
                    onExecuteCommand(commandInput)
                    asciiPreview = HeadlessKonsoleServer.renderAsciiBunkerMap(gameState)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian),
                modifier = Modifier.testTag("konsole_run_command_button")
            ) {
                Icon(Icons.Default.Terminal, contentDescription = "Execute")
                Spacer(modifier = Modifier.width(4.dp))
                Text("RUN")
            }
        }

        // Command Output Log
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CryoCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                konsoleLog.takeLast(6).forEach { entry ->
                    Text(
                        text = entry,
                        fontFamily = ShareTechMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.startsWith(">")) MysteryGold else CryoCyan
                    )
                }
            }
        }
    }
}

@Composable
fun WeaponArmoryDialog(
    gameState: GameSessionState,
    onUnlockOrEquip: (String) -> Unit,
    onUpgradeWeapon: (String) -> Unit,
    onDismiss: () -> Unit
) {
    EmulatorModalContainer(
        title = "BOXHEAD & ZOMBIES WEAPON ARMORY",
        subtitle = "Current Bank: ${gameState.points} PTS | Balanced Weapon Progression & Pack-a-Punch Tiers",
        onDismiss = onDismiss
    ) {
        DEFAULT_WEAPONS.forEach { spec ->
            val isUnlocked = spec.id in gameState.unlockedWeaponIds
            val isEquipped = gameState.currentWeapon.id == spec.id
            val level = gameState.weaponLevels[spec.id] ?: 1
            val upgradeCost = spec.upgradeBaseCost * level

            Card(
                colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = if (isEquipped) 2.dp else 1.dp,
                        color = if (isEquipped) PhosphorGreen else Color(spec.colorHex).copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${spec.name} ${if (isUnlocked) "[MK-$level]" else "[LOCKED]"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(spec.colorHex)
                        )
                        Text(
                            text = "DMG: ${(spec.baseDamage * (1f + (level - 1) * 0.45f)).toInt()}x${spec.pelletCount} | Mag: ${spec.magazineSize + (level - 1) * 4} | Reload: ${"%.1f".format(spec.reloadMs / 1000f)}s",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!isUnlocked) {
                            Button(
                                onClick = { onUnlockOrEquip(spec.id) },
                                enabled = gameState.points >= spec.unlockCost,
                                colors = ButtonDefaults.buttonColors(containerColor = MysteryGold, contentColor = HandheldObsidian)
                            ) {
                                Text("BUY ${spec.unlockCost}")
                            }
                        } else {
                            if (!isEquipped) {
                                OutlinedButton(onClick = { onUnlockOrEquip(spec.id) }) {
                                    Text("EQUIP")
                                }
                            }
                            if (level < 5) {
                                Button(
                                    onClick = { onUpgradeWeapon(spec.id) },
                                    enabled = gameState.points >= upgradeCost,
                                    colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian)
                                ) {
                                    Text("UPG $upgradeCost")
                                }
                            } else {
                                Text("MAX MK-5", style = MaterialTheme.typography.labelLarge, color = MysteryGold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiTacticalDirectorDialog(
    aiState: AiDirectorUiState,
    preferOfflineAiEngine: Boolean,
    onToggleOfflineAiEngine: (Boolean) -> Unit,
    onGenerateDirective: (String) -> Unit,
    onApplyDirective: (AiDirectiveResult) -> Unit,
    onRequestFastAdvice: () -> Unit,
    onDismiss: () -> Unit
) {
    var customPrompt by remember {
        mutableStateOf("High-voltage Cryo Bunker siege with extra explosive barrels and fast sprinter waves")
    }

    val quickThemes = listOf(
        "Cryo Bunker Barrel Siege",
        "Nightmare Red Devil Boss Rush",
        "Hyper Sprinter SMG Blitz",
        "Railgun Sniper Corridor",
        "Wonder RayBox Plasma Defense"
    )

    EmulatorModalContainer(
        title = "AI TACTICAL DIRECTOR (100% OFFLINE + CLOUD GEMINI)",
        subtitle = "Works 100% offline with zero account via On-Device Tactical Synthesizer, or online via Gemini AI",
        onDismiss = onDismiss
    ) {
        // AI Engine Mode Selector Card (100% Offline On-Device vs Hybrid Cloud Gemini)
        Card(
            colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = if (preferOfflineAiEngine) CryoCyan else PhosphorGreen,
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (preferOfflineAiEngine) {
                                "MODE: 100% OFFLINE ON-DEVICE AI ENGINE"
                            } else {
                                "MODE: CLOUD GEMINI + AUTO OFFLINE FALLBACK"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (preferOfflineAiEngine) CryoCyan else PhosphorGreen
                        )
                        Text(
                            text = if (preferOfflineAiEngine) {
                                "Runs 100% locally on your device CPU with zero internet & zero account required."
                            } else {
                                "Uses Gemini 3.8 Flash / 3.5 Flash-Lite when online, and automatically falls back to On-Device AI if offline."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = preferOfflineAiEngine,
                        onCheckedChange = onToggleOfflineAiEngine,
                        modifier = Modifier.testTag("toggle_offline_ai_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("1-TAP TACTICAL MISSION THEMES", style = MaterialTheme.typography.labelLarge, color = MysteryGold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickThemes.forEach { preset ->
                FilterChip(
                    selected = customPrompt == preset,
                    onClick = {
                        customPrompt = preset
                        onGenerateDirective(preset)
                    },
                    label = { Text(preset) }
                )
            }
        }

        OutlinedTextField(
            value = customPrompt,
            onValueChange = { customPrompt = it },
            label = { Text("Describe Custom Zombies Challenge / Procedural Mutator") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_prompt_input")
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onGenerateDirective(customPrompt) },
                enabled = aiState !is AiDirectorUiState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = HandheldObsidian),
                modifier = Modifier
                    .weight(1f)
                    .testTag("generate_ai_directive_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Architect Mission")
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (preferOfflineAiEngine) "SYNTHESIZE OFFLINE" else "ARCHITECT MISSION")
            }
            OutlinedButton(
                onClick = onRequestFastAdvice,
                enabled = aiState !is AiDirectorUiState.Loading,
                modifier = Modifier
                    .weight(1f)
                    .testTag("fast_radio_advice_button")
            ) {
                Text(if (preferOfflineAiEngine) "OFFLINE RADIO TIP" else "LIVE RADIO TIP")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        when (aiState) {
            is AiDirectorUiState.Idle -> {
                Text(
                    "Synthesize a custom seeded procedural bunker challenge or request an instant wave survival radio callout--100% functional offline without any account!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is AiDirectorUiState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = PhosphorGreen)
                    Text("Tactical AI Director computing bunker telemetry...")
                }
            }
            is AiDirectorUiState.StreamingAdvice -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = aiState.partialText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PhosphorGreen,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            is AiDirectorUiState.AdviceReady -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PhosphorGreen, RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (preferOfflineAiEngine) "ON-DEVICE TACTICAL RADIO CALLOUT" else "TACTICAL RADIO CALLOUT",
                            style = MaterialTheme.typography.labelLarge,
                            color = MysteryGold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(aiState.text, style = MaterialTheme.typography.bodyLarge, color = PhosphorGreen)
                    }
                }
            }
            is AiDirectorUiState.DirectiveReady -> {
                val d = aiState.directive
                Card(
                    colors = CardDefaults.cardColors(containerColor = HandheldObsidian),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, MysteryGold, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(d.operationTitle, style = MaterialTheme.typography.titleLarge, color = MysteryGold)
                        Text(
                            "Seed #${d.mapSeed} | Start Wave ${d.startingWave} | Grant +${d.bonusPoints} PTS | Weapon: ${d.recommendedWeaponId.uppercase()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = CryoCyan
                        )
                        Text(d.tacticalBriefing, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                onApplyDirective(d)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MysteryGold, contentColor = HandheldObsidian),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("LAUNCH DIRECTIVE INTO BUNKER")
                        }
                    }
                }
            }
            is AiDirectorUiState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ZombieCrimson.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ZombieCrimson, RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = aiState.message,
                        color = ZombieCrimson,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmulatorModalContainer(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, PhosphorGreen, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = HandheldChassis
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = PhosphorGreen)
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text("RESUME")
                    }
                }
                HorizontalDivider(color = PhosphorGreen.copy(alpha = 0.3f))
                content()
            }
        }
    }
}
