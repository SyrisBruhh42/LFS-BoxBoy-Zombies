package com.example.data

import android.content.Context
import com.example.R
import com.example.network.CoopPeerState
import com.example.network.OasisHostedServerClient
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class BoxBoyRepository(private val db: FirebaseFirestore) {

    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth
        get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- SAVE SLOTS ---

    fun observeSaveSlots(): Flow<List<SaveSlot>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/save_slots"
        emitAll(
            db.collection("users").document(uid).collection("save_slots")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SaveSlot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun getSaveSlots(): Result<List<SaveSlot>> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val path = "users/$uid/save_slots"
        return try {
            val snapshot = db.collection("users").document(uid).collection("save_slots")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val list = snapshot.toObjects(SaveSlot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getSaveSlotById(targetUserId: String, slotId: String): Result<SaveSlot> {
        val path = "users/$targetUserId/save_slots/$slotId"
        return try {
            val doc = db.collection("users").document(targetUserId)
                .collection("save_slots").document(slotId)
                .get()
                .await()
            val slot = doc.toObject(SaveSlot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: throw IllegalStateException("SaveSlot not found")
            Result.success(slot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            throw e
        }
    }

    suspend fun createSaveSlot(
        slotName: String,
        highestWave: Int,
        totalKills: Int,
        points: Int,
        loadoutSummary: String,
        encryptedPayload: String,
        deviceTag: String,
        customSlotId: String? = null
    ): Result<String> {
        val uid = requireUserId()
        val slotId = customSlotId?.takeIf { it.isNotBlank() } ?: "slot_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("save_slots").document(slotId)
        val payload = mapOf(
            "userId" to uid,
            "slotName" to slotName.take(60).ifBlank { "Slot 1" },
            "highestWave" to highestWave.coerceIn(1, 9999).toLong(),
            "totalKills" to totalKills.coerceIn(0, 100_000_000).toLong(),
            "points" to points.coerceIn(0, 100_000_000).toLong(),
            "loadoutSummary" to loadoutSummary.take(500).ifBlank { "Pistol Lv1" },
            "encryptedPayload" to encryptedPayload.take(8000).ifBlank { "OFFLINE_STATE" },
            "deviceTag" to deviceTag.take(80).ifBlank { "MyBoy-Android" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(slotId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSaveSlot(slot: SaveSlot): Result<Unit> {
        val uid = requireUserId()
        require(slot.id.isNotBlank()) { "SaveSlot ID must not be blank for update" }
        requireNotNull(slot.createdAt) { "SaveSlot createdAt must not be null for update" }
        val docRef = db.collection("users").document(uid).collection("save_slots").document(slot.id)
        return try {
            docRef.set(slot.copy(userId = uid, updatedAt = null)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSaveSlot(slotId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("save_slots").document(slotId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- CONTROLLER PROFILES ---

    fun observeControllerProfiles(): Flow<List<ControllerProfile>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/controller_profiles"
        emitAll(
            db.collection("users").document(uid).collection("controller_profiles")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ControllerProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createControllerProfile(
        profileName: String,
        dpadScale: Int,
        buttonOpacity: Int,
        keyMapCode: String
    ): Result<String> {
        val uid = requireUserId()
        val profileId = "prof_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("controller_profiles").document(profileId)
        val payload = mapOf(
            "userId" to uid,
            "profileName" to profileName.take(50).ifBlank { "MyBoy Custom" },
            "dpadScale" to dpadScale.coerceIn(50, 200).toLong(),
            "buttonOpacity" to buttonOpacity.coerceIn(10, 100).toLong(),
            "keyMapCode" to keyMapCode.take(1000).ifBlank { "DEFAULT" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(profileId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteControllerProfile(profileId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("controller_profiles").document(profileId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- TIER-B FIRESTORE CLOUD ROOM RELAY (/multiplayer_rooms/{roomCode}) ---

    suspend fun createOrUpdateCloudRoom(
        roomCode: String,
        hostName: String,
        hostMode: LfsHostMode,
        lifecyclePolicy: LfsLifecyclePolicy,
        hostEpoch: Long,
        wave: Int,
        activePlayers: Int,
        maxPlayers: Int = 4,
        status: String = "OPEN",
        worldSnapshotJson: String = "{}",
        latestEventJson: String = "{}"
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val normalizedCode = OasisHostedServerClient.normalizeRoomCode(roomCode)
        val docRef = db.collection("multiplayer_rooms").document(normalizedCode)
        return try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val createPayload = mapOf(
                    "roomCode" to normalizedCode,
                    "hostUserId" to uid,
                    "hostName" to hostName.take(40).ifBlank { "BOXBOY-HOST" },
                    "hostMode" to hostMode.name,
                    "lifecyclePolicy" to lifecyclePolicy.wireId,
                    "hostEpoch" to hostEpoch.coerceAtLeast(1L),
                    "wave" to wave.coerceIn(1, 9999).toLong(),
                    "activePlayers" to activePlayers.coerceIn(1, 4).toLong(),
                    "maxPlayers" to maxPlayers.coerceIn(1, 4).toLong(),
                    "status" to status,
                    "worldSnapshotJson" to worldSnapshotJson.take(32000),
                    "latestEventJson" to latestEventJson.take(8000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "hostUserId" to uid,
                    "hostName" to hostName.take(40).ifBlank { "BOXBOY-HOST" },
                    "hostMode" to hostMode.name,
                    "lifecyclePolicy" to lifecyclePolicy.wireId,
                    "hostEpoch" to hostEpoch.coerceAtLeast(1L),
                    "wave" to wave.coerceIn(1, 9999).toLong(),
                    "activePlayers" to activePlayers.coerceIn(1, 4).toLong(),
                    "maxPlayers" to maxPlayers.coerceIn(1, 4).toLong(),
                    "status" to status,
                    "worldSnapshotJson" to worldSnapshotJson.take(32000),
                    "latestEventJson" to latestEventJson.take(8000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(normalizedCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeCloudRoom(roomCode: String): Flow<CloudMultiplayerRoom?> = flow {
        requireUserId()
        val normalizedCode = OasisHostedServerClient.normalizeRoomCode(roomCode)
        val path = "multiplayer_rooms/$normalizedCode"
        emitAll(
            db.collection("multiplayer_rooms").document(normalizedCode)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(CloudMultiplayerRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }

    fun observeOpenCloudRooms(): Flow<List<HostedRoomDescriptor>> = flow {
        requireUserId()
        val path = "multiplayer_rooms"
        emitAll(
            db.collection("multiplayer_rooms")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(12)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CloudMultiplayerRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status != "CLOSED" }
                        .map { room ->
                            HostedRoomDescriptor(
                                roomId = room.roomCode,
                                roomCode = room.roomCode,
                                hostMode = runCatching { LfsHostMode.valueOf(room.hostMode) }
                                    .getOrDefault(LfsHostMode.LAUNCH_U2_HOSTING_SERVICE),
                                lifecyclePolicy = LfsLifecyclePolicy.entries.firstOrNull { it.wireId == room.lifecyclePolicy }
                                    ?: LfsLifecyclePolicy.ON_DEMAND_WAKE,
                                playerCount = room.activePlayers.toInt(),
                                activePlayers = room.activePlayers.toInt(),
                                maxPlayers = room.maxPlayers.toInt(),
                                currentWave = room.wave.toInt().coerceAtLeast(1),
                                serverRegion = "Firestore-Cloud-Relay",
                                hostEpoch = room.hostEpoch.toInt().coerceAtLeast(1),
                                pingMs = 28L,
                                isStandbyWakeable = true
                            )
                        }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun publishPeerStateToRoom(roomCode: String, peer: CoopPeerState): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val normalizedCode = OasisHostedServerClient.normalizeRoomCode(roomCode)
        val sanitizedPeerId = peer.peerId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64).ifBlank { "peer_$uid" }
        val docRef = db.collection("multiplayer_rooms").document(normalizedCode)
            .collection("peers").document(sanitizedPeerId)
        val payload = mapOf<String, Any>(
            "peerId" to sanitizedPeerId,
            "userId" to uid,
            "roomCode" to normalizedCode,
            "playerName" to peer.callsign.take(40).ifBlank { "SQUAD-PEER" },
            "x" to peer.x.toDouble().coerceIn(-1000.0, 10000.0),
            "y" to peer.y.toDouble().coerceIn(-1000.0, 10000.0),
            "vx" to peer.vx.toDouble().coerceIn(-5000.0, 5000.0),
            "vy" to peer.vy.toDouble().coerceIn(-5000.0, 5000.0),
            "aimAngleRad" to peer.aimAngleRad.toDouble(),
            "hp" to peer.hp.coerceIn(0, 10000).toLong(),
            "maxHp" to peer.maxHp.coerceIn(1, 10000).toLong(),
            "shield" to peer.shield.coerceIn(0, 10000).toLong(),
            "weaponId" to peer.weaponId.take(40).ifBlank { "pistol" },
            "weaponLevel" to peer.weaponLevel.coerceIn(1, 20).toLong(),
            "isFiring" to peer.isFiring,
            "isDowned" to peer.isDowned,
            "reviveProgress" to peer.reviveProgress.toDouble().coerceIn(0.0, 1.0),
            "score" to peer.score.coerceIn(0, 100_000_000).toLong(),
            "wave" to peer.wave.coerceIn(1, 9999).toLong(),
            "isAiBot" to peer.isAiBot,
            "botPersonaId" to peer.botPersonaId.take(32),
            "visorColorHex" to peer.visorColorHex,
            "radioCalloutText" to peer.radioCalloutText.take(160),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeRoomPeers(roomCode: String): Flow<List<CoopPeerState>> = flow {
        val uid = requireUserId()
        val normalizedCode = OasisHostedServerClient.normalizeRoomCode(roomCode)
        val path = "multiplayer_rooms/$normalizedCode/peers"
        emitAll(
            db.collection("multiplayer_rooms").document(normalizedCode)
                .collection("peers")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CloudRoomPeer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.userId != uid || it.isAiBot }
                        .map { cloudPeer ->
                            val resolvedName = cloudPeer.playerName.ifBlank { cloudPeer.peerName }
                            CoopPeerState(
                                peerId = cloudPeer.peerId,
                                peerName = resolvedName,
                                playerName = resolvedName,
                                x = cloudPeer.x.toFloat(),
                                y = cloudPeer.y.toFloat(),
                                vx = cloudPeer.vx.toFloat(),
                                vy = cloudPeer.vy.toFloat(),
                                aimAngleRad = cloudPeer.aimAngleRad.toFloat(),
                                hp = cloudPeer.hp.toInt(),
                                maxHp = cloudPeer.maxHp.toInt().coerceAtLeast(1),
                                shield = cloudPeer.shield.toInt(),
                                weaponId = cloudPeer.weaponId,
                                weaponLevel = cloudPeer.weaponLevel.toInt().coerceAtLeast(1),
                                isFiring = cloudPeer.isFiring,
                                isDowned = cloudPeer.isDowned,
                                reviveProgress = cloudPeer.reviveProgress.toFloat(),
                                score = cloudPeer.score.toInt(),
                                kills = cloudPeer.kills.toInt(),
                                wave = cloudPeer.wave.toInt().coerceAtLeast(1),
                                isAiBot = cloudPeer.isAiBot,
                                botPersonaId = cloudPeer.botPersonaId,
                                visorColorHex = cloudPeer.visorColorHex,
                                radioCalloutText = cloudPeer.radioCalloutText,
                                radioCalloutTtlMs = if (cloudPeer.radioCalloutText.isNotBlank()) 2500L else 0L,
                                lastUpdatedMs = cloudPeer.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                                updatedAtMs = cloudPeer.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                            )
                        }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    // --- GOOGLE PLAY USER DATA POLICY: COMPLETE CLOUD DATA WIPE ---

    /**
     * Permanently deletes all Firestore cloud records owned by the currently authenticated user
     * (`users/{uid}/save_slots`, `users/{uid}/controller_profiles`, and closes any hosted `multiplayer_rooms`).
     */
    suspend fun deleteAllUserCloudData(): Result<Int> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            var deletedCount = 0

            val saveSnap = db.collection("users").document(uid).collection("save_slots")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            for (doc in saveSnap.documents) {
                doc.reference.delete().await()
                deletedCount++
            }

            val profileSnap = db.collection("users").document(uid).collection("controller_profiles")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            for (doc in profileSnap.documents) {
                doc.reference.delete().await()
                deletedCount++
            }

            // Close any active rooms hosted by this user so no profile metadata remains active
            runCatching {
                val roomSnap = db.collection("multiplayer_rooms")
                    .whereEqualTo("hostUserId", uid)
                    .get()
                    .await()
                for (roomDoc in roomSnap.documents) {
                    roomDoc.reference.update(
                        mapOf(
                            "hostName" to "DELETED-HOST",
                            "status" to "CLOSED",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    deletedCount++
                }
            }

            Result.success(deletedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }
}

