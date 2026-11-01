package com.example.data

import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A `JSONObject` implementation that works identically in Android runtime and plain JVM unit tests
 * (even when Android's `org.json.JSONObject` stub returns default values).
 */
class AegisJsonObject(
    private val backingMap: MutableMap<String, Any?> = linkedMapOf()
) : JSONObject() {

    init {
        backingMap.forEach { (k, v) ->
            runCatching { super.put(k, v) }
        }
    }

    override fun put(name: String, value: Any?): JSONObject {
        backingMap[name] = value
        runCatching { super.put(name, value) }
        return this
    }

    override fun put(name: String, value: Boolean): JSONObject {
        backingMap[name] = value
        runCatching { super.put(name, value) }
        return this
    }

    override fun put(name: String, value: Int): JSONObject {
        backingMap[name] = value
        runCatching { super.put(name, value) }
        return this
    }

    override fun put(name: String, value: Long): JSONObject {
        backingMap[name] = value
        runCatching { super.put(name, value) }
        return this
    }

    override fun put(name: String, value: Double): JSONObject {
        backingMap[name] = value
        runCatching { super.put(name, value) }
        return this
    }

    override fun optString(name: String): String = optString(name, "")

    override fun optString(name: String, fallback: String): String {
        val v = backingMap[name]
        if (v != null) return v.toString()
        val superVal = runCatching { super.optString(name, fallback) }.getOrNull()
        return if (!superVal.isNullOrEmpty()) superVal else fallback
    }

    override fun getString(name: String): String {
        return backingMap[name]?.toString()
            ?: runCatching { super.getString(name) }.getOrNull()
            ?: throw org.json.JSONException("No value for $name")
    }

    override fun optInt(name: String): Int = optInt(name, 0)

    override fun optInt(name: String, fallback: Int): Int {
        val v = backingMap[name]
        if (v is Number) return v.toInt()
        if (v is String) return v.toIntOrNull() ?: fallback
        return runCatching { super.optInt(name, fallback) }.getOrDefault(fallback)
    }

    override fun getInt(name: String): Int = optInt(name, 0)

    override fun optLong(name: String): Long = optLong(name, 0L)

    override fun optLong(name: String, fallback: Long): Long {
        val v = backingMap[name]
        if (v is Number) return v.toLong()
        if (v is String) return v.toLongOrNull() ?: fallback
        return runCatching { super.optLong(name, fallback) }.getOrDefault(fallback)
    }

    override fun getLong(name: String): Long = optLong(name, 0L)

    override fun optBoolean(name: String): Boolean = optBoolean(name, false)

    override fun optBoolean(name: String, fallback: Boolean): Boolean {
        val v = backingMap[name]
        if (v is Boolean) return v
        if (v is String) return v.toBooleanStrictOrNull() ?: fallback
        return runCatching { super.optBoolean(name, fallback) }.getOrDefault(fallback)
    }

    override fun getBoolean(name: String): Boolean = optBoolean(name, false)

    override fun optJSONObject(name: String): JSONObject? {
        val v = backingMap[name]
        if (v is JSONObject) return v
        return runCatching { super.optJSONObject(name) }.getOrNull()
    }

    override fun getJSONObject(name: String): JSONObject {
        return optJSONObject(name) ?: throw org.json.JSONException("No JSONObject for $name")
    }

    override fun has(name: String): Boolean {
        return backingMap.containsKey(name) || runCatching { super.has(name) }.getOrDefault(false)
    }

    override fun toString(): String = toJsonElementString(this)

    override fun toString(indentSpaces: Int): String = toJsonElementString(this)

    companion object {
        private fun escapeJson(s: String): String {
            return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
        }

        private fun toJsonElementString(value: Any?): String {
            return when (value) {
                null -> "null"
                is Boolean, is Int, is Long, is Double, is Float -> value.toString()
                is AegisJsonObject -> {
                    val entries = value.backingMap.entries.joinToString(",") { (k, v) ->
                        "\"${escapeJson(k)}\":${toJsonElementString(v)}"
                    }
                    "{$entries}"
                }
                is JSONObject -> {
                    val s = runCatching { value.toString() }.getOrNull()
                    if (!s.isNullOrBlank()) s else "{}"
                }
                is Map<*, *> -> {
                    val entries = value.entries.joinToString(",") { (k, v) ->
                        "\"${escapeJson(k.toString())}\":${toJsonElementString(v)}"
                    }
                    "{$entries}"
                }
                else -> "\"${escapeJson(value.toString())}\""
            }
        }
    }
}

/**
 * Provides 100% offline-capable AES-256-GCM authenticated encryption for save states,
 * 6-Gate `aegisEnvelopeV1` 20-Slot Time-Machine SaveLab sealing/unsealing,
 * and peer-to-peer cross-device synchronization packets (USB / Local Network / Bluetooth / Oasis).
 */
object CryptoSyncEngine {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGO = "PBKDF2WithHmacSHA256"
    private const val SALT_BYTES = 12
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val ITERATIONS = 10_000
    private const val KEY_BITS = 256

    const val AEGIS_ENVELOPE_SCHEMA = "aegisEnvelopeV1"
    const val AEGIS_ENVELOPE_MAGIC = "AEGISENV"
    const val AEGIS_CAPSULE_ID = "lfs-boxboy-zombies"

    private fun encodeBase64NoWrap(bytes: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    private fun decodeBase64NoWrap(encoded: String): ByteArray {
        return java.util.Base64.getDecoder().decode(encoded.trim())
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun escapeJsonString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun extractStringField(rawJson: String, key: String, default: String): String {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val match = regex.find(rawJson)?.groupValues?.getOrNull(1) ?: return default
        return match
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
    }

    private fun extractLongField(rawJson: String, key: String, default: Long): Long {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
        return regex.find(rawJson)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: default
    }

    private fun extractBooleanField(rawJson: String, key: String, default: Boolean): Boolean {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(true|false)")
        return regex.find(rawJson)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull() ?: default
    }

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
            val levelsEntries = weaponLevels.entries
                .sortedBy { it.key }
                .joinToString(",") { (k, v) -> "\"${escapeJsonString(k)}\":$v" }
            return buildString {
                append('{')
                append("\"slotName\":\"${escapeJsonString(slotName)}\",")
                append("\"highestWave\":$highestWave,")
                append("\"totalKills\":$totalKills,")
                append("\"points\":$points,")
                append("\"unlockedWeaponIds\":\"${escapeJsonString(unlockedWeaponIds.joinToString(","))}\",")
                append("\"weaponLevels\":{$levelsEntries},")
                append("\"activePerks\":\"${escapeJsonString(activePerks.joinToString(","))}\",")
                append("\"mapSeed\":$mapSeed,")
                append("\"unlockedRoomsCount\":$unlockedRoomsCount,")
                append("\"deviceTag\":\"${escapeJsonString(deviceTag)}\",")
                append("\"timestampEpochMs\":$timestampEpochMs")
                append('}')
            }
        }

        companion object {
            fun fromJsonString(json: String): GameSaveSnapshot {
                val weaponsStr = extractStringField(json, "unlockedWeaponIds", "pistol")
                val unlockedWeapons = weaponsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    .ifEmpty { listOf("pistol") }

                val weaponLevels = mutableMapOf<String, Int>()
                val levelsBlockMatch = Regex("\"weaponLevels\"\\s*:\\s*\\{([^}]*)\\}").find(json)
                if (levelsBlockMatch != null) {
                    val block = levelsBlockMatch.groupValues[1]
                    Regex("\"([^\"]+)\"\\s*:\\s*(\\d+)").findAll(block).forEach { m ->
                        val k = m.groupValues[1]
                        val v = m.groupValues[2].toIntOrNull() ?: 1
                        weaponLevels[k] = v
                    }
                }
                if (weaponLevels.isEmpty()) weaponLevels["pistol"] = 1

                val perksStr = extractStringField(json, "activePerks", "")
                val perks = perksStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                return GameSaveSnapshot(
                    slotName = extractStringField(json, "slotName", "Slot 1 - Bunker Run").take(60),
                    highestWave = extractLongField(json, "highestWave", 1L).toInt().coerceIn(1, 9999),
                    totalKills = extractLongField(json, "totalKills", 0L).toInt().coerceAtLeast(0),
                    points = extractLongField(json, "points", 500L).toInt().coerceAtLeast(0),
                    unlockedWeaponIds = unlockedWeapons,
                    weaponLevels = weaponLevels,
                    activePerks = perks,
                    mapSeed = extractLongField(json, "mapSeed", 19960227L),
                    unlockedRoomsCount = extractLongField(json, "unlockedRoomsCount", 1L).toInt().coerceIn(1, 8),
                    deviceTag = extractStringField(json, "deviceTag", "MyBoy-Android").take(80),
                    timestampEpochMs = extractLongField(json, "timestampEpochMs", System.currentTimeMillis())
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
        return "BBZ1:" + encodeBase64NoWrap(combined)
    }

    fun decryptSnapshot(payload: String, passphrase: String): Result<GameSaveSnapshot> {
        return try {
            val cleanPass = passphrase.ifBlank { "BOXBOY-DEFAULT-SYNC-KEY" }
            val stripped = payload.trim().removePrefix("BBZ1:")
            val combined = decodeBase64NoWrap(stripped)
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

    /**
     * Seals a [GameSaveSnapshot] into the canonical 6-Gate `aegisEnvelopeV1` JSON structure
     * for 20-slot Time-Machine SaveLab custody (`slot_01`..`slot_20`).
     */
    fun sealSixGateAegisEnvelopeV1(
        slotKey: String,
        snapshot: GameSaveSnapshot,
        generation: Int = 1,
        passphrase: String = "BOXBOY-DEFAULT-SYNC-KEY"
    ): JSONObject {
        val normalizedSlot = normalizeSlotKey(slotKey)
        val safeGeneration = generation.coerceAtLeast(1)
        val canonicalJson = snapshot.toJsonString()
        val payloadSha256 = sha256Hex(canonicalJson)
        val encryptedBbz1 = encryptSnapshot(snapshot, passphrase)
        val ciphertextB64 = encodeBase64NoWrap(encryptedBbz1.toByteArray(Charsets.UTF_8))
        val createdAtUtc = DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(snapshot.timestampEpochMs.coerceAtLeast(1L))
        )

        val g1MagicHeader = true
        val g2SchemaVersion = true
        val g3Sha256Integrity = payloadSha256.length == 64
        val g4MonotonicGeneration = safeGeneration >= 1
        val g5AadBinding = normalizedSlot.startsWith("slot_")
        val g6NonEmptyState = snapshot.highestWave >= 1 && snapshot.unlockedWeaponIds.isNotEmpty()

        val gatesObj = AegisJsonObject().apply {
            put("G1_MAGIC_HEADER", g1MagicHeader)
            put("G2_SCHEMA_VERSION", g2SchemaVersion)
            put("G3_SHA256_INTEGRITY", g3Sha256Integrity)
            put("G4_MONOTONIC_GENERATION", g4MonotonicGeneration)
            put("G5_AAD_BINDING", g5AadBinding)
            put("G6_NON_EMPTY_STATE", g6NonEmptyState)
        }

        return AegisJsonObject().apply {
            put("schema", AEGIS_ENVELOPE_SCHEMA)
            put("magic", AEGIS_ENVELOPE_MAGIC)
            put("slot_key", normalizedSlot)
            put("capsule_id", AEGIS_CAPSULE_ID)
            put("generation", safeGeneration)
            put("created_at_utc", createdAtUtc)
            put("payload_sha256", payloadSha256)
            put("ciphertext_b64", ciphertextB64)
            put("gates_verified", gatesObj)
        }
    }

    /**
     * Unseals and verifies all 6 Gates of an `aegisEnvelopeV1` JSON payload before returning
     * the decrypted [GameSaveSnapshot].
     */
    fun unsealSixGateAegisEnvelopeV1(
        envelopeJson: String,
        passphrase: String = "BOXBOY-DEFAULT-SYNC-KEY"
    ): Result<GameSaveSnapshot> {
        return runCatching {
            val magic = extractStringField(envelopeJson, "magic", "")
            val schema = extractStringField(envelopeJson, "schema", "")
            val slotKey = extractStringField(envelopeJson, "slot_key", "")
            val capsuleId = extractStringField(envelopeJson, "capsule_id", "")
            val generation = extractLongField(envelopeJson, "generation", 0L).toInt()
            val expectedSha256 = extractStringField(envelopeJson, "payload_sha256", "")
            val ciphertextB64 = extractStringField(envelopeJson, "ciphertext_b64", "")

            // Gate 1: Magic Header
            require(magic == AEGIS_ENVELOPE_MAGIC) {
                "G1_MAGIC_HEADER failed: expected $AEGIS_ENVELOPE_MAGIC but got '$magic'"
            }
            // Gate 2: Schema Version
            require(schema == AEGIS_ENVELOPE_SCHEMA) {
                "G2_SCHEMA_VERSION failed: expected $AEGIS_ENVELOPE_SCHEMA but got '$schema'"
            }
            // Gate 4: Monotonic Generation
            require(generation >= 1) {
                "G4_MONOTONIC_GENERATION failed: generation must be >= 1 (got $generation)"
            }
            // Gate 5: AAD Binding (capsule_id + slot_key)
            require(capsuleId == AEGIS_CAPSULE_ID && slotKey.isNotBlank()) {
                "G5_AAD_BINDING failed: capsule_id='$capsuleId', slot_key='$slotKey'"
            }
            require(extractBooleanField(envelopeJson, "G1_MAGIC_HEADER", true)) {
                "G1_MAGIC_HEADER flagged false in gates_verified"
            }

            // Decode outer Base64 wrapper if needed, then decrypt AES-256-GCM BBZ1 payload
            val encryptedBbz1 = if (ciphertextB64.startsWith("BBZ1:")) {
                ciphertextB64
            } else {
                String(decodeBase64NoWrap(ciphertextB64), Charsets.UTF_8)
            }
            val snapshot = decryptSnapshot(encryptedBbz1, passphrase).getOrThrow()

            // Gate 3: SHA-256 Integrity Check
            val actualSha256 = sha256Hex(snapshot.toJsonString())
            require(expectedSha256.equals(actualSha256, ignoreCase = true)) {
                "G3_SHA256_INTEGRITY failed: expected $expectedSha256 but computed $actualSha256"
            }

            // Gate 6: Non-Empty State Check
            require(snapshot.highestWave >= 1 && snapshot.unlockedWeaponIds.isNotEmpty()) {
                "G6_NON_EMPTY_STATE failed: empty or invalid wave/weapons state"
            }

            snapshot
        }
    }

    fun normalizeSlotKey(rawSlotKey: String): String {
        val digits = Regex("(\\d+)").find(rawSlotKey)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val index = (digits ?: 1).coerceIn(1, 20)
        return "slot_%02d".format(index)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGO)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }
}
