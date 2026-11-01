package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

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
        "Launch on U2 Hosting Service",
        "Dedicated 24/7 cloud room on play.lewisfamilysystems.com"
    ),
    HOST_LOCAL_DEVICE(
        "HOST_LOCAL_DEVICE",
        "Host on Local Device",
        "Authoritative on-device host bridged via sync.lewisfamilysystems.com"
    );

    val title: String get() = label
    val shortTitle: String get() = label
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

data class LfsServerEndpointConfig(
    val primaryEdgeHost: String = "https://play.lewisfamilysystems.com",
    val syncRelayWsUrl: String = "wss://play.lewisfamilysystems.com/ws",
    val fallbackRelayWsUrl: String = "wss://sync.lewisfamilysystems.com/ws",
    val localHosterUrl: String = "http://127.0.0.1:19448",
    val customServerUrl: String = ""
)

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
            totalKills >= 1500 || highestWave >= 25 -> "ABYSSAL COMMANDER ★5"
            totalKills >= 600 || highestWave >= 15 -> "BOXHEAD VANGUARD ★4"
            totalKills >= 250 || highestWave >= 10 -> "CRYO SPECIALIST ★3"
            totalKills >= 75 || highestWave >= 5 -> "BUNKER CORPORAL ★2"
            else -> "RECRUIT SURVIVOR ★1"
        }
}

data class AppStoreSettingsState(
    val sfxEnabled: Boolean = true,
    val sfxVolume: Float = 0.85f,
    val musicVolume: Float = 0.78f,
    val hapticsEnabled: Boolean = true,
    val developerModeEnabled: Boolean = false,
    val hasSeenFieldManual: Boolean = false
)


