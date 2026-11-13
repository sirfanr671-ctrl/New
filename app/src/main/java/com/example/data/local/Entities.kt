package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_history")
data class VoiceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val transcript: String,
    val response: String,
    val languageCode: String,
    val executionMode: String, // OFFLINE_LOCAL, DEVICE_ACTION, GEMINI_AI, CUSTOM_ROUTINE
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_routines")
data class CustomRoutineEntity(
    @PrimaryKey val id: String,
    val triggerPhrase: String,
    val actionsCsv: String, // e.g. "READ_TIME,READ_WEATHER,READ_REMINDERS,READ_BATTERY"
    val customReply: String,
    val enabled: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes_reminders")
data class LocalNoteReminderEntity(
    @PrimaryKey val id: String,
    val kind: String, // "note" or "reminder"
    val title: String,
    val content: String,
    val dueAtMillis: Long = 0L,
    val completed: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "notification_rules")
data class NotificationRuleEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val allowReadAloud: Boolean = false,
    val muted: Boolean = false
)

@Entity(tableName = "error_logs")
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tag: String,
    val message: String,
    val stackSummary: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)
