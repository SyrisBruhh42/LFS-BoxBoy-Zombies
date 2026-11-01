package com.example.viewmodel

import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BoxBoyRepository
import com.example.data.ControllerProfile
import com.example.data.CryptoSyncEngine
import com.example.data.DEFAULT_WEAPONS
import com.example.data.PerkType
import com.example.data.SaveSlot
import com.example.game.ArmorRigState
import com.example.game.ArmorSlot
import com.example.game.BoxBoyGameEngine
import com.example.game.CameraFramingState
import com.example.game.DepthEscrowState
import com.example.game.ElementalAffinity
import com.example.game.FortificationType
import com.example.game.GameSessionState
import com.example.game.LightingAtmosphereMode
import com.example.game.LootDropEntity
import com.example.game.LootPayloadKind
import com.example.game.LootRarity
import com.example.game.MazeDirector
import com.example.game.ModularWeaponGenerator
import com.example.game.OfflineTacticalAiEngine
import com.example.game.ProceduralMapEngine
import com.example.game.RpgArmorGenerator
import com.example.game.SpeedConfig
import com.example.game.ZombieArchetype
import com.example.input.ControllerManager
import com.example.input.GamepadAction
import com.example.input.GamepadMapping
import com.example.input.TouchButtonId
import com.example.network.HeadlessKonsoleServer
import com.example.network.LocalP2pSocketManager
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "BoxBoyVM"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class OverlayPreset(val label: String) {
    MYBOY_GBA("MyBoy! Classic GBA"),
    TACTICAL_CLAW("Tactical Claw 4-Finger"),
    COMPACT_ARCADE("Compact Arcade Bottom")
}

enum class DisplayScreenMode(val label: String, val subtitle: String) {
    HANDHELD_TOUCH("Handheld Touch", "Full MyBoy! on-screen gamepad overlay for phone & tablet play"),
    TV_CAST_CINEMATIC("TV / Linux Cast (Big Screen)", "Widescreen zoomed-out arena with minimal touch clutter for TV casting & hardware gamepads"),
    SPLIT_DECK("Arcade Deck Bezels", "Wide tactical HUD with high-contrast side control zones")
}

data class ControllerConfigState(
    val profileName: String = "MyBoy! Classic GBA",
    val dpadScalePercent: Int = 110,
    val buttonOpacityPercent: Int = 82,
    val cameraZoomPercent: Int = 100,
    val displayMode: DisplayScreenMode = DisplayScreenMode.HANDHELD_TOUCH,
    val preset: OverlayPreset = OverlayPreset.MYBOY_GBA,
    val crtScanlinesEnabled: Boolean = true,
    val showFpsCounter: Boolean = true,
    val turboFireEnabled: Boolean = true,
    val keyFire: Int = KeyEvent.KEYCODE_BUTTON_A,
    val keyInteract: Int = KeyEvent.KEYCODE_BUTTON_B,
    val keyPlaceBarrel: Int = KeyEvent.KEYCODE_BUTTON_X,
    val keyReload: Int = KeyEvent.KEYCODE_BUTTON_Y,
    val keyPrevWeapon: Int = KeyEvent.KEYCODE_BUTTON_L1,
    val keyNextWeapon: Int = KeyEvent.KEYCODE_BUTTON_R1,
    val awaitingKeyBindAction: String? = null
) {
    fun toKeyMapCode(): String {
        return "PRESET:${preset.name}|DISP:${displayMode.name}|ZOOM:$cameraZoomPercent|" +
            "CRT:$crtScanlinesEnabled|TURBO:$turboFireEnabled|" +
            "FIRE:$keyFire|INT:$keyInteract|BARREL:$keyPlaceBarrel|RELOAD:$keyReload|PREV:$keyPrevWeapon|NEXT:$keyNextWeapon"
    }
}

data class AiDirectiveResult(
    val operationTitle: String,
    val mapSeed: Long,
    val startingWave: Int,
    val bonusPoints: Int,
    val recommendedWeaponId: String,
    val tacticalBriefing: String
)

sealed interface AiDirectorUiState {
    data object Idle : AiDirectorUiState
    data object Loading : AiDirectorUiState
    data class StreamingAdvice(val partialText: String) : AiDirectorUiState
    data class DirectiveReady(val directive: AiDirectiveResult) : AiDirectorUiState
    data class AdviceReady(val text: String) : AiDirectorUiState
    data class Error(val message: String) : AiDirectorUiState
}

class BoxBoyViewModel(
    private val repository: BoxBoyRepository,
    private val localPrefs: SharedPreferences,
    val currentUserId: String,
    val isCloudAuthenticated: Boolean
) : ViewModel() {

    private val engine = BoxBoyGameEngine()

    private val _gameState = MutableStateFlow(GameSessionState())
    val gameState: StateFlow<GameSessionState> = _gameState.asStateFlow()

    private val _controllerConfig = MutableStateFlow(ControllerConfigState())
    val controllerConfig: StateFlow<ControllerConfigState> = _controllerConfig.asStateFlow()

    private val _syncPassphrase = MutableStateFlow("BOXBOY-LINK-2026")
    val syncPassphrase: StateFlow<String> = _syncPassphrase.asStateFlow()

    private val _localOfflineSaves = MutableStateFlow<List<CryptoSyncEngine.GameSaveSnapshot>>(emptyList())
    val localOfflineSaves: StateFlow<List<CryptoSyncEngine.GameSaveSnapshot>> = _localOfflineSaves.asStateFlow()

    private val _exportedCapsule = MutableStateFlow("")
    val exportedCapsule: StateFlow<String> = _exportedCapsule.asStateFlow()

    private val _aiState = MutableStateFlow<AiDirectorUiState>(AiDirectorUiState.Idle)
    val aiState: StateFlow<AiDirectorUiState> = _aiState.asStateFlow()

    private val _preferOfflineAiEngine = MutableStateFlow(!isCloudAuthenticated)
    val preferOfflineAiEngine: StateFlow<Boolean> = _preferOfflineAiEngine.asStateFlow()

    val gamepadMapping: StateFlow<GamepadMapping> = ControllerManager.activeMapping

    val p2pManager = LocalP2pSocketManager(
        scope = viewModelScope,
        onRemoteSaveReceived = { snapshot ->
            applySnapshotToGame(snapshot, "P2P Encrypted Socket Sync")
            saveLocalOfflineSlot(snapshot)
        },
        onTacticalMessageReceived = { netMsg ->
            _gameState.value = _gameState.value.copy(
                statusToast = "P2P [${netMsg.senderTag}]: ${netMsg.content}"
            )
        }
    )

    val konsoleServer = HeadlessKonsoleServer(
        scope = viewModelScope,
        commandDispatcher = ::dispatchKonsoleCommand
    )

    // Strict Auth-Gated Cloud Flows: In 100% Offline Mode (isCloudAuthenticated == false),
    // never attach Firestore listeners so offline play works with zero account requirement.
    val cloudSaveSlots: StateFlow<UiState<List<SaveSlot>>> = (
        if (isCloudAuthenticated) {
            repository.observeSaveSlots()
                .map<List<SaveSlot>, UiState<List<SaveSlot>>> { UiState.Success(it) }
                .catch { err ->
                    Log.w(TAG, "Error observing cloud save slots", err)
                    emit(UiState.Error(err.localizedMessage ?: "Failed to observe cloud save slots"))
                }
        } else {
            flowOf(UiState.Success(emptyList()))
        }
        ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = if (isCloudAuthenticated) UiState.Loading else UiState.Success(emptyList())
    )

    val cloudControllerProfiles: StateFlow<UiState<List<ControllerProfile>>> = (
        if (isCloudAuthenticated) {
            repository.observeControllerProfiles()
                .map<List<ControllerProfile>, UiState<List<ControllerProfile>>> { UiState.Success(it) }
                .catch { err ->
                    Log.w(TAG, "Error observing controller profiles", err)
                    emit(UiState.Error(err.localizedMessage ?: "Failed to observe controller profiles"))
                }
        } else {
            flowOf(UiState.Success(emptyList()))
        }
        ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = if (isCloudAuthenticated) UiState.Loading else UiState.Success(emptyList())
    )

    private val directiveSchema = Schema.obj(
        properties = mapOf(
            "operationTitle" to Schema.string(description = "Tactical codename for this procedural Boxhead Zombies mission"),
            "mapSeed" to Schema.integer(description = "Deterministic positive integer map seed between 100000 and 99999999"),
            "startingWave" to Schema.integer(description = "Recommended starting wave between 1 and 15"),
            "bonusPoints" to Schema.integer(description = "Starting survival points between 500 and 5000"),
            "recommendedWeaponId" to Schema.enumeration(
                values = listOf("pistol", "uzi", "shotgun", "barrels", "ar", "railgun", "raybox"),
                description = "Featured weapon ID for this mission"
            ),
            "tacticalBriefing" to Schema.string(description = "Concise 2-sentence survival strategy for the procedural bunker rooms and zombie archetypes")
        )
    )

    private val directorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = directiveSchema
            }
        )
    }

    private val fastRadioModel by lazy {
        Firebase.ai.generativeModel("gemini-3.5-flash-lite")
    }

    private var frameSyncCounter = 0

    init {
        loadOfflineSlotsFromPrefs()
        loadGamepadMappingFromPrefs()
        konsoleServer.startServer()
    }

    // --- 60 FPS GAME LOOP TICK ---

    fun tickFrame(
        deltaMs: Long,
        moveX: Float,
        moveY: Float,
        isFiring: Boolean,
        isAltBarrelDrop: Boolean,
        isInteractPressed: Boolean,
        aimAngleOverrideRad: Float? = null
    ) {
        val peer = p2pManager.coopPeerState.value
        val next = engine.stepFrame(
            state = _gameState.value,
            deltaMsRaw = deltaMs,
            moveXInput = moveX,
            moveYInput = moveY,
            isFiringInput = isFiring,
            isAltBarrelDropInput = isAltBarrelDrop,
            isInteractPressedInput = isInteractPressed,
            coopPeerX = peer?.x,
            coopPeerY = peer?.y,
            aimAngleOverrideRad = aimAngleOverrideRad
        )
        _gameState.value = next

        frameSyncCounter++
        if (frameSyncCounter % 2 == 0 && p2pManager.sessionStatus.value.isConnected) {
            p2pManager.sendPlayerState(
                peerName = "Survivor-${currentUserId.take(4)}",
                x = next.playerX,
                y = next.playerY,
                aimAngleRad = next.aimAngleRad,
                hp = next.hp,
                maxHp = next.maxHp,
                weaponId = next.currentWeapon.id,
                isFiring = isFiring,
                score = next.points,
                kills = next.totalKills
            )
        }
    }

    fun setPaused(paused: Boolean) {
        _gameState.value = _gameState.value.copy(isPaused = paused)
    }

    fun cycleEmulatorSpeed() {
        val cur = _gameState.value.speedConfig.clockMultiplier
        val nextClock = when {
            cur < 0.9f -> 1.0f
            cur < 1.2f -> 1.5f
            cur < 1.8f -> 2.0f
            else -> 0.75f
        }
        updateSpeedConfig {
            it.copy(
                presetName = "Clock ${nextClock}x",
                clockMultiplier = nextClock
            )
        }
    }

    fun updateSpeedConfig(transform: (SpeedConfig) -> SpeedConfig) {
        val updated = transform(_gameState.value.speedConfig)
        _gameState.value = _gameState.value.copy(
            speedConfig = updated,
            statusToast = "SPEED TUNED: Clock ${updated.clockMultiplier}x | Move ${updated.playerMoveMultiplier}x | Zombies ${updated.zombieSpeedMultiplier}x"
        )
    }

    fun toggleAiBot() {
        val nextBot = !_gameState.value.aiBotEnabled
        _gameState.value = _gameState.value.copy(
            aiBotEnabled = nextBot,
            statusToast = if (nextBot) "AUTONOMOUS AI/LLM COMBAT BOT ENABLED" else "AUTONOMOUS AI BOT DISABLED"
        )
    }

    fun spawnStressZombies(count: Int, archetype: ZombieArchetype = ZombieArchetype.SHAMBLER) {
        _gameState.value = engine.spawnStressZombies(_gameState.value, count, archetype)
    }

    fun runStressBenchmark(zombieCount: Int = 120, ticks: Int = 300): String {
        val (updatedState, report) = engine.runHeadlessBenchmark(_gameState.value, zombieCount, ticks)
        _gameState.value = updatedState
        return "BENCHMARK COMPLETE: ${report.ticksSimulated} ticks @ ${report.zombieCountPeak} zombies | " +
            "Total: ${"%.2f".format(report.totalElapsedMs)}ms | Avg: ${"%.1f".format(report.avgTickMicros)}µs/tick (~${report.estimatedMaxFps} FPS ceiling) | " +
            "Bot Kills: ${report.killsDuringTest}"
    }

    fun cycleWeapon(delta: Int) {
        val st = _gameState.value
        val list = st.unlockedWeaponIds
        if (list.isEmpty()) return
        val nextIdx = ((st.currentWeaponIndex + delta) % list.size + list.size) % list.size
        val nextWpn = DEFAULT_WEAPONS.find { it.id == list[nextIdx] }
        _gameState.value = st.copy(
            currentWeaponIndex = nextIdx,
            reloadRemainingMs = 0L,
            statusToast = "SWITCHED TO ${nextWpn?.name ?: "WEAPON"} (Lv.${st.weaponLevels[list[nextIdx]] ?: 1})"
        )
    }

    fun triggerManualReload() {
        val st = _gameState.value
        val wpn = st.currentWeapon
        val maxMag = st.currentMaxMagSize
        if ((st.currentMagAmmo[wpn.id] ?: maxMag) < maxMag && st.reloadRemainingMs == 0L) {
            val perkReloadFactor = if (PerkType.SPEED_COLA in st.activePerks) 0.5f else 1.0f
            val totalReloadMs = ((wpn.reloadMs * perkReloadFactor) / st.speedConfig.fireReloadMultiplier.coerceAtLeast(0.25f))
                .toLong().coerceAtLeast(180L)
            _gameState.value = st.copy(
                reloadRemainingMs = totalReloadMs,
                ammoTelemetry = st.ammoTelemetry.copy(currentReloadTotalMs = totalReloadMs),
                currentMagAmmo = st.currentMagAmmo + (wpn.id to 0),
                statusToast = "RELOADING ${wpn.shortCode} (${"%.1f".format(totalReloadMs / 1000f)}s)..."
            )
        }
    }

    fun unlockOrEquipWeaponFromArmory(weaponId: String) {
        val st = _gameState.value
        val spec = DEFAULT_WEAPONS.find { it.id == weaponId } ?: return
        if (weaponId in st.unlockedWeaponIds) {
            val idx = st.unlockedWeaponIds.indexOf(weaponId)
            _gameState.value = st.copy(
                currentWeaponIndex = idx,
                reloadRemainingMs = 0L,
                statusToast = "EQUIPPED ${spec.name}"
            )
        } else if (st.points >= spec.unlockCost) {
            val updatedList = st.unlockedWeaponIds + weaponId
            val updatedLevels = st.weaponLevels + (weaponId to 1)
            val updatedAmmo = st.currentMagAmmo + (weaponId to spec.magazineSize)
            _gameState.value = st.copy(
                points = st.points - spec.unlockCost,
                unlockedWeaponIds = updatedList,
                weaponLevels = updatedLevels,
                currentMagAmmo = updatedAmmo,
                currentWeaponIndex = updatedList.lastIndex,
                reloadRemainingMs = 0L,
                statusToast = "UNLOCKED & EQUIPPED ${spec.name}!"
            )
        }
    }

    fun upgradeWeaponInArmory(weaponId: String) {
        val st = _gameState.value
        val spec = DEFAULT_WEAPONS.find { it.id == weaponId } ?: return
        val curLvl = st.weaponLevels[weaponId] ?: 1
        if (curLvl >= 5) return
        val cost = (spec.upgradeBaseCost * curLvl)
        if (st.points >= cost && weaponId in st.unlockedWeaponIds) {
            val nextLvl = curLvl + 1
            _gameState.value = st.copy(
                points = st.points - cost,
                weaponLevels = st.weaponLevels + (weaponId to nextLvl),
                currentMagAmmo = st.currentMagAmmo + (weaponId to (spec.magazineSize + (nextLvl - 1) * 4)),
                statusToast = "UPGRADED ${spec.name} TO PACK-A-PUNCH MK-$nextLvl!"
            )
        }
    }

    fun resetOrGenerateNewMap(newSeed: Long = System.currentTimeMillis() and 0xFFFFFFFL) {
        val cleanSeed = if (newSeed <= 0L) 19960227L else newSeed
        val newMap = ProceduralMapEngine.generateMap(cleanSeed, ProceduralMapEngine.DEFAULT_UNLOCKED_ROOMS)
        val prev = _gameState.value
        _gameState.value = GameSessionState(
            mapSeed = cleanSeed,
            map = newMap,
            unlockedRooms = ProceduralMapEngine.DEFAULT_UNLOCKED_ROOMS,
            mazeState = MazeDirector.createInitialMazeState(cleanSeed, newMap),
            speedConfig = prev.speedConfig,
            cameraTension = prev.cameraTension,
            armorRig = prev.armorRig,
            depthEscrow = prev.depthEscrow,
            statusToast = "NEW OPEN CITY METROPOLIS GENERATED (SEED #$cleanSeed)"
        )
    }

    // --- PHASE 1: LIGHTING & CAMERA TENSION ACTIONS ---

    fun cycleLightingAtmosphereMode() {
        val st = _gameState.value
        val modes = LightingAtmosphereMode.entries
        val nextMode = modes[(st.cameraTension.lightingMode.ordinal + 1) % modes.size]
        _gameState.value = st.copy(
            cameraTension = st.cameraTension.copy(lightingMode = nextMode),
            statusToast = "ATMOSPHERE: ${nextMode.label}"
        )
    }

    fun toggleAutoCameraTension() {
        val st = _gameState.value
        val nextAuto = !st.cameraTension.autoTensionEnabled
        _gameState.value = st.copy(
            cameraTension = st.cameraTension.copy(autoTensionEnabled = nextAuto),
            statusToast = if (nextAuto) {
                "CAMERA TENSION: AUTO (2.2x Corridor <-> 1.1x Siege Cubic Tween)"
            } else {
                "CAMERA TENSION: MANUAL LOCK (${st.cameraTension.framingState.displayName})"
            }
        )
    }

    fun cycleCameraFramingState() {
        val st = _gameState.value
        val states = CameraFramingState.entries
        val nextFraming = states[(st.cameraTension.framingState.ordinal + 1) % states.size]
        _gameState.value = st.copy(
            cameraTension = st.cameraTension.copy(
                autoTensionEnabled = false,
                framingState = nextFraming,
                startZoom = st.cameraTension.currentZoom,
                targetZoom = nextFraming.targetZoomMultiplier,
                tweenElapsedMs = 0L
            ),
            statusToast = "CAMERA FRAMING: ${nextFraming.displayName}"
        )
    }

    // --- PHASE 3: FORTIFICATION & SIEGE FABRICATOR ACTIONS ---

    fun cycleFortificationType() {
        val st = _gameState.value
        val types = FortificationType.entries
        val nextType = types[(st.selectedFortificationType.ordinal + 1) % types.size]
        _gameState.value = st.copy(
            selectedFortificationType = nextType,
            statusToast = "FORTIFY SLOT: ${nextType.displayName} (${nextType.costPoints} PTS)"
        )
    }

    fun selectFortificationType(type: FortificationType) {
        _gameState.value = _gameState.value.copy(
            selectedFortificationType = type,
            statusToast = "FORTIFY SLOT: ${type.displayName} (${type.costPoints} PTS)"
        )
    }

    fun triggerSiegeFabricatorNow() {
        val st = _gameState.value
        if (st.siegeFabricator.isActive) return
        _gameState.value = st.copy(
            siegeFabricator = st.siegeFabricator.copy(
                isActive = true,
                roomIndex = st.mazeState.activeRoomSector,
                remainingMs = 20_000L,
                totalDurationMs = 20_000L
            ),
            statusToast = "SIEGE FABRICATOR OVERDRIVE (20s) — Camera Tweening to 1.1x!"
        )
    }

    // --- PHASE 4: MODULAR WEAPONS & TIME-FREEZE LOOT INSPECTION ACTIONS ---

    fun inspectLootDrop(drop: LootDropEntity?) {
        _gameState.value = _gameState.value.copy(
            inspectedLootDrop = drop,
            statusToast = if (drop != null) "TIME-FREEZE INSPECTING: ${drop.displayTitle}" else "RESUMED COMBAT"
        )
    }

    fun equipInspectedLootDrop() {
        val st = _gameState.value
        val drop = st.inspectedLootDrop ?: st.nearbyLootDrop ?: return
        val remainingDrops = st.lootDrops.filter { it.id != drop.id }

        when (drop.payloadKind) {
            LootPayloadKind.MODULAR_WEAPON -> {
                val wpn = drop.weaponPayload ?: return
                val updatedUnlocked = (st.unlockedWeaponIds + wpn.baseWeaponId).distinct()
                val updatedInv = st.modularWeaponInventory + (wpn.baseWeaponId to wpn)
                _gameState.value = st.copy(
                    unlockedWeaponIds = updatedUnlocked,
                    currentWeaponIndex = updatedUnlocked.indexOf(wpn.baseWeaponId).coerceAtLeast(0),
                    equippedModularWeapon = wpn,
                    modularWeaponInventory = updatedInv,
                    currentMagAmmo = st.currentMagAmmo + (wpn.baseWeaponId to wpn.effectiveMagSize),
                    lootDrops = remainingDrops,
                    nearbyLootDrop = null,
                    inspectedLootDrop = null,
                    statusToast = "EQUIPPED [${wpn.rarity.shortBadge}] ${wpn.customTitle}!"
                )
            }
            LootPayloadKind.RPG_ARMOR_PIECE -> {
                val piece = drop.armorPayload ?: return
                val updatedRig = st.armorRig.copy(
                    equippedSlots = st.armorRig.equippedSlots + (piece.slot to piece)
                )
                val newMaxShield = 50 + updatedRig.totalBonusMaxShield
                _gameState.value = st.copy(
                    armorRig = updatedRig,
                    maxShield = newMaxShield,
                    shield = newMaxShield,
                    lootDrops = remainingDrops,
                    nearbyLootDrop = null,
                    inspectedLootDrop = null,
                    statusToast = "EQUIPPED [${piece.rarity.shortBadge}] ${piece.displayName} (${updatedRig.paletteProfile.activeSetName})!"
                )
            }
        }
    }

    fun salvageInspectedLootDrop() {
        val st = _gameState.value
        val drop = st.inspectedLootDrop ?: st.nearbyLootDrop ?: return
        val remainingDrops = st.lootDrops.filter { it.id != drop.id }
        val alchemistBonus = if (st.armorRig.countForSet(com.example.game.ArmorSetArchetype.VOID_ALCHEMIST) >= 2) 1.25f else 1.0f
        val scrapEarned = (drop.rarity.salvageScrapValue * alchemistBonus).toInt()
        val updatedEscrow = st.depthEscrow.copy(abyssalScrap = st.depthEscrow.abyssalScrap + scrapEarned)
        _gameState.value = st.copy(
            depthEscrow = updatedEscrow,
            lootDrops = remainingDrops,
            nearbyLootDrop = null,
            inspectedLootDrop = null,
            statusToast = "SALVAGED ${drop.displayTitle} FOR +$scrapEarned ABYSSAL SCRAP!"
        )
    }

    fun rollProceduralModularWeaponWithScrap() {
        val st = _gameState.value
        val costScrap = 40
        if (st.depthEscrow.abyssalScrap < costScrap && st.points < 900) return
        val rolled = ModularWeaponGenerator.generateProceduralWeapon(
            seed = System.currentTimeMillis() xor st.mapSeed,
            wave = st.wave + 1,
            guaranteedMinRarity = LootRarity.RARE,
            preferredBaseWeaponId = st.currentWeapon.id
        )
        val updatedEscrow = if (st.depthEscrow.abyssalScrap >= costScrap) {
            st.depthEscrow.copy(abyssalScrap = st.depthEscrow.abyssalScrap - costScrap)
        } else st.depthEscrow
        val updatedPoints = if (st.depthEscrow.abyssalScrap >= costScrap) st.points else (st.points - 900)
        _gameState.value = st.copy(
            points = updatedPoints,
            depthEscrow = updatedEscrow,
            equippedModularWeapon = rolled,
            modularWeaponInventory = st.modularWeaponInventory + (rolled.baseWeaponId to rolled),
            currentMagAmmo = st.currentMagAmmo + (rolled.baseWeaponId to rolled.effectiveMagSize),
            statusToast = "FORGED 4-PART [${rolled.rarity.shortBadge}] ${rolled.customTitle}!"
        )
    }

    // --- PHASE 5: 7-SLOT RPG ARMOR, UPGRADE ANVIL & DEPTH ESCROW ACTIONS ---

    fun anvilTemperEquippedWeapon() {
        val st = _gameState.value
        val mod = st.activeModularWeaponForCurrentSlot
        val scrapCost = 30 + mod.anvilUpgradeTier * 15
        if (st.depthEscrow.abyssalScrap < scrapCost) return
        val tempered = mod.copy(anvilUpgradeTier = mod.anvilUpgradeTier + 1)
        _gameState.value = st.copy(
            depthEscrow = st.depthEscrow.copy(
                abyssalScrap = st.depthEscrow.abyssalScrap - scrapCost,
                totalAnvilUpgrades = st.depthEscrow.totalAnvilUpgrades + 1
            ),
            equippedModularWeapon = tempered,
            modularWeaponInventory = st.modularWeaponInventory + (tempered.baseWeaponId to tempered),
            currentMagAmmo = st.currentMagAmmo + (tempered.baseWeaponId to tempered.effectiveMagSize),
            statusToast = "ANVIL TEMPERED ${tempered.customTitle} TO +${tempered.anvilUpgradeTier}!"
        )
    }

    fun anvilReforgeWeaponElement() {
        val st = _gameState.value
        val mod = st.activeModularWeaponForCurrentSlot
        val scrapCost = 25
        if (st.depthEscrow.abyssalScrap < scrapCost) return
        val elements = ElementalAffinity.entries
        val nextElement = elements[(mod.magazine.elementalAffinity.ordinal + 1) % elements.size]
        val reforged = mod.copy(
            magazine = mod.magazine.copy(elementalAffinity = nextElement)
        )
        _gameState.value = st.copy(
            depthEscrow = st.depthEscrow.copy(
                abyssalScrap = st.depthEscrow.abyssalScrap - scrapCost,
                totalAnvilUpgrades = st.depthEscrow.totalAnvilUpgrades + 1
            ),
            equippedModularWeapon = reforged,
            modularWeaponInventory = st.modularWeaponInventory + (reforged.baseWeaponId to reforged),
            statusToast = "ANVIL REFORGED ELEMENT -> ${nextElement.displayName.uppercase()}!"
        )
    }

    fun anvilUpgradeArmorSlot(slot: ArmorSlot) {
        val st = _gameState.value
        val piece = st.armorRig.equippedSlots[slot] ?: return
        val scrapCost = 25 + piece.anvilTier * 12
        if (st.depthEscrow.abyssalScrap < scrapCost) return
        val upgraded = piece.copy(anvilTier = piece.anvilTier + 1)
        val updatedRig = st.armorRig.copy(equippedSlots = st.armorRig.equippedSlots + (slot to upgraded))
        val newMaxShield = 50 + updatedRig.totalBonusMaxShield
        _gameState.value = st.copy(
            armorRig = updatedRig,
            maxShield = newMaxShield,
            shield = newMaxShield,
            depthEscrow = st.depthEscrow.copy(
                abyssalScrap = st.depthEscrow.abyssalScrap - scrapCost,
                totalAnvilUpgrades = st.depthEscrow.totalAnvilUpgrades + 1
            ),
            statusToast = "ANVIL FORTIFIED ${upgraded.displayName} TO +${upgraded.anvilTier} (DEF ${upgraded.effectiveDefense})!"
        )
    }

    fun anvilCraftArmorPiece(slot: ArmorSlot) {
        val st = _gameState.value
        val scrapCost = 35
        if (st.depthEscrow.abyssalScrap < scrapCost) return
        val crafted = RpgArmorGenerator.generateProceduralArmorPiece(
            seed = System.currentTimeMillis() xor (slot.ordinal * 4099L),
            wave = st.wave + 1,
            targetSlot = slot,
            minRarity = LootRarity.RARE
        )
        val updatedRig = st.armorRig.copy(equippedSlots = st.armorRig.equippedSlots + (slot to crafted))
        val newMaxShield = 50 + updatedRig.totalBonusMaxShield
        _gameState.value = st.copy(
            armorRig = updatedRig,
            maxShield = newMaxShield,
            shield = newMaxShield,
            depthEscrow = st.depthEscrow.copy(
                abyssalScrap = st.depthEscrow.abyssalScrap - scrapCost,
                totalAnvilUpgrades = st.depthEscrow.totalAnvilUpgrades + 1
            ),
            statusToast = "FORGED [${crafted.rarity.shortBadge}] ${crafted.displayName} (${updatedRig.paletteProfile.activeSetName})!"
        )
    }

    fun bankPointsIntoDepthEscrow(amount: Int = 500) {
        val st = _gameState.value
        val deposit = amount.coerceAtMost(st.points)
        if (deposit <= 0) return
        val updatedEscrow = st.depthEscrow.copy(
            bankedEscrowPoints = st.depthEscrow.bankedEscrowPoints + deposit
        )
        localPrefs.edit().putInt("depth_escrow_points_v1", updatedEscrow.bankedEscrowPoints).apply()
        _gameState.value = st.copy(
            points = st.points - deposit,
            depthEscrow = updatedEscrow,
            statusToast = "DEPTH ESCROW: Banked $deposit PTS (Vault Total: ${updatedEscrow.bankedEscrowPoints} PTS)"
        )
    }

    fun withdrawPointsFromDepthEscrow() {
        val st = _gameState.value
        val withdraw = st.depthEscrow.bankedEscrowPoints
        if (withdraw <= 0) return
        val updatedEscrow = st.depthEscrow.copy(bankedEscrowPoints = 0)
        localPrefs.edit().putInt("depth_escrow_points_v1", 0).apply()
        _gameState.value = st.copy(
            points = st.points + withdraw,
            depthEscrow = updatedEscrow,
            statusToast = "DEPTH ESCROW: Extracted +$withdraw PTS into active run!"
        )
    }

    // --- HEADLESS KONSOLE CLI / LLM COMMAND DISPATCHER ---

    fun dispatchKonsoleCommand(rawCommand: String): String {
        val tokens = rawCommand.trim().split(Regex("\\s+"))
        val verb = tokens.firstOrNull()?.uppercase() ?: return ""
        return when (verb) {
            "HELP" -> """
                KONSOLE COMMANDS (TCP :48900 & In-App CLI):
                  MAP                                  -> Render live 2D ASCII Bunker Map
                  STATE | JSON                         -> Dump complete structured JSON telemetry
                  STEP <ms> [mx] [my] [fire] [act]     -> Step deterministic engine (e.g. STEP 100 1 0 1 0)
                  SPAWN <count> [SHAMBLER|SPRINTER|CALCIFIED_STALKER|ABYSSAL_WEAVER|DEVIL_BOSS] -> Spawn hostiles
                  STRESS [zombieCount] [ticks]         -> Run headless stress benchmark (e.g. STRESS 150 300)
                  BOT [ON|OFF]                         -> Toggle autonomous AI combat & kiting bot
                  SPEED <clock> <move> <zombie> <fire> -> Set dual-axis speed multipliers
                  MAZE                                 -> Inspect Phase 2 7-Node Ring Buffer & Chunk Entropy
                  LIGHT                                -> Cycle Phase 1 Chiaroscuro Lighting & Camera Tension
                  LOOT                                 -> Spawn & equip a Phase 4 4-Part Modular Weapon
                  ANVIL                                -> Temper weapon & upgrade Phase 5 7-Slot Armor Rig
                  GIVE <points>                        -> Grant survival points & Abyssal Scrap
                  WAVE <number>                        -> Jump to target wave
                  WEAPON <id>                          -> Unlock & equip weapon (pistol/uzi/shotgun/barrels/ar/railgun/raybox)
                  RELOAD                               -> Trigger tactical reload
                  UNLOCK_ALL                           -> Unlock all 6 bunker sectors, perks & Legendary gear
                  AI_MISSION [theme]                   -> Synthesize 100% offline AI procedural mission directive
                  AI_RADIO                             -> Generate 100% offline tactical survival radio advice
                  RESET [seed]                         -> Generate fresh procedural bunker
            """.trimIndent()
            "MAP" -> HeadlessKonsoleServer.renderAsciiBunkerMap(_gameState.value)
            "STATE", "JSON" -> HeadlessKonsoleServer.dumpStateJson(_gameState.value)
            "MAZE" -> {
                val ms = _gameState.value.mazeState
                val cur = ms.currentRoomNode
                "7-NODE RING BUFFER (${ms.ringBufferNodes.size}/7 active) | Active Sector: 0${ms.activeRoomSector + 1} " +
                    "[${cur?.shapeTemplate?.displayName}] | Entropy: ${cur?.timeSpentSeconds ?: 0}s/180s (${cur?.entropyStage?.label}) | " +
                    "Non-Euclidean Rewrites: ${ms.nonEuclideanRewritesCount} | Event: ${ms.lastTopologicalEvent}"
            }
            "LIGHT" -> {
                cycleLightingAtmosphereMode()
                val ct = _gameState.value.cameraTension
                "PHASE 1 LIGHTING: ${ct.lightingMode.label} | Camera Framing: ${ct.framingState.displayName} (Zoom ${"%.2f".format(ct.currentZoom)}x)"
            }
            "LOOT" -> {
                rollProceduralModularWeaponWithScrap()
                val w = _gameState.value.activeModularWeaponForCurrentSlot
                "FORGED PHASE 4 MODULAR WEAPON: [${w.rarity.displayName}] ${w.customTitle} | DMG:${w.effectiveDamage} MAG:${w.effectiveMagSize} CRIT:${(w.illuminatedCritMultiplier * 100).toInt()}%"
            }
            "ANVIL" -> {
                _gameState.value = _gameState.value.copy(
                    depthEscrow = _gameState.value.depthEscrow.copy(abyssalScrap = _gameState.value.depthEscrow.abyssalScrap + 200)
                )
                anvilTemperEquippedWeapon()
                ArmorSlot.entries.forEach { anvilUpgradeArmorSlot(it) }
                val rig = _gameState.value.armorRig
                "PHASE 5 ANVIL UPGRADED: Weapon +${_gameState.value.activeModularWeaponForCurrentSlot.anvilUpgradeTier} | 7-Slot Armor DEF:${rig.totalDefenseRating} (${(rig.damageReductionFraction * 100).toInt()}% DR) | Set: ${rig.paletteProfile.activeSetName}"
            }
            "STEP" -> {
                val ms = tokens.getOrNull(1)?.toLongOrNull() ?: 16L
                val mx = tokens.getOrNull(2)?.toFloatOrNull() ?: 0f
                val my = tokens.getOrNull(3)?.toFloatOrNull() ?: 0f
                val fire = (tokens.getOrNull(4)?.toIntOrNull() ?: 0) != 0
                val act = (tokens.getOrNull(5)?.toIntOrNull() ?: 0) != 0
                val next = engine.stepFrame(
                    state = _gameState.value.copy(isPaused = false, inspectedLootDrop = null),
                    deltaMsRaw = ms,
                    moveXInput = mx,
                    moveYInput = my,
                    isFiringInput = fire,
                    isAltBarrelDropInput = false,
                    isInteractPressedInput = act,
                    coopPeerX = null,
                    coopPeerY = null
                )
                _gameState.value = next
                "STEPPED ${ms}ms -> Player(${next.playerX.toInt()},${next.playerY.toInt()}) HP:${next.hp} SHD:${next.shield} Zombies:${next.zombies.size} Zoom:${"%.2f".format(next.cameraTension.currentZoom)}x"
            }
            "SPAWN" -> {
                val count = tokens.getOrNull(1)?.toIntOrNull() ?: 25
                val archName = tokens.getOrNull(2)?.uppercase() ?: "SHAMBLER"
                val arch = ZombieArchetype.entries.find { it.name.contains(archName) } ?: ZombieArchetype.SHAMBLER
                spawnStressZombies(count, arch)
                "SPAWNED $count ${arch.label}s. Active Zombies: ${_gameState.value.zombies.size}"
            }
            "STRESS" -> {
                val zCount = tokens.getOrNull(1)?.toIntOrNull() ?: 120
                val ticks = tokens.getOrNull(2)?.toIntOrNull() ?: 300
                runStressBenchmark(zCount, ticks)
            }
            "BOT" -> {
                val arg = tokens.getOrNull(1)?.uppercase()
                val enable = when (arg) {
                    "ON", "1", "TRUE" -> true
                    "OFF", "0", "FALSE" -> false
                    else -> !_gameState.value.aiBotEnabled
                }
                _gameState.value = _gameState.value.copy(aiBotEnabled = enable, isPaused = false, inspectedLootDrop = null)
                "AI BOT = $enable"
            }
            "SPEED" -> {
                val clk = tokens.getOrNull(1)?.toFloatOrNull() ?: 1.0f
                val mv = tokens.getOrNull(2)?.toFloatOrNull() ?: 1.0f
                val zSpd = tokens.getOrNull(3)?.toFloatOrNull() ?: 1.0f
                val fr = tokens.getOrNull(4)?.toFloatOrNull() ?: 1.0f
                updateSpeedConfig {
                    SpeedConfig(
                        presetName = "Konsole Custom",
                        clockMultiplier = clk.coerceIn(0.25f, 3.0f),
                        playerMoveMultiplier = mv.coerceIn(0.4f, 2.5f),
                        zombieSpeedMultiplier = zSpd.coerceIn(0.3f, 2.5f),
                        fireReloadMultiplier = fr.coerceIn(0.4f, 3.0f)
                    )
                }
                "SPEED UPDATED: Clock=${clk}x Move=${mv}x Zombie=${zSpd}x FireReload=${fr}x"
            }
            "GIVE" -> {
                val pts = tokens.getOrNull(1)?.toIntOrNull() ?: 5000
                val st = _gameState.value
                _gameState.value = st.copy(
                    points = st.points + pts,
                    depthEscrow = st.depthEscrow.copy(abyssalScrap = st.depthEscrow.abyssalScrap + 250)
                )
                "GRANTED +$pts PTS & +250 ABYSSAL SCRAP (Total: ${_gameState.value.points} PTS)"
            }
            "WAVE" -> {
                val w = (tokens.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, 999)
                _gameState.value = _gameState.value.copy(
                    wave = w,
                    zombiesRemainingInWave = 8 + w * 4,
                    statusToast = "KONSOLE SET WAVE $w"
                )
                "JUMPED TO WAVE $w"
            }
            "WEAPON" -> {
                val wId = tokens.getOrNull(1)?.lowercase() ?: "raybox"
                val spec = DEFAULT_WEAPONS.find { it.id == wId } ?: DEFAULT_WEAPONS.last()
                val st = _gameState.value
                val list = (st.unlockedWeaponIds + spec.id).distinct()
                _gameState.value = st.copy(
                    unlockedWeaponIds = list,
                    currentWeaponIndex = list.indexOf(spec.id).coerceAtLeast(0),
                    currentMagAmmo = st.currentMagAmmo + (spec.id to spec.magazineSize)
                )
                "EQUIPPED ${spec.name}"
            }
            "RELOAD" -> {
                triggerManualReload()
                "TRIGGERED RELOAD (${_gameState.value.reloadRemainingMs}ms)"
            }
            "UNLOCK_ALL" -> {
                val st = _gameState.value
                val allRooms = (0..5).toSet()
                val allWpns = DEFAULT_WEAPONS.map { it.id }
                _gameState.value = st.copy(
                    unlockedRooms = allRooms,
                    map = ProceduralMapEngine.generateMap(st.mapSeed, allRooms),
                    unlockedWeaponIds = allWpns,
                    weaponLevels = allWpns.associateWith { 5 },
                    points = st.points + 25000,
                    depthEscrow = st.depthEscrow.copy(abyssalScrap = st.depthEscrow.abyssalScrap + 1000, abyssalCrystals = 25),
                    activePerks = PerkType.entries.toSet(),
                    maxHp = 200,
                    hp = 200,
                    statusToast = "KONSOLE UNLOCKED ALL SECTORS, PERKS, SCRAP & MK-5 WEAPONS!"
                )
                "UNLOCKED ALL 6 SECTORS, ALL 5 PERKS, +1000 SCRAP, AND ALL MK-5 WEAPONS"
            }
            "AI_MISSION" -> {
                val theme = tokens.drop(1).joinToString(" ").ifBlank {
                    "Nightmare Boxhead Red Devil siege with explosive barrels"
                }
                val directive = OfflineTacticalAiEngine.synthesizeOfflineDirective(theme, _gameState.value)
                applyAiDirective(directive)
                "APPLIED OFFLINE AI MISSION: ${directive.operationTitle} | Seed #${directive.mapSeed} | Wave ${directive.startingWave} | +${directive.bonusPoints} PTS | Weapon: ${directive.recommendedWeaponId} | Briefing: ${directive.tacticalBriefing}"
            }
            "AI_RADIO" -> {
                OfflineTacticalAiEngine.generateOfflineRadioCallout(_gameState.value)
            }
            "RESET" -> {
                val seed = tokens.getOrNull(1)?.toLongOrNull() ?: (System.currentTimeMillis() and 0xFFFFFFFL)
                resetOrGenerateNewMap(seed)
                "RESET MAP WITH SEED #$seed"
            }
            else -> "Unknown command '$verb'. Type HELP for available Konsole commands."
        }
    }

    // --- OFFLINE + CLOUD + P2P ENCRYPTED SAVE SYSTEM ---

    fun buildCurrentSnapshot(slotName: String): CryptoSyncEngine.GameSaveSnapshot {
        val st = _gameState.value
        return CryptoSyncEngine.GameSaveSnapshot(
            slotName = slotName.ifBlank { "Slot - Wave ${st.wave}" },
            highestWave = st.wave,
            totalKills = st.totalKills,
            points = st.points,
            unlockedWeaponIds = st.unlockedWeaponIds,
            weaponLevels = st.weaponLevels,
            activePerks = st.activePerks.map { it.name },
            mapSeed = st.mapSeed,
            unlockedRoomsCount = st.unlockedRooms.size,
            deviceTag = "MyBoy-Android-${currentUserId.take(5)}",
            timestampEpochMs = System.currentTimeMillis()
        )
    }

    fun saveStateOfflineAndCloud(slotName: String) {
        val snap = buildCurrentSnapshot(slotName)
        saveLocalOfflineSlot(snap)
        val encrypted = CryptoSyncEngine.encryptSnapshot(snap, _syncPassphrase.value)
        _exportedCapsule.value = encrypted

        if (isCloudAuthenticated) {
            val loadoutSummary = snap.unlockedWeaponIds.joinToString(", ") { id ->
                val name = DEFAULT_WEAPONS.find { it.id == id }?.shortCode ?: id.uppercase()
                val lv = snap.weaponLevels[id] ?: 1
                "$name Lv$lv"
            }
            viewModelScope.launch {
                repository.createSaveSlot(
                    slotName = snap.slotName,
                    highestWave = snap.highestWave,
                    totalKills = snap.totalKills,
                    points = snap.points,
                    loadoutSummary = loadoutSummary,
                    encryptedPayload = encrypted,
                    deviceTag = snap.deviceTag
                )
            }
            _gameState.value = _gameState.value.copy(
                statusToast = "SAVED (Offline AES-GCM + Cloud Firestore): ${snap.slotName}"
            )
        } else {
            _gameState.value = _gameState.value.copy(
                statusToast = "SAVED 100% OFFLINE (AES-256-GCM): ${snap.slotName}"
            )
        }
    }

    fun loadFromCloudSaveSlot(slot: SaveSlot) {
        val decrypted = CryptoSyncEngine.decryptSnapshot(slot.encryptedPayload, _syncPassphrase.value)
        decrypted.onSuccess { snap ->
            applySnapshotToGame(snap, "Cloud Slot '${slot.slotName}'")
        }.onFailure {
            val st = _gameState.value
            _gameState.value = st.copy(
                wave = slot.highestWave.toInt().coerceAtLeast(1),
                totalKills = slot.totalKills.toInt().coerceAtLeast(0),
                points = slot.points.toInt().coerceAtLeast(500),
                isGameOver = false,
                hp = st.maxHp,
                statusToast = "LOADED CLOUD SLOT: ${slot.slotName} (Wave ${slot.highestWave})"
            )
        }
    }

    fun deleteCloudSaveSlot(slotId: String) {
        if (!isCloudAuthenticated) return
        viewModelScope.launch {
            repository.deleteSaveSlot(slotId)
        }
    }

    fun applySnapshotToGame(snap: CryptoSyncEngine.GameSaveSnapshot, sourceLabel: String) {
        val unlockedRooms = (0 until snap.unlockedRoomsCount.coerceIn(1, 6)).toSet()
        val perks = snap.activePerks.mapNotNull { name ->
            PerkType.entries.find { it.name == name }
        }.toSet()
        val maxHp = if (PerkType.JUGGERNOG in perks) 200 else 100
        _gameState.value = GameSessionState(
            mapSeed = snap.mapSeed,
            map = ProceduralMapEngine.generateMap(snap.mapSeed, unlockedRooms),
            unlockedRooms = unlockedRooms,
            hp = maxHp,
            maxHp = maxHp,
            wave = snap.highestWave,
            zombiesRemainingInWave = 8 + snap.highestWave * 4,
            points = snap.points,
            totalKills = snap.totalKills,
            unlockedWeaponIds = snap.unlockedWeaponIds.ifEmpty { listOf("pistol") },
            weaponLevels = snap.weaponLevels.ifEmpty { mapOf("pistol" to 1) },
            speedConfig = _gameState.value.speedConfig,
            activePerks = perks,
            statusToast = "RESTORED FROM $sourceLabel (Wave ${snap.highestWave})"
        )
    }

    fun updateSyncPassphrase(newPass: String) {
        _syncPassphrase.value = newPass
    }

    fun exportCurrentEncryptedCapsule(): String {
        val snap = buildCurrentSnapshot("P2P Capsule Wave ${_gameState.value.wave}")
        val capsule = CryptoSyncEngine.encryptSnapshot(snap, _syncPassphrase.value)
        _exportedCapsule.value = capsule
        return capsule
    }

    fun importEncryptedCapsule(capsuleText: String): Result<CryptoSyncEngine.GameSaveSnapshot> {
        val res = CryptoSyncEngine.decryptSnapshot(capsuleText, _syncPassphrase.value)
        res.onSuccess { snap ->
            applySnapshotToGame(snap, "Encrypted P2P Capsule")
            saveLocalOfflineSlot(snap)
        }
        return res
    }

    fun pushCurrentSaveOverP2pSocket() {
        val snap = buildCurrentSnapshot("Socket Sync Wave ${_gameState.value.wave}")
        p2pManager.pushEncryptedSaveOverSocket(snap, _syncPassphrase.value)
    }

    fun sendP2pTacticalMessage(messageText: String) {
        val sender = "Survivor-${currentUserId.take(4)}"
        val sentToSocket = p2pManager.sendTacticalMessage(sender, messageText)
        _gameState.value = _gameState.value.copy(
            statusToast = if (sentToSocket) {
                "P2P SOCKET SENT: \"$messageText\""
            } else {
                "QUEUED P2P CALLOUT: \"$messageText\""
            }
        )
    }

    fun runP2pLoopbackVerification() {
        p2pManager.runLoopbackSocketVerification("COOP PING WAVE ${_gameState.value.wave}")
    }

    private fun saveLocalOfflineSlot(snap: CryptoSyncEngine.GameSaveSnapshot) {
        val updated = (listOf(snap) + _localOfflineSaves.value.filter { it.slotName != snap.slotName }).take(10)
        _localOfflineSaves.value = updated
        val ciphertexts = updated.map { CryptoSyncEngine.encryptSnapshot(it, _syncPassphrase.value) }
        localPrefs.edit().putString("offline_capsules_v1", ciphertexts.joinToString("|||")).apply()
    }

    private fun loadOfflineSlotsFromPrefs() {
        val raw = localPrefs.getString("offline_capsules_v1", null) ?: return
        val restored = raw.split("|||").mapNotNull { cap ->
            CryptoSyncEngine.decryptSnapshot(cap, _syncPassphrase.value).getOrNull()
        }
        _localOfflineSaves.value = restored
    }

    private fun loadGamepadMappingFromPrefs() {
        val raw = localPrefs.getString("local_gamepad_mapping_v1", null) ?: return
        ControllerManager.applyMapping(GamepadMapping.fromSerializedString(raw))
    }

    private fun persistGamepadMapping(mapping: GamepadMapping) {
        localPrefs.edit().putString("local_gamepad_mapping_v1", mapping.toSerializedString()).apply()
    }

    // --- CONTROLLER MAPPING & BIG SCREEN DISPLAY CONFIG ---

    fun bindTouchButtonToAction(buttonId: TouchButtonId, action: GamepadAction) {
        val next = ControllerManager.bindTouchButton(buttonId, action)
        persistGamepadMapping(next)
        _gameState.value = _gameState.value.copy(
            statusToast = "MAPPED TOUCH [${buttonId.shortLabel}] -> ${action.displayName}"
        )
    }

    fun applyGamepadMappingPreset(presetMapping: GamepadMapping) {
        ControllerManager.applyMapping(presetMapping)
        persistGamepadMapping(presetMapping)
        _gameState.value = _gameState.value.copy(
            statusToast = "APPLIED TOUCH MAPPING: ${presetMapping.mappingName}"
        )
    }

    fun updateDpadDeadzone(deadzone: Float) {
        val next = ControllerManager.updateDeadzone(deadzone)
        persistGamepadMapping(next)
    }

    fun updateControllerConfig(transform: (ControllerConfigState) -> ControllerConfigState) {
        _controllerConfig.value = transform(_controllerConfig.value)
        localPrefs.edit().putString("local_controller_code", _controllerConfig.value.toKeyMapCode()).apply()
    }

    fun saveControllerProfileToCloud(profileName: String) {
        val cfg = _controllerConfig.value.copy(profileName = profileName.ifBlank { "MyBoy Custom" })
        _controllerConfig.value = cfg
        localPrefs.edit().putString("local_controller_code", cfg.toKeyMapCode()).apply()
        if (isCloudAuthenticated) {
            viewModelScope.launch {
                repository.createControllerProfile(
                    profileName = cfg.profileName,
                    dpadScale = cfg.dpadScalePercent,
                    buttonOpacity = cfg.buttonOpacityPercent,
                    keyMapCode = cfg.toKeyMapCode()
                )
            }
            _gameState.value = _gameState.value.copy(
                statusToast = "SAVED CONTROLLER PROFILE (Local + Cloud): ${cfg.profileName}"
            )
        } else {
            _gameState.value = _gameState.value.copy(
                statusToast = "SAVED CONTROLLER PROFILE OFFLINE: ${cfg.profileName}"
            )
        }
    }

    fun applyCloudControllerProfile(profile: ControllerProfile) {
        val parts = profile.keyMapCode.split("|").associate {
            val k = it.substringBefore(":")
            val v = it.substringAfter(":", "")
            k to v
        }
        val preset = OverlayPreset.entries.find { it.name == parts["PRESET"] } ?: OverlayPreset.MYBOY_GBA
        val disp = DisplayScreenMode.entries.find { it.name == parts["DISP"] } ?: DisplayScreenMode.HANDHELD_TOUCH
        _controllerConfig.value = _controllerConfig.value.copy(
            profileName = profile.profileName,
            dpadScalePercent = profile.dpadScale.toInt().coerceIn(50, 200),
            buttonOpacityPercent = profile.buttonOpacity.toInt().coerceIn(10, 100),
            cameraZoomPercent = parts["ZOOM"]?.toIntOrNull()?.coerceIn(70, 140) ?: 100,
            displayMode = disp,
            preset = preset,
            crtScanlinesEnabled = parts["CRT"]?.toBooleanStrictOrNull() ?: true,
            turboFireEnabled = parts["TURBO"]?.toBooleanStrictOrNull() ?: true,
            keyFire = parts["FIRE"]?.toIntOrNull() ?: KeyEvent.KEYCODE_BUTTON_A,
            keyInteract = parts["INT"]?.toIntOrNull() ?: KeyEvent.KEYCODE_BUTTON_B,
            keyPlaceBarrel = parts["BARREL"]?.toIntOrNull() ?: KeyEvent.KEYCODE_BUTTON_X,
            keyReload = parts["RELOAD"]?.toIntOrNull() ?: KeyEvent.KEYCODE_BUTTON_Y,
            keyPrevWeapon = parts["PREV"]?.toIntOrNull() ?: KeyEvent.KEYCODE_BUTTON_L1,
            keyNextWeapon = parts["NEXT"]?.toIntOrNull() ?: KeyEvent.KEYCODE_BUTTON_R1
        )
        _gameState.value = _gameState.value.copy(
            statusToast = "APPLIED INPUT PROFILE: ${profile.profileName}"
        )
    }

    fun deleteCloudControllerProfile(profileId: String) {
        if (!isCloudAuthenticated) return
        viewModelScope.launch {
            repository.deleteControllerProfile(profileId)
        }
    }

    fun onHardwareKeyCaptured(keyCode: Int): Boolean {
        val awaiting = _controllerConfig.value.awaitingKeyBindAction ?: return false
        _controllerConfig.value = when (awaiting) {
            "FIRE" -> _controllerConfig.value.copy(keyFire = keyCode, awaitingKeyBindAction = null)
            "INTERACT" -> _controllerConfig.value.copy(keyInteract = keyCode, awaitingKeyBindAction = null)
            "BARREL" -> _controllerConfig.value.copy(keyPlaceBarrel = keyCode, awaitingKeyBindAction = null)
            "RELOAD" -> _controllerConfig.value.copy(keyReload = keyCode, awaitingKeyBindAction = null)
            "PREV" -> _controllerConfig.value.copy(keyPrevWeapon = keyCode, awaitingKeyBindAction = null)
            "NEXT" -> _controllerConfig.value.copy(keyNextWeapon = keyCode, awaitingKeyBindAction = null)
            else -> _controllerConfig.value.copy(awaitingKeyBindAction = null)
        }
        return true
    }

    // --- GEMINI AI TACTICAL DIRECTOR & 100% OFFLINE ON-DEVICE AI ENGINE ---

    fun setPreferOfflineAiEngine(preferOffline: Boolean) {
        _preferOfflineAiEngine.value = preferOffline
    }

    fun generateProceduralDirective(userThemePrompt: String) {
        val st = _gameState.value
        if (_preferOfflineAiEngine.value) {
            val offlineDirective = OfflineTacticalAiEngine.synthesizeOfflineDirective(userThemePrompt, st)
            _aiState.value = AiDirectorUiState.DirectiveReady(offlineDirective)
            return
        }

        viewModelScope.launch {
            _aiState.value = AiDirectorUiState.Loading
            val fullPrompt = """
                You are the Tactical AI Director for BoxBoy Zombies (a 2D Boxhead + Black Ops 2 Zombies survival game).
                Current Player Wave: ${st.wave}, Kills: ${st.totalKills}, Points: ${st.points}, Current Weapon: ${st.currentWeapon.name}.
                Player Request / Theme: "${userThemePrompt.ifBlank { "High-intensity Boxhead Red Devil boss siege with tactical choke points" }}"
                Generate a balanced procedural mission directive with a deterministic mapSeed, startingWave, bonusPoints, recommendedWeaponId, and a 2-sentence tacticalBriefing.
            """.trimIndent()

            try {
                val response = withTimeout(15_000L) {
                    directorModel.generateContent(fullPrompt)
                }
                val jsonText = response.text ?: throw IllegalStateException("Model returned empty response.")
                val json = JSONObject(jsonText)
                val directive = AiDirectiveResult(
                    operationTitle = json.optString("operationTitle", "OP: RED BARREL PROTOCOL"),
                    mapSeed = json.optLong("mapSeed", 42088199L).coerceAtLeast(1000L),
                    startingWave = json.optInt("startingWave", st.wave).coerceIn(1, 25),
                    bonusPoints = json.optInt("bonusPoints", 1500).coerceIn(500, 10000),
                    recommendedWeaponId = json.optString("recommendedWeaponId", "shotgun"),
                    tacticalBriefing = json.optString(
                        "tacticalBriefing",
                        "Unlock the East Armory early and kite Shamblers around explosive barrels before the Wave 5 Boxhead Devil spawns."
                    )
                )
                _aiState.value = AiDirectorUiState.DirectiveReady(directive)
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                Log.w(TAG, "Cloud AI timed out; falling back to 100% Offline On-Device AI Engine", e)
                val fallback = OfflineTacticalAiEngine.synthesizeOfflineDirective(userThemePrompt, st)
                _aiState.value = AiDirectorUiState.DirectiveReady(fallback)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud AI unavailable offline; seamlessly using 100% Offline On-Device AI Engine", e)
                val fallback = OfflineTacticalAiEngine.synthesizeOfflineDirective(userThemePrompt, st)
                _aiState.value = AiDirectorUiState.DirectiveReady(fallback)
            }
        }
    }

    fun applyAiDirective(directive: AiDirectiveResult) {
        val st = _gameState.value
        val wpnId = directive.recommendedWeaponId
        val updatedWeapons = (st.unlockedWeaponIds + wpnId).distinct()
        val updatedLevels = st.weaponLevels + (wpnId to (st.weaponLevels[wpnId] ?: 1))
        val newMap = ProceduralMapEngine.generateMap(directive.mapSeed, setOf(0, 1))
        _gameState.value = st.copy(
            mapSeed = directive.mapSeed,
            map = newMap,
            unlockedRooms = setOf(0, 1),
            wave = directive.startingWave,
            zombiesRemainingInWave = 8 + directive.startingWave * 4,
            points = st.points + directive.bonusPoints,
            unlockedWeaponIds = updatedWeapons,
            weaponLevels = updatedLevels,
            currentWeaponIndex = updatedWeapons.indexOf(wpnId).coerceAtLeast(0),
            hp = st.maxHp,
            isGameOver = false,
            statusToast = "AI DIRECTIVE ACTIVE: ${directive.operationTitle} (+${directive.bonusPoints} PTS)"
        )
    }

    fun streamFastRadioAdvice() {
        val st = _gameState.value
        if (_preferOfflineAiEngine.value) {
            val offlineAdvice = OfflineTacticalAiEngine.generateOfflineRadioCallout(st)
            _aiState.value = AiDirectorUiState.AdviceReady(offlineAdvice)
            return
        }

        viewModelScope.launch {
            _aiState.value = AiDirectorUiState.Loading
            val prompt = "Give a short 2-sentence tactical radio callout for surviving Wave ${st.wave} in BoxBoy Zombies with ${st.points} points, ${st.unlockedRooms.size}/6 rooms unlocked, and weapon ${st.currentWeapon.name} (Tier ${st.currentWeaponLevel})."
            val builder = StringBuilder()
            try {
                withTimeout(15_000L) {
                    fastRadioModel.generateContentStream(prompt).collect { chunk ->
                        builder.append(chunk.text.orEmpty())
                        _aiState.value = AiDirectorUiState.StreamingAdvice(builder.toString())
                    }
                }
                val finalAdvice = builder.toString()
                if (finalAdvice.isNotBlank()) {
                    _aiState.value = AiDirectorUiState.AdviceReady(finalAdvice)
                } else {
                    _aiState.value = AiDirectorUiState.AdviceReady(
                        OfflineTacticalAiEngine.generateOfflineRadioCallout(st)
                    )
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                Log.w(TAG, "Fast radio stream timed out; using 100% Offline Tactical Radio Advisor", e)
                _aiState.value = AiDirectorUiState.AdviceReady(
                    OfflineTacticalAiEngine.generateOfflineRadioCallout(st)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Cloud radio unavailable offline; using 100% Offline Tactical Radio Advisor", e)
                _aiState.value = AiDirectorUiState.AdviceReady(
                    OfflineTacticalAiEngine.generateOfflineRadioCallout(st)
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        p2pManager.stopSession()
        konsoleServer.stopServer()
    }
}
