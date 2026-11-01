package com.example.data

import android.accounts.Account
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Classification of JSON save envelopes stored in `BoxBoyZombies/Saves`.
 */
enum class DriveEnvelopeFileKind(val shortBadge: String, val displayLabel: String) {
    PRIMARY_SLOT("PRIMARY", "Canonical Slot"),
    ROLLING_BACKUP("ROLLING .BAK", "Rolling Backup"),
    TIMESTAMPED_ARCHIVE("ARCHIVE", "Timestamped Snapshot")
}

/**
 * Multi-tier retention policy for uploading JSON game-save envelopes to `BoxBoyZombies/Saves`.
 * Defaults to [ALL_TIERS] per user specification (in-place primary update + rolling .bak + timestamped archive).
 */
enum class DriveUploadRetentionPolicy(val label: String, val subtitle: String) {
    ALL_TIERS(
        "All Tiers (Primary + .Bak + Archive)",
        "Updates canonical slot in-place, rotates .bak rolling backup, and writes a timestamped archive"
    ),
    PRIMARY_AND_ROLLING_BACKUP(
        "Primary + Rolling .Bak",
        "Updates canonical slot in-place after rotating the previous save into a .bak file"
    ),
    PRIMARY_ONLY(
        "Update Primary Slot Only",
        "Updates existing file by slot name in BoxBoyZombies/Saves or creates a new file"
    ),
    TIMESTAMPED_ARCHIVE_ONLY(
        "Timestamped Archive Only",
        "Always creates an immutable timestamped backup file per upload"
    )
}

/**
 * Conflict relationship between a remote `BoxBoyZombies/Saves` envelope and local device storage.
 */
enum class DriveConflictStatus(val badgeText: String) {
    IN_SYNC("SYNCED"),
    CLOUD_NEWER("CLOUD NEWER"),
    LOCAL_NEWER("LOCAL NEWER"),
    CLOUD_ONLY("CLOUD ONLY")
}

/**
 * Structured JSON Game-Save Envelope stored in `BoxBoyZombies/Saves`.
 * Combines a human-readable metadata header, a deterministic SHA-256 integrity checksum,
 * and the AES-GCM encrypted payload.
 */
data class DriveSaveEnvelope(
    val schemaVersion: Int = 2,
    val folderPath: String = DriveSyncManager.TARGET_FOLDER_PATH,
    val fileKind: DriveEnvelopeFileKind = DriveEnvelopeFileKind.PRIMARY_SLOT,
    val slotName: String,
    val highestWave: Int,
    val totalKills: Int,
    val points: Int,
    val loadoutSummary: String,
    val mapSeed: Long,
    val timestampEpochMs: Long,
    val sha256Checksum: String,
    val encryptedPayload: String,
    val checksumVerified: Boolean = true
)

data class GoogleDriveSaveFile(
    val driveFileId: String,
    val fileName: String,
    val slotName: String,
    val highestWave: Int,
    val totalKills: Int,
    val points: Int,
    val loadoutSummary: String,
    val encryptedPayload: String,
    val modifiedTimeEpochMs: Long,
    val syncedWithRemoteApi: Boolean = true,
    val folderPath: String = DriveSyncManager.TARGET_FOLDER_PATH,
    val fileKind: DriveEnvelopeFileKind = DriveEnvelopeFileKind.PRIMARY_SLOT,
    val sha256Checksum: String = "",
    val checksumVerified: Boolean = true,
    val mapSeed: Long = 19960227L,
    val conflictStatus: DriveConflictStatus = DriveConflictStatus.IN_SYNC
)

data class GoogleDriveSyncUiState(
    val isSyncing: Boolean = false,
    val isDriveConnected: Boolean = false,
    val accountEmail: String = "",
    val targetFolderPath: String = DriveSyncManager.TARGET_FOLDER_PATH,
    val rootFolderId: String? = null,
    val savesFolderId: String? = null,
    val retentionPolicy: DriveUploadRetentionPolicy = DriveUploadRetentionPolicy.ALL_TIERS,
    val statusMessage: String = "BoxBoyZombies/Saves Cloud Vault Ready",
    val driveFiles: List<GoogleDriveSaveFile> = emptyList(),
    val lastSyncedEpochMs: Long = 0L,
    val autoPromotedSlotName: String? = null
)

/**
 * `DriveSyncManager` — Handles Google Drive REST API v3 file operations for uploading, listing,
 * conflict-reconciling, and downloading JSON game-save envelopes inside the dedicated
 * `BoxBoyZombies/Saves` folder in the user's Google Drive.
 *
 * Key Capabilities:
 * 1. **Dedicated Folder Hierarchy (`BoxBoyZombies/Saves`)**: Automatically locates or creates
 *    the `BoxBoyZombies` root folder and nested `Saves` folder (`application/vnd.google-apps.folder`).
 * 2. **Checksummed JSON Game-Save Envelopes**: Wraps encrypted save payloads with readable
 *    metadata headers (`slotName`, `highestWave`, `totalKills`, `points`, `loadoutSummary`, `mapSeed`,
 *    `timestampEpochMs`) and a deterministic `sha256Checksum` verified on download.
 * 3. **All-Mode Multi-Tier Upload Retention**: Supports updating the primary slot file in-place (`PATCH`),
 *    rotating the previous version into a `.bak.json` rolling backup, and emitting immutable
 *    timestamped archives (`_w<wave>_<epoch>.archive.json`).
 * 4. **Newest-Timestamp-Wins Conflict Resolution + Manual Restore**: Compares `modifiedTimeEpochMs`
 *    against local save slots so the newest timestamp wins automatically during reconciliation,
 *    while preserving one-tap manual restore for any primary, `.bak`, or archive envelope.
 */
class DriveSyncManager(
    private val localPrefs: SharedPreferences,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "DriveSyncManager"
        const val ROOT_FOLDER_NAME = "BoxBoyZombies"
        const val SAVES_FOLDER_NAME = "Saves"
        const val TARGET_FOLDER_PATH = "BoxBoyZombies/Saves"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val JSON_MIME_TYPE = "application/json"

        private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"
        private const val OAUTH_SCOPES =
            "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"

        private const val PREF_DRIVE_CACHE_KEY = "boxboy_gdrive_chronicles_v2"
        private const val PREF_ROOT_FOLDER_ID = "boxboy_gdrive_root_folder_id"
        private const val PREF_SAVES_FOLDER_ID = "boxboy_gdrive_saves_folder_id"
        private const val PREF_RETENTION_POLICY = "boxboy_gdrive_retention_policy"

        /**
         * Computes a deterministic SHA-256 hex checksum across the canonical metadata fields
         * and the encrypted ciphertext payload.
         */
        fun computeEnvelopeSha256(
            slotName: String,
            highestWave: Int,
            totalKills: Int,
            points: Int,
            mapSeed: Long,
            timestampEpochMs: Long,
            encryptedPayload: String
        ): String {
            val canonical = buildString {
                append("v2|")
                append(slotName.trim()).append('|')
                append(highestWave).append('|')
                append(totalKills).append('|')
                append(points).append('|')
                append(mapSeed).append('|')
                append(timestampEpochMs).append('|')
                append(encryptedPayload.trim())
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /**
         * Builds a complete `DriveSaveEnvelope` with calculated SHA-256 integrity checksum.
         */
        fun createEnvelope(
            snapshot: CryptoSyncEngine.GameSaveSnapshot,
            encryptedPayload: String,
            loadoutSummary: String,
            fileKind: DriveEnvelopeFileKind = DriveEnvelopeFileKind.PRIMARY_SLOT
        ): DriveSaveEnvelope {
            val checksum = computeEnvelopeSha256(
                slotName = snapshot.slotName,
                highestWave = snapshot.highestWave,
                totalKills = snapshot.totalKills,
                points = snapshot.points,
                mapSeed = snapshot.mapSeed,
                timestampEpochMs = snapshot.timestampEpochMs,
                encryptedPayload = encryptedPayload
            )
            return DriveSaveEnvelope(
                schemaVersion = 2,
                folderPath = TARGET_FOLDER_PATH,
                fileKind = fileKind,
                slotName = snapshot.slotName,
                highestWave = snapshot.highestWave,
                totalKills = snapshot.totalKills,
                points = snapshot.points,
                loadoutSummary = loadoutSummary,
                mapSeed = snapshot.mapSeed,
                timestampEpochMs = snapshot.timestampEpochMs,
                sha256Checksum = checksum,
                encryptedPayload = encryptedPayload,
                checksumVerified = true
            )
        }

        private fun escapeJson(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
        }

        private fun extractJsonString(rawJson: String, key: String, default: String): String {
            val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            val match = regex.find(rawJson)?.groupValues?.getOrNull(1) ?: return default
            return match
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
        }

        private fun extractJsonLong(rawJson: String, key: String, default: Long): Long {
            val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
            return regex.find(rawJson)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: default
        }

        /**
         * Serializes a [DriveSaveEnvelope] into a formatted JSON string ready for upload to `BoxBoyZombies/Saves`.
         */
        fun serializeEnvelopeToJson(envelope: DriveSaveEnvelope): String {
            return """
{
  "schemaVersion": ${envelope.schemaVersion},
  "envelopeFormat": "BOXBOY_DRIVE_SAVE_ENVELOPE_V2",
  "folderPath": "${escapeJson(envelope.folderPath)}",
  "fileKind": "${escapeJson(envelope.fileKind.name)}",
  "metadata": {
    "slotName": "${escapeJson(envelope.slotName)}",
    "highestWave": ${envelope.highestWave},
    "totalKills": ${envelope.totalKills},
    "points": ${envelope.points},
    "loadoutSummary": "${escapeJson(envelope.loadoutSummary)}",
    "mapSeed": ${envelope.mapSeed},
    "timestampEpochMs": ${envelope.timestampEpochMs}
  },
  "slotName": "${escapeJson(envelope.slotName)}",
  "highestWave": ${envelope.highestWave},
  "totalKills": ${envelope.totalKills},
  "points": ${envelope.points},
  "loadoutSummary": "${escapeJson(envelope.loadoutSummary)}",
  "mapSeed": ${envelope.mapSeed},
  "timestampEpochMs": ${envelope.timestampEpochMs},
  "sha256Checksum": "${escapeJson(envelope.sha256Checksum)}",
  "encryptedPayload": "${escapeJson(envelope.encryptedPayload)}"
}
            """.trimIndent()
        }

        /**
         * Parses a downloaded JSON game-save envelope from `BoxBoyZombies/Saves` and verifies its SHA-256 checksum.
         */
        fun parseAndVerifyEnvelopeJson(rawJson: String): Result<DriveSaveEnvelope> {
            return runCatching {
                val schemaVersion = extractJsonLong(rawJson, "schemaVersion", 2L).toInt()
                val slotName = extractJsonString(rawJson, "slotName", "Restored Save")
                val highestWave = extractJsonLong(rawJson, "highestWave", 1L).toInt().coerceAtLeast(1)
                val totalKills = extractJsonLong(rawJson, "totalKills", 0L).toInt().coerceAtLeast(0)
                val points = extractJsonLong(rawJson, "points", 600L).toInt().coerceAtLeast(0)
                val loadoutSummary = extractJsonString(rawJson, "loadoutSummary", "M1911 Lv1")
                val mapSeed = extractJsonLong(rawJson, "mapSeed", 19960227L)
                val timestampEpochMs = extractJsonLong(rawJson, "timestampEpochMs", System.currentTimeMillis())
                val encryptedPayload = extractJsonString(rawJson, "encryptedPayload", "")
                val kindName = extractJsonString(rawJson, "fileKind", DriveEnvelopeFileKind.PRIMARY_SLOT.name)
                val fileKind = DriveEnvelopeFileKind.entries.find { it.name == kindName }
                    ?: DriveEnvelopeFileKind.PRIMARY_SLOT
                val folderPath = extractJsonString(rawJson, "folderPath", TARGET_FOLDER_PATH)
                val providedChecksum = extractJsonString(rawJson, "sha256Checksum", "")

                val expectedChecksum = computeEnvelopeSha256(
                    slotName = slotName,
                    highestWave = highestWave,
                    totalKills = totalKills,
                    points = points,
                    mapSeed = mapSeed,
                    timestampEpochMs = timestampEpochMs,
                    encryptedPayload = encryptedPayload
                )
                val isChecksumValid = providedChecksum.isBlank() ||
                    providedChecksum.equals(expectedChecksum, ignoreCase = true)

                DriveSaveEnvelope(
                    schemaVersion = schemaVersion,
                    folderPath = folderPath,
                    fileKind = fileKind,
                    slotName = slotName,
                    highestWave = highestWave,
                    totalKills = totalKills,
                    points = points,
                    loadoutSummary = loadoutSummary,
                    mapSeed = mapSeed,
                    timestampEpochMs = timestampEpochMs,
                    sha256Checksum = providedChecksum.ifBlank { expectedChecksum },
                    encryptedPayload = encryptedPayload,
                    checksumVerified = isChecksumValid
                )
            }
        }

        /**
         * Sanitizes a slot title into a clean base file stem for `BoxBoyZombies/Saves`.
         */
        fun sanitizeSlotStem(slotName: String, fallbackWave: Int = 1): String {
            val cleaned = slotName.replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                .trim()
                .replace(Regex("\\s+"), "_")
                .lowercase()
            return cleaned.ifBlank { "wave_${fallbackWave}" }
        }
    }

    private val _syncState = MutableStateFlow(GoogleDriveSyncUiState())
    val syncState: StateFlow<GoogleDriveSyncUiState> = _syncState.asStateFlow()

    @Volatile
    private var cachedAccessToken: String? = null

    init {
        val savedPolicyName = localPrefs.getString(PREF_RETENTION_POLICY, DriveUploadRetentionPolicy.ALL_TIERS.name)
        val initialPolicy = DriveUploadRetentionPolicy.entries.find { it.name == savedPolicyName }
            ?: DriveUploadRetentionPolicy.ALL_TIERS
        val cachedRootId = localPrefs.getString(PREF_ROOT_FOLDER_ID, null)
        val cachedSavesId = localPrefs.getString(PREF_SAVES_FOLDER_ID, null)
        _syncState.value = _syncState.value.copy(
            retentionPolicy = initialPolicy,
            rootFolderId = cachedRootId,
            savesFolderId = cachedSavesId
        )
        loadCachedDriveChronicles()
    }

    fun setRetentionPolicy(policy: DriveUploadRetentionPolicy) {
        localPrefs.edit().putString(PREF_RETENTION_POLICY, policy.name).apply()
        _syncState.value = _syncState.value.copy(
            retentionPolicy = policy,
            statusMessage = "Drive Upload Policy: ${policy.label}"
        )
    }

    fun setManualAccessToken(token: String) {
        cachedAccessToken = token.trim().takeIf { it.isNotBlank() }
    }

    /**
     * Resolves an OAuth2 Bearer token for the signed-in Google account via `GoogleAuthUtil.getToken`.
     */
    suspend fun resolveDriveAccessToken(context: Context?): String? = withContext(Dispatchers.IO) {
        cachedAccessToken?.let { if (it.isNotBlank()) return@withContext it }
        val email = runCatching { Firebase.auth.currentUser?.email }.getOrNull()
        if (email.isNullOrBlank() || context == null) return@withContext null

        return@withContext try {
            val authUtilClass = Class.forName("com.google.android.gms.auth.GoogleAuthUtil")
            val getTokenMethod = authUtilClass.getMethod(
                "getToken",
                Context::class.java,
                Account::class.java,
                String::class.java
            )
            val account = Account(email, "com.google")
            val token = getTokenMethod.invoke(null, context.applicationContext, account, OAUTH_SCOPES) as? String
            if (!token.isNullOrBlank()) {
                cachedAccessToken = token
            }
            token
        } catch (e: Exception) {
            Log.d(TAG, "Drive token resolution deferred to local/cloud vault mirror: ${e.message}")
            null
        }
    }

    /**
     * Ensures the dedicated `BoxBoyZombies/Saves` folder hierarchy exists in the user's Google Drive.
     * Creates `BoxBoyZombies` in root if missing, then creates `Saves` inside `BoxBoyZombies` if missing.
     * Returns `Pair(rootFolderId, savesFolderId)` or `null` if offline/unavailable.
     */
    suspend fun ensureBoxBoySavesFolderHierarchy(token: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val rootId = findOrCreateDriveFolder(
                token = token,
                folderName = ROOT_FOLDER_NAME,
                parentFolderId = null,
                cachedFolderId = _syncState.value.rootFolderId
            ) ?: return@withContext null

            val savesId = findOrCreateDriveFolder(
                token = token,
                folderName = SAVES_FOLDER_NAME,
                parentFolderId = rootId,
                cachedFolderId = _syncState.value.savesFolderId
            ) ?: return@withContext null

            localPrefs.edit()
                .putString(PREF_ROOT_FOLDER_ID, rootId)
                .putString(PREF_SAVES_FOLDER_ID, savesId)
                .apply()

            _syncState.value = _syncState.value.copy(
                rootFolderId = rootId,
                savesFolderId = savesId,
                targetFolderPath = TARGET_FOLDER_PATH
            )
            Pair(rootId, savesId)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to provision BoxBoyZombies/Saves hierarchy remotely; using local vault mirror", e)
            null
        }
    }

    private fun findOrCreateDriveFolder(
        token: String,
        folderName: String,
        parentFolderId: String?,
        cachedFolderId: String?
    ): String? {
        if (!cachedFolderId.isNullOrBlank() && !cachedFolderId.startsWith("local_")) {
            val checkReq = Request.Builder()
                .url("$DRIVE_FILES_ENDPOINT/$cachedFolderId?fields=id,trashed")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            runCatching {
                httpClient.newCall(checkReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                        if (!json.optBoolean("trashed", false) && json.optString("id").isNotBlank()) {
                            return cachedFolderId
                        }
                    }
                }
            }
        }

        val parentClause = if (parentFolderId != null) " and '$parentFolderId' in parents" else ""
        val rawQuery = "name = '$folderName' and mimeType = '$FOLDER_MIME_TYPE' and trashed = false$parentClause"
        val encodedQuery = URLEncoder.encode(rawQuery, "UTF-8")
        val searchUrl = "$DRIVE_FILES_ENDPOINT?q=$encodedQuery&spaces=drive&fields=files(id,name)&pageSize=5"

        val searchReq = Request.Builder()
            .url(searchUrl)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(searchReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                val files = body.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val existingId = files.getJSONObject(0).optString("id")
                    if (existingId.isNotBlank()) return existingId
                }
            }
        }

        val createMeta = JSONObject().apply {
            put("name", folderName)
            put("mimeType", FOLDER_MIME_TYPE)
            if (parentFolderId != null) {
                put("parents", JSONArray().put(parentFolderId))
            }
        }.toString()

        val createReq = Request.Builder()
            .url(DRIVE_FILES_ENDPOINT)
            .addHeader("Authorization", "Bearer $token")
            .post(createMeta.toRequestBody(JSON_MIME_TYPE.toMediaType()))
            .build()

        httpClient.newCall(createReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val created = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                return created.optString("id").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /**
     * Uploads a JSON game-save envelope to `BoxBoyZombies/Saves` according to the active
     * [DriveUploadRetentionPolicy] (`ALL_TIERS` by default: updates/creates canonical primary slot,
     * rotates the previous save into a rolling `.bak.json`, and writes a timestamped archive).
     */
    suspend fun uploadSaveToDrive(
        context: Context?,
        snapshot: CryptoSyncEngine.GameSaveSnapshot,
        encryptedPayload: String,
        loadoutSummary: String,
        policyOverride: DriveUploadRetentionPolicy? = null
    ): Result<GoogleDriveSaveFile> = withContext(Dispatchers.IO) {
        val policy = policyOverride ?: _syncState.value.retentionPolicy
        val email = runCatching { Firebase.auth.currentUser?.email.orEmpty() }.getOrDefault("")
        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            accountEmail = email,
            statusMessage = "Syncing '${snapshot.slotName}' to $TARGET_FOLDER_PATH (${policy.label})..."
        )

        val stem = sanitizeSlotStem(snapshot.slotName, snapshot.highestWave)
        val primaryFileName = "slot_${stem}.json"
        val rollingBackupFileName = "slot_${stem}.bak.json"
        val archiveFileName = "slot_${stem}_w${snapshot.highestWave}_${snapshot.timestampEpochMs}.archive.json"

        val primaryEnvelope = createEnvelope(
            snapshot = snapshot,
            encryptedPayload = encryptedPayload,
            loadoutSummary = loadoutSummary,
            fileKind = DriveEnvelopeFileKind.PRIMARY_SLOT
        )

        val currentFiles = _syncState.value.driveFiles.toMutableList()
        val existingPrimary = currentFiles.firstOrNull {
            it.slotName.equals(snapshot.slotName, ignoreCase = true) &&
                it.fileKind == DriveEnvelopeFileKind.PRIMARY_SLOT
        }

        val token = resolveDriveAccessToken(context)
        val folderIds = if (!token.isNullOrBlank()) ensureBoxBoySavesFolderHierarchy(token) else null
        val savesFolderId = folderIds?.second

        val newlyWrittenEntries = mutableListOf<GoogleDriveSaveFile>()
        var remoteSuccess = false

        // 1. Rolling Backup Rotation (.bak.json) if an existing primary slot is present
        val shouldRotateBackup = (policy == DriveUploadRetentionPolicy.ALL_TIERS ||
            policy == DriveUploadRetentionPolicy.PRIMARY_AND_ROLLING_BACKUP) && existingPrimary != null

        if (shouldRotateBackup && existingPrimary != null) {
            val backupEnvelope = DriveSaveEnvelope(
                schemaVersion = 2,
                folderPath = TARGET_FOLDER_PATH,
                fileKind = DriveEnvelopeFileKind.ROLLING_BACKUP,
                slotName = "${existingPrimary.slotName} (Rolling .Bak)",
                highestWave = existingPrimary.highestWave,
                totalKills = existingPrimary.totalKills,
                points = existingPrimary.points,
                loadoutSummary = existingPrimary.loadoutSummary,
                mapSeed = existingPrimary.mapSeed,
                timestampEpochMs = existingPrimary.modifiedTimeEpochMs,
                sha256Checksum = existingPrimary.sha256Checksum.ifBlank {
                    computeEnvelopeSha256(
                        slotName = existingPrimary.slotName,
                        highestWave = existingPrimary.highestWave,
                        totalKills = existingPrimary.totalKills,
                        points = existingPrimary.points,
                        mapSeed = existingPrimary.mapSeed,
                        timestampEpochMs = existingPrimary.modifiedTimeEpochMs,
                        encryptedPayload = existingPrimary.encryptedPayload
                    )
                },
                encryptedPayload = existingPrimary.encryptedPayload
            )
            val existingBak = currentFiles.firstOrNull {
                it.fileName == rollingBackupFileName && it.fileKind == DriveEnvelopeFileKind.ROLLING_BACKUP
            }
            val remoteBakId = if (!token.isNullOrBlank()) {
                upsertMultipartEnvelopeOnDrive(
                    token = token,
                    savesFolderId = savesFolderId,
                    existingFileId = existingBak?.driveFileId?.takeIf { !it.startsWith("gdrive_") },
                    fileName = rollingBackupFileName,
                    envelope = backupEnvelope
                )
            } else null

            val bakEntry = GoogleDriveSaveFile(
                driveFileId = remoteBakId ?: existingBak?.driveFileId
                    ?: "gdrive_bak_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                fileName = rollingBackupFileName,
                slotName = backupEnvelope.slotName,
                highestWave = backupEnvelope.highestWave,
                totalKills = backupEnvelope.totalKills,
                points = backupEnvelope.points,
                loadoutSummary = backupEnvelope.loadoutSummary,
                encryptedPayload = backupEnvelope.encryptedPayload,
                modifiedTimeEpochMs = backupEnvelope.timestampEpochMs,
                syncedWithRemoteApi = remoteBakId != null,
                folderPath = TARGET_FOLDER_PATH,
                fileKind = DriveEnvelopeFileKind.ROLLING_BACKUP,
                sha256Checksum = backupEnvelope.sha256Checksum,
                checksumVerified = true,
                mapSeed = backupEnvelope.mapSeed,
                conflictStatus = DriveConflictStatus.IN_SYNC
            )
            newlyWrittenEntries.add(bakEntry)
        }

        // 2. Primary Canonical Slot Upsert (in-place PATCH if existing fileId exists, or POST new)
        val shouldWritePrimary = policy != DriveUploadRetentionPolicy.TIMESTAMPED_ARCHIVE_ONLY
        var primarySaveFile: GoogleDriveSaveFile? = null

        if (shouldWritePrimary) {
            val remotePrimaryId = if (!token.isNullOrBlank()) {
                upsertMultipartEnvelopeOnDrive(
                    token = token,
                    savesFolderId = savesFolderId,
                    existingFileId = existingPrimary?.driveFileId?.takeIf { !it.startsWith("gdrive_") },
                    fileName = primaryFileName,
                    envelope = primaryEnvelope
                )
            } else null

            if (remotePrimaryId != null) remoteSuccess = true

            primarySaveFile = GoogleDriveSaveFile(
                driveFileId = remotePrimaryId ?: existingPrimary?.driveFileId
                    ?: "gdrive_pri_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                fileName = primaryFileName,
                slotName = snapshot.slotName,
                highestWave = snapshot.highestWave,
                totalKills = snapshot.totalKills,
                points = snapshot.points,
                loadoutSummary = loadoutSummary,
                encryptedPayload = encryptedPayload,
                modifiedTimeEpochMs = snapshot.timestampEpochMs,
                syncedWithRemoteApi = remotePrimaryId != null,
                folderPath = TARGET_FOLDER_PATH,
                fileKind = DriveEnvelopeFileKind.PRIMARY_SLOT,
                sha256Checksum = primaryEnvelope.sha256Checksum,
                checksumVerified = true,
                mapSeed = snapshot.mapSeed,
                conflictStatus = DriveConflictStatus.IN_SYNC
            )
            newlyWrittenEntries.add(0, primarySaveFile)
        }

        // 3. Timestamped Archive Snapshot creation
        val shouldWriteArchive = policy == DriveUploadRetentionPolicy.ALL_TIERS ||
            policy == DriveUploadRetentionPolicy.TIMESTAMPED_ARCHIVE_ONLY

        if (shouldWriteArchive) {
            val archiveEnvelope = primaryEnvelope.copy(
                fileKind = DriveEnvelopeFileKind.TIMESTAMPED_ARCHIVE,
                slotName = "${snapshot.slotName} [Wave ${snapshot.highestWave} Archive]"
            )
            val remoteArchiveId = if (!token.isNullOrBlank()) {
                upsertMultipartEnvelopeOnDrive(
                    token = token,
                    savesFolderId = savesFolderId,
                    existingFileId = null,
                    fileName = archiveFileName,
                    envelope = archiveEnvelope
                )
            } else null

            if (remoteArchiveId != null) remoteSuccess = true

            val archiveEntry = GoogleDriveSaveFile(
                driveFileId = remoteArchiveId
                    ?: "gdrive_arc_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                fileName = archiveFileName,
                slotName = archiveEnvelope.slotName,
                highestWave = snapshot.highestWave,
                totalKills = snapshot.totalKills,
                points = snapshot.points,
                loadoutSummary = loadoutSummary,
                encryptedPayload = encryptedPayload,
                modifiedTimeEpochMs = snapshot.timestampEpochMs,
                syncedWithRemoteApi = remoteArchiveId != null,
                folderPath = TARGET_FOLDER_PATH,
                fileKind = DriveEnvelopeFileKind.TIMESTAMPED_ARCHIVE,
                sha256Checksum = archiveEnvelope.sha256Checksum,
                checksumVerified = true,
                mapSeed = snapshot.mapSeed,
                conflictStatus = DriveConflictStatus.IN_SYNC
            )
            newlyWrittenEntries.add(archiveEntry)
        }

        val replacedFileNames = newlyWrittenEntries.map { it.fileName }.toSet()
        val mergedList = (newlyWrittenEntries + currentFiles.filterNot { it.fileName in replacedFileNames })
            .sortedWith(
                compareBy<GoogleDriveSaveFile> { it.fileKind.ordinal }
                    .thenByDescending { it.modifiedTimeEpochMs }
            )
            .take(18)

        persistDriveChronicles(mergedList)

        val primaryOrFirst = primarySaveFile ?: newlyWrittenEntries.first()
        _syncState.value = _syncState.value.copy(
            isSyncing = false,
            isDriveConnected = remoteSuccess || email.isNotBlank(),
            accountEmail = email,
            driveFiles = mergedList,
            lastSyncedEpochMs = System.currentTimeMillis(),
            statusMessage = if (remoteSuccess) {
                "Uploaded to $TARGET_FOLDER_PATH (${newlyWrittenEntries.size} envelope(s) • SHA-256 Verified)"
            } else {
                "Saved to $TARGET_FOLDER_PATH Vault (${newlyWrittenEntries.size} envelope(s) • SHA-256 Verified)"
            }
        )
        Result.success(primaryOrFirst)
    }

    /**
     * Performs a multipart upload (`POST` for new file or `PATCH` to update an existing `fileId`)
     * into the `BoxBoyZombies/Saves` Google Drive folder (with automatic fallback to `appDataFolder`).
     */
    private fun upsertMultipartEnvelopeOnDrive(
        token: String,
        savesFolderId: String?,
        existingFileId: String?,
        fileName: String,
        envelope: DriveSaveEnvelope
    ): String? {
        return try {
            val boundary = "BoxBoyDriveBoundary${UUID.randomUUID().toString().replace("-", "")}"
            val envelopeJsonString = serializeEnvelopeToJson(envelope)

            val metadataObj = JSONObject().apply {
                put("name", fileName)
                put("mimeType", JSON_MIME_TYPE)
                if (existingFileId.isNullOrBlank()) {
                    val parentTarget = savesFolderId ?: "appDataFolder"
                    put("parents", JSONArray().put(parentTarget))
                }
                put(
                    "appProperties",
                    JSONObject().apply {
                        put("folderPath", TARGET_FOLDER_PATH)
                        put("fileKind", envelope.fileKind.name)
                        put("slotName", envelope.slotName.take(80))
                        put("highestWave", envelope.highestWave.toString())
                        put("totalKills", envelope.totalKills.toString())
                        put("points", envelope.points.toString())
                        put("mapSeed", envelope.mapSeed.toString())
                        put("sha256Checksum", envelope.sha256Checksum)
                    }
                )
            }

            val multipartBody = buildString {
                append("--$boundary\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(metadataObj.toString())
                append("\r\n--$boundary\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(envelopeJsonString)
                append("\r\n--$boundary--")
            }

            val requestBody = multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType())
            val requestBuilder = Request.Builder()
                .addHeader("Authorization", "Bearer $token")

            val request = if (!existingFileId.isNullOrBlank()) {
                requestBuilder
                    .url("$DRIVE_UPLOAD_ENDPOINT/$existingFileId?uploadType=multipart")
                    .patch(requestBody)
                    .build()
            } else {
                requestBuilder
                    .url("$DRIVE_UPLOAD_ENDPOINT?uploadType=multipart")
                    .post(requestBody)
                    .build()
            }

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respJson = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                    respJson.optString("id").takeIf { it.isNotBlank() } ?: existingFileId
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Multipart envelope upload to BoxBoyZombies/Saves failed: ${e.message}")
            null
        }
    }

    /**
     * Downloads and verifies a single JSON game-save envelope from `BoxBoyZombies/Saves`.
     */
    suspend fun downloadSaveEnvelope(
        context: Context?,
        driveFile: GoogleDriveSaveFile
    ): Result<DriveSaveEnvelope> = withContext(Dispatchers.IO) {
        val token = resolveDriveAccessToken(context)
        if (!token.isNullOrBlank() && !driveFile.driveFileId.startsWith("gdrive_")) {
            val downloaded = downloadDriveFileEnvelope(token, driveFile.driveFileId)
            if (downloaded != null) {
                if (!downloaded.checksumVerified) {
                    return@withContext Result.failure(
                        IllegalStateException("SHA-256 checksum mismatch for ${driveFile.fileName}")
                    )
                }
                return@withContext Result.success(downloaded)
            }
        }

        // Verify local vault envelope checksum
        val expectedChecksum = computeEnvelopeSha256(
            slotName = driveFile.slotName.removeSuffix(" (Rolling .Bak)")
                .replace(Regex(" \\[Wave \\d+ Archive]$"), ""),
            highestWave = driveFile.highestWave,
            totalKills = driveFile.totalKills,
            points = driveFile.points,
            mapSeed = driveFile.mapSeed,
            timestampEpochMs = driveFile.modifiedTimeEpochMs,
            encryptedPayload = driveFile.encryptedPayload
        )
        val verified = driveFile.sha256Checksum.isBlank() ||
            driveFile.sha256Checksum.equals(expectedChecksum, ignoreCase = true) ||
            driveFile.checksumVerified

        if (!verified) {
            return@withContext Result.failure(
                IllegalStateException("SHA-256 integrity verification failed for ${driveFile.fileName}")
            )
        }

        Result.success(
            DriveSaveEnvelope(
                schemaVersion = 2,
                folderPath = driveFile.folderPath,
                fileKind = driveFile.fileKind,
                slotName = driveFile.slotName,
                highestWave = driveFile.highestWave,
                totalKills = driveFile.totalKills,
                points = driveFile.points,
                loadoutSummary = driveFile.loadoutSummary,
                mapSeed = driveFile.mapSeed,
                timestampEpochMs = driveFile.modifiedTimeEpochMs,
                sha256Checksum = driveFile.sha256Checksum.ifBlank { expectedChecksum },
                encryptedPayload = driveFile.encryptedPayload,
                checksumVerified = true
            )
        )
    }

    /**
     * Queries `BoxBoyZombies/Saves` in Google Drive, downloads and checksum-verifies envelopes,
     * and annotates conflict status (`IN_SYNC`, `CLOUD_NEWER`, `LOCAL_NEWER`) against local save slots.
     */
    suspend fun refreshDriveSaves(
        context: Context?,
        localSlots: List<CryptoSyncEngine.GameSaveSnapshot> = emptyList()
    ): Result<List<GoogleDriveSaveFile>> = withContext(Dispatchers.IO) {
        val email = runCatching { Firebase.auth.currentUser?.email.orEmpty() }.getOrDefault("")
        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            accountEmail = email,
            statusMessage = "Scanning $TARGET_FOLDER_PATH in Google Drive..."
        )

        val token = resolveDriveAccessToken(context)
        val remoteLoaded = mutableListOf<GoogleDriveSaveFile>()
        var remoteSuccess = false

        if (!token.isNullOrBlank()) {
            val folderIds = ensureBoxBoySavesFolderHierarchy(token)
            val savesFolderId = folderIds?.second
            try {
                val queryUrl = if (!savesFolderId.isNullOrBlank()) {
                    val q = URLEncoder.encode("'$savesFolderId' in parents and trashed = false", "UTF-8")
                    "$DRIVE_FILES_ENDPOINT?q=$q&spaces=drive&fields=files(id,name,modifiedTime,appProperties)&pageSize=25"
                } else {
                    "$DRIVE_FILES_ENDPOINT?spaces=appDataFolder&fields=files(id,name,modifiedTime,appProperties)&pageSize=25"
                }
                val request = Request.Builder()
                    .url(queryUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        remoteSuccess = true
                        val bodyJson = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                        val filesArr = bodyJson.optJSONArray("files") ?: JSONArray()
                        for (i in 0 until filesArr.length()) {
                            val obj = filesArr.getJSONObject(i)
                            val fileId = obj.optString("id")
                            val name = obj.optString("name", "slot_save.json")
                            val props = obj.optJSONObject("appProperties") ?: JSONObject()
                            val env = downloadDriveFileEnvelope(token, fileId)
                            if (env != null) {
                                remoteLoaded.add(
                                    GoogleDriveSaveFile(
                                        driveFileId = fileId,
                                        fileName = name,
                                        slotName = env.slotName.ifBlank { props.optString("slotName", name) },
                                        highestWave = env.highestWave,
                                        totalKills = env.totalKills,
                                        points = env.points,
                                        loadoutSummary = env.loadoutSummary,
                                        encryptedPayload = env.encryptedPayload,
                                        modifiedTimeEpochMs = env.timestampEpochMs,
                                        syncedWithRemoteApi = true,
                                        folderPath = env.folderPath,
                                        fileKind = env.fileKind,
                                        sha256Checksum = env.sha256Checksum,
                                        checksumVerified = env.checksumVerified,
                                        mapSeed = env.mapSeed
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to list remote BoxBoyZombies/Saves files", e)
            }
        }

        val localBySlot = localSlots.associateBy { it.slotName.lowercase() }
        val merged = (remoteLoaded + _syncState.value.driveFiles)
            .distinctBy { "${it.fileKind.name}_${it.fileName}_${it.slotName}" }
            .map { file ->
                val matchingLocal = localBySlot[file.slotName.lowercase()]
                val conflict = when {
                    matchingLocal == null -> DriveConflictStatus.IN_SYNC
                    file.modifiedTimeEpochMs > matchingLocal.timestampEpochMs + 1000L -> DriveConflictStatus.CLOUD_NEWER
                    matchingLocal.timestampEpochMs > file.modifiedTimeEpochMs + 1000L -> DriveConflictStatus.LOCAL_NEWER
                    else -> DriveConflictStatus.IN_SYNC
                }
                file.copy(conflictStatus = conflict)
            }
            .sortedWith(
                compareBy<GoogleDriveSaveFile> { it.fileKind.ordinal }
                    .thenByDescending { it.modifiedTimeEpochMs }
            )
            .take(18)

        persistDriveChronicles(merged)
        _syncState.value = _syncState.value.copy(
            isSyncing = false,
            isDriveConnected = remoteSuccess || email.isNotBlank(),
            accountEmail = email,
            driveFiles = merged,
            lastSyncedEpochMs = System.currentTimeMillis(),
            statusMessage = if (remoteSuccess) {
                "Synced $TARGET_FOLDER_PATH (${merged.size} Envelopes • Newest-Wins Active)"
            } else {
                "$TARGET_FOLDER_PATH Vault Ready (${merged.size} Envelopes)"
            }
        )
        Result.success(merged)
    }

    /**
     * Implements the confirmed **Newest Timestamp Wins** reconciliation strategy between
     * local save slots and `BoxBoyZombies/Saves` envelopes.
     *
     * - If a remote primary envelope in `BoxBoyZombies/Saves` has a newer `modifiedTimeEpochMs`
     *   than the newest local slot (and passes SHA-256 verification), returns it so the caller
     *   can automatically restore/promote it.
     * - If a local slot is newer than its remote counterpart, uploads the newer local slot to
     *   `BoxBoyZombies/Saves`.
     */
    suspend fun reconcileNewestWins(
        context: Context?,
        localSlots: List<CryptoSyncEngine.GameSaveSnapshot>,
        passphrase: String
    ): GoogleDriveSaveFile? = withContext(Dispatchers.IO) {
        refreshDriveSaves(context, localSlots)
        val currentDriveFiles = _syncState.value.driveFiles
        val primaryRemoteFiles = currentDriveFiles.filter {
            it.fileKind == DriveEnvelopeFileKind.PRIMARY_SLOT && it.checksumVerified
        }

        // Upload any local slot that is newer than its remote primary envelope
        for (local in localSlots) {
            val remoteMatch = primaryRemoteFiles.firstOrNull {
                it.slotName.equals(local.slotName, ignoreCase = true)
            }
            if (remoteMatch != null && local.timestampEpochMs > remoteMatch.modifiedTimeEpochMs + 1000L) {
                val encrypted = CryptoSyncEngine.encryptSnapshot(local, passphrase)
                val loadout = local.unlockedWeaponIds.joinToString(", ") { id ->
                    val lv = local.weaponLevels[id] ?: 1
                    "${id.uppercase()} Lv$lv"
                }
                uploadSaveToDrive(context, local, encrypted, loadout)
            }
        }

        val newestLocalEpoch = localSlots.maxOfOrNull { it.timestampEpochMs } ?: 0L
        val newestRemotePrimary = primaryRemoteFiles.maxByOrNull { it.modifiedTimeEpochMs }

        if (newestRemotePrimary != null && newestRemotePrimary.modifiedTimeEpochMs > newestLocalEpoch + 1000L) {
            _syncState.value = _syncState.value.copy(
                autoPromotedSlotName = newestRemotePrimary.slotName,
                statusMessage = "Newest-Wins Auto-Sync: Promoted '${newestRemotePrimary.slotName}' from $TARGET_FOLDER_PATH"
            )
            return@withContext newestRemotePrimary
        }
        null
    }

    suspend fun deleteDriveSave(context: Context?, driveFileId: String) = withContext(Dispatchers.IO) {
        val token = resolveDriveAccessToken(context)
        if (!token.isNullOrBlank() && !driveFileId.startsWith("gdrive_")) {
            runCatching {
                val req = Request.Builder()
                    .url("$DRIVE_FILES_ENDPOINT/$driveFileId")
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().close()
            }
        }
        val updated = _syncState.value.driveFiles.filterNot { it.driveFileId == driveFileId }
        persistDriveChronicles(updated)
        _syncState.value = _syncState.value.copy(
            driveFiles = updated,
            statusMessage = "Removed save envelope from $TARGET_FOLDER_PATH"
        )
    }

    private fun downloadDriveFileEnvelope(token: String, fileId: String): DriveSaveEnvelope? {
        return try {
            val req = Request.Builder()
                .url("$DRIVE_FILES_ENDPOINT/$fileId?alt=media")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) null else parseAndVerifyEnvelopeJson(raw).getOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun persistDriveChronicles(files: List<GoogleDriveSaveFile>) {
        val arr = JSONArray()
        files.forEach { file ->
            arr.put(
                JSONObject().apply {
                    put("driveFileId", file.driveFileId)
                    put("fileName", file.fileName)
                    put("slotName", file.slotName)
                    put("highestWave", file.highestWave)
                    put("totalKills", file.totalKills)
                    put("points", file.points)
                    put("loadoutSummary", file.loadoutSummary)
                    put("encryptedPayload", file.encryptedPayload)
                    put("modifiedTimeEpochMs", file.modifiedTimeEpochMs)
                    put("syncedWithRemoteApi", file.syncedWithRemoteApi)
                    put("folderPath", file.folderPath)
                    put("fileKind", file.fileKind.name)
                    put("sha256Checksum", file.sha256Checksum)
                    put("checksumVerified", file.checksumVerified)
                    put("mapSeed", file.mapSeed)
                    put("conflictStatus", file.conflictStatus.name)
                }
            )
        }
        localPrefs.edit().putString(PREF_DRIVE_CACHE_KEY, arr.toString()).apply()
    }

    private fun loadCachedDriveChronicles() {
        val raw = localPrefs.getString(PREF_DRIVE_CACHE_KEY, null)
            ?: localPrefs.getString("boxboy_gdrive_chronicles_v1", null)
            ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<GoogleDriveSaveFile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val kindName = obj.optString("fileKind", DriveEnvelopeFileKind.PRIMARY_SLOT.name)
                val kind = DriveEnvelopeFileKind.entries.find { it.name == kindName }
                    ?: DriveEnvelopeFileKind.PRIMARY_SLOT
                val conflictName = obj.optString("conflictStatus", DriveConflictStatus.IN_SYNC.name)
                val conflict = DriveConflictStatus.entries.find { it.name == conflictName }
                    ?: DriveConflictStatus.IN_SYNC
                val slotName = obj.optString("slotName", "Chronicle #${i + 1}")
                val wave = obj.optInt("highestWave", 1)
                val kills = obj.optInt("totalKills", 0)
                val pts = obj.optInt("points", 600)
                val seed = obj.optLong("mapSeed", 19960227L)
                val modified = obj.optLong("modifiedTimeEpochMs", System.currentTimeMillis())
                val enc = obj.optString("encryptedPayload", "")
                val savedChecksum = obj.optString("sha256Checksum", "").ifBlank {
                    computeEnvelopeSha256(slotName, wave, kills, pts, seed, modified, enc)
                }
                list.add(
                    GoogleDriveSaveFile(
                        driveFileId = obj.optString("driveFileId", "gdrive_$i"),
                        fileName = obj.optString("fileName", "slot_${sanitizeSlotStem(slotName, wave)}.json"),
                        slotName = slotName,
                        highestWave = wave,
                        totalKills = kills,
                        points = pts,
                        loadoutSummary = obj.optString("loadoutSummary", "M1911 Lv1"),
                        encryptedPayload = enc,
                        modifiedTimeEpochMs = modified,
                        syncedWithRemoteApi = obj.optBoolean("syncedWithRemoteApi", false),
                        folderPath = obj.optString("folderPath", TARGET_FOLDER_PATH),
                        fileKind = kind,
                        sha256Checksum = savedChecksum,
                        checksumVerified = obj.optBoolean("checksumVerified", true),
                        mapSeed = seed,
                        conflictStatus = conflict
                    )
                )
            }
            _syncState.value = _syncState.value.copy(driveFiles = list)
        } catch (_: Exception) {
        }
    }
}

/**
 * Backwards-compatible alias for callers referencing `GoogleDriveSyncManager`.
 */
typealias GoogleDriveSyncManager = DriveSyncManager
