package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.remote.CsiCloudRepository
import com.example.data.remote.CustomRoutineDoc
import com.example.data.remote.NoteReminderDoc
import com.example.data.remote.UserProfileDoc
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CsiRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertProfile_and_observeProfile_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = CsiCloudRepository(firestore, auth)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertProfile(
                UserProfileDoc(
                    userId = uid,
                    displayName = "S Irfan",
                    wakeWord = "Hey Voice Master",
                    assistantPersonality = "professional",
                    preferredLanguage = "en-US",
                    offlineModeOnly = false,
                    analyticsConsent = true,
                    cloudSyncEnabled = true
                )
            )
        }
        assertTrue("Expected profile save to succeed", saveResult.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProfile(uid).first { it != null }
        }
        assertEquals("S Irfan", observed?.displayName)
    }

    @Test
    fun routines_and_notes_crud_succeedsForOwner_andBlocksCrossUser() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CsiCloudRepository(firestore, auth)

        val routineId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createOrUpdateRoutine(
                CustomRoutineDoc(
                    triggerPhrase = "Good morning",
                    actions = listOf("READ_TIME", "READ_WEATHER", "READ_BATTERY"),
                    customReply = "Good morning S Irfan",
                    enabled = true
                )
            ).getOrThrow()
        }
        assertTrue(routineId.isNotBlank())

        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertNoteReminder(
                NoteReminderDoc(
                    kind = "reminder",
                    title = "Meeting",
                    content = "CSI Voice Master sync",
                    dueAtMillis = 0L,
                    completed = false
                )
            ).getOrThrow()
        }
        assertTrue(noteId.isNotBlank())

        val telemetryId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.logTelemetryEvent(
                commandCategory = "routine_${UUID.randomUUID().toString().take(8)}",
                language = "en-US",
                offlineHandled = true,
                latencyMs = 35L
            ).getOrThrow()
        }
        assertTrue(telemetryId.isNotBlank())

        // Cross-user read must fail with PERMISSION_DENIED
        signInTestUser(BOB_EMAIL)
        val bobRepo = CsiCloudRepository(firestore, auth)
        var denied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeRoutines(aliceUid).first()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Expected PERMISSION_DENIED for cross-user routine access", denied)
    }

    @Test
    fun observeRoutines_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CsiCloudRepository(firestore, auth)
        var denied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeRoutines("some_user_123").first()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                denied = true
            }
        }
        assertTrue("Expected PERMISSION_DENIED for unauthenticated caller", denied)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
