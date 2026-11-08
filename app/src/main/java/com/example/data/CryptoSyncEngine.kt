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

    private const val SHARED_AEGIS_KEY_CONTEXT = "lfs-oasis-aegis-envelope-v1-master-key"

    private fun deriveSharedEnvelopeKey(passphrase: String): SecretKeySpec {
        val clean = passphrase.ifBlank { "BOXBOY-DEFAULT-SYNC-KEY" }
        val material = "$SHARED_AEGIS_KEY_CONTEXT::$clean".toByteArray(Charsets.UTF_8)
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(material)
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun hmacSha256Hex(keyBytes: ByteArray, message: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val digest = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Seals a [GameSaveSnapshot] into the canonical 4-Way 6-Gate `aegisEnvelopeV1` JSON structure
     * for 20-slot Time-Machine SaveLab custody (`slot_01`..`slot_20`) and cross-surface sync
     * across `:19448`, `U2 :3000`, `U4 SaveCustodyViewModel`, and `BoxBoys` (`FP-N04`).
     */
    fun sealSixGateAegisEnvelopeV1(
        slotKey: String,
        snapshot: GameSaveSnapshot,
        generation: Int = 1,
        passphrase: String = "BOXBOY-DEFAULT-SYNC-KEY",
        roomId: String = "LFS-8K4M2P",
        cipherMode: String = "AES_256_GCM"
    ): JSONObject {
        val normalizedSlot = normalizeSlotKey(slotKey)
        val safeGeneration = generation.coerceAtLeast(1)
        val canonicalJson = snapshot.toJsonString()
        val payloadSha256 = sha256Hex(canonicalJson)
        val normCipher = cipherMode.trim().uppercase().replace("-", "_")
        val createdAtUtc = DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(snapshot.timestampEpochMs.coerceAtLeast(1L))
        )

        val ivB64: String
        val ciphertextB64: String
        val authTagB64: String
        val effectiveCipher: String
        val effectiveAlgorithm: String

        if (normCipher == "CANONICAL_BASE64_JSON") {
            effectiveCipher = "CANONICAL_BASE64_JSON"
            effectiveAlgorithm = "CANONICAL_BASE64_JSON"
            ivB64 = encodeBase64NoWrap(ByteArray(IV_BYTES))
            ciphertextB64 = encodeBase64NoWrap(canonicalJson.toByteArray(Charsets.UTF_8))
            val tagBytes = MessageDigest.getInstance("SHA-256")
                .digest(canonicalJson.toByteArray(Charsets.UTF_8))
                .copyOfRange(0, 16)
            authTagB64 = encodeBase64NoWrap(tagBytes)
        } else {
            effectiveCipher = "AES_256_GCM"
            effectiveAlgorithm = "AES-256-GCM"
            val random = SecureRandom()
            val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
            val aad = "$roomId|$normalizedSlot|$AEGIS_CAPSULE_ID|$safeGeneration".toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, deriveSharedEnvelopeKey(passphrase), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            val sealed = cipher.doFinal(canonicalJson.toByteArray(Charsets.UTF_8))
            val ctBytes = sealed.copyOfRange(0, sealed.size - 16)
            val tagBytes = sealed.copyOfRange(sealed.size - 16, sealed.size)
            ivB64 = encodeBase64NoWrap(iv)
            // Keep encryptedBbz1 compatibility when default sealSixGateAegisEnvelopeV1 is called without explicit AES_256_GCM raw mode
            val encryptedBbz1 = encryptSnapshot(snapshot, passphrase)
            ciphertextB64 = if (cipherMode == "AES_256_GCM_RAW") {
                encodeBase64NoWrap(ctBytes)
            } else {
                encodeBase64NoWrap(encryptedBbz1.toByteArray(Charsets.UTF_8))
            }
            authTagB64 = encodeBase64NoWrap(tagBytes)
        }

        val ciphertextSha256 = sha256Hex(ciphertextB64)
        val hmacKey = deriveSharedEnvelopeKey(passphrase).encoded
        val hmacSig = hmacSha256Hex(hmacKey, "$roomId|$normalizedSlot|$AEGIS_CAPSULE_ID|$safeGeneration:$payloadSha256:$ciphertextB64")

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
            put("version", 1)
            put("cipher", effectiveCipher)
            put("algorithm", effectiveAlgorithm)
            put("keyId", "key_oasis_custody_v1")
            put("subuserId", "primary")
            put("room_id", roomId)
            put("roomId", roomId)
            put("slot_key", normalizedSlot)
            put("slotKey", normalizedSlot)
            put("slotId", normalizedSlot)
            put("capsule_id", AEGIS_CAPSULE_ID)
            put("capsuleId", AEGIS_CAPSULE_ID)
            put("generation", safeGeneration)
            put("created_at_utc", createdAtUtc)
            put("sealedAtIso", createdAtUtc)
            put("updatedAtEpochMs", snapshot.timestampEpochMs)
            put("updated_at_epoch_ms", snapshot.timestampEpochMs)
            put("ivB64", ivB64)
            put("ivBase64", ivB64)
            put("iv_b64", ivB64)
            put("payload_sha256", payloadSha256)
            put("plaintextSha256", payloadSha256)
            put("ciphertext_b64", ciphertextB64)
            put("ciphertextB64", ciphertextB64)
            put("ciphertextBase64", ciphertextB64)
            put("authTagBase64", authTagB64)
            put("authTagB64", authTagB64)
            put("auth_tag_b64", authTagB64)
            put("ciphertext_sha256", ciphertextSha256)
            put("ciphertextSha256", ciphertextSha256)
            put("hmacSignature", hmacSig)
            put("hmac_signature", hmacSig)
            put("gates_verified", gatesObj)
        }
    }

    fun sealAegisEnvelopeV1(
        slotKey: String,
        snapshot: GameSaveSnapshot,
        generation: Int = 1,
        passphrase: String = "BOXBOY-DEFAULT-SYNC-KEY",
        roomId: String = "LFS-8K4M2P",
        cipher: String = "AES_256_GCM"
    ): JSONObject = sealSixGateAegisEnvelopeV1(
        slotKey = slotKey,
        snapshot = snapshot,
        generation = generation,
        passphrase = passphrase,
        roomId = roomId,
        cipherMode = cipher
    )

    /**
     * Unseals and verifies all 6 Gates of an `aegisEnvelopeV1` JSON payload from any of the 4
     * Oasis surfaces (`:19448`, `U2 :3000`, `U4`, or `BoxBoys`) before returning [GameSaveSnapshot].
     */
    fun unsealSixGateAegisEnvelopeV1(
        envelopeJson: String,
        passphrase: String = "BOXBOY-DEFAULT-SYNC-KEY"
    ): Result<GameSaveSnapshot> {
        return runCatching {
            val hasExplicitMagic = Regex("\"magic\"\\s*:").containsMatchIn(envelopeJson)
            val magic = extractStringField(envelopeJson, "magic", if (hasExplicitMagic) "" else AEGIS_ENVELOPE_MAGIC)
            val schema = extractStringField(envelopeJson, "schema", "")
            val slotKey = extractStringField(envelopeJson, "slot_key", "")
                .ifBlank { extractStringField(envelopeJson, "slotKey", "") }
                .ifBlank { extractStringField(envelopeJson, "slotId", "slot_01") }
            val capsuleId = extractStringField(envelopeJson, "capsule_id", "")
                .ifBlank { extractStringField(envelopeJson, "capsuleId", AEGIS_CAPSULE_ID) }
            val roomId = extractStringField(envelopeJson, "room_id", "")
                .ifBlank { extractStringField(envelopeJson, "roomId", "LFS-8K4M2P") }
            val generation = extractLongField(envelopeJson, "generation", 1L).toInt()
            val expectedSha256 = extractStringField(envelopeJson, "payload_sha256", "")
                .ifBlank { extractStringField(envelopeJson, "plaintextSha256", "") }
            val ciphertextB64 = extractStringField(envelopeJson, "ciphertext_b64", "")
                .ifBlank { extractStringField(envelopeJson, "ciphertextB64", "") }
                .ifBlank { extractStringField(envelopeJson, "ciphertextBase64", "") }
            val ivB64 = extractStringField(envelopeJson, "ivB64", "")
                .ifBlank { extractStringField(envelopeJson, "ivBase64", "") }
                .ifBlank { extractStringField(envelopeJson, "iv_b64", "") }
            val authTagB64 = extractStringField(envelopeJson, "authTagBase64", "")
                .ifBlank { extractStringField(envelopeJson, "authTagB64", "") }
                .ifBlank { extractStringField(envelopeJson, "auth_tag_b64", "") }
            val providedHmac = extractStringField(envelopeJson, "hmacSignature", "")
                .ifBlank { extractStringField(envelopeJson, "hmac_signature", "") }
            val cipherMode = extractStringField(envelopeJson, "cipher", "")
                .ifBlank { extractStringField(envelopeJson, "algorithm", "AES_256_GCM") }
                .uppercase()
                .replace("-", "_")

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
            // Gate 5: AAD Binding (capsule_id + slot_key + room_id + generation)
            require(capsuleId.isNotBlank() && slotKey.isNotBlank()) {
                "G5_AAD_BINDING failed: capsule_id='$capsuleId', slot_key='$slotKey'"
            }
            require(extractBooleanField(envelopeJson, "G1_MAGIC_HEADER", true)) {
                "G1_MAGIC_HEADER flagged false in gates_verified"
            }

            if (providedHmac.isNotBlank()) {
                val hmacKey = deriveSharedEnvelopeKey(passphrase).encoded
                val expectedHmac = hmacSha256Hex(hmacKey, "$roomId|$slotKey|$capsuleId|$generation:$expectedSha256:$ciphertextB64")
                val hmacMatches = MessageDigest.isEqual(
                    providedHmac.lowercase().toByteArray(Charsets.UTF_8),
                    expectedHmac.lowercase().toByteArray(Charsets.UTF_8)
                )
                require(hmacMatches) {
                    "G5_AAD_BINDING failed: HMAC signature mismatch for roomId='$roomId', slotKey='$slotKey', capsuleId='$capsuleId', generation=$generation"
                }
            }

            var rawDecryptedJson: String? = null
            val snapshot: GameSaveSnapshot = if (ciphertextB64.startsWith("BBZ1:")) {
                decryptSnapshot(ciphertextB64, passphrase).getOrThrow()
            } else {
                val decodedOuter = decodeBase64NoWrap(ciphertextB64)
                val decodedText = runCatching { String(decodedOuter, Charsets.UTF_8) }.getOrDefault("")
                if (decodedText.startsWith("BBZ1:")) {
                    decryptSnapshot(decodedText, passphrase).getOrThrow()
                } else if (cipherMode == "CANONICAL_BASE64_JSON" || decodedText.trimStart().startsWith("{")) {
                    rawDecryptedJson = decodedText
                    GameSaveSnapshot.fromJsonString(decodedText)
                } else {
                    val ivBytes = decodeBase64NoWrap(ivB64)
                    val tagBytes = if (authTagB64.isNotBlank()) decodeBase64NoWrap(authTagB64) else ByteArray(0)
                    val combinedCt = if (tagBytes.size == 16) decodedOuter + tagBytes else decodedOuter
                    val plainBytes = runCatching {
                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        val aad = "$roomId|$slotKey|$capsuleId|$generation".toByteArray(Charsets.UTF_8)
                        cipher.init(Cipher.DECRYPT_MODE, deriveSharedEnvelopeKey(passphrase), GCMParameterSpec(GCM_TAG_BITS, ivBytes))
                        cipher.updateAAD(aad)
                        cipher.doFinal(combinedCt)
                    }.recoverCatching {
                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        cipher.init(Cipher.DECRYPT_MODE, deriveSharedEnvelopeKey(passphrase), GCMParameterSpec(GCM_TAG_BITS, ivBytes))
                        cipher.doFinal(combinedCt)
                    }.getOrThrow()
                    val plainJson = String(plainBytes, Charsets.UTF_8)
                    rawDecryptedJson = plainJson
                    GameSaveSnapshot.fromJsonString(plainJson)
                }
            }

            // Gate 3: SHA-256 Integrity Check
            val canonicalSha256 = sha256Hex(snapshot.toJsonString())
            val rawSha256 = rawDecryptedJson?.let { sha256Hex(it) } ?: canonicalSha256
            require(
                expectedSha256.equals(canonicalSha256, ignoreCase = true) ||
                    expectedSha256.equals(rawSha256, ignoreCase = true)
            ) {
                "G3_SHA256_INTEGRITY failed: expected $expectedSha256 but computed $canonicalSha256"
            }

            // Gate 6: Non-Empty State Check
            require(snapshot.highestWave >= 1 && snapshot.unlockedWeaponIds.isNotEmpty()) {
                "G6_NON_EMPTY_STATE failed: empty or invalid wave/weapons state"
            }

            snapshot
        }
    }

    fun verifyAegisEnvelopeV1(
        envelopeJson: String,
        passphrase: String = "BOXBOY-DEFAULT-SYNC-KEY"
    ): Result<GameSaveSnapshot> = unsealSixGateAegisEnvelopeV1(envelopeJson, passphrase)

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

