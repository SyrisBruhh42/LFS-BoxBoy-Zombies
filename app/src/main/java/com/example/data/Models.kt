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
    LAN_WIFI("Local Network (UDP/TCP)", "Zero-config LAN broadcast + low-latency TCP socket"),
    USB_ADB("USB Cable (ADB Tunnel)", "Direct wired 127.0.0.1:48899 socket via USB tethering/ADB"),
    BLUETOOTH_P2P("Bluetooth / P2P Capsule", "Encrypted AES-256-GCM P2P capsule & paired socket")
}
