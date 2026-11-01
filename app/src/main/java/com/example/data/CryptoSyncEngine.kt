package com.example.data

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides 100% offline-capable AES-256-GCM authenticated encryption for save states
 * and peer-to-peer cross-device synchronization packets (USB / Local Network / Bluetooth).
 */
object CryptoSyncEngine {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGO = "PBKDF2WithHmacSHA256"
    private const val SALT_BYTES = 12
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val ITERATIONS = 10_000
    private const val KEY_BITS = 256

    data class GameSaveSnapshot(
        val slotName: String,
        val highestWave: Int,
        val totalKills: Int,
        val points: Int,
        val unlockedWeaponIds: List<String>,
        val weaponLevels: Map<String, Int>,
        val activePerks: List<String>,
        val mapSeed: Long,
        val unlockedRoomsCount: Int,
        val deviceTag: String,
        val timestampEpochMs: Long
    ) {
        fun toJsonString(): String {
            val root = JSONObject()
            root.put("slotName", slotName)
            root.put("highestWave", highestWave)
            root.put("totalKills", totalKills)
            root.put("points", points)
            root.put("unlockedWeaponIds", unlockedWeaponIds.joinToString(","))
            val levelsObj = JSONObject()
            weaponLevels.forEach { (k, v) -> levelsObj.put(k, v) }
            root.put("weaponLevels", levelsObj)
            root.put("activePerks", activePerks.joinToString(","))
            root.put("mapSeed", mapSeed)
            root.put("unlockedRoomsCount", unlockedRoomsCount)
            root.put("deviceTag", deviceTag)
            root.put("timestampEpochMs", timestampEpochMs)
            return root.toString()
        }

        companion object {
            fun fromJsonString(json: String): GameSaveSnapshot {
                val obj = JSONObject(json)
                val weaponsStr = obj.optString("unlockedWeaponIds", "pistol")
                val unlockedWeapons = weaponsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    .ifEmpty { listOf("pistol") }
                val levelsObj = obj.optJSONObject("weaponLevels") ?: JSONObject()
                val weaponLevels = mutableMapOf<String, Int>()
                val keys = levelsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    weaponLevels[k] = levelsObj.optInt(k, 1)
                }
                if (weaponLevels.isEmpty()) weaponLevels["pistol"] = 1
                val perksStr = obj.optString("activePerks", "")
                val perks = perksStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                return GameSaveSnapshot(
                    slotName = obj.optString("slotName", "Slot 1 - Bunker Run").take(60),
                    highestWave = obj.optInt("highestWave", 1).coerceIn(1, 9999),
                    totalKills = obj.optInt("totalKills", 0).coerceAtLeast(0),
                    points = obj.optInt("points", 500).coerceAtLeast(0),
                    unlockedWeaponIds = unlockedWeapons,
                    weaponLevels = weaponLevels,
                    activePerks = perks,
                    mapSeed = obj.optLong("mapSeed", 19960227L),
                    unlockedRoomsCount = obj.optInt("unlockedRoomsCount", 1).coerceIn(1, 8),
                    deviceTag = obj.optString("deviceTag", "MyBoy-Android").take(80),
                    timestampEpochMs = obj.optLong("timestampEpochMs", System.currentTimeMillis())
                )
            }
        }
    }

    fun encryptSnapshot(snapshot: GameSaveSnapshot, passphrase: String): String {
        val cleanPass = passphrase.ifBlank { "BOXBOY-DEFAULT-SYNC-KEY" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val keySpec = deriveKey(cleanPass, salt)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = snapshot.toJsonString().toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintext)

        val combined = ByteArray(salt.size + iv.size + ciphertext.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(ciphertext, 0, combined, salt.size + iv.size, ciphertext.size)
        return "BBZ1:" + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptSnapshot(payload: String, passphrase: String): Result<GameSaveSnapshot> {
        return try {
            val cleanPass = passphrase.ifBlank { "BOXBOY-DEFAULT-SYNC-KEY" }
            val stripped = payload.trim().removePrefix("BBZ1:")
            val combined = Base64.decode(stripped, Base64.NO_WRAP)
            require(combined.size > SALT_BYTES + IV_BYTES + 16) { "Invalid encrypted capsule length" }

            val salt = combined.copyOfRange(0, SALT_BYTES)
            val iv = combined.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
            val ciphertext = combined.copyOfRange(SALT_BYTES + IV_BYTES, combined.size)

            val keySpec = deriveKey(cleanPass, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            val json = String(decryptedBytes, Charsets.UTF_8)
            Result.success(GameSaveSnapshot.fromJsonString(json))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGO)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }
}
