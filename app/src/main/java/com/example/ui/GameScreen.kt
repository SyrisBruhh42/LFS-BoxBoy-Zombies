package com.example.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DEFAULT_WEAPONS
import com.example.game.AbyssalChiaroscuroLightingEngine
import com.example.game.AbyssalChiaroscuroLightingEngine.renderChiaroscuroLightingPass
import com.example.game.FortificationType
import com.example.game.GameSessionState
import com.example.game.StationKind
import com.example.input.ControllerManager
import com.example.input.GamepadAction
import com.example.input.GamepadActionCallbacks
import com.example.input.GamepadMapping
import com.example.input.TouchButtonId
import com.example.network.CoopPeerState
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.game.ZombieArchetype
import com.example.ui.theme.CryoCyan
import com.example.ui.theme.HandheldChassis
import com.example.ui.theme.HandheldObsidian
import com.example.ui.theme.HandheldSurfaceVariant
import com.example.ui.theme.HighContrastBlack
import com.example.ui.theme.HighContrastCyan
import com.example.ui.theme.HighContrastWhite
import com.example.ui.theme.HighContrastYellow
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.MysteryGold
import com.example.ui.theme.PackPurple
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.TextCrisp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.ZombieCrimson
import com.example.viewmodel.BoxBoyViewModel
import com.example.viewmodel.ControllerConfigState
import com.example.viewmodel.DisplayScreenMode
import com.example.viewmodel.OverlayPreset
import kotlinx.coroutines.isActive
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import com.example.audio.CombatAudioEngine
import com.example.audio.CombatSfxEvent
import com.example.audio.RememberCombatAudioEngineEffect
import com.example.audio.RememberRetroMusicTrackEffect
import com.example.game.ConvexShadowHull
import com.example.game.PointLightSource2D
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private class LiveInputState {
    // Direct World Viewport Mouse/Touch Pointer Aiming (360 deg free-aim on the city canvas)
    @Volatile var pointerAimActive: Boolean = false
    @Volatile var pointerAimAngleRad: Float = 0f
    @Volatile var pointerFireHold: Boolean = false

    @Volatile var isFiring: Boolean = false
    @Volatile var isAltBarrelDrop: Boolean = false
    @Volatile var isInteractPressed: Boolean = false

    fun computeMoveVector(nowUptimeMs: Long, dtSec: Float): Pair<Float, Float> {
        return ControllerManager.stepAndSampleJoystickVector(nowUptimeMs, dtSec)
    }

    fun computeAimOverrideAngleRad(nowUptimeMs: Long): Float? {
        if (pointerAimActive) return pointerAimAngleRad
        return ControllerManager.sampleArrowAimAngleRad(nowUptimeMs)
    }

    fun computeIsFiring(nowUptimeMs: Long): Boolean {
        return isFiring || pointerFireHold || ControllerManager.isArrowOrHwFiring(nowUptimeMs)
    }
}

@Composable
fun GameScreen(
    viewModel: BoxBoyViewModel,
    onSignOutRequested: () -> Unit
) {
    // Coalesced HUD state: only updates when discrete HUD-visible counters or modals change
    var hudGameState by remember { mutableStateOf(viewModel.gameState.value) }
    val controllerConfig by viewModel.controllerConfig.collectAsStateWithLifecycle()
    val gamepadMapping by viewModel.gamepadMapping.collectAsStateWithLifecycle()
    val offlineSaves by viewModel.localOfflineSaves.collectAsStateWithLifecycle()
    val cloudSavesState by viewModel.cloudSaveSlots.collectAsStateWithLifecycle()
    val cloudProfilesState by viewModel.cloudControllerProfiles.collectAsStateWithLifecycle()
    val p2pStatus by viewModel.p2pManager.sessionStatus.collectAsStateWithLifecycle()
    val coopPeer by viewModel.p2pManager.coopPeerState.collectAsStateWithLifecycle()
    val oasisStatus by viewModel.oasisStatus.collectAsStateWithLifecycle()
    val oasisEndpointConfig by viewModel.oasisEndpointConfig.collectAsStateWithLifecycle()
    val networkHealth by viewModel.networkHealth.collectAsStateWithLifecycle()
    val syncPassphrase by viewModel.syncPassphrase.collectAsStateWithLifecycle()
    val exportedCapsule by viewModel.exportedCapsule.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val preferOfflineAiEngine by viewModel.preferOfflineAiEngine.collectAsStateWithLifecycle()
    val konsoleLog by viewModel.konsoleServer.konsoleLog.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val careerDossier by viewModel.careerDossier.collectAsStateWithLifecycle()
    val dailyDirectives by viewModel.dailyDirectives.collectAsStateWithLifecycle()
    val accountDeletionStatus by viewModel.accountDeletionStatus.collectAsStateWithLifecycle()
    val driveSyncState by viewModel.driveSyncState.collectAsStateWithLifecycle()
    val unlockedTrashArcadeRelics by viewModel.unlockedTrashArcadeRelics.collectAsStateWithLifecycle()
    val storytellerState by viewModel.storytellerState.collectAsStateWithLifecycle()
    val zt9ComfortState by viewModel.zt9ComfortState.collectAsStateWithLifecycle()
    val friendlyGuideCard by viewModel.friendlyGuideCard.collectAsStateWithLifecycle()
    val activeOasisWing by viewModel.activeOasisWing.collectAsStateWithLifecycle()
    val isOasisOverlayOpen by viewModel.isOasisOverlayOpen.collectAsStateWithLifecycle()
    val rmblynMessages by viewModel.rmblynMessages.collectAsStateWithLifecycle()

    var activeSheet by remember { mutableStateOf(ActiveEmulatorSheet.NONE) }
    var isWorldMapModalOpen by remember { mutableStateOf(false) }
    var customWaypoint by remember { mutableStateOf<Offset?>(null) }
    var isMusicMuted by remember { mutableStateOf(false) }
    // Collapsible secondary HUD drawer (collapsed by default so the Open City viewport fills the screen!)
    var isHudDrawerExpanded by remember { mutableStateOf(false) }
    // Floating Touch Pad visibility (can be toggled or auto-hidden when WASD hardware keys are pressed)
    var isTouchPadVisible by remember { mutableStateOf(true) }
    // Lightweight frame counter read ONLY inside Canvas DrawScope -- zero Composable recompositions at 60 FPS
    val frameTickState = remember { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    // Non-Compose input holder: touching/dragging D-Pad, WASD, or holding Fire causes ZERO Compose Snapshot writes
    val liveInput = remember { LiveInputState() }

    val gamepadCallbacks = remember(viewModel, liveInput) {
        object : GamepadActionCallbacks {
            override fun onFireHoldChanged(firing: Boolean) {
                liveInput.isFiring = firing
            }
            override fun onInteractTriggered() {
                liveInput.isInteractPressed = true
            }
            override fun onDropBarrelTriggered() {
                liveInput.isAltBarrelDrop = true
            }
            override fun onReloadTriggered() {
                viewModel.triggerManualReload()
            }
            override fun onCycleWeapon(delta: Int) {
                viewModel.cycleWeapon(delta)
            }
            override fun onCycleSpeed() {
                viewModel.cycleEmulatorSpeed()
            }
            override fun onToggleAiBot() {
                viewModel.toggleAiBot()
            }
        }
    }

    // 60 FPS deterministic survival game loop + WASD-to-Joystick analog bridge
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        var lastFrameMs = withFrameMillis { it }
        var smoothedDeltaMs = 16.6f
        var lastHudSyncMs = 0L
        while (isActive) {
            val nowMs = withFrameMillis { it }
            val rawDelta = (nowMs - lastFrameMs).coerceIn(4L, 40L)
            lastFrameMs = nowMs
            // Smooth frame delta with EMA to eliminate browser/emulator frame-pacing micro-stutter
            smoothedDeltaMs = smoothedDeltaMs * 0.80f + rawDelta.toFloat() * 0.20f
            val stepMs = smoothedDeltaMs.roundToInt().toLong().coerceIn(8L, 28L)
            val dtSec = stepMs / 1000f
            val uptimeMs = SystemClock.uptimeMillis()

            // Consume window-level one-shot triggers from ControllerManager & LiveInputState
            val dropTrigger = liveInput.isAltBarrelDrop || ControllerManager.pendingBarrelDropTrigger
            val interactTrigger = liveInput.isInteractPressed || ControllerManager.pendingInteractTrigger
            if (dropTrigger) {
                liveInput.isAltBarrelDrop = false
                ControllerManager.pendingBarrelDropTrigger = false
            }
            if (interactTrigger) {
                liveInput.isInteractPressed = false
                ControllerManager.pendingInteractTrigger = false
            }
            if (ControllerManager.pendingReloadTrigger) {
                ControllerManager.pendingReloadTrigger = false
                viewModel.triggerManualReload()
            }
            val cycleDelta = ControllerManager.pendingWeaponCycleDelta
            if (cycleDelta != 0) {
                ControllerManager.pendingWeaponCycleDelta = 0
                viewModel.cycleWeapon(cycleDelta)
            }

            val (mvX, mvY) = liveInput.computeMoveVector(uptimeMs, dtSec)
            val aimOverride = liveInput.computeAimOverrideAngleRad(uptimeMs)
            val firingNow = liveInput.computeIsFiring(uptimeMs)

            viewModel.tickFrame(
                deltaMs = stepMs,
                moveX = mvX,
                moveY = mvY,
                isFiring = firingNow,
                isAltBarrelDrop = dropTrigger,
                isInteractPressed = interactTrigger,
                aimAngleOverrideRad = aimOverride
            )
            // Invalidate ONLY the Canvas DrawScope layers (never read in @Composable body)
            frameTickState.longValue = nowMs

            val latest = viewModel.gameState.value
            val prevHud = hudGameState

            // Immediate HUD changes (modals, weapon switch, station prompt, wave transition)
            val immediateHudChange = latest.wave != prevHud.wave ||
                latest.currentWeaponIndex != prevHud.currentWeaponIndex ||
                (latest.reloadRemainingMs > 0L) != (prevHud.reloadRemainingMs > 0L) ||
                latest.nearbyStation?.id != prevHud.nearbyStation?.id ||
                latest.nearbyLootDrop?.id != prevHud.nearbyLootDrop?.id ||
                latest.inspectedLootDrop?.id != prevHud.inspectedLootDrop?.id ||
                latest.statusToast != prevHud.statusToast ||
                latest.isPaused != prevHud.isPaused ||
                latest.isGameOver != prevHud.isGameOver ||
                latest.cameraTension.lightingMode != prevHud.cameraTension.lightingMode ||
                latest.cameraTension.framingState != prevHud.cameraTension.framingState ||
                latest.selectedFortificationType != prevHud.selectedFortificationType ||
                latest.aiBotEnabled != prevHud.aiBotEnabled ||
                latest.speedConfig != prevHud.speedConfig ||
                latest.mazeState.activeRoomSector != prevHud.mazeState.activeRoomSector ||
                latest.isPlayerDowned != prevHud.isPlayerDowned ||
                latest.coopPeers.size != prevHud.coopPeers.size

            // Low-frequency counter sync (480ms throttle) so active movement/shooting stays 100% hitch-free
            val counterHudChange = latest.points != prevHud.points ||
                latest.hp != prevHud.hp ||
                latest.shield != prevHud.shield ||
                latest.totalKills != prevHud.totalKills ||
                latest.zombies.size != prevHud.zombies.size ||
                latest.currentAmmoCount != prevHud.currentAmmoCount ||
                latest.reloadRemainingMs > 0L

            if (immediateHudChange || (counterHudChange && nowMs - lastHudSyncMs >= 480L)) {
                hudGameState = latest
                lastHudSyncMs = nowMs
            }
        }
    }

    val gameState = hudGameState

    // Hardware-accelerated looping retro synth soundtrack (Inspired_Retro_Music_Loop_Track_642cf9e3.mp3)
    RememberRetroMusicTrackEffect(
        isMuted = isMusicMuted,
        isDucked = gameState.isPaused || gameState.inspectedLootDrop != null || activeSheet != ActiveEmulatorSheet.NONE,
        emulatorSpeed = gameState.speedConfig.clockMultiplier
    )

    // Weapon-stat-scaled procedural PCM AudioTrack CombatAudioEngine + Hit-Reg Haptics
    val sfxEngine: CombatAudioEngine = RememberCombatAudioEngineEffect(
        gameState = gameState,
        sfxEnabled = storeSettings.sfxEnabled,
        sfxVolume = storeSettings.sfxVolume,
        hapticsEnabled = storeSettings.hapticsEnabled
    )

    BackHandler(enabled = true) {
        when {
            isOasisOverlayOpen -> viewModel.toggleOasisUniversalOverlay(forceOpen = false)
            isWorldMapModalOpen -> isWorldMapModalOpen = false
            activeSheet != ActiveEmulatorSheet.NONE -> activeSheet = ActiveEmulatorSheet.NONE
            gameState.inspectedLootDrop != null -> viewModel.inspectLootDrop(null)
            else -> {
                viewModel.setPaused(true)
                activeSheet = ActiveEmulatorSheet.TITLE_HUB
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val context = androidx.compose.ui.platform.LocalContext.current

    MyApplicationTheme(accessibilityState = controllerConfig.toAccessibilityThemeState()) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = if (controllerConfig.highContrastMode) HighContrastBlack else HandheldObsidian,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .semantics {
                if (controllerConfig.screenReaderAnnouncements) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Wave ${gameState.wave}, Health ${gameState.hp} of ${gameState.maxHp}, Points ${gameState.points}, Active Zombies ${gameState.zombies.size}"
                }
            }
            .onKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                val nativeCode = nativeEvent.keyCode
                val isOverlayShortcut = event.type == KeyEventType.KeyDown && (
                    (nativeEvent.isShiftPressed && nativeCode == AndroidKeyEvent.KEYCODE_TAB) ||
                        (nativeEvent.isCtrlPressed && nativeCode == AndroidKeyEvent.KEYCODE_K)
                    )
                if (isOverlayShortcut) {
                    sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                    viewModel.toggleOasisUniversalOverlay()
                    true
                } else if (activeSheet == ActiveEmulatorSheet.CONTROLLER_MAPPER &&
                    controllerConfig.awaitingKeyBindAction != null &&
                    event.type == KeyEventType.KeyDown
                ) {
                    viewModel.onHardwareKeyCaptured(nativeCode)
                } else if (nativeCode == AndroidKeyEvent.KEYCODE_M && event.type == KeyEventType.KeyDown) {
                    isWorldMapModalOpen = !isWorldMapModalOpen
                    true
                } else {
                    handleHardwareGamepadEvent(
                        keyCode = nativeCode,
                        isDown = event.type == KeyEventType.KeyDown,
                        config = controllerConfig,
                        liveInput = liveInput,
                        onReloadTap = { if (it) viewModel.triggerManualReload() },
                        onCycleWeapon = { viewModel.cycleWeapon(it) }
                    )
                }
            }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isTvCastMode = controllerConfig.displayMode == DisplayScreenMode.TV_CAST_CINEMATIC

            Column(modifier = Modifier.fillMaxSize()) {
                // Uninterrupted Top Oasis Universal Shell Ribbon (stays mounted across Title, Gameplay, Modals & Wing switches)
                OasisMultiplayerBar(
                    oasisStatus = oasisStatus,
                    storytellerState = storytellerState,
                    comfortState = zt9ComfortState,
                    activeWing = activeOasisWing,
                    isOverlayOpen = isOasisOverlayOpen,
                    onToggleOverlay = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        viewModel.toggleOasisUniversalOverlay()
                    },
                    onToggleStorytellerPlay = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        viewModel.toggleStorytellerPlay()
                    },
                    onCycleStorytellerChapter = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        viewModel.cycleStorytellerChapter()
                    },
                    onCycleSleepTimer = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        viewModel.cycleStorytellerSleepTimer()
                    },
                    onOpenWingSwitcher = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        viewModel.toggleOasisUniversalOverlay(forceOpen = true)
                    },
                    onOpenFamilyRoomModal = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        activeSheet = ActiveEmulatorSheet.P2P_MULTIPLAYER_SYNC
                    }
                )

                // Sleek Compact Top Toolbar (adapts to Landscape & Portrait, with [ROTATE] & [WASD/PAD] toggles)
                EmulatorTopRomBar(
                    gameState = gameState,
                    p2pConnected = p2pStatus.isConnected || oasisStatus.isConnected,
                    isCloudAuthenticated = viewModel.isCloudAuthenticated,
                    isMusicMuted = isMusicMuted,
                    isHudDrawerExpanded = isHudDrawerExpanded,
                    isTouchPadVisible = isTouchPadVisible && !isTvCastMode,
                    isLandscape = isLandscape,
                    displayMode = controllerConfig.displayMode,
                    highContrastMode = controllerConfig.highContrastMode,
                    developerModeEnabled = storeSettings.developerModeEnabled,
                    readyDirectivesCount = dailyDirectives.count { it.isCompleted && !it.isClaimed },
                    onToggleHighContrast = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        viewModel.toggleHighContrastMode()
                    },
                    onToggleMusic = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        isMusicMuted = !isMusicMuted
                    },
                    onToggleHudDrawer = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        isHudDrawerExpanded = !isHudDrawerExpanded
                    },
                    onToggleTouchPad = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        isTouchPadVisible = !isTouchPadVisible
                    },
                    onToggleOrientation = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        val activity = context as? Activity
                        if (activity != null) {
                            activity.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        }
                    },
                    onTogglePause = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        viewModel.setPaused(!gameState.isPaused)
                    },
                    onCycleSpeed = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        viewModel.cycleEmulatorSpeed()
                    },
                    onToggleTvCastMode = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        val nextMode = if (controllerConfig.displayMode == DisplayScreenMode.TV_CAST_CINEMATIC) {
                            DisplayScreenMode.HANDHELD_TOUCH
                        } else {
                            DisplayScreenMode.TV_CAST_CINEMATIC
                        }
                        viewModel.updateControllerConfig { it.copy(displayMode = nextMode) }
                    },
                    onOpenWorldMap = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        isWorldMapModalOpen = true
                    },
                    onOpenSheet = {
                        sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                        activeSheet = it
                    },
                    onSignOut = onSignOutRequested
                )

                // Collapsible Secondary Tactical & Weapon HUD Drawer (hidden by default so the Open City fills the screen!)
                if (isHudDrawerExpanded) {
                    if (gameState.statusToast.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HandheldChassis)
                                .border(1.dp, PhosphorGreen.copy(alpha = 0.4f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SYS: ${gameState.statusToast}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PhosphorGreen,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    WeaponAndAmmoTrackerBar(
                        gameState = gameState,
                        onSelectWeapon = { viewModel.unlockOrEquipWeaponFromArmory(it) },
                        onDropBarrel = { liveInput.isAltBarrelDrop = true },
                        onTriggerReload = { viewModel.triggerManualReload() },
                        onToggleAiBot = { viewModel.toggleAiBot() },
                        onOpenArmory = { activeSheet = ActiveEmulatorSheet.WEAPON_ARMORY }
                    )

                    AbyssalTacticalHudStrip(
                        state = gameState,
                        onCycleLightingMode = { viewModel.cycleLightingAtmosphereMode() },
                        onCycleCameraFraming = { viewModel.cycleCameraFramingState() },
                        onCycleFortification = { viewModel.cycleFortificationType() },
                        onInspectNearbyLoot = {
                            val drop = gameState.nearbyLootDrop ?: gameState.lootDrops.firstOrNull()
                            viewModel.inspectLootDrop(drop)
                        },
                        onOpenRpgAnvilModal = { activeSheet = ActiveEmulatorSheet.ABYSSAL_RPG_ANVIL }
                    )
                }

                // FULL-SCREEN OPEN CITY VIEWPORT + NON-INVASIVE FLOATING CORNER CONTROLS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    BoxBoyViewportCanvas(
                        hudState = gameState,
                        gameStateFlow = viewModel.gameState,
                        frameTickState = frameTickState,
                        liveInput = liveInput,
                        coopPeer = coopPeer,
                        customWaypoint = customWaypoint,
                        isLandscape = isLandscape,
                        cameraZoomPercent = controllerConfig.cameraZoomPercent,
                        dpadScale = (controllerConfig.dpadScalePercent / 100f).coerceIn(0.65f, 1.45f),
                        crtScanlines = controllerConfig.crtScanlinesEnabled && !controllerConfig.reduceMotionAndFlashes && !controllerConfig.highContrastMode,
                        highContrastMode = controllerConfig.highContrastMode,
                        colorblindHazardMarkers = controllerConfig.colorblindHazardMarkers,
                        isTouchPadVisible = isTouchPadVisible && !isTvCastMode,
                        onOpenWorldMap = { isWorldMapModalOpen = true },
                        onTriggerReload = { viewModel.triggerManualReload() },
                        onDropFortification = { liveInput.isAltBarrelDrop = true },
                        onTriggerInteract = { liveInput.isInteractPressed = true },
                        onCycleWeapon = { viewModel.cycleWeapon(1) },
                        onEquipInspectedLoot = { viewModel.equipInspectedLootDrop() },
                        onSalvageInspectedLoot = { viewModel.salvageInspectedLootDrop() },
                        onDismissLootInspect = { viewModel.inspectLootDrop(null) },
                        onRestart = { viewModel.resetOrGenerateNewMap(gameState.mapSeed) },
                        onNewSeed = { viewModel.resetOrGenerateNewMap() }
                    )

                    // Non-Invasive Translucent Floating Corner Touch Controls (synced with WASD joystick bridge!)
                    if (isTouchPadVisible && !isTvCastMode) {
                        FloatingCornerGamepadOverlay(
                            config = controllerConfig,
                            gamepadMapping = gamepadMapping,
                            frameTickState = frameTickState,
                            isLandscape = isLandscape,
                            isReloading = gameState.reloadRemainingMs > 0,
                            nearbyStationLabel = gameState.nearbyStation?.label,
                            onMoveDirection = { rawDx, rawDy ->
                                ControllerManager.updateTouchJoystick(rawDx, rawDy)
                            },
                            onTouchButtonEvent = { buttonId, isPressed ->
                                ControllerManager.dispatchTouchButtonEvent(buttonId, isPressed, gamepadCallbacks)
                            }
                        )
                    }
                }

                if (isTvCastMode) {
                    TvCastStatusStrip(
                        config = controllerConfig,
                        aiBotEnabled = gameState.aiBotEnabled,
                        onSwitchToTouch = {
                            isTouchPadVisible = true
                            viewModel.updateControllerConfig {
                                it.copy(displayMode = DisplayScreenMode.HANDHELD_TOUCH)
                            }
                        },
                        onAdjustZoom = { delta ->
                            viewModel.updateControllerConfig {
                                it.copy(cameraZoomPercent = (it.cameraZoomPercent + delta).coerceIn(65, 140))
                            }
                        },
                        onToggleAiBot = { viewModel.toggleAiBot() },
                        onOpenKonsole = { activeSheet = ActiveEmulatorSheet.KONSOLE_DEBUGGER }
                    )
                }
            }
        }
    }

    // Emulator Modal Dialogs
    when (activeSheet) {
        ActiveEmulatorSheet.SAVE_SLOTS -> {
            SaveSlotsDialog(
                gameState = gameState,
                isCloudAuthenticated = viewModel.isCloudAuthenticated,
                offlineSlots = offlineSaves,
                cloudState = cloudSavesState,
                driveSyncState = driveSyncState,
                onSaveNewSlot = { slotTitle -> viewModel.saveStateOfflineAndCloud(slotTitle, context) },
                onSyncToGoogleDrive = { slotTitle -> viewModel.syncCurrentSaveToGoogleDrive(slotTitle, context) },
                onRefreshGoogleDrive = { viewModel.refreshGoogleDriveSaves(context) },
                onReconcileGoogleDrive = { viewModel.reconcileNewestWinsWithGoogleDrive(context) },
                onSelectDriveRetentionPolicy = { policy -> viewModel.setDriveUploadRetentionPolicy(policy) },
                onLoadGoogleDriveSlot = { dFile -> viewModel.loadFromGoogleDriveSave(dFile, context) },
                onDeleteGoogleDriveSlot = { fileId -> viewModel.deleteGoogleDriveSave(context, fileId) },
                onLoadOfflineSlot = { snap ->
                    viewModel.applySnapshotToGame(snap, "Local Chronicle '${snap.slotName}'")
                },
                onLoadCloudSlot = { slot ->
                    viewModel.loadFromCloudSaveSlot(slot)
                },
                onDeleteCloudSlot = { slotId -> viewModel.deleteCloudSaveSlot(slotId) },
                onRequestGoogleSignIn = onSignOutRequested,
                onDismiss = { activeSheet = ActiveEmulatorSheet.NONE }
            )
        }

        ActiveEmulatorSheet.CONTROLLER_MAPPER -> {
            ControllerMapperDialog(
                config = controllerConfig,
                gamepadMapping = gamepadMapping,
                cloudProfiles = cloudProfilesState,
                onUpdateConfig = { transform -> viewModel.updateControllerConfig(transform) },
                onBindTouchButton = { btnId, act -> viewModel.bindTouchButtonToAction(btnId, act) },
                onApplyMappingPreset = { preset -> viewModel.applyGamepadMappingPreset(preset) },
                onUpdateDeadzone = { dz -> viewModel.updateDpadDeadzone(dz) },
                onSaveProfileToCloud = { name -> viewModel.saveControllerProfileToCloud(name) },
                onApplyCloudProfile = { prof -> viewModel.applyCloudControllerProfile(prof) },
                onDeleteCloudProfile = { id -> viewModel.deleteCloudControllerProfile(id) },
                onDismiss = {
                    viewModel.updateControllerConfig { it.copy(awaitingKeyBindAction = null) }
                    activeSheet = ActiveEmulatorSheet.NONE
                }
            )
        }

        ActiveEmulatorSheet.SPEED_CONTROLLER -> {
            SpeedControllerDialog(
                speedConfig = gameState.speedConfig,
                onUpdateSpeed = { transform -> viewModel.updateSpeedConfig(transform) },
                onDismiss = { activeSheet = ActiveEmulatorSheet.NONE }
            )
        }

        ActiveEmulatorSheet.P2P_MULTIPLAYER_SYNC -> {
            P2pMultiplayerSyncDialog(
                p2pStatus = p2pStatus,
                passphrase = syncPassphrase,
                exportedCapsule = exportedCapsule,
                onSelectTransport = { mode -> viewModel.p2pManager.setTransportMode(mode) },
                onUpdatePassphrase = { pass -> viewModel.updateSyncPassphrase(pass) },
                onStartHost = { viewModel.p2pManager.startHosting(syncPassphrase) },
                onConnectPeer = { hostIp -> viewModel.p2pManager.connectToPeer(hostIp, syncPassphrase) },
                onStopSession = { viewModel.p2pManager.stopSession() },
                onSendTacticalMessage = { text -> viewModel.sendP2pTacticalMessage(text) },
                onRunLoopbackTest = { viewModel.runP2pLoopbackVerification() },
                onPushSaveSocket = { viewModel.pushCurrentSaveOverP2pSocket() },
                onExportCapsule = { viewModel.exportCurrentEncryptedCapsule() },
                onImportCapsule = { cipher -> viewModel.importEncryptedCapsule(cipher) },
                onDismiss = { activeSheet = ActiveEmulatorSheet.NONE },
                oasisStatus = oasisStatus,
                oasisEndpointConfig = oasisEndpointConfig,
                coopPeers = gameState.coopPeers,
                networkHealth = networkHealth,
                onRefreshNetworkHealth = { viewModel.refreshNetworkHealth() },
                onSetPrivateNetworkOverride = { enabled -> viewModel.setManualPrivateNetworkOverride(enabled) },
                onUpdateCustomServerUrl = { url -> viewModel.updateCustomLfsServerUrl(url) },
                onLaunchOrJoinLfsRoom = { code, hostMode, policy ->
                    viewModel.launchOrJoinLfsHostedRoom(code, hostMode, policy)
                },
                onMigrateHostMode = { nextMode -> viewModel.migrateActiveRoomHostMode(nextMode) },
                onRefreshPublicRooms = { viewModel.refreshPublicLfsRooms() },
                onDisconnectLfsRoom = { viewModel.disconnectLfsRoom() },
                onToggleSquadBot = { persona -> viewModel.toggleSquadBotPersona(persona) },
                onFillSquadWithBots = { viewModel.fillSquadWithOasisBots() },
                currentPoints = gameState.points,
                unlockedTrashArcadeRelics = unlockedTrashArcadeRelics,
                onProbeTailscaleHandshake = { viewModel.probeTailscaleHandshakeNow() }
            )
        }

        ActiveEmulatorSheet.WEAPON_ARMORY -> {
            WeaponArmoryDialog(
                gameState = gameState,
                onUnlockOrEquip = { weaponId -> viewModel.unlockOrEquipWeaponFromArmory(weaponId) },
                onUpgradeWeapon = { weaponId -> viewModel.upgradeWeaponInArmory(weaponId) },
                onDismiss = { activeSheet = ActiveEmulatorSheet.NONE }
            )
        }

        ActiveEmulatorSheet.AI_TACTICAL_DIRECTOR -> {
            AiTacticalDirectorDialog(
                aiState = aiState,
                preferOfflineAiEngine = preferOfflineAiEngine,
                onToggleOfflineAiEngine = { viewModel.setPreferOfflineAiEngine(it) },
                onGenerateDirective = { prompt -> viewModel.generateProceduralDirective(prompt) },
                onApplyDirective = { directive -> viewModel.applyAiDirective(directive) },
                onRequestFastAdvice = { viewModel.streamFastRadioAdvice() },
                onDismiss = { activeSheet = ActiveEmulatorSheet.NONE }
            )
        }

        ActiveEmulatorSheet.KONSOLE_DEBUGGER -> {
            KonsoleDebuggerDialog(
                gameState = gameState,
                konsoleLog = konsoleLog,
                onExecuteCommand = { cmd -> viewModel.konsoleServer.executeAndRecord(cmd) },
                onToggleBot = { viewModel.toggleAiBot() },
                onDismiss = { activeSheet = ActiveEmulatorSheet.NONE }
            )
        }

        ActiveEmulatorSheet.ABYSSAL_RPG_ANVIL -> {
            AbyssalRpgAnvilAndArmorDialog(
                state = gameState,
                onTemperWeapon = { viewModel.anvilTemperEquippedWeapon() },
                onReforgeElement = { viewModel.anvilReforgeWeaponElement() },
                onRollNewModularWeapon = { viewModel.rollProceduralModularWeaponWithScrap() },
                onUpgradeArmorSlot = { slot -> viewModel.anvilUpgradeArmorSlot(slot) },
                onCraftArmorSlot = { slot -> viewModel.anvilCraftArmorPiece(slot) },
                onSelectFortification = { ft -> viewModel.selectFortificationType(ft) },
                onTriggerSiegeNow = { viewModel.triggerSiegeFabricatorNow() },
                onBankEscrowPoints = { viewModel.bankPointsIntoDepthEscrow(500) },
                onWithdrawEscrowPoints = { viewModel.withdrawPointsFromDepthEscrow() },
                onDismiss = { activeSheet = ActiveEmulatorSheet.NONE }
            )
        }

        ActiveEmulatorSheet.TITLE_HUB -> {
            TitleHub(
                gameState = gameState,
                careerDossier = careerDossier,
                dailyDirectives = dailyDirectives,
                driveSyncState = driveSyncState,
                networkHealth = networkHealth,
                isCloudAuthenticated = viewModel.isCloudAuthenticated,
                developerModeEnabled = storeSettings.developerModeEnabled,
                onStartSurvival = {
                    sfxEngine.playUiCrystalChime()
                    viewModel.setPaused(false)
                    activeSheet = ActiveEmulatorSheet.NONE
                },
                onNewAstralSeed = {
                    sfxEngine.playCrystallineFanfare(isWaveClear = true)
                    viewModel.resetOrGenerateNewMap()
                    viewModel.setPaused(false)
                    activeSheet = ActiveEmulatorSheet.NONE
                },
                onQuickSyncToGoogleDrive = {
                    sfxEngine.playCrystallineFanfare(isWaveClear = false)
                    viewModel.syncCurrentSaveToGoogleDrive(
                        slotName = "Chronicle - Wave ${gameState.wave} (${gameState.currentWeapon.shortCode})",
                        context = context
                    )
                },
                onOpenSaveAndDriveModal = { activeSheet = ActiveEmulatorSheet.SAVE_SLOTS },
                onOpenCareerDossier = { activeSheet = ActiveEmulatorSheet.CAREER_DOSSIER },
                onOpenCoopLobby = { activeSheet = ActiveEmulatorSheet.P2P_MULTIPLAYER_SYNC },
                onOpenArmory = { activeSheet = ActiveEmulatorSheet.WEAPON_ARMORY },
                onOpenFieldManual = { activeSheet = ActiveEmulatorSheet.FIELD_MANUAL },
                onOpenSettingsPrivacy = { activeSheet = ActiveEmulatorSheet.STORE_SETTINGS_PRIVACY },
                onToggleDeveloperTools = { enabled ->
                    sfxEngine.playUiCrystalChime()
                    viewModel.updateStoreSettings { it.copy(developerModeEnabled = enabled) }
                },
                onToggleAiStressBot = { viewModel.toggleAiBot() },
                onOpenKonsoleDebugger = { activeSheet = ActiveEmulatorSheet.KONSOLE_DEBUGGER }
            )
        }

        ActiveEmulatorSheet.CAREER_DOSSIER -> {
            CareerDossierAndDirectivesDialog(
                careerDossier = careerDossier,
                dailyDirectives = dailyDirectives,
                onClaimDirective = { id ->
                    sfxEngine.trigger(CombatSfxEvent.PACK_A_PUNCH_UPGRADE)
                    viewModel.claimDailyDirectiveReward(id)
                },
                onDismiss = { activeSheet = ActiveEmulatorSheet.NONE }
            )
        }

        ActiveEmulatorSheet.FIELD_MANUAL -> {
            InteractiveFieldManualDialog(
                onCompleteAndClose = {
                    viewModel.markFieldManualCompleted()
                    activeSheet = ActiveEmulatorSheet.NONE
                }
            )
        }

        ActiveEmulatorSheet.STORE_SETTINGS_PRIVACY -> {
            StoreSettingsPrivacyAndAccountDialog(
                storeSettings = storeSettings,
                isMusicMuted = isMusicMuted,
                highContrastMode = controllerConfig.highContrastMode,
                isCloudAuthenticated = viewModel.isCloudAuthenticated,
                currentUserId = viewModel.currentUserId,
                deletionStatus = accountDeletionStatus,
                onToggleMusicMute = { isMusicMuted = !isMusicMuted },
                onUpdateStoreSettings = { transform -> viewModel.updateStoreSettings(transform) },
                onToggleHighContrast = { viewModel.toggleHighContrastMode() },
                onTestSfxAndHaptics = { evt -> sfxEngine.trigger(evt) },
                onOpenKonsoleDebugger = { activeSheet = ActiveEmulatorSheet.KONSOLE_DEBUGGER },
                onOpenFieldManual = { activeSheet = ActiveEmulatorSheet.FIELD_MANUAL },
                onDeleteAccountAndData = {
                    viewModel.deleteAccountAndPurgeAllData {
                        activeSheet = ActiveEmulatorSheet.NONE
                        onSignOutRequested()
                    }
                },
                onDismiss = { activeSheet = ActiveEmulatorSheet.NONE }
            )
        }

        ActiveEmulatorSheet.NONE -> Unit
    }

    if (isOasisOverlayOpen) {
        OasisUniversalOverlayDialog(
            oasisStatus = oasisStatus,
            storytellerState = storytellerState,
            comfortState = zt9ComfortState,
            friendlyGuideCard = friendlyGuideCard,
            activeWing = activeOasisWing,
            rmblynMessages = rmblynMessages,
            onSelectWing = { wing ->
                sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                viewModel.selectOasisWing(wing)
            },
            onToggleFamilyArcadeMode = { enabled ->
                sfxEngine.trigger(CombatSfxEvent.UI_CLICK)
                viewModel.setFamilyFriendlyArcadeMode(enabled)
            },
            onSendRmblynChat = { msg -> viewModel.sendRmblynFamilyChatMessage(msg) },
            onToggleStorytellerPlay = { viewModel.toggleStorytellerPlay() },
            onSelectStorytellerTrack = { key -> viewModel.selectStorytellerTrack(key) },
            onSetSleepTimerMinutes = { mins -> viewModel.setStorytellerSleepTimer(mins) },
            onRefreshStorytellerLibrary = { viewModel.refreshStorytellerLibrary() },
            onProbeHearthKeyHandshake = {
                viewModel.probeTailscaleHandshakeNow()
                activeSheet = ActiveEmulatorSheet.P2P_MULTIPLAYER_SYNC
                viewModel.toggleOasisUniversalOverlay(forceOpen = false)
            },
            onDismiss = {
                viewModel.toggleOasisUniversalOverlay(forceOpen = false)
            }
        )
    }

    if (isWorldMapModalOpen) {
        InteractiveWorldMapModal(
            state = gameState,
            coopPeer = coopPeer,
            customWaypoint = customWaypoint,
            onSetWaypoint = { wp -> customWaypoint = wp },
            onGenerateNewWorldSeed = {
                customWaypoint = null
                viewModel.resetOrGenerateNewMap()
            },
            onDismiss = { isWorldMapModalOpen = false }
        )
    }
    }
}

@Composable
private fun EmulatorTopRomBar(
    gameState: GameSessionState,
    p2pConnected: Boolean,
    isCloudAuthenticated: Boolean,
    isMusicMuted: Boolean,
    isHudDrawerExpanded: Boolean,
    isTouchPadVisible: Boolean,
    isLandscape: Boolean,
    displayMode: DisplayScreenMode,
    highContrastMode: Boolean = false,
    developerModeEnabled: Boolean = false,
    readyDirectivesCount: Int = 0,
    onToggleHighContrast: () -> Unit = {},
    onToggleMusic: () -> Unit,
    onToggleHudDrawer: () -> Unit,
    onToggleTouchPad: () -> Unit,
    onToggleOrientation: () -> Unit,
    onTogglePause: () -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleTvCastMode: () -> Unit,
    onOpenWorldMap: () -> Unit,
    onOpenSheet: (ActiveEmulatorSheet) -> Unit,
    onSignOut: () -> Unit
) {
    Surface(
        color = if (highContrastMode) HighContrastBlack else HandheldChassis,
        modifier = Modifier
            .fillMaxWidth()
            .border(if (highContrastMode) 2.dp else 1.dp, if (highContrastMode) HighContrastYellow else PhosphorGreen.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = if (isLandscape) 2.dp else 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wave, Points & Speed Telemetry
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ZombieCrimson.copy(alpha = 0.22f))
                            .border(1.dp, ZombieCrimson, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "WAVE ${gameState.wave}",
                            style = MaterialTheme.typography.labelLarge,
                            color = ZombieCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${gameState.points} PTS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MysteryGold,
                        fontWeight = FontWeight.Bold
                    )
                    if (gameState.comboCount > 1) {
                        Text(
                            text = "${gameState.comboCount}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = CryoCyan
                        )
                    }
                    // Speed multiplier indicator pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MysteryGold.copy(alpha = 0.16f))
                            .border(1.dp, MysteryGold.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .clickable { onOpenSheet(ActiveEmulatorSheet.SPEED_CONTROLLER) }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2gx", gameState.speedConfig.clockMultiplier),
                            style = MaterialTheme.typography.labelSmall,
                            color = MysteryGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Emulator Quick Controls (Offline/Cloud Badge, BGM Toggle, TV Cast, FastForward, Pause, Exit)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isCloudAuthenticated) PhosphorGreen.copy(alpha = 0.15f)
                                else CryoCyan.copy(alpha = 0.15f)
                            )
                            .clickable { onSignOut() }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                            .testTag("mode_badge_button")
                    ) {
                        Icon(
                            imageVector = if (isCloudAuthenticated) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = "Connection Mode",
                            tint = if (isCloudAuthenticated) PhosphorGreen else CryoCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isCloudAuthenticated) "CLOUD" else "OFFLINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCloudAuthenticated) PhosphorGreen else CryoCyan,
                            fontSize = 9.sp
                        )
                    }

                    IconButton(
                        onClick = onToggleMusic,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("toggle_bgm_music_button")
                    ) {
                        Icon(
                            imageVector = if (isMusicMuted) Icons.Default.MusicOff else Icons.Default.MusicNote,
                            contentDescription = "Toggle Retro Synth Soundtrack",
                            tint = if (isMusicMuted) TextMuted else PhosphorGreen
                        )
                    }

                    IconButton(
                        onClick = onToggleTvCastMode,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("toggle_tv_cast_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Toggle Big Screen TV Cast Mode",
                            tint = if (displayMode == DisplayScreenMode.TV_CAST_CINEMATIC) {
                                CryoCyan
                            } else {
                                TextMuted
                            }
                        )
                    }

                    IconButton(
                        onClick = onCycleSpeed,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("fast_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Cycle Emulator Speed",
                            tint = if (gameState.speedConfig.clockMultiplier > 1.05f) MysteryGold else TextMuted
                        )
                    }
                    IconButton(
                        onClick = onTogglePause,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("pause_game_button")
                    ) {
                        Icon(
                            imageVector = if (gameState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Pause or Resume ROM",
                            tint = PhosphorGreen
                        )
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Switch Account / Exit",
                            tint = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Second Row: Consumer Hub, Contracts, Manual, Settings, & Gameplay Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                EmulatorMenuChip(
                    icon = Icons.Default.Home,
                    label = "TITLE HUB",
                    accent = PhosphorGreen,
                    tag = "menu_title_hub",
                    onClick = { onOpenSheet(ActiveEmulatorSheet.TITLE_HUB) }
                )
                EmulatorMenuChip(
                    icon = Icons.Default.MilitaryTech,
                    label = if (readyDirectivesCount > 0) "CONTRACTS ($readyDirectivesCount!)" else "DOSSIER",
                    accent = MysteryGold,
                    tag = "menu_career_dossier",
                    onClick = { onOpenSheet(ActiveEmulatorSheet.CAREER_DOSSIER) }
                )
                EmulatorMenuChip(
                    icon = Icons.Default.MenuBook,
                    label = "MANUAL",
                    accent = CryoCyan,
                    tag = "menu_field_manual",
                    onClick = { onOpenSheet(ActiveEmulatorSheet.FIELD_MANUAL) }
                )
                EmulatorMenuChip(
                    icon = Icons.Default.Settings,
                    label = "SETTINGS",
                    accent = PhosphorGreen,
                    tag = "menu_store_settings",
                    onClick = { onOpenSheet(ActiveEmulatorSheet.STORE_SETTINGS_PRIVACY) }
                )
                EmulatorMenuChip(
                    icon = Icons.Default.Gamepad,
                    label = if (isTouchPadVisible) "PAD+WASD" else "WASD ONLY",
                    accent = if (isTouchPadVisible) PhosphorGreen else CryoCyan,
                    tag = "menu_toggle_touchpad",
                    onClick = onToggleTouchPad
                )
                EmulatorMenuChip(
                    icon = Icons.Default.AutoAwesome,
                    label = if (highContrastMode) "HC: ON (AAA)" else "A11Y / HC",
                    accent = if (highContrastMode) HighContrastYellow else MysteryGold,
                    tag = "high_contrast_toggle_button",
                    onClick = onToggleHighContrast
                )
                EmulatorMenuChip(
                    icon = Icons.Default.Refresh,
                    label = if (isLandscape) "PORTRAIT \u27F3" else "LANDSCAPE \u27F3",
                    accent = CryoCyan,
                    tag = "menu_rotate_orientation",
                    onClick = onToggleOrientation
                )
                EmulatorMenuChip(
                    icon = Icons.Default.AutoAwesome,
                    label = "WORLD ATLAS [M]",
                    accent = MysteryGold,
                    tag = "menu_world_atlas",
                    onClick = onOpenWorldMap
                )
                EmulatorMenuChip(
                    icon = Icons.Default.Refresh,
                    label = if (isHudDrawerExpanded) "HUD: FULL" else "HUD: MINIMAL",
                    accent = if (isHudDrawerExpanded) MysteryGold else CryoCyan,
                    tag = "menu_toggle_hud_drawer",
                    onClick = onToggleHudDrawer
                )
                EmulatorMenuChip(
                    icon = Icons.Default.Security,
                    label = "ARMORY",
                    accent = ZombieCrimson,
                    tag = "menu_weapon_armory",
                    onClick = { onOpenSheet(ActiveEmulatorSheet.WEAPON_ARMORY) }
                )
                EmulatorMenuChip(
                    icon = Icons.Default.AutoAwesome,
                    label = "ANVIL/7-ARMOR",
                    accent = MysteryGold,
                    tag = "menu_rpg_anvil_top",
                    onClick = { onOpenSheet(ActiveEmulatorSheet.ABYSSAL_RPG_ANVIL) }
                )
                EmulatorMenuChip(
                    icon = Icons.Default.Gamepad,
                    label = "MAPPER/TV",
                    accent = CryoCyan,
                    tag = "menu_controller_mapper",
                    onClick = { onOpenSheet(ActiveEmulatorSheet.CONTROLLER_MAPPER) }
                )
                EmulatorMenuChip(
                    icon = Icons.Default.Speed,
                    label = "SPEED",
                    accent = MysteryGold,
                    tag = "menu_speed_controller",
                    onClick = { onOpenSheet(ActiveEmulatorSheet.SPEED_CONTROLLER) }
                )
                EmulatorMenuChip(
                    icon = Icons.Default.WifiTethering,
                    label = if (p2pConnected) "P2P ON" else "MULTIPLAYER",
                    accent = if (p2pConnected) PhosphorGreen else CryoCyan,
                    tag = "menu_p2p_sync",
                    onClick = { onOpenSheet(ActiveEmulatorSheet.P2P_MULTIPLAYER_SYNC) }
                )
                EmulatorMenuChip(
                    icon = Icons.Default.Save,
                    label = "STATES",
                    accent = PhosphorGreen,
                    tag = "menu_save_states",
                    onClick = { onOpenSheet(ActiveEmulatorSheet.SAVE_SLOTS) }
                )
                EmulatorMenuChip(
                    icon = if (isMusicMuted) Icons.Default.MusicOff else Icons.Default.MusicNote,
                    label = if (isMusicMuted) "BGM: MUTE" else "BGM: SYNTH",
                    accent = if (isMusicMuted) TextMuted else PhosphorGreen,
                    tag = "menu_bgm_toggle",
                    onClick = onToggleMusic
                )
                if (developerModeEnabled) {
                    EmulatorMenuChip(
                        icon = Icons.Default.Terminal,
                        label = "KONSOLE",
                        accent = PhosphorGreen,
                        tag = "menu_konsole_debugger",
                        onClick = { onOpenSheet(ActiveEmulatorSheet.KONSOLE_DEBUGGER) }
                    )
                }
                EmulatorMenuChip(
                    icon = Icons.Default.AutoAwesome,
                    label = "AI DIRECTOR",
                    accent = PackPurple,
                    tag = "menu_tactical_ai",
                    onClick = { onOpenSheet(ActiveEmulatorSheet.AI_TACTICAL_DIRECTOR) }
                )
            }
        }
    }
}

@Composable
private fun EmulatorMenuChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(HandheldObsidian)
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WeaponAndAmmoTrackerBar(
    gameState: GameSessionState,
    onSelectWeapon: (String) -> Unit,
    onDropBarrel: () -> Unit,
    onTriggerReload: () -> Unit,
    onToggleAiBot: () -> Unit,
    onOpenArmory: () -> Unit
) {
    val currentAmmo = gameState.currentAmmoCount
    val maxMag = gameState.currentMaxMagSize.coerceAtLeast(1)
    val isReloading = gameState.reloadRemainingMs > 0
    val isLowAmmo = !isReloading && currentAmmo <= (maxMag / 4).coerceAtLeast(2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HandheldSurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: Weapon Quick-Switch Strip + Explosive Barrel Drop Chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DEFAULT_WEAPONS.forEach { weapon ->
                val isUnlocked = gameState.unlockedWeaponIds.contains(weapon.id)
                val isSelected = gameState.currentWeapon.id == weapon.id
                val ammo = gameState.currentMagAmmo[weapon.id] ?: weapon.magazineSize
                val borderColor = when {
                    isSelected -> PhosphorGreen
                    isUnlocked -> TextMuted
                    else -> HandheldObsidian
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) PhosphorGreen.copy(alpha = 0.2f) else HandheldObsidian)
                        .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                        .clickable {
                            if (isUnlocked || gameState.points >= weapon.unlockCost) {
                                onSelectWeapon(weapon.id)
                            } else {
                                onOpenArmory()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("weapon_slot_${weapon.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(weapon.colorHex))
                    )
                    Text(
                        text = if (isUnlocked) "${weapon.shortCode} [$ammo]" else "${weapon.shortCode} (${weapon.unlockCost})",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) PhosphorGreen else if (isUnlocked) TextCrisp else TextMuted
                    )
                }
            }

            // Quick Red Barrel Placement Chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MysteryGold.copy(alpha = 0.16f))
                    .border(1.dp, MysteryGold, RoundedCornerShape(6.dp))
                    .clickable(onClick = onDropBarrel)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("barrel_quick_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DROP BARREL [X] (150 PTS)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MysteryGold,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Row 2: Dedicated Reload & Magazine Bullet Pip Tracker + Telemetry
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(HandheldObsidian)
                .border(
                    1.dp,
                    when {
                        isReloading -> MysteryGold
                        isLowAmmo -> ZombieCrimson
                        else -> HandheldChassis
                    },
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Magazine Counter + Visual Bullet Pips or Reload Progress Bar
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isReloading) {
                            val secLeft = gameState.reloadRemainingMs / 1000f
                            String.format(Locale.US, "RELOADING %.2fs", secLeft)
                        } else if (isLowAmmo) {
                            "LOW AMMO: $currentAmmo / $maxMag"
                        } else {
                            "MAG: $currentAmmo / $maxMag"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isReloading -> MysteryGold
                            isLowAmmo -> ZombieCrimson
                            else -> PhosphorGreen
                        },
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "| HIT ${gameState.ammoTelemetry.accuracyPercent}% | RLD ${gameState.ammoTelemetry.reloadsCompleted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                if (isReloading) {
                    LinearProgressIndicator(
                        progress = { gameState.reloadProgressFraction },
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MysteryGold,
                        trackColor = HandheldChassis
                    )
                } else {
                    // Single fast Canvas draw for magazine bullet pips (zero child Box layout nodes)
                    val displayPips = min(maxMag, 24)
                    val filledPips = ((currentAmmo.toFloat() / maxMag.toFloat()) * displayPips).toInt()
                    val activePipColor = if (isLowAmmo) ZombieCrimson else PhosphorGreen
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(7.dp)
                    ) {
                        val pipW = 14f
                        val gap = 5f
                        for (i in 0 until displayPips) {
                            val px = i * (pipW + gap)
                            if (px + pipW > size.width) break
                            drawRect(
                                color = if (i < filledPips) activePipColor else HandheldChassis,
                                topLeft = Offset(px, 0f),
                                size = Size(pipW, size.height)
                            )
                        }
                    }
                }
            }

            // Right: 1-Tap Manual Reload Button + AI Auto-Bot Toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (isReloading) MysteryGold.copy(alpha = 0.25f)
                            else CryoCyan.copy(alpha = 0.16f)
                        )
                        .border(
                            1.dp,
                            if (isReloading) MysteryGold else CryoCyan,
                            RoundedCornerShape(5.dp)
                        )
                        .clickable { onTriggerReload() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                        .testTag("hud_reload_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload Magazine",
                        tint = if (isReloading) MysteryGold else CryoCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (isReloading) "RLD..." else "RELOAD",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isReloading) MysteryGold else CryoCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (gameState.aiBotEnabled) PhosphorGreen.copy(alpha = 0.2f)
                            else HandheldChassis
                        )
                        .border(
                            1.dp,
                            if (gameState.aiBotEnabled) PhosphorGreen else TextMuted,
                            RoundedCornerShape(5.dp)
                        )
                        .clickable { onToggleAiBot() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                        .testTag("hud_ai_bot_toggle"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Toggle AI Stress Bot",
                        tint = if (gameState.aiBotEnabled) PhosphorGreen else TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (gameState.aiBotEnabled) "BOT ON" else "BOT",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (gameState.aiBotEnabled) PhosphorGreen else TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TvCastStatusStrip(
    config: ControllerConfigState,
    aiBotEnabled: Boolean,
    onSwitchToTouch: () -> Unit,
    onAdjustZoom: (Int) -> Unit,
    onToggleAiBot: () -> Unit,
    onOpenKonsole: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HandheldChassis)
            .border(1.dp, CryoCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = CryoCyan,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "TV / LINUX BIG SCREEN MODE (Zoom ${config.cameraZoomPercent}%)",
                style = MaterialTheme.typography.labelSmall,
                color = CryoCyan,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "[ZOOM -]",
                style = MaterialTheme.typography.labelSmall,
                color = MysteryGold,
                modifier = Modifier
                    .clickable { onAdjustZoom(-10) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Text(
                text = "[ZOOM +]",
                style = MaterialTheme.typography.labelSmall,
                color = MysteryGold,
                modifier = Modifier
                    .clickable { onAdjustZoom(10) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Text(
                text = if (aiBotEnabled) "[BOT: ON]" else "[BOT: OFF]",
                style = MaterialTheme.typography.labelSmall,
                color = PhosphorGreen,
                modifier = Modifier
                    .clickable { onToggleAiBot() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Text(
                text = "[KONSOLE]",
                style = MaterialTheme.typography.labelSmall,
                color = CryoCyan,
                modifier = Modifier
                    .clickable { onOpenKonsole() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Text(
                text = "[SHOW TOUCH PAD]",
                style = MaterialTheme.typography.labelSmall,
                color = ZombieCrimson,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onSwitchToTouch() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

private class ViewportRenderTracker(
    initialX: Float,
    initialY: Float
) {
    var smoothCamX: Float = initialX
    var smoothCamY: Float = initialY
    var lastPlayerX: Float = initialX
    var lastPlayerY: Float = initialY
    var walkPhaseRad: Float = 0f
    var lastZoomFactor: Float = 0.65f
    var lastLightKey: Int = Int.MIN_VALUE
    var cachedLights: List<PointLightSource2D> = emptyList()
    var lastHullKey: Int = Int.MIN_VALUE
    var cachedHulls: List<ConvexShadowHull> = emptyList()
}

@Composable
private fun BoxBoyViewportCanvas(
    hudState: GameSessionState,
    gameStateFlow: StateFlow<GameSessionState>,
    frameTickState: LongState,
    liveInput: LiveInputState,
    coopPeer: CoopPeerState?,
    customWaypoint: Offset?,
    isLandscape: Boolean,
    cameraZoomPercent: Int,
    dpadScale: Float = 1.0f,
    crtScanlines: Boolean,
    highContrastMode: Boolean = false,
    colorblindHazardMarkers: Boolean = false,
    isTouchPadVisible: Boolean,
    onOpenWorldMap: () -> Unit,
    onTriggerReload: () -> Unit,
    onDropFortification: () -> Unit,
    onTriggerInteract: () -> Unit,
    onCycleWeapon: () -> Unit,
    onEquipInspectedLoot: () -> Unit,
    onSalvageInspectedLoot: () -> Unit,
    onDismissLootInspect: () -> Unit,
    onRestart: () -> Unit,
    onNewSeed: () -> Unit
) {
    // Plain non-Compose tracker: mutated ONLY inside DrawScope so ZERO Composable recompositions occur at 60 FPS!
    val tracker = remember {
        val init = gameStateFlow.value
        ViewportRenderTracker(init.playerX, init.playerY)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highContrastMode) HighContrastBlack else Color(0xFF0A1114))
            .border(
                width = if (highContrastMode) 2.5.dp else 1.5.dp,
                color = if (highContrastMode) HighContrastYellow else PhosphorGreen.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Direct 360 deg World Viewport Multi-Touch Pointer-ID Aiming & Firing (tracks independent aim pointer even while thumb holds D-Pad!)
                .pointerInput(isTouchPadVisible, dpadScale) {
                    awaitPointerEventScope {
                        var aimPointerId: PointerId? = null

                        fun updateAimFromCanvasOffset(pos: Offset) {
                            val dxScreen = pos.x - size.width / 2f
                            val dyScreen = pos.y - size.height / 2f
                            if (hypot(dxScreen, dyScreen) > 8f) {
                                liveInput.pointerAimAngleRad = kotlin.math.atan2(dyScreen, dxScreen)
                                liveInput.pointerAimActive = true
                                liveInput.pointerFireHold = true
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val cornerExclusionPx = (196f * dpadScale.coerceIn(0.65f, 1.45f)).dp.toPx()

                            if (aimPointerId == null) {
                                val candidate = event.changes.firstOrNull { change ->
                                    if (!change.pressed || change.isConsumed) return@firstOrNull false
                                    val inBottomCornerPadZone = isTouchPadVisible &&
                                        change.position.y > (size.height - cornerExclusionPx) &&
                                        (change.position.x < cornerExclusionPx || change.position.x > (size.width - cornerExclusionPx))
                                    !inBottomCornerPadZone
                                }
                                if (candidate != null) {
                                    aimPointerId = candidate.id
                                    updateAimFromCanvasOffset(candidate.position)
                                }
                            } else {
                                val tracked = event.changes.firstOrNull { it.id == aimPointerId }
                                if (tracked == null || !tracked.pressed) {
                                    aimPointerId = null
                                    liveInput.pointerFireHold = false
                                    liveInput.pointerAimActive = false
                                } else {
                                    updateAimFromCanvasOffset(tracked.position)
                                }
                            }
                        }
                    }
                }
                .testTag("boxboy_game_canvas")
        ) {
            // Reading frameTickState.longValue inside DrawScope invalidates ONLY the Skia draw layer each frame
            val tick = frameTickState.longValue
            val liveState = if (tick >= 0L) gameStateFlow.value else hudState

            val dxMove = liveState.playerX - tracker.lastPlayerX
            val dyMove = liveState.playerY - tracker.lastPlayerY
            val stepDist = hypot(dxMove, dyMove)
            if (stepDist > 0.04f && stepDist < 120f) {
                tracker.walkPhaseRad = (tracker.walkPhaseRad + stepDist * 0.19f) % (Math.PI.toFloat() * 20f)
            }
            tracker.lastPlayerX = liveState.playerX
            tracker.lastPlayerY = liveState.playerY

            // Lock camera 1:1 to player coordinates: eliminates aim-angle camera jerk & beat-frequency jitter
            tracker.smoothCamX = liveState.playerX
            tracker.smoothCamY = liveState.playerY

            // Recompute lights & shadow hulls only when discrete spatial buckets change
            val lightKey = ((liveState.playerX.toInt() shr 4) * 31) xor
                ((liveState.playerY.toInt() shr 4) * 17) xor
                ((liveState.aimAngleRad * 10f).toInt() * 13) xor
                (liveState.zombies.size shl 8) xor
                (liveState.lootDrops.size shl 12) xor
                (liveState.elementalPuddles.size shl 15) xor
                (liveState.fortifications.size shl 18) xor
                liveState.cameraTension.framingState.ordinal
            if (lightKey != tracker.lastLightKey || tracker.cachedLights.isEmpty()) {
                tracker.lastLightKey = lightKey
                tracker.cachedLights = AbyssalChiaroscuroLightingEngine.collectFrameLights(
                    state = liveState,
                    lanternRadiusBonus = liveState.armorRig.totalLanternRangeBonusPx +
                        liveState.activeModularWeaponForCurrentSlot.optic.lanternRangeBonusPx
                )
            }

            val hullKey = ((liveState.playerX.toInt() shr 6) * 31) xor
                ((liveState.playerY.toInt() shr 6) * 17) xor
                liveState.map.wallObstacles.size
            if (hullKey != tracker.lastHullKey || tracker.cachedHulls.isEmpty()) {
                tracker.lastHullKey = hullKey
                tracker.cachedHulls = AbyssalChiaroscuroLightingEngine.buildSimplifiedShadowHulls(
                    obstacles = liveState.map.wallObstacles,
                    playerX = liveState.playerX,
                    playerY = liveState.playerY
                )
            }

            // Adaptive Landscape & Portrait Camera Framing for the 3480x2040 Open City Metropolis
            val isViewportLandscape = size.width > size.height
            val baseScale = if (isViewportLandscape) {
                max(size.width / 1720f, size.height / 980f).coerceIn(0.42f, 0.88f)
            } else {
                min(size.width / 1380f, size.height / 1080f).coerceAtLeast(0.38f)
            }
            val tensionScale = (liveState.cameraTension.currentZoom / 2.2f).coerceIn(0.72f, 0.92f)
            val zoomFactor = (cameraZoomPercent.coerceIn(65, 140) / 100f) * baseScale * tensionScale
            tracker.lastZoomFactor = zoomFactor

            withTransform({
                translate(left = size.width / 2f, top = size.height / 2f)
                scale(scaleX = zoomFactor, scaleY = zoomFactor, pivot = Offset.Zero)
                translate(left = -tracker.smoothCamX, top = -tracker.smoothCamY)
            }) {
                with(OrthographicWorldRenderer) {
                    renderOrthographicWorld(
                        state = liveState,
                        coopPeer = coopPeer,
                        smoothCamX = tracker.smoothCamX,
                        smoothCamY = tracker.smoothCamY,
                        walkPhaseRad = tracker.walkPhaseRad,
                        isMoving = stepDist > 0.05f,
                        frameLights = tracker.cachedLights,
                        shadowHulls = tracker.cachedHulls
                    )
                }

                // High Contrast WCAG AAA & Colorblind Geometric Hazard Overlay Pass
                if (highContrastMode || colorblindHazardMarkers) {
                    // Player high-visibility beacon ring + aim laser
                    drawCircle(
                        color = HighContrastYellow,
                        radius = 32f,
                        center = Offset(liveState.playerX, liveState.playerY),
                        style = Stroke(width = 4f)
                    )
                    val aimLen = 220f
                    drawLine(
                        color = HighContrastCyan,
                        start = Offset(liveState.playerX, liveState.playerY),
                        end = Offset(
                            liveState.playerX + cos(liveState.aimAngleRad) * aimLen,
                            liveState.playerY + sin(liveState.aimAngleRad) * aimLen
                        ),
                        strokeWidth = 3.2f
                    )

                    // Squad Co-Op Peers high-visibility rings
                    liveState.coopPeers.forEach { sqPeer ->
                        drawCircle(
                            color = HighContrastCyan,
                            radius = 28f,
                            center = Offset(sqPeer.x, sqPeer.y),
                            style = Stroke(width = 3.5f)
                        )
                    }

                    // Zombies high-contrast outlines + geometric colorblind symbols
                    liveState.zombies.forEach { z ->
                        val zx = z.x
                        val zy = z.y
                        if (highContrastMode) {
                            drawCircle(
                                color = HighContrastWhite,
                                radius = z.archetype.radius + 6f,
                                center = Offset(zx, zy),
                                style = Stroke(width = 3f)
                            )
                        }
                        if (colorblindHazardMarkers) {
                            val badgeY = zy - z.archetype.radius - 16f
                            when (z.archetype) {
                                ZombieArchetype.CALCIFIED_STALKER -> {
                                    // Triangle \u25B2 for ranged Stalker
                                    drawLine(HighContrastYellow, Offset(zx, badgeY - 8f), Offset(zx - 8f, badgeY + 6f), 3f)
                                    drawLine(HighContrastYellow, Offset(zx - 8f, badgeY + 6f), Offset(zx + 8f, badgeY + 6f), 3f)
                                    drawLine(HighContrastYellow, Offset(zx + 8f, badgeY + 6f), Offset(zx, badgeY - 8f), 3f)
                                }
                                ZombieArchetype.ABYSSAL_WEAVER -> {
                                    // Diamond \u25C6 for mage Weaver
                                    drawLine(HighContrastCyan, Offset(zx, badgeY - 9f), Offset(zx - 9f, badgeY), 3f)
                                    drawLine(HighContrastCyan, Offset(zx - 9f, badgeY), Offset(zx, badgeY + 9f), 3f)
                                    drawLine(HighContrastCyan, Offset(zx, badgeY + 9f), Offset(zx + 9f, badgeY), 3f)
                                    drawLine(HighContrastCyan, Offset(zx + 9f, badgeY), Offset(zx, badgeY - 9f), 3f)
                                }
                                ZombieArchetype.DEVIL_BOSS -> {
                                    // Double Ring \u2B21 for Boss
                                    drawCircle(HighContrastYellow, radius = 11f, center = Offset(zx, badgeY), style = Stroke(width = 3.5f))
                                    drawCircle(HighContrastWhite, radius = 5f, center = Offset(zx, badgeY))
                                }
                                else -> {
                                    // Crisp Square \u25A0 for melee Shamblers/Runners/Brutes
                                    drawRect(
                                        color = HighContrastWhite,
                                        topLeft = Offset(zx - 6f, badgeY - 6f),
                                        size = Size(12f, 12f),
                                        style = Stroke(width = 2.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Optional CRT Scanlines Overlay
            if (crtScanlines) {
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.11f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.2f
                    )
                    y += 12f
                }
            }
        }

        // Phase 4 Time-Freeze Loot Inspection Overlay (renders when inspecting a ground drop)
        hudState.inspectedLootDrop?.let { inspectedDrop ->
            TimeFreezeLootInspectOverlay(
                state = hudState,
                drop = inspectedDrop,
                onEquip = onEquipInspectedLoot,
                onSalvage = onSalvageInspectedLoot,
                onDismiss = onDismissLootInspect
            )
        }

        // Non-Invasive Top-Left Compact HUD Pill (HP, Shield, Ammo, Reload, Fortify, District & Controls Hint)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HandheldObsidian.copy(alpha = 0.78f))
                .border(1.dp, PhosphorGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "HP ${hudState.hp}/${hudState.maxHp}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hudState.hp > 35) PhosphorGreen else ZombieCrimson,
                    fontWeight = FontWeight.Bold
                )
                if (hudState.shield > 0) {
                    Text(
                        text = "SHD ${hudState.shield}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CryoCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "KILLS ${hudState.totalKills}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextCrisp
                )
                Text(
                    text = "ZOMBIES ${hudState.zombies.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MysteryGold
                )
            }
            LinearProgressIndicator(
                progress = { (hudState.hp.toFloat() / hudState.maxHp.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .width(190.dp)
                    .height(4.dp),
                color = if (hudState.hp > 35) PhosphorGreen else ZombieCrimson,
                trackColor = HandheldChassis,
                strokeCap = StrokeCap.Round
            )
            // Compact Weapon + Ammo + Reload + Fortify Action Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isReloading = hudState.reloadRemainingMs > 0
                Text(
                    text = "${hudState.currentWeapon.shortCode}: ${hudState.currentAmmoCount}/${hudState.currentMaxMagSize}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isReloading) MysteryGold else CryoCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onCycleWeapon() }
                )
                Text(
                    text = if (isReloading) "[RLD...]" else "[R] RELOAD",
                    style = MaterialTheme.typography.labelSmall,
                    color = MysteryGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onTriggerReload() }
                )
                Text(
                    text = "[Q] ${hudState.selectedFortificationType.shortCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PhosphorGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onDropFortification() }
                )
            }
            Text(
                text = "WASD: Move | Click/Drag or Arrows: Aim & Fire | E: Interact | M: Atlas",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 9.sp
            )
        }

        // Top-Right Live County Minimap Radar (tap to open full-screen 6-Town World Atlas & GPS Waypoint Setter)
        LiveCountyMinimapRadar(
            hudState = hudState,
            gameStateFlow = gameStateFlow,
            frameTickState = frameTickState,
            coopPeer = coopPeer,
            customWaypoint = customWaypoint,
            isLandscape = isLandscape,
            onOpenFullWorldMap = onOpenWorldMap,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )

        // Top-Center Nearby Interactive Station / Borderlands 2 Swap-on-Spot Comparison Prompt
        val nearbyStation = hudState.nearbyStation
        val nearbyLoot = hudState.nearbyLootDrop
        if (nearbyStation != null || nearbyLoot != null) {
            val swapCard = nearbyLoot?.weaponPayload?.let { candidateWeapon ->
                com.example.game.loot.borderlands.SwapOnSpotLoadoutManager.buildWeaponComparisonCard(
                    equipped = hudState.activeModularWeaponForCurrentSlot,
                    candidate = candidateWeapon
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(HandheldObsidian.copy(alpha = 0.90f))
                    .border(1.5.dp, MysteryGold, RoundedCornerShape(8.dp))
                    .clickable { onTriggerInteract() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = nearbyStation?.let { "[PRESS E / TAP B] ${it.label}" }
                        ?: "[PRESS E / TAP] SWAP ON SPOT: ${nearbyLoot?.displayTitle}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MysteryGold,
                    fontWeight = FontWeight.Bold
                )
                if (swapCard != null) {
                    Text(
                        text = "${swapCard.manufacturerName} | ${swapCard.elementalAffinity.name} | ${swapCard.statDeltas.joinToString(" · ") { "${it.label} ${it.arrowBadge}${it.candidateFormatted}" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CryoCyan,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Bottom-Center Risk of Rain 2 Drop-Only Passive Relic Ribbon
        val hudView = com.example.ui.hud.SwapCardAndRelicRibbonHud.buildHudViewState(
            currencyPoints = hudState.points,
            comboStreak = hudState.comboCount,
            comboMultiplier = (1 + hudState.comboCount / 5).coerceAtMost(8).toFloat(),
            relicState = hudState.passiveRelics,
            transport = com.example.network.p2p.OfflineHostTransportKind.WIFI_LAN_HOTSPOT
        )
        if (hudView.relicChips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (!isLandscape && isTouchPadVisible) 220.dp else 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(HandheldObsidian.copy(alpha = 0.86f))
                    .border(1.dp, PackPurple.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("ror2_relic_ribbon"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RELICS:",
                    style = MaterialTheme.typography.labelSmall,
                    color = PackPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                for (chip in hudView.relicChips) {
                    Text(
                        text = chip.stackBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MysteryGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Game Over / Pause CRT Modal Overlay
        if (hudState.isGameOver || hudState.isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HandheldObsidian.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (hudState.isGameOver) "SURVIVOR OVERRUN" else "EMULATOR PAUSED",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (hudState.isGameOver) ZombieCrimson else PhosphorGreen
                    )
                    Text(
                        text = "WAVE ${hudState.wave} | POINTS ${hudState.points} | KILLS ${hudState.totalKills}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MysteryGold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRestart,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorGreen,
                                contentColor = HandheldObsidian
                            ),
                            modifier = Modifier.testTag("restart_wave_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("REPLAY SEED", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onNewSeed,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CryoCyan,
                                contentColor = HandheldObsidian
                            ),
                            modifier = Modifier.testTag("reroll_seed_button")
                        ) {
                            Text("NEW CITY SEED", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Non-Invasive Translucent Floating Corner Touch Gamepad Overlay.
 * Places the Virtual 8-Way D-Pad in the bottom-left corner and the ABXY Action Diamond
 * in the bottom-right corner directly over the full-screen city viewport without any
 * opaque background chassis blocking the center of the screen!
 */
@Composable
private fun FloatingCornerGamepadOverlay(
    config: ControllerConfigState,
    gamepadMapping: GamepadMapping,
    frameTickState: LongState,
    isLandscape: Boolean,
    isReloading: Boolean,
    nearbyStationLabel: String?,
    onMoveDirection: (Float, Float) -> Unit,
    onTouchButtonEvent: (TouchButtonId, Boolean) -> Unit
) {
    val scaleFactor = (config.dpadScalePercent / 100f).coerceIn(0.6f, 1.35f) * (if (isLandscape) 0.94f else 1.0f)
    val dpadSize = (118.dp * scaleFactor).coerceIn(88.dp, 152.dp)
    val opacity = (config.buttonOpacityPercent / 100f).coerceIn(0.25f, 0.82f)
    val hasNearbyInteract = nearbyStationLabel != null
    val swapSides = config.preset == OverlayPreset.TACTICAL_CLAW
    val edgePad = if (isLandscape) 18.dp else 12.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Bottom-Left Floating Cluster
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = edgePad, bottom = 10.dp)
                .alpha(opacity),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShoulderBumperButton(
                label = gamepadMapping.labelForTouchButton(TouchButtonId.BTN_L1, isReloading, hasNearbyInteract),
                isContinuousHold = gamepadMapping.actionForTouchButton(TouchButtonId.BTN_L1).isContinuousHold,
                tag = "gamepad_l_shoulder",
                onPressChanged = { pressed -> onTouchButtonEvent(TouchButtonId.BTN_L1, pressed) }
            )
            if (!swapSides) {
                Virtual8WayDpad(
                    sizeDp = dpadSize,
                    frameTickState = frameTickState,
                    onDirectionChanged = onMoveDirection
                )
            } else {
                ActionClusterDiamond(
                    scale = scaleFactor * 0.92f,
                    gamepadMapping = gamepadMapping,
                    isReloading = isReloading,
                    hasNearbyInteract = hasNearbyInteract,
                    onTouchButtonEvent = onTouchButtonEvent
                )
            }
        }

        // Bottom-Right Floating Cluster
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = edgePad, bottom = 10.dp)
                .alpha(opacity),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShoulderBumperButton(
                label = gamepadMapping.labelForTouchButton(TouchButtonId.BTN_R1, isReloading, hasNearbyInteract),
                isContinuousHold = gamepadMapping.actionForTouchButton(TouchButtonId.BTN_R1).isContinuousHold,
                tag = "gamepad_r_shoulder",
                onPressChanged = { pressed -> onTouchButtonEvent(TouchButtonId.BTN_R1, pressed) }
            )
            if (!swapSides) {
                ActionClusterDiamond(
                    scale = scaleFactor * 0.92f,
                    gamepadMapping = gamepadMapping,
                    isReloading = isReloading,
                    hasNearbyInteract = hasNearbyInteract,
                    onTouchButtonEvent = onTouchButtonEvent
                )
            } else {
                Virtual8WayDpad(
                    sizeDp = dpadSize,
                    frameTickState = frameTickState,
                    onDirectionChanged = onMoveDirection
                )
            }
        }
    }
}

@Composable
private fun ShoulderBumperButton(
    label: String,
    isContinuousHold: Boolean,
    tag: String,
    onPressChanged: (Boolean) -> Unit
) {
    var isHolding by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHolding) CryoCyan.copy(alpha = 0.28f) else HandheldObsidian)
            .border(1.5.dp, CryoCyan.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .pointerInput(isContinuousHold) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pid = down.id
                    down.consume()
                    isHolding = true
                    onPressChanged(true)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.firstOrNull { it.id == pid && it.pressed }
                            if (active == null) break
                            active.consume()
                        }
                    } finally {
                        isHolding = false
                        onPressChanged(false)
                    }
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = CryoCyan,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Virtual8WayDpad(
    sizeDp: androidx.compose.ui.unit.Dp,
    frameTickState: LongState,
    onDirectionChanged: (Float, Float) -> Unit
) {
    // Read ONLY inside the Canvas DrawScope below -- dragging thumbstick or holding WASD causes zero recompositions
    val knobOffsetState = remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(HandheldObsidian)
            .border(2.dp, PhosphorGreen.copy(alpha = 0.80f), CircleShape)
            .pointerInput(Unit) {
                // Zero-slop continuous 360 deg analog pointer-ID tracking: responds on the very first touch frame
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val trackedPointerId = down.id
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = max(size.width * 0.42f, 1f)

                    fun updateFromPos(pos: Offset) {
                        val delta = pos - center
                        val dist = hypot(delta.x, delta.y)
                        val clamped = if (dist > maxRadius) delta * (maxRadius / dist) else delta
                        knobOffsetState.value = clamped
                        onDirectionChanged(clamped.x / maxRadius, clamped.y / maxRadius)
                    }

                    updateFromPos(down.position)
                    down.consume()

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChange = event.changes.firstOrNull { it.id == trackedPointerId && it.pressed }
                            if (activeChange == null) {
                                break
                            }
                            updateFromPos(activeChange.position)
                            activeChange.consume()
                        }
                    } finally {
                        knobOffsetState.value = Offset.Zero
                        onDirectionChanged(0f, 0f)
                    }
                }
            }
            .testTag("virtual_dpad"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read frameTickState inside DrawScope so WASD-to-Joystick analog movement animates the thumbstick live!
            val _tick = frameTickState.longValue
            val touchKnob = knobOffsetState.value
            val maxRadius = max(size.width * 0.42f, 1f)
            val currentKnob = if (hypot(touchKnob.x, touchKnob.y) > 1f) {
                touchKnob
            } else {
                Offset(
                    ControllerManager.smoothedJoyX * maxRadius,
                    ControllerManager.smoothedJoyY * maxRadius
                )
            }
            val cx = size.width / 2f
            val cy = size.height / 2f
            val armThickness = size.width * 0.27f
            // Outer tactical ring
            drawCircle(
                color = Color(0xFF101B14),
                radius = size.width * 0.44f,
                center = Offset(cx, cy)
            )
            // 3D Beveled Cross Arms
            drawRect(
                color = Color(0xFF0A120D),
                topLeft = Offset(cx - armThickness / 2f, size.height * 0.14f + 3f),
                size = Size(armThickness, size.height * 0.72f)
            )
            drawRect(
                color = Color(0xFF0A120D),
                topLeft = Offset(size.width * 0.14f, cy - armThickness / 2f + 3f),
                size = Size(size.width * 0.72f, armThickness)
            )
            drawRect(
                color = HandheldSurfaceVariant,
                topLeft = Offset(cx - armThickness / 2f, size.height * 0.12f),
                size = Size(armThickness, size.height * 0.76f)
            )
            drawRect(
                color = HandheldSurfaceVariant,
                topLeft = Offset(size.width * 0.12f, cy - armThickness / 2f),
                size = Size(size.width * 0.76f, armThickness)
            )
            // Center pivot socket
            drawCircle(
                color = Color(0xFF0B140E),
                radius = size.width * 0.14f,
                center = Offset(cx, cy)
            )
            // Thumb stick shadow + 3D cap
            val stickCenter = Offset(cx + currentKnob.x * 0.62f, cy + currentKnob.y * 0.62f)
            drawCircle(
                color = Color(0xAA000000),
                radius = size.width * 0.19f,
                center = Offset(stickCenter.x, stickCenter.y + 4f)
            )
            drawCircle(
                color = PhosphorGreen,
                radius = size.width * 0.18f,
                center = stickCenter
            )
            drawCircle(
                color = Color(0xFF99FFC2),
                radius = size.width * 0.11f,
                center = Offset(stickCenter.x - 2f, stickCenter.y - 2f)
            )
        }
    }
}

@Composable
private fun ActionClusterDiamond(
    scale: Float,
    gamepadMapping: GamepadMapping,
    isReloading: Boolean,
    hasNearbyInteract: Boolean,
    onTouchButtonEvent: (TouchButtonId, Boolean) -> Unit
) {
    val buttonSize = (52.dp * scale).coerceIn(48.dp, 68.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Top Button (BTN_Y)
        MappedArcadeButton(
            buttonId = TouchButtonId.BTN_Y,
            gamepadMapping = gamepadMapping,
            isReloading = isReloading,
            hasNearbyInteract = hasNearbyInteract,
            color = MysteryGold,
            sizeDp = buttonSize,
            tag = "gamepad_btn_y_reload",
            onTouchButtonEvent = onTouchButtonEvent
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Left Button (BTN_X)
            MappedArcadeButton(
                buttonId = TouchButtonId.BTN_X,
                gamepadMapping = gamepadMapping,
                isReloading = isReloading,
                hasNearbyInteract = hasNearbyInteract,
                color = CryoCyan,
                sizeDp = buttonSize,
                tag = "gamepad_btn_x_barrel",
                onTouchButtonEvent = onTouchButtonEvent
            )

            // Right/Primary Button (BTN_A)
            MappedArcadeButton(
                buttonId = TouchButtonId.BTN_A,
                gamepadMapping = gamepadMapping,
                isReloading = isReloading,
                hasNearbyInteract = hasNearbyInteract,
                color = ZombieCrimson,
                sizeDp = buttonSize * 1.12f,
                tag = "gamepad_btn_a_fire",
                onTouchButtonEvent = onTouchButtonEvent
            )
        }

        // Bottom Button (BTN_B)
        MappedArcadeButton(
            buttonId = TouchButtonId.BTN_B,
            gamepadMapping = gamepadMapping,
            isReloading = isReloading,
            hasNearbyInteract = hasNearbyInteract,
            color = if (hasNearbyInteract) MysteryGold else PhosphorGreen,
            sizeDp = buttonSize,
            tag = "gamepad_btn_b_interact",
            onTouchButtonEvent = onTouchButtonEvent
        )
    }
}

@Composable
private fun MappedArcadeButton(
    buttonId: TouchButtonId,
    gamepadMapping: GamepadMapping,
    isReloading: Boolean,
    hasNearbyInteract: Boolean,
    color: Color,
    sizeDp: androidx.compose.ui.unit.Dp,
    tag: String,
    onTouchButtonEvent: (TouchButtonId, Boolean) -> Unit
) {
    var isHolding by remember { mutableStateOf(false) }
    val label = gamepadMapping.labelForTouchButton(buttonId, isReloading, hasNearbyInteract)
    val isPrimaryFire = gamepadMapping.actionForTouchButton(buttonId) == GamepadAction.FIRE_WEAPON

    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(if (isHolding) color else color.copy(alpha = if (isPrimaryFire) 0.28f else 0.2f))
            .border(if (isPrimaryFire) 2.5.dp else 2.dp, color, CircleShape)
            .pointerInput(buttonId, gamepadMapping) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pid = down.id
                    down.consume()
                    isHolding = true
                    onTouchButtonEvent(buttonId, true)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.firstOrNull { it.id == pid && it.pressed }
                            if (active == null) break
                            active.consume()
                        }
                    } finally {
                        isHolding = false
                        onTouchButtonEvent(buttonId, false)
                    }
                }
            }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isHolding || isPrimaryFire) TextCrisp else color,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun handleHardwareGamepadEvent(
    keyCode: Int,
    isDown: Boolean,
    config: ControllerConfigState,
    liveInput: LiveInputState,
    onReloadTap: (Boolean) -> Unit,
    onCycleWeapon: (Int) -> Unit
): Boolean {
    val nowMs = SystemClock.uptimeMillis()
    if (ControllerManager.onHardwareKeyEvent(keyCode, isDown, nowMs)) {
        return true
    }
    return when (keyCode) {
        config.keyFire -> {
            liveInput.isFiring = isDown
            true
        }
        config.keyInteract -> {
            if (isDown) liveInput.isInteractPressed = true
            true
        }
        config.keyPlaceBarrel -> {
            if (isDown) liveInput.isAltBarrelDrop = true
            true
        }
        config.keyReload -> {
            onReloadTap(isDown)
            true
        }
        config.keyPrevWeapon -> {
            if (isDown) onCycleWeapon(-1)
            true
        }
        config.keyNextWeapon -> {
            if (isDown) onCycleWeapon(1)
            true
        }
        else -> false
    }
}
