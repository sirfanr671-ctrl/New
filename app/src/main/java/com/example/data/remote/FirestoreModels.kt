package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "S Irfan",
    val wakeWord: String = "Hey Voice Master",
    val assistantPersonality: String = "professional",
    val preferredLanguage: String = "en-US",
    val offlineModeOnly: Boolean = false,
    val analyticsConsent: Boolean = false,
    val cloudSyncEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CustomRoutineDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val triggerPhrase: String = "",
    val actions: List<String> = emptyList(),
    val customReply: String = "",
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NoteReminderDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val kind: String = "note",
    val title: String = "",
    val content: String = "",
    val dueAtMillis: Long = 0L,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UsageMetricDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val commandCategory: String = "",
    val language: String = "en-US",
    val offlineHandled: Boolean = false,
    val latencyMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppConfigDoc(
    @DocumentId val id: String = "",
    val minSupportedVersion: String = "1.0",
    val latestVersion: String = "1.0",
    val maintenanceMode: Boolean = false,
    val announcement: String = "CSI Voice Master AI operational",
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)
