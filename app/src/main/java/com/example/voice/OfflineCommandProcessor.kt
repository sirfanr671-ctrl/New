package com.example.voice

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import com.example.core.CsiLocalSettings
import com.example.data.local.CustomRoutineEntity
import com.example.data.local.LocalNoteReminderEntity
import com.example.data.local.NoteReminderDao
import com.example.data.remote.WeatherRepository
import com.example.system.CsiNotificationListenerService
import com.example.system.DeviceActionController
import java.util.Locale
import java.util.UUID

sealed interface CommandOutcome {
    data class Handled(
        val reply: String,
        val category: String,
        val executionMode: String = "OFFLINE_LOCAL",
        val pendingCall: PendingCallConfirmation? = null,
        val ambiguousContacts: List<ContactMatch> = emptyList(),
        val pendingSms: PendingSmsConfirmation? = null,
        val requestDeviceAdminPrompt: Boolean = false,
        val stopListeningRequested: Boolean = false,
        val startListeningRequested: Boolean = false
    ) : CommandOutcome

    data class RequiresOnlineAi(val prompt: String) : CommandOutcome
}

class OfflineCommandProcessor(
    private val deviceController: DeviceActionController,
    private val noteReminderDao: NoteReminderDao,
    private val weatherRepository: WeatherRepository
) {

    suspend fun process(
        rawTranscript: String,
        settings: CsiLocalSettings,
        routines: List<CustomRoutineEntity>,
        activity: Activity?
    ): CommandOutcome {
        val text = rawTranscript.trim()
        val lower = text.lowercase(Locale.ROOT)

        // 1. Listening control commands
        if (lower == "stop listening" || lower == "pause listening" || lower == "mute microphone") {
            return CommandOutcome.Handled(
                reply = "Microphone listening paused. Tap Start Listening whenever you need me.",
                category = "listening_control",
                stopListeningRequested = true
            )
        }
        if (lower == "start listening" || lower == "resume listening") {
            return CommandOutcome.Handled(
                reply = "Voice Master AI is now listening.",
                category = "listening_control",
                startListeningRequested = true
            )
        }

        // 2. Match custom routines (e.g., "Good morning")
        val matchedRoutine = routines.firstOrNull { r ->
            r.enabled && lower.contains(r.triggerPhrase.trim().lowercase(Locale.ROOT))
        }
        if (matchedRoutine != null) {
            val reply = executeCustomRoutine(matchedRoutine, settings)
            return CommandOutcome.Handled(
                reply = reply,
                category = "custom_routine",
                executionMode = "CUSTOM_ROUTINE"
            )
        }

        // 3. Phone lock
        if (lower.contains("lock my phone") || lower.contains("lock the screen") || lower == "lock screen") {
            val (ok, msg) = deviceController.lockScreenIfAuthorized()
            return CommandOutcome.Handled(
                reply = msg,
                category = "device_lock",
                executionMode = "DEVICE_ACTION",
                requestDeviceAdminPrompt = !ok
            )
        }

        // 4. Cancel call
        if (lower.contains("cancel the call") || lower == "cancel call") {
            return CommandOutcome.Handled(
                reply = "Pending call cancelled.",
                category = "calling",
                executionMode = "DEVICE_ACTION"
            )
        }

        // 5. Recent calls / Last number
        if (lower.contains("recent calls") || lower.contains("call history")) {
            val calls = deviceController.getRecentCalls(5)
            val reply = if (calls.isEmpty()) {
                "No recent calls found, or Call Log permission has not been granted in Privacy & Security."
            } else {
                "Your recent calls: " + calls.joinToString("; ") { "${it.typeLabel} ${it.displayName}" } + "."
            }
            return CommandOutcome.Handled(reply, "calling", "DEVICE_ACTION")
        }
        if (lower.contains("call the last number") || lower.contains("redial")) {
            val last = deviceController.getRecentCalls(1).firstOrNull()
                ?: return CommandOutcome.Handled(
                    "I couldn't access your last call. Check Call Log permission in Privacy & Security.",
                    "calling",
                    "DEVICE_ACTION"
                )
            return if (settings.confirmCallBeforeDialing) {
                CommandOutcome.Handled(
                    reply = "Ready to call ${last.displayName} (${last.number}). Please confirm.",
                    category = "calling",
                    executionMode = "DEVICE_ACTION",
                    pendingCall = PendingCallConfirmation(last.displayName, last.number, settings.preferredSimSlot)
                )
            } else {
                val (_, msg) = deviceController.placeCall(last.number, settings.preferredSimSlot)
                CommandOutcome.Handled(msg, "calling", "DEVICE_ACTION")
            }
        }

        // 6. Find contact / Call contact / Call my father/mother
        if (lower.startsWith("find a contact") || lower.startsWith("find contact") || lower.startsWith("search contact")) {
            val query = lower
                .removePrefix("find a contact")
                .removePrefix("find contact")
                .removePrefix("search contact")
                .trim()
            val matches = deviceController.searchContacts(query)
            val reply = if (matches.isEmpty()) {
                "No contacts found matching '$query'. Verify Contacts permission is enabled."
            } else {
                "Found ${matches.size} contact(s): " + matches.take(4).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
            }
            return CommandOutcome.Handled(reply, "calling", "DEVICE_ACTION", ambiguousContacts = matches)
        }
        if (lower.startsWith("call ")) {
            val targetRaw = text.substring(5).trim()
            val targetLower = targetRaw.lowercase(Locale.ROOT)
            if (targetLower == "my father" || targetLower == "father" || targetLower == "dad") {
                val alias = settings.fatherPhoneAlias.trim()
                if (alias.isNotBlank()) {
                    return handleResolvedCall("Father", alias, settings)
                }
            }
            if (targetLower == "my mother" || targetLower == "mother" || targetLower == "mom") {
                val alias = settings.motherPhoneAlias.trim()
                if (alias.isNotBlank()) {
                    return handleResolvedCall("Mother", alias, settings)
                }
            }
            val cleanedQuery = targetRaw.removePrefix("my ").trim()
            val matches = deviceController.searchContacts(cleanedQuery)
            return when {
                matches.isEmpty() -> {
                    if (cleanedQuery.all { it.isDigit() || it == '+' || it == '-' || it == ' ' } && cleanedQuery.any { it.isDigit() }) {
                        handleResolvedCall(cleanedQuery, cleanedQuery, settings)
                    } else {
                        CommandOutcome.Handled(
                            "Could not find '$cleanedQuery' in contacts. Check Contacts permission or set relationship aliases in Contact & Calling Settings.",
                            "calling",
                            "DEVICE_ACTION"
                        )
                    }
                }
                matches.size == 1 -> handleResolvedCall(matches.first().name, matches.first().phoneNumber, settings)
                else -> CommandOutcome.Handled(
                    reply = "I found ${matches.size} contacts matching '$cleanedQuery'. Select who you want to call.",
                    category = "calling",
                    executionMode = "DEVICE_ACTION",
                    ambiguousContacts = matches
                )
            }
        }

        // 7. SMS and Message Controls
        if (lower.contains("unread messages") || lower.contains("latest messages") || lower.contains("read messages")) {
            val unreadOnly = lower.contains("unread")
            val fromIdx = lower.indexOf("from ")
            val senderFilter = if (fromIdx >= 0) text.substring(fromIdx + 5).trim() else null
            return handleReadMessages(senderFilter, unreadOnly, settings)
        }
        if (lower.startsWith("reply to the latest message") || lower.startsWith("reply to latest message")) {
            val latest = deviceController.readSmsMessages(limit = 1).firstOrNull()
                ?: return CommandOutcome.Handled(
                    "No recent SMS found to reply to, or SMS permission is not enabled.",
                    "messaging",
                    "DEVICE_ACTION"
                )
            val body = text.substringAfter(":", "").ifBlank { "I'll get back to you shortly." }
            return CommandOutcome.Handled(
                reply = "Drafted reply to ${latest.sender}. Edit or confirm before sending.",
                category = "messaging",
                executionMode = "DEVICE_ACTION",
                pendingSms = PendingSmsConfirmation(latest.sender, latest.sender, body)
            )
        }
        if (lower.startsWith("send a message to ") || lower.startsWith("send message to ") || lower.startsWith("text ")) {
            val afterPrefix = when {
                lower.startsWith("send a message to ") -> text.substring("send a message to ".length)
                lower.startsWith("send message to ") -> text.substring("send message to ".length)
                else -> text.substring("text ".length)
            }.trim()
            val parts = afterPrefix.split(Regex("\\s+(saying|that|:)\\s+", RegexOption.IGNORE_CASE), limit = 2)
            val recipientQuery = parts[0].trim()
            val messageBody = if (parts.size > 1) parts[1].trim() else ""
            val resolvedNumber = when (recipientQuery.lowercase(Locale.ROOT)) {
                "my father", "father", "dad" -> settings.fatherPhoneAlias.takeIf { it.isNotBlank() }
                "my mother", "mother", "mom" -> settings.motherPhoneAlias.takeIf { it.isNotBlank() }
                else -> null
            }
            if (resolvedNumber != null) {
                return CommandOutcome.Handled(
                    reply = "Message ready for $recipientQuery. Confirm recipient and message before sending.",
                    category = "messaging",
                    executionMode = "DEVICE_ACTION",
                    pendingSms = PendingSmsConfirmation(recipientQuery, resolvedNumber, messageBody)
                )
            }
            val matches = deviceController.searchContacts(recipientQuery.removePrefix("my ").trim())
            if (matches.isEmpty()) {
                return CommandOutcome.Handled(
                    "No contact found for '$recipientQuery'. Grant Contacts permission or set an alias in Calling Settings.",
                    "messaging",
                    "DEVICE_ACTION"
                )
            }
            val first = matches.first()
            return CommandOutcome.Handled(
                reply = "Prepared message to ${first.name}. Edit and confirm before sending.",
                category = "messaging",
                executionMode = "DEVICE_ACTION",
                pendingSms = PendingSmsConfirmation(first.name, first.phoneNumber, messageBody)
            )
        }

        // 8. Flashlight
        if (lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("turn on flashlight") || lower.contains("turn on the flashlight")) {
            val (_, msg) = deviceController.setFlashlight(true)
            return CommandOutcome.Handled(msg, "device_control", "DEVICE_ACTION")
        }
        if (lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("turn off flashlight") || lower.contains("turn off the flashlight")) {
            val (_, msg) = deviceController.setFlashlight(false)
            return CommandOutcome.Handled(msg, "device_control", "DEVICE_ACTION")
        }

        // 9. Volume & Brightness
        if (lower.contains("mute volume") || lower == "mute") {
            return CommandOutcome.Handled(deviceController.adjustVolume(AudioManager.ADJUST_MUTE, mute = true), "device_control", "DEVICE_ACTION")
        }
        if (lower.contains("volume up") || lower.contains("increase volume") || lower.contains("louder")) {
            return CommandOutcome.Handled(deviceController.adjustVolume(AudioManager.ADJUST_RAISE), "device_control", "DEVICE_ACTION")
        }
        if (lower.contains("volume down") || lower.contains("decrease volume") || lower.contains("lower volume")) {
            return CommandOutcome.Handled(deviceController.adjustVolume(AudioManager.ADJUST_LOWER), "device_control", "DEVICE_ACTION")
        }
        if (lower.contains("increase brightness") || lower.contains("brightness up")) {
            return CommandOutcome.Handled(deviceController.adjustAppBrightness(activity, 0.25f), "device_control", "DEVICE_ACTION")
        }
        if (lower.contains("decrease brightness") || lower.contains("brightness down") || lower.contains("lower brightness")) {
            return CommandOutcome.Handled(deviceController.adjustAppBrightness(activity, -0.25f), "device_control", "DEVICE_ACTION")
        }

        // 10. System Settings & Hardware Intents
        if (lower.contains("wifi settings") || lower.contains("wi-fi settings") || lower == "open wifi" || lower == "open wi-fi") {
            val (_, msg) = deviceController.openSystemSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi settings")
            return CommandOutcome.Handled("$msg Note: Android restricts silent Wi-Fi toggling by apps.", "device_control", "DEVICE_ACTION")
        }
        if (lower.contains("bluetooth settings") || lower == "open bluetooth") {
            val (_, msg) = deviceController.openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth settings")
            return CommandOutcome.Handled(msg, "device_control", "DEVICE_ACTION")
        }
        if (lower == "open settings" || lower.contains("system settings")) {
            val (_, msg) = deviceController.openSystemSettings(Settings.ACTION_SETTINGS, "System Settings")
            return CommandOutcome.Handled(msg, "device_control", "DEVICE_ACTION")
        }
        if (lower.contains("open camera") || lower == "camera") {
            val (_, msg) = deviceController.openCamera()
            return CommandOutcome.Handled(msg, "device_control", "DEVICE_ACTION")
        }
        if (lower.contains("open calculator")) {
            val (_, msg) = deviceController.openCalculator()
            return CommandOutcome.Handled(msg, "device_control", "DEVICE_ACTION")
        }
        if (lower.startsWith("open ")) {
            val appName = text.substring(5).trim()
            val (_, msg) = deviceController.openAppByName(appName)
            return CommandOutcome.Handled(msg, "device_control", "DEVICE_ACTION")
        }

        // 11. Alarms, Timers, Media, Search, Navigation, Screenshot
        if (lower.contains("set alarm") || lower.contains("set an alarm")) {
            val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
            val m = timeRegex.find(lower)
            var hour = m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 7
            val minute = m?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            val amPm = m?.groupValues?.getOrNull(3)?.lowercase(Locale.ROOT) ?: ""
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            val (_, msg) = deviceController.setAlarm(hour, minute)
            return CommandOutcome.Handled(msg, "device_control", "DEVICE_ACTION")
        }
        if (lower.contains("set timer") || lower.contains("set a timer")) {
            val num = Regex("(\\d+)").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 60
            val seconds = if (lower.contains("minute")) num * 60 else num
            val (_, msg) = deviceController.setTimer(seconds)
            return CommandOutcome.Handled(msg, "device_control", "DEVICE_ACTION")
        }
        if (lower.contains("pause music") || lower.contains("pause media") || lower.contains("play music") || lower.contains("resume media")) {
            return CommandOutcome.Handled(deviceController.controlMediaPlayback(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "play/pause"), "media", "DEVICE_ACTION")
        }
        if (lower.contains("next track") || lower.contains("next song")) {
            return CommandOutcome.Handled(deviceController.controlMediaPlayback(KeyEvent.KEYCODE_MEDIA_NEXT, "next"), "media", "DEVICE_ACTION")
        }
        if (lower.contains("previous track") || lower.contains("previous song")) {
            return CommandOutcome.Handled(deviceController.controlMediaPlayback(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "previous"), "media", "DEVICE_ACTION")
        }
        if (lower.startsWith("search the internet for ") || lower.startsWith("search for ") || lower.startsWith("google ")) {
            val q = text.replaceFirst(Regex("^(search the internet for|search for|google)\\s+", RegexOption.IGNORE_CASE), "").trim()
            val (_, msg) = deviceController.searchWeb(q)
            return CommandOutcome.Handled(msg, "web_search", "DEVICE_ACTION")
        }
        if (lower.startsWith("navigate to ") || lower.startsWith("directions to ")) {
            val dest = text.replaceFirst(Regex("^(navigate to|directions to)\\s+", RegexOption.IGNORE_CASE), "").trim()
            val (_, msg) = deviceController.navigateToLocation(dest)
            return CommandOutcome.Handled(msg, "navigation", "DEVICE_ACTION")
        }
        if (lower.contains("take a screenshot") || lower == "screenshot") {
            return CommandOutcome.Handled(
                "Android restricts silent background screenshots without an active MediaProjection screen-capture session or system Accessibility shortcut. Use your device Power + Volume Down hardware keys to capture safely.",
                "device_control",
                "DEVICE_ACTION"
            )
        }

        // 12. Battery, Date/Time, Offline Calculator
        if (lower.contains("battery")) {
            return CommandOutcome.Handled(deviceController.getBatteryReport(), "device_telemetry")
        }
        if (lower.contains("time") || lower.contains("date") || lower.contains("what day")) {
            return CommandOutcome.Handled(deviceController.getCurrentDateTimeReport(), "time_date")
        }
        if (lower.startsWith("calculate ") || Regex("^[0-9.+\\-*/()\\s]+$").matches(lower)) {
            val expr = lower.removePrefix("calculate ").trim()
            val evaluated = evaluateSimpleMath(expr)
            if (evaluated != null) {
                return CommandOutcome.Handled("The result is $evaluated.", "calculator")
            }
        }

        // 13. Offline Notes & Reminders
        if (lower.startsWith("create a note") || lower.startsWith("create note") || lower.startsWith("take a note")) {
            val body = text.replaceFirst(Regex("^(create a note|create note|take a note)\\s*(about|that|:)?\\s*", RegexOption.IGNORE_CASE), "").trim()
            val title = body.take(40).ifBlank { "Voice Note" }
            noteReminderDao.upsert(
                LocalNoteReminderEntity(
                    id = UUID.randomUUID().toString(),
                    kind = "note",
                    title = title,
                    content = body.ifBlank { "Saved note" }
                )
            )
            return CommandOutcome.Handled("Saved note: $title.", "notes_reminders")
        }
        if (lower.startsWith("remind me to ") || lower.startsWith("set a reminder") || lower.startsWith("add reminder")) {
            val body = text.replaceFirst(Regex("^(remind me to|set a reminder to|set a reminder|add reminder)\\s*", RegexOption.IGNORE_CASE), "").trim()
            val title = body.take(50).ifBlank { "Voice Reminder" }
            noteReminderDao.upsert(
                LocalNoteReminderEntity(
                    id = UUID.randomUUID().toString(),
                    kind = "reminder",
                    title = title,
                    content = body.ifBlank { "Reminder" },
                    dueAtMillis = System.currentTimeMillis() + 3600_000L
                )
            )
            return CommandOutcome.Handled("Reminder saved locally: $title.", "notes_reminders")
        }
        if (lower.contains("read my reminders") || lower.contains("show reminders")) {
            val reminders = noteReminderDao.getActiveReminders()
            val msg = if (reminders.isEmpty()) {
                "You have no pending reminders."
            } else {
                "Your active reminders: " + reminders.take(5).joinToString("; ") { it.title } + "."
            }
            return CommandOutcome.Handled(msg, "notes_reminders")
        }

        // 14. Weather
        if (lower.contains("weather")) {
            if (settings.offlineModeOnly) {
                return CommandOutcome.Handled(
                    "Live weather requires network access, and CSI is currently in 100% Offline Mode.",
                    "weather"
                )
            }
            val weather = weatherRepository.fetchWeatherSummary()
            return CommandOutcome.Handled(
                weather.getOrElse { "Could not fetch live weather right now." },
                "weather",
                "ONLINE_WEATHER"
            )
        }

        // 15. Offline mode vs Gemini AI fallback
        return if (settings.offlineModeOnly || !settings.aiCloudConsent) {
            val reason = if (settings.offlineModeOnly) "Offline Mode is enabled" else "AI Cloud Consent is turned off in Privacy & Security"
            CommandOutcome.Handled(
                reply = "$reason, so I did not send '$text' to cloud AI. Locally I can control your flashlight, volume, calls, messages, alarms, notes, reminders, battery, time, and custom routines.",
                category = "offline_fallback"
            )
        } else {
            CommandOutcome.RequiresOnlineAi(text)
        }
    }

    private fun handleResolvedCall(name: String, number: String, settings: CsiLocalSettings): CommandOutcome.Handled {
        return if (settings.confirmCallBeforeDialing) {
            CommandOutcome.Handled(
                reply = "Confirm call to $name ($number).",
                category = "calling",
                executionMode = "DEVICE_ACTION",
                pendingCall = PendingCallConfirmation(name, number, settings.preferredSimSlot)
            )
        } else {
            val (_, msg) = deviceController.placeCall(number, settings.preferredSimSlot)
            CommandOutcome.Handled(msg, "calling", "DEVICE_ACTION")
        }
    }

    private suspend fun handleReadMessages(
        senderFilter: String?,
        unreadOnly: Boolean,
        settings: CsiLocalSettings
    ): CommandOutcome.Handled {
        val smsList = deviceController.readSmsMessages(senderFilter, unreadOnly, limit = 5)
        if (smsList.isNotEmpty()) {
            val formatted = smsList.joinToString(" | ") { sms ->
                if (sms.containsOtpOrSensitiveCode && settings.blockOtpAloud) {
                    "Message from ${sms.sender}: [OTP or banking verification code hidden for security]"
                } else {
                    "From ${sms.sender}: ${sms.body}"
                }
            }
            return CommandOutcome.Handled(formatted, "messaging", "DEVICE_ACTION")
        }

        if (settings.useNotificationFallbackForMessages) {
            val notifs = CsiNotificationListenerService.recentNotifications.value
                .filter { senderFilter == null || it.title.contains(senderFilter, ignoreCase = true) || it.appLabel.contains(senderFilter, ignoreCase = true) }
                .take(4)
            if (notifs.isNotEmpty()) {
                val summary = notifs.joinToString(" | ") { n ->
                    if (n.isSensitiveHidden && settings.blockOtpAloud) {
                        "${n.appLabel} (${n.title}): [Hidden for privacy]"
                    } else {
                        "${n.appLabel} (${n.title}): ${n.text}"
                    }
                }
                return CommandOutcome.Handled("From notifications: $summary", "messaging", "DEVICE_ACTION")
            }
        }

        return CommandOutcome.Handled(
            "No matching messages found. Grant SMS permission or enable Notification Access in Message Settings.",
            "messaging",
            "DEVICE_ACTION"
        )
    }

    private suspend fun executeCustomRoutine(routine: CustomRoutineEntity, settings: CsiLocalSettings): String {
        val parts = mutableListOf<String>()
        if (routine.customReply.isNotBlank()) parts.add(routine.customReply)
        val actions = routine.actionsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        for (action in actions) {
            when (action) {
                RoutineAction.READ_TIME.key -> parts.add(deviceController.getCurrentDateTimeReport())
                RoutineAction.READ_BATTERY.key -> parts.add(deviceController.getBatteryReport())
                RoutineAction.READ_REMINDERS.key -> {
                    val reminders = noteReminderDao.getActiveReminders()
                    if (reminders.isEmpty()) parts.add("No pending reminders.")
                    else parts.add("Reminders: " + reminders.take(3).joinToString(", ") { it.title } + ".")
                }
                RoutineAction.READ_WEATHER.key -> {
                    if (settings.offlineModeOnly) {
                        parts.add("Weather skipped in Offline Mode.")
                    } else {
                        val res = weatherRepository.fetchWeatherSummary()
                        parts.add(res.getOrElse { "Weather unavailable offline." })
                    }
                }
            }
        }
        return parts.joinToString(" ")
    }

    private fun evaluateSimpleMath(expr: String): String? {
        return try {
            val clean = expr.replace("plus", "+").replace("minus", "-")
                .replace("times", "*").replace("x", "*").replace("divided by", "/")
                .replace(Regex("[^0-9.+\\-*/]"), "")
            if (clean.isBlank()) return null
            val addParts = clean.split("+")
            if (addParts.size == 2) return (addParts[0].toDouble() + addParts[1].toDouble()).formatClean()
            val subParts = clean.split("-")
            if (subParts.size == 2 && subParts[0].isNotEmpty()) return (subParts[0].toDouble() - subParts[1].toDouble()).formatClean()
            val mulParts = clean.split("*")
            if (mulParts.size == 2) return (mulParts[0].toDouble() * mulParts[1].toDouble()).formatClean()
            val divParts = clean.split("/")
            if (divParts.size == 2 && divParts[1].toDouble() != 0.0) return (divParts[0].toDouble() / divParts[1].toDouble()).formatClean()
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun Double.formatClean(): String =
        if (this % 1.0 == 0.0) this.toLong().toString() else String.format(Locale.US, "%.2f", this)
}
