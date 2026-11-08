package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class SaveSlot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val slotName: String = "Slot 1 - Bunker Run",
    val highestWave: Long = 1L,
    val totalKills: Long = 0L,
    val points: Long = 500L,
    val loadoutSummary: String = "M1911 Pistol Lv1",
    val encryptedPayload: String = "OFFLINE_AESGCM_STATE_V1",
    val deviceTag: String = "MyBoy-Shell-Android",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ControllerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val profileName: String = "MyBoy Classic GBA",
    val dpadScale: Long = 110L,
    val buttonOpacity: Long = 80L,
    val keyMapCode: String = "DPAD:BL|AB:BR|LR:TOP|TURBO:ON|DEADZONE:12|KEY_FIRE:96|KEY_ALT:97|KEY_RELOAD:99|KEY_NEXT:103",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
data class WeaponSpec(
    val id: String,
    val name: String,
    val shortCode: String,
    val baseDamage: Int,
    val fireCooldownMs: Long,
    val magazineSize: Int,
    val reloadMs: Long,
    val unlockCost: Int,
    val upgradeBaseCost: Int,
    val pelletCount: Int = 1,
    val spreadDegrees: Float = 2f,
    val pierceCount: Int = 1,
    val isExplosive: Boolean = false,
    val colorHex: Long = 0xFF00FF66
)

val DEFAULT_WEAPONS = listOf(
    WeaponSpec(
        id = "pistol",
        name = "M1911 Sidearm",
        shortCode = "M1911",
        baseDamage = 34,
        fireCooldownMs = 230L,
        magazineSize = 12,
        reloadMs = 950L,
        unlockCost = 0,
        upgradeBaseCost = 750,
        pelletCount = 1,
        spreadDegrees = 2f,
        pierceCount = 1,
        colorHex = 0xFF00FF66
    ),
    WeaponSpec(
        id = "uzi",
        name = "Micro UZI Rapid",
        shortCode = "UZI",
        baseDamage = 24,
        fireCooldownMs = 95L,
        magazineSize = 36,
        reloadMs = 1150L,
        unlockCost = 750,
        upgradeBaseCost = 1200,
        pelletCount = 1,
        spreadDegrees = 7f,
        pierceCount = 1,
        colorHex = 0xFF00E5FF
    ),
    WeaponSpec(
        id = "shotgun",
        name = "Boxhead 12G Trench",
        shortCode = "12G-SG",
        baseDamage = 28,
        fireCooldownMs = 520L,
        magazineSize = 8,
        reloadMs = 1400L,
        unlockCost = 1250,
        upgradeBaseCost = 1600,
        pelletCount = 6,
        spreadDegrees = 18f,
        pierceCount = 2,
        colorHex = 0xFFFFD166
    ),
    WeaponSpec(
        id = "barrels",
        name = "Cryo & Red Barrels",
        shortCode = "BARREL",
        baseDamage = 180,
        fireCooldownMs = 600L,
        magazineSize = 5,
        reloadMs = 1600L,
        unlockCost = 1500,
        upgradeBaseCost = 1800,
        pelletCount = 1,
        spreadDegrees = 0f,
        pierceCount = 4,
        isExplosive = true,
        colorHex = 0xFFFF2A54
    ),
    WeaponSpec(
        id = "ar",
        name = "Commando Galil",
        shortCode = "GALIL",
        baseDamage = 52,
        fireCooldownMs = 130L,
        magazineSize = 35,
        reloadMs = 1300L,
        unlockCost = 2000,
        upgradeBaseCost = 2500,
        pelletCount = 1,
        spreadDegrees = 3.5f,
        pierceCount = 2,
        colorHex = 0xFF7AE582
    ),
    WeaponSpec(
        id = "railgun",
        name = "Hyper Railgun",
        shortCode = "RAIL",
        baseDamage = 195,
        fireCooldownMs = 680L,
        magazineSize = 6,
        reloadMs = 1500L,
        unlockCost = 3200,
        upgradeBaseCost = 4000,
        pelletCount = 1,
        spreadDegrees = 0.5f,
        pierceCount = 8,
        colorHex = 0xFFB388FF
    ),
    WeaponSpec(
        id = "raybox",
        name = "Ray-Box Mk II",
        shortCode = "RAY-MK2",
        baseDamage = 260,
        fireCooldownMs = 260L,
        magazineSize = 20,
        reloadMs = 1600L,
        unlockCost = 5000,
        upgradeBaseCost = 5000,
        pelletCount = 3,
        spreadDegrees = 6f,
        pierceCount = 4,
        isExplosive = true,
        colorHex = 0xFFFF007F
    )
)

enum class PerkType(
    val displayName: String,
    val shortTag: String,
    val cost: Int,
    val description: String,
    val colorHex: Long
) {
    JUGGERNOG("Juggernog", "JUG", 2500, "Max HP doubled to 200 & faster shield regen", 0xFFFF2A54),
    SPEED_COLA("Speed Cola", "SPD", 1500, "Reload speed +50% and faster barricade repair", 0xFF00FF66),
    DOUBLE_TAP("Double Tap II", "DBL", 2000, "Fire rate +35% and bonus pellet damage", 0xFFFFD166),
    STAMIN_UP("Stamin-Up", "STM", 1500, "Movement speed +28% for kiting zombie trains", 0xFF00E5FF),
    QUICK_REVIVE("Quick Revive", "REV", 1000, "Auto self-revive in Solo & 2x faster Co-op revive", 0xFF7B61FF)
}

enum class SyncTransportMode(val label: String, val subtitle: String) {
    LFS_HOSTED_SERVER("LFS Dedicated Server (U1/U2)", "24/7 Hosted LFS Oasis Room via play.lewisfamilysystems.com (LFS-XXXXXX code)"),
    LFS_LOCAL_HOST_TUNNEL("Host on Device + LFS Tunnel", "Authoritative local host bridged globally via sync.lewisfamilysystems.com"),
    FIRESTORE_CLOUD_RELAY("Cloud Room Relay (Firestore)", "Zero-config 6-char room code relay for instant internet co-op"),
    LAN_WIFI("Local Network (UDP/TCP)", "Zero-config LAN broadcast + low-latency TCP socket :48899"),
    USB_ADB("USB Cable (ADB Tunnel)", "Direct wired 127.0.0.1:48899 socket via USB tethering/ADB"),
    BLUETOOTH_P2P("Bluetooth / P2P Capsule", "Encrypted AES-256-GCM P2P capsule & paired socket")
}

enum class LfsHostMode(val wireId: String, val label: String, val subtitle: String) {
    LAUNCH_U2_HOSTING_SERVICE(
        "LAUNCH_U2_HOSTING_SERVICE",
        "Open a Family Cloud Room",
        "Always-on family room hosted in the Oasis Sanctuary cloud (play.lewisfamilysystems.com)"
    ),
    HOST_LOCAL_DEVICE(
        "HOST_LOCAL_DEVICE",
        "Host From My Desk",
        "Your device leads the game and bridges family rooms instantly"
    ),
    HOST_LOCAL_PC(
        "HOST_LOCAL_PC",
        "Host From My Desk",
        "Local PC or companion device leads the game and bridges family rooms instantly"
    );

    val title: String get() = label
    val shortTitle: String get() = label
    val isLocalHost: Boolean get() = this == HOST_LOCAL_DEVICE || this == HOST_LOCAL_PC

    companion object {
        fun fromWireValue(raw: String?): LfsHostMode {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return when (normalized) {
                "HOST_LOCAL_PC" -> HOST_LOCAL_PC
                "HOST_LOCAL_DEVICE", "HOST-02" -> HOST_LOCAL_DEVICE
                "LAUNCH_U2_HOSTING_SERVICE", "HOST-01" -> LAUNCH_U2_HOSTING_SERVICE
                else -> entries.find { it.wireId.equals(normalized, ignoreCase = true) }
                    ?: LAUNCH_U2_HOSTING_SERVICE
            }
        }
    }
}

enum class LfsLifecyclePolicy(val wireId: String, val label: String) {
    ON_DEMAND_WAKE("ON_DEMAND_WAKE", "Wake-on-Join Standby (<500ms, 0 MB Idle)"),
    AUTO_CLOSE_IDLE("AUTO_CLOSE_IDLE", "Auto-Close When Empty (5m Grace + Auto-Save)"),
    KEEP_LIVE_PINNED("KEEP_LIVE_PINNED", "Keep-Live Pinned (24/7 Persistent World)"),
    LEASE_TTL("LEASE_TTL", "Timed Session Lease (2h + Extend)"),
    HARDWARE_SYMPATHETIC_YIELD("HARDWARE_SYMPATHETIC_YIELD", "Hardware-Sympathetic RAM Yield")
}

enum class OasisBotPersona(
    val id: String,
    val displayName: String,
    val roleSummary: String,
    val visorColorHex: Long = 0xFF00FF66,
    val preferredWeaponId: String = "ar"
) {
    WILLOW("willow", "@willow", "Tactical Medic & Barricade Engineer", 0xFF00FF66, "ar"),
    CINDER("cinder", "@cinder", "Aggressive Crowd-Control & Barrel Detonator", 0xFFFF7B00, "shotgun"),
    MOTE("mote", "@mote", "High-Precision Rail/Crit Scout", 0xFF00E5FF, "railgun")
}

@Keep
data class LfsServerEndpointConfig(
    val primaryEdgeHost: String = "https://play.lewisfamilysystems.com",
    val syncRelayWsUrl: String = "wss://play.lewisfamilysystems.com/ws",
    val fallbackRelayWsUrl: String = "wss://sync.lewisfamilysystems.com/ws",
    val localHosterUrl: String = "http://127.0.0.1:19448",
    val customServerUrl: String = "",
    val tailscaleAnchorHost: String = "https://lfs-prod.tail420c4d.ts.net",
    val tailscaleAnchorSsoUrl: String = "http://100.106.12.104:13379/lfs-sso",
    val tailscaleLaputaHosterUrl: String = "http://100.115.219.120:19448",
    val expectedTailnetId: String = "TNoZe9MmWS11CNTRL",
    val expectedTailnetDomain: String = "tail420c4d.ts.net"
)

@Keep
data class HostedRoomDescriptor(
    val roomId: String = "LFS-8K4M2P",
    val roomCode: String = "LFS-8K4M2P",
    val roomTitle: String = "BoxBoy Oasis Squad",
    val hostMode: LfsHostMode = LfsHostMode.LAUNCH_U2_HOSTING_SERVICE,
    val lifecyclePolicy: LfsLifecyclePolicy = LfsLifecyclePolicy.ON_DEMAND_WAKE,
    val hostEpoch: Int = 1,
    val mapSeed: Long = 19960227L,
    val currentWave: Int = 1,
    val playerCount: Int = 1,
    val activePlayers: Int = playerCount,
    val maxPlayers: Int = 4,
    val serverRegion: String = "LFS-Oasis-Edge",
    val pingMs: Long = 18L,
    val isStandbyWakeable: Boolean = true,
    val wsEndpointUrl: String = "wss://play.lewisfamilysystems.com/ws/room/LFS-8K4M2P",
    val wsTicket: String = "oasis-ticket-v1",
    val transportTier: String = "TIER_A_LFS_EDGE",
    val initialStateEnvelope: String? = null
) {
    val wave: Int get() = currentWave
    val wsUrl: String get() = wsEndpointUrl
}

typealias HostedRoomSession = HostedRoomDescriptor
typealias HostedRoomSummary = HostedRoomDescriptor


@Keep
@IgnoreExtraProperties
data class CloudMultiplayerRoom(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val hostUserId: String = "",
    val hostName: String = "BOXBOY-HOST",
    val roomTitle: String = "BoxBoy Squad Room",
    val hostMode: String = LfsHostMode.LAUNCH_U2_HOSTING_SERVICE.wireId,
    val lifecyclePolicy: String = LfsLifecyclePolicy.ON_DEMAND_WAKE.wireId,
    val hostEpoch: Long = 1L,
    val mapSeed: Long = 19960227L,
    val currentWave: Long = 1L,
    val wave: Long = 1L,
    val unlockedRoomsCsv: String = "0",
    val isPublic: Boolean = true,
    val playerCount: Long = 1L,
    val activePlayers: Long = 1L,
    val maxPlayers: Long = 4L,
    val status: String = "OPEN",
    val worldSnapshotJson: String = "{}",
    val latestEventJson: String = "{}",
    val lastEventJson: String = "{}",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudRoomPeer(
    @DocumentId val id: String = "",
    val peerId: String = "",
    val userId: String = "",
    val roomCode: String = "",
    val peerName: String = "Survivor",
    val playerName: String = "Survivor",
    val x: Double = 510.0,
    val y: Double = 1200.0,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
    val aimAngleRad: Double = 0.0,
    val hp: Long = 100L,
    val maxHp: Long = 100L,
    val shield: Long = 60L,
    val weaponId: String = "pistol",
    val weaponLevel: Long = 1L,
    val isFiring: Boolean = false,
    val isDowned: Boolean = false,
    val reviveProgress: Double = 0.0,
    val score: Long = 600L,
    val kills: Long = 0L,
    val wave: Long = 1L,
    val isAiBot: Boolean = false,
    val botPersonaId: String = "",
    val visorColorHex: Long = 0xFF00E5FF,
    val radioCalloutText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// --- APP STORE RELEASE: CAREER DOSSIER, DAILY SURVIVAL DIRECTIVES & STORE SETTINGS ---

enum class DailyDirectiveMetric {
    KILL_ZOMBIES,
    REACH_WAVE,
    DEPLOY_FORTIFICATIONS,
    UPGRADE_OR_FORGE
}

@Keep
data class DailyDirective(
    val id: String,
    val dateKey: String,
    val title: String,
    val description: String,
    val metricType: DailyDirectiveMetric,
    val targetCount: Int,
    val currentProgress: Int = 0,
    val rewardPoints: Int,
    val rewardScrap: Int,
    val isClaimed: Boolean = false
) {
    val isCompleted: Boolean
        get() = currentProgress >= targetCount

    val progressFraction: Float
        get() = if (targetCount <= 0) 1f else (currentProgress.toFloat() / targetCount.toFloat()).coerceIn(0f, 1f)
}

@Keep
data class CareerDossier(
    val highestWave: Int = 1,
    val totalKills: Int = 0,
    val bossKills: Int = 0,
    val fortificationsDeployed: Int = 0,
    val packAPunchUpgrades: Int = 0,
    val squadRevives: Int = 0,
    val lifetimePointsEarned: Long = 600L,
    val completedDirectivesCount: Int = 0
) {
    val careerRankTitle: String
        get() = when {
            totalKills >= 1500 || highestWave >= 25 -> "ABYSSAL COMMANDER [TIER-5]"
            totalKills >= 600 || highestWave >= 15 -> "BOXHEAD VANGUARD [TIER-4]"
            totalKills >= 250 || highestWave >= 10 -> "CRYO SPECIALIST [TIER-3]"
            totalKills >= 75 || highestWave >= 5 -> "BUNKER CORPORAL [TIER-2]"
            else -> "RECRUIT SURVIVOR [TIER-1]"
        }
}

@Keep
data class AppStoreSettingsState(
    val sfxEnabled: Boolean = true,
    val sfxVolume: Float = 0.85f,
    val musicVolume: Float = 0.78f,
    val hapticsEnabled: Boolean = true,
    val developerModeEnabled: Boolean = false,
    val hasSeenFieldManual: Boolean = false
)

// --- TRASHARCADE LAUNCHER DEEP-LINK & CROSS-GAME ASSET BRIDGE (lfs-local-abac-v1) ---

@Keep
data class TrashArcadeLaunchParams(
    val oasisRoomCode: String? = null,
    val oasisHostMode: String? = null,
    val oasisSubuserId: String? = null,
    val oasisDisplayName: String? = null,
    val oasisLdsTheme: String? = null,
    val oasisAutostart: Boolean = false
) {
    val hasDeepLinkOverrides: Boolean
        get() = !oasisRoomCode.isNullOrBlank() ||
            !oasisHostMode.isNullOrBlank() ||
            !oasisSubuserId.isNullOrBlank() ||
            !oasisDisplayName.isNullOrBlank() ||
            !oasisLdsTheme.isNullOrBlank() ||
            oasisAutostart
}

enum class TrashArcadeSharedRelic(
    val assetId: String,
    val title: String,
    val targetGames: String,
    val unlockConditionDescription: String
) {
    CHARM_VALKOR9_BRASS_COLLAR(
        assetId = "charm_valkor9_brass_collar",
        title = "Valkor-9 Brass Collar",
        targetGames = "willow-desktop-pet, undercity-crawler",
        unlockConditionDescription = "First Boss kill -- tames Valkor-9 Slag-Hound in willow-desktop-pet & undercity-crawler"
    ),
    UNDERCITY_DELVER_TORCH(
        assetId = "undercity-delver-torch",
        title = "Undercity Delver Torch",
        targetGames = "undercity-crawler",
        unlockConditionDescription = "Survive Wave 5 in Abyssal Bio-Noir lighting -- boosts visibility in undercity-crawler"
    ),
    CRUCIBLE_ATOMIC_SIGNET(
        assetId = "crucible-atomic-signet",
        title = "Crucible Atomic Signet",
        targetGames = "lfs-tycoon",
        unlockConditionDescription = "First Pack-a-Punch weapon upgrade -- boosts smelting in lfs-tycoon"
    ),
    WILLOW_VISOR_EMERALD(
        assetId = "willow-visor-emerald",
        title = "Willow Emerald Tactical Visor",
        targetGames = "willow-desktop-pet",
        unlockConditionDescription = "Revive a squadmate or AI bot in Co-Op -- equips Tactical Emerald Visor on willow-desktop-pet"
    );

    val relicId: String get() = assetId
    val crossGamePerk: String get() = unlockConditionDescription
}

// --- DISNEY IMAGINEERING ENGAGEMENT TRANSLATOR (GRADE <= 8.0) & ZT-9 COMFORT OVERRIDES ---

@Keep
data class Zt9ComfortOverridesState(
    val familyFriendlyArcadeMode: Boolean = true,
    val autoEnforceForChildRings: Boolean = true,
    val activeTrustRing: Int = 3
) {
    val isEffectiveFamilyArcade: Boolean
        get() = if (autoEnforceForChildRings && activeTrustRing <= 2) true else familyFriendlyArcadeMode

    val isComfortEnforced: Boolean
        get() = isEffectiveFamilyArcade

    val modeBadgeLabel: String
        get() = if (isEffectiveFamilyArcade) {
            "FAMILY ARCADE MODE (Cardboard Critters & Confetti KOs)"
        } else {
            "CLASSIC TACTICAL MODE"
        }
}

@Keep
data class FriendlyGuideCardState(
    val id: String = "guide_ready",
    val title: String = "Friendly Guide Card",
    val whatHappened: String = "Your Hearth Key (Instant Family Pass) is active and ready.",
    val whyItHappened: String = "Oasis automatically links your family room and backs up your adventure.",
    val yourProgressIsSafe: String = "Every Starlight Rupee, Wonder Relic, and wave milestone is safely tucked into your Time-Machine Memory Vaults.",
    val oneClickNextStepLabel: String = "Check Family Room Status",
    val isWarning: Boolean = false
) {
    val nextStepButtonLabel: String
        get() = oneClickNextStepLabel
}

enum class OasisWingDestination(
    val wingId: String,
    val displayName: String,
    val shortBadge: String,
    val description: String,
    val deepLinkUri: String
) {
    TRASH_ARCADE_HALL(
        wingId = "lfs-trash-arcade",
        displayName = "Return to TrashArcade Hall",
        shortBadge = "TrashArcade Hall",
        description = "Main family arcade foyer, Starlight Rupee prize counter, and Wonder Relic showcase.",
        deepLinkUri = "oasis://hub/wing/lfs-trash-arcade"
    ),
    COMMONPLACE_WING_I(
        wingId = "commonplace-wing-1",
        displayName = "Commonplace Wing I",
        shortBadge = "Wing I: Commonplace",
        description = "Shared family commonplace book, keepsake clippings, and daily discovery notes.",
        deepLinkUri = "oasis://hub/wing/commonplace-wing-1"
    ),
    SCRIPTORIUM_WING_II(
        wingId = "scriptorium-wing-2",
        displayName = "Scriptorium Wing II",
        shortBadge = "Wing II: Scriptorium",
        description = "Quiet writing studio, story drafting desk, and illustrated family chronicles.",
        deepLinkUri = "oasis://hub/wing/scriptorium-wing-2"
    ),
    CHAPTERHOUSE_WING_III(
        wingId = "chapterhouse-wing-3",
        displayName = "Chapterhouse Wing III",
        shortBadge = "Wing III: Chapterhouse",
        description = "Cozy reading room and AudioBookshelf Sanctuary Storyteller library.",
        deepLinkUri = "oasis://hub/wing/chapterhouse-wing-3"
    ),
    VIGIL_3D_WING_IV(
        wingId = "vigil-3d-wing-4",
        displayName = "3D Vigil Wing IV",
        shortBadge = "Wing IV: 3D Vigil",
        description = "Interactive 3D starlight watchtower, constellation map, and home status globe.",
        deepLinkUri = "oasis://hub/wing/vigil-3d-wing-4"
    ),
    CODE_RUNNER(
        wingId = "lfs-code-runner",
        displayName = "Code Runner",
        shortBadge = "Code Runner",
        description = "Fast-paced logic puzzles and co-op coding obstacle courses.",
        deepLinkUri = "oasis://hub/capsule/lfs-code-runner"
    ),
    BOXHEAD_FORT(
        wingId = "lfs-boxhead-fort",
        displayName = "Boxhead Fort",
        shortBadge = "Boxhead Fort",
        description = "Co-op cardboard fort builder and Neon Arcade Bot defense.",
        deepLinkUri = "oasis://hub/capsule/lfs-boxhead-fort"
    ),
    BOXBOY_DEFENSE(
        wingId = "lfs-boxboy-zombies",
        displayName = "BoxBoy Defense",
        shortBadge = "BoxBoy Defense",
        description = "Active Starlight Citadel defense with Cardboard Critters and Tag-Team Rescue.",
        deepLinkUri = "oasis://hub/capsule/lfs-boxboy-zombies"
    );

    val displayTitle: String get() = displayName
}

val CANONICAL_RMBLYN_CHANNELS: List<String> = listOf("global", "guild", "party", "trade", "whisper")

enum class RmblynChannel(val wireId: String, val badgeLabel: String, val description: String) {
    GLOBAL("global", "#global", "Cross-capsule Oasis sanctuary broadcast"),
    GUILD("guild", "#guild", "Family & clan tactical coordination"),
    PARTY("party", "#party", "Active 4-player co-op squad channel"),
    TRADE("trade", "#trade", "Wonder Relics, Modular Weapons & Starlight Rupee exchange"),
    WHISPER("whisper", "@whisper", "Direct companion (@willow, @cinder, @mote) & peer whisper");

    companion object {
        fun fromWireId(raw: String?): RmblynChannel {
            val clean = raw?.trim()?.removePrefix("#")?.removePrefix("@")?.lowercase().orEmpty()
            return entries.find { it.wireId == clean } ?: PARTY
        }
    }
}

@Keep
data class RmblynChatMessage(
    val id: String,
    val channelId: String = "party",
    val senderHandle: String,
    val messageText: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isCompanionBot: Boolean = false
)

@Keep
data class OpenAudibleChapterTrack(
    val key: String = "cb64f9e7",
    val bookTitle: String = "Chronicles of the Starlight Citadel",
    val chapterTitle: String = "Chapter 1: The Hearth Key & The Clockwork Lantern",
    val author: String = "LFS Sanctuary Storyteller",
    val durationSec: Int = 900,
    val streamPath: String = "/api/lfs/openaudible/stream/cb64f9e7"
)

@Keep
data class SanctuaryStorytellerState(
    val isPlaying: Boolean = false,
    val activeTrackKey: String = "cb64f9e7",
    val bookTitle: String = "Chronicles of the Starlight Citadel",
    val chapterTitle: String = "Ch. 1: The Hearth Key & Clockwork Lantern",
    val availableTracks: List<OpenAudibleChapterTrack> = listOf(
        OpenAudibleChapterTrack(
            key = "cb64f9e7",
            bookTitle = "Chronicles of the Starlight Citadel",
            chapterTitle = "Ch. 1: The Hearth Key & Clockwork Lantern",
            streamPath = "/api/lfs/openaudible/stream/cb64f9e7"
        ),
        OpenAudibleChapterTrack(
            key = "cb64f9e7-ch2",
            bookTitle = "Chronicles of the Starlight Citadel",
            chapterTitle = "Ch. 2: Willow's Emerald Visor",
            streamPath = "/api/lfs/openaudible/stream/cb64f9e7"
        ),
        OpenAudibleChapterTrack(
            key = "cb64f9e7-ch3",
            bookTitle = "Chronicles of the Starlight Citadel",
            chapterTitle = "Ch. 3: Starlight Quiet Hours",
            streamPath = "/api/lfs/openaudible/stream/cb64f9e7"
        )
    ),
    val sleepTimerMinutes: Int = 0, // 0 = Off, or 15, 30, 45
    val sleepRemainingSeconds: Int = 0,
    val sleepFadeVolumeMultiplier: Float = 1.0f,
    val sourceModeLabel: String = "Sanctuary Stream Ready (cb64f9e7)",
    val isUsingOfflineLullabyFallback: Boolean = false
)

object OasisImagineeringTranslator {
    const val HEARTH_KEY_LABEL = "Hearth Key (Instant Family Pass)"
    const val TIME_MACHINE_VAULTS_LABEL = "Time-Machine Memory Vaults"
    const val AEGIS_SAVE_VAULTS_LABEL = TIME_MACHINE_VAULTS_LABEL
    const val HOST_FROM_MY_DESK_LABEL = "Host From My Desk"
    const val OPEN_FAMILY_CLOUD_ROOM_LABEL = "Open a Family Cloud Room"
    const val FRIENDLY_GUIDE_CARD_LABEL = "Friendly Guide Cards"
    const val STARLIGHT_QUIET_HOURS_LABEL = "Starlight Quiet Hours (Rest & Recharge Time)"
    const val STARLIGHT_RUPEES_AND_RELICS_LABEL = "Starlight Rupees & Wonder Relics"
    const val STARLIGHT_RUPEES_LABEL = STARLIGHT_RUPEES_AND_RELICS_LABEL
    const val FAMILY_ARCADE_SUBTITLE = "Family Arcade: Cardboard Critters, Neon Arcade Bots, Confetti KOs & Tag-Team Rescue"

    fun translateTerm(technicalTerm: String): String {
        val key = technicalTerm.trim()
        return when {
            key.contains("Node.StableID", ignoreCase = true) ||
                key.contains("Tailscale Auto-SSO", ignoreCase = true) ||
                key.contains("Tailscale SSO", ignoreCase = true) -> HEARTH_KEY_LABEL
            key.contains("aegisEnvelopeV1", ignoreCase = true) ||
                key.contains("AES-256-GCM", ignoreCase = true) -> TIME_MACHINE_VAULTS_LABEL
            key.equals("HOST_LOCAL_PC", ignoreCase = true) ||
                key.equals("HOST_LOCAL_DEVICE", ignoreCase = true) -> HOST_FROM_MY_DESK_LABEL
            key.equals("LAUNCH_U2_HOSTING_SERVICE", ignoreCase = true) -> OPEN_FAMILY_CLOUD_ROOM_LABEL
            key.contains("ZT-6", ignoreCase = true) -> FRIENDLY_GUIDE_CARD_LABEL
            key.contains("Z_1440", ignoreCase = true) ||
                key.contains("Curfew", ignoreCase = true) -> STARLIGHT_QUIET_HOURS_LABEL
            key.contains("CMY Rupee", ignoreCase = true) ||
                key.contains("lfs-local-abac-v1", ignoreCase = true) -> STARLIGHT_RUPEES_AND_RELICS_LABEL
            else -> technicalTerm
        }
    }

    fun enemyLabel(familyArcadeMode: Boolean): String =
        if (familyArcadeMode) "Cardboard Critters & Neon Arcade Bots" else "Hostile Zombies"

    fun enemyShortLabel(familyArcadeMode: Boolean): String =
        if (familyArcadeMode) "Critters" else "Zombies"

    fun koLabel(familyArcadeMode: Boolean): String =
        if (familyArcadeMode) "Confetti KOs" else "Total Kills"

    fun reviveLabel(familyArcadeMode: Boolean): String =
        if (familyArcadeMode) "Tag-Team Rescue" else "Squad Revive"

    fun eliminationVerb(comfort: Zt9ComfortOverridesState): String =
        koLabel(comfort.isEffectiveFamilyArcade)

    fun reviveVerb(comfort: Zt9ComfortOverridesState): String =
        reviveLabel(comfort.isEffectiveFamilyArcade)

    fun translateEnemyName(rawName: String, comfort: Zt9ComfortOverridesState): String {
        if (!comfort.isEffectiveFamilyArcade) return rawName
        val lower = rawName.lowercase()
        return when {
            lower.contains("devil") || lower.contains("boss") || lower.contains("bot") -> "Neon Arcade Bots"
            else -> "Cardboard Critters"
        }
    }

    fun formatPartyRoomBadge(roomCode: String): String {
        val suffix = roomCode.removePrefix("LFS-").removePrefix("OASIS-").take(6).ifBlank { "8K4M2P" }
        return "OASIS-$suffix"
    }

    fun formatOasisPartyRoomCode(roomCode: String): String = formatPartyRoomBadge(roomCode)

    fun buildFriendlyGuideCard(
        whatHappened: String,
        whyItHappened: String,
        yourProgressIsSafe: String = "Your Starlight Rupees, Wonder Relics, and wave progress are safely stored in your Time-Machine Memory Vaults.",
        oneClickNextStepLabel: String = "Reconnect Family Room",
        isWarning: Boolean = false
    ): FriendlyGuideCardState {
        return FriendlyGuideCardState(
            title = FRIENDLY_GUIDE_CARD_LABEL,
            whatHappened = whatHappened,
            whyItHappened = whyItHappened,
            yourProgressIsSafe = yourProgressIsSafe,
            oneClickNextStepLabel = oneClickNextStepLabel,
            isWarning = isWarning
        )
    }

    fun buildFriendlyGuideCard(
        rawReason: String,
        isOfflineFallback: Boolean = false,
        roomCode: String = "LFS-8K4M2P"
    ): FriendlyGuideCardState {
        val partyCode = formatPartyRoomBadge(roomCode)
        val lower = rawReason.lowercase()
        return when {
            lower.contains("curfew") -> FriendlyGuideCardState(
                id = "guide_curfew",
                title = FRIENDLY_GUIDE_CARD_LABEL,
                whatHappened = "It is currently $STARLIGHT_QUIET_HOURS_LABEL in $partyCode.",
                whyItHappened = "Oasis pauses late-night cloud arcade sessions so everyone can rest and recharge.",
                yourProgressIsSafe = "All of your Starlight Rupees, Wonder Relics, and wave progress are safely tucked into your $TIME_MACHINE_VAULTS_LABEL.",
                oneClickNextStepLabel = "Play Cozy Offline Story Mode",
                isWarning = true
            )
            lower.contains("deny") || isOfflineFallback -> FriendlyGuideCardState(
                id = "guide_offline",
                title = FRIENDLY_GUIDE_CARD_LABEL,
                whatHappened = "Playing in Cozy Desk Mode for room $partyCode while the Family Cloud Room is resting.",
                whyItHappened = "Your $HEARTH_KEY_LABEL is keeping your local session running smoothly without waiting on the cloud.",
                yourProgressIsSafe = "Every Starlight Rupee, Wonder Relic, and wave milestone is saved in your $TIME_MACHINE_VAULTS_LABEL.",
                oneClickNextStepLabel = "Check Family Room Connection",
                isWarning = false
            )
            else -> FriendlyGuideCardState(
                id = "guide_ready",
                title = FRIENDLY_GUIDE_CARD_LABEL,
                whatHappened = "Your $HEARTH_KEY_LABEL is active and connected to $partyCode.",
                whyItHappened = "Oasis automatically links your family room and backs up your adventure.",
                yourProgressIsSafe = "Every Starlight Rupee, Wonder Relic, and wave milestone is safely tucked into your $TIME_MACHINE_VAULTS_LABEL.",
                oneClickNextStepLabel = "Open Family Cloud Room",
                isWarning = false
            )
        }
    }
}




