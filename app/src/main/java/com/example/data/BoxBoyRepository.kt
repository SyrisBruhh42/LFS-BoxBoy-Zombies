package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
}
