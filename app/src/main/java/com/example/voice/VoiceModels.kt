package com.example.voice

enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

enum class VoiceLanguage(val code: String, val label: String, val nativeLabel: String) {
    ENGLISH("en-US", "English", "English"),
    TELUGU("te-IN", "Telugu", "తెలుగు"),
    HINDI("hi-IN", "Hindi", "हिन्दी"),
    AUTO("auto", "Auto-Detect", "Auto (EN / TE / HI)");

    companion object {
        fun fromCode(code: String): VoiceLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class AssistantPersonality(val key: String, val label: String, val description: String) {
    PROFESSIONAL("professional", "Executive", "Calm, polished, and dependable"),
    FRIENDLY("friendly", "Warm", "Conversational, encouraging, and expressive"),
    CONCISE("concise", "Brief", "Ultra-short direct replies"),
    TECHNICAL("technical", "Analytical", "Detailed, structured, and precise");

    companion object {
        fun fromKey(key: String): AssistantPersonality =
            entries.firstOrNull { it.key == key } ?: PROFESSIONAL
    }
}

enum class BatteryListeningMode(val key: String, val label: String, val description: String) {
    PUSH_TO_TALK(
        "PUSH_TO_TALK",
        "Ultra Saver (Tap to Talk)",
        "Zero background mic usage. Press orb or mic button to speak."
    ),
    SCREEN_ON_ONLY(
        "SCREEN_ON_ONLY",
        "Smart Screen-On Wake Word",
        "Listens for wake words only while CSI is open on screen."
    ),
    FOREGROUND_WAKE_WORD(
        "FOREGROUND_WAKE_WORD",
        "Always-Ready Foreground Service",
        "Shows persistent notification; restarts SpeechRecognizer within Android limits."
    );

    companion object {
        fun fromKey(key: String): BatteryListeningMode =
            entries.firstOrNull { it.key == key } ?: FOREGROUND_WAKE_WORD
    }
}

data class ContactMatch(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val label: String = "Mobile"
)

data class CallLogSummary(
    val displayName: String,
    val number: String,
    val typeLabel: String,
    val timestampMillis: Long
)

data class SmsSummary(
    val sender: String,
    val body: String,
    val timestampMillis: Long,
    val isUnread: Boolean,
    val containsOtpOrSensitiveCode: Boolean
)

data class PendingCallConfirmation(
    val contactName: String,
    val phoneNumber: String,
    val simSlotIndex: Int = -1
)

data class PendingSmsConfirmation(
    val recipientName: String,
    val recipientNumber: String,
    val messageBody: String
)

data class ChatTurn(
    val id: String,
    val userText: String,
    val assistantText: String,
    val modeLabel: String,
    val languageCode: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

enum class RoutineAction(val key: String, val label: String) {
    READ_TIME("READ_TIME", "Read current time & date"),
    READ_WEATHER("READ_WEATHER", "Read live weather"),
    READ_REMINDERS("READ_REMINDERS", "Read active reminders"),
    READ_BATTERY("READ_BATTERY", "Read battery percentage")
}
