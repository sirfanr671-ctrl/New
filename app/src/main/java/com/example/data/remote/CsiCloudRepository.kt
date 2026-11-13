package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CsiCloudRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProfile(userId: String): Flow<UserProfileDoc?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfileDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertProfile(profile: UserProfileDoc): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updateMap = mapOf(
                    "displayName" to profile.displayName.take(80).ifBlank { "S Irfan" },
                    "wakeWord" to profile.wakeWord.take(64).ifBlank { "Hey Voice Master" },
                    "assistantPersonality" to profile.assistantPersonality,
                    "preferredLanguage" to profile.preferredLanguage,
                    "offlineModeOnly" to profile.offlineModeOnly,
                    "analyticsConsent" to profile.analyticsConsent,
                    "cloudSyncEnabled" to profile.cloudSyncEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            } else {
                val createMap = mapOf(
                    "userId" to uid,
                    "displayName" to profile.displayName.take(80).ifBlank { "S Irfan" },
                    "wakeWord" to profile.wakeWord.take(64).ifBlank { "Hey Voice Master" },
                    "assistantPersonality" to profile.assistantPersonality,
                    "preferredLanguage" to profile.preferredLanguage,
                    "offlineModeOnly" to profile.offlineModeOnly,
                    "analyticsConsent" to profile.analyticsConsent,
                    "cloudSyncEnabled" to profile.cloudSyncEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeRoutines(userId: String): Flow<List<CustomRoutineDoc>> = flow {
        val colRef = db.collection("users").document(userId).collection("custom_commands")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CustomRoutineDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun createOrUpdateRoutine(routine: CustomRoutineDoc): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("custom_commands")
        val docRef = if (routine.id.isNotBlank()) colRef.document(routine.id) else colRef.document()
        val safeActions = routine.actions.take(10).ifEmpty { listOf("READ_TIME") }
        return try {
            val existingSnap = if (routine.id.isNotBlank()) docRef.get().await() else null
            if (existingSnap != null && existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "triggerPhrase" to routine.triggerPhrase.take(100),
                        "actions" to safeActions,
                        "customReply" to routine.customReply.take(300),
                        "enabled" to routine.enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "triggerPhrase" to routine.triggerPhrase.take(100),
                        "actions" to safeActions,
                        "customReply" to routine.customReply.take(300),
                        "enabled" to routine.enabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteRoutine(routineId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("custom_commands").document(routineId)
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

    fun observeNotesReminders(userId: String): Flow<List<NoteReminderDoc>> = flow {
        val colRef = db.collection("users").document(userId).collection("notes_reminders")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        NoteReminderDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertNoteReminder(item: NoteReminderDoc): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("notes_reminders")
        val docRef = if (item.id.isNotBlank()) colRef.document(item.id) else colRef.document()
        return try {
            val existingSnap = if (item.id.isNotBlank()) docRef.get().await() else null
            if (existingSnap != null && existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "kind" to item.kind,
                        "title" to item.title.take(120),
                        "content" to item.content.take(2000),
                        "dueAtMillis" to item.dueAtMillis.coerceAtLeast(0L),
                        "completed" to item.completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "kind" to item.kind,
                        "title" to item.title.take(120),
                        "content" to item.content.take(2000),
                        "dueAtMillis" to item.dueAtMillis.coerceAtLeast(0L),
                        "completed" to item.completed,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNoteReminder(itemId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("notes_reminders").document(itemId)
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

    suspend fun logTelemetryEvent(
        commandCategory: String,
        language: String,
        offlineHandled: Boolean,
        latencyMs: Long
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("telemetry_events").document()
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "commandCategory" to commandCategory.take(50).ifBlank { "general" },
                    "language" to language.take(20).ifBlank { "en-US" },
                    "offlineHandled" to offlineHandled,
                    "latencyMs" to latencyMs.coerceIn(0L, 600_000L),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getAppConfig(configId: String = "release"): Result<AppConfigDoc?> {
        requireUserId()
        val docRef = db.collection("app_config").document(configId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                if (snap.exists()) {
                    snap.toObject(
                        AppConfigDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateAppConfig(
        configId: String = "release",
        minSupportedVersion: String,
        latestVersion: String,
        maintenanceMode: Boolean,
        announcement: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("app_config").document(configId)
        return try {
            docRef.set(
                mapOf(
                    "minSupportedVersion" to minSupportedVersion.take(32),
                    "latestVersion" to latestVersion.take(32),
                    "maintenanceMode" to maintenanceMode,
                    "announcement" to announcement.take(500),
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
