package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BoxBoyRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createSaveSlot_validPayload_createsDocumentAndReturnsId() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = BoxBoyRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSaveSlot(
                slotName = "Slot 1 - Bunker Wave 14",
                highestWave = 14,
                totalKills = 520,
                points = 9400,
                loadoutSummary = "M1911 Lv2, Shotgun Lv3",
                encryptedPayload = "BBZ1:SAMPLE_AESGCM_CIPHERTEXT",
                deviceTag = "MyBoy-Test"
            )
        }
        assertTrue(result.isSuccess)
        val slotId = result.getOrThrow()
        assertTrue(slotId.isNotEmpty())
    }

    @Test
    fun observeSaveSlots_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = BoxBoyRepository(firestore)
        val slotId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSaveSlot(
                slotName = "Realtime Slot",
                highestWave = 8,
                totalKills = 210,
                points = 3400,
                loadoutSummary = "UZI Lv2",
                encryptedPayload = "BBZ1:PAYLOAD",
                deviceTag = "MyBoy-Test"
            ).getOrThrow()
        }

        val emittedSlots = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSaveSlots().first { list -> list.any { it.id == slotId } }
        }
        assertTrue(emittedSlots.any { it.id == slotId })
    }

    @Test
    fun getSaveSlotById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = BoxBoyRepository(firestore)
        val slotId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createSaveSlot(
                slotName = "Alice Private Save",
                highestWave = 10,
                totalKills = 300,
                points = 4500,
                loadoutSummary = "Railgun Lv1",
                encryptedPayload = "BBZ1:ALICE_SECRET",
                deviceTag = "MyBoy-Alice"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = BoxBoyRepository(firestore)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getSaveSlotById(aliceUid, slotId).getOrThrow()
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun getSaveSlotById_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = BoxBoyRepository(firestore)
        val slotId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSaveSlot(
                slotName = "Alice Save Unauth Test",
                highestWave = 5,
                totalKills = 100,
                points = 1200,
                loadoutSummary = "Pistol Lv1",
                encryptedPayload = "BBZ1:UNAUTH_TEST",
                deviceTag = "MyBoy-Alice"
            ).getOrThrow()
        }

        auth.signOut()
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.getSaveSlotById(aliceUid, slotId).getOrThrow()
            }
            fail("Expected PERMISSION_DENIED when signed out")
        } catch (e: Exception) {
            val firestoreEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    @Test
    fun createControllerProfile_validPayload_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = BoxBoyRepository(firestore)
        val res = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createControllerProfile(
                profileName = "MyBoy Claw Pro",
                dpadScale = 125,
                buttonOpacity = 75,
                keyMapCode = "DPAD:BL|AB:BR|TURBO:ON"
            )
        }
        assertTrue(res.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
