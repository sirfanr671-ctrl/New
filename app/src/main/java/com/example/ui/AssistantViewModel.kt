package com.example.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.CryptoPreferences
import com.example.core.CsiLocalSettings
import com.example.data.local.CsiDatabase
import com.example.data.local.CustomRoutineEntity
import com.example.data.local.ErrorLogEntity
import com.example.data.local.LocalNoteReminderEntity
import com.example.data.local.NotificationRuleEntity
import com.example.data.local.VoiceHistoryEntity
import com.example.data.remote.AiStreamEvent
import com.example.data.remote.AppConfigDoc
import com.example.data.remote.CsiCloudRepository
import com.example.data.remote.CustomRoutineDoc
import com.example.data.remote.FirebaseAiRepository
import com.example.data.remote.UserProfileDoc
import com.example.system.CapturedNotification
import com.example.system.CsiNotificationListenerService
import com.example.system.DeviceActionController
import com.example.system.RoutineCheckWorker
import com.example.voice.ChatTurn
import com.example.voice.CommandOutcome
import com.example.voice.ContactMatch
import com.example.voice.OfflineCommandProcessor
import com.example.voice.OrbState
import com.example.voice.PendingCallConfirmation
import com.example.voice.PendingSmsConfirmation
import com.example.voice.VoiceListeningService
import com.example.voice.VoiceTtsManager
import com.example.voice.WakeWordDetector
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class AssistantUiState(
    val settings: CsiLocalSettings = CsiLocalSettings(),
    val orbState: OrbState = OrbState.IDLE,
    val isListening: Boolean = false,
    val rmsLevel: Float = 0f,
    val liveTranscript: String = "",
    val lastStatusBanner: String = "Welcome S Irfan • CSI Ready",
    val chatTurns: List<ChatTurn> = emptyList(),
    val pendingCall: PendingCallConfirmation? = null,
    val ambiguousContacts: List<ContactMatch> = emptyList(),
    val pendingSms: PendingSmsConfirmation? = null,
    val needsDeviceAdminPrompt: Boolean = false,
    val appConfig: AppConfigDoc? = null,
    val cloudSyncError: String? = null,
    val isPinLocked: Boolean = false,
    val keystoreHardwareBacked: Boolean = false
)

class AssistantViewModel(
    private val currentUserId: String,
    private val cloudRepo: CsiCloudRepository,
    private val localDb: CsiDatabase,
    private val cryptoPrefs: CryptoPreferences,
    private val aiRepo: FirebaseAiRepository,
    private val commandProcessor: OfflineCommandProcessor,
    private val ttsManager: VoiceTtsManager,
    val deviceController: DeviceActionController
) : ViewModel() {

    private val _orbState = MutableStateFlow(OrbState.IDLE)
    private val _liveTranscript = MutableStateFlow("")
    private val _lastStatusBanner = MutableStateFlow("Welcome S Irfan • Voice Master AI Ready")
    private val _chatTurns = MutableStateFlow<List<ChatTurn>>(emptyList())
    private val _pendingCall = MutableStateFlow<PendingCallConfirmation?>(null)
    private val _ambiguousContacts = MutableStateFlow<List<ContactMatch>>(emptyList())
    private val _pendingSms = MutableStateFlow<PendingSmsConfirmation?>(null)
    private val _needsDeviceAdminPrompt = MutableStateFlow(false)
    private val _appConfig = MutableStateFlow<AppConfigDoc?>(null)
    private val _cloudSyncError = MutableStateFlow<String?>(null)
    private val _isPinLocked = MutableStateFlow(false)

    private var awaitWakeWordInContinuous = true
    private var currentActivityRef: Activity? = null

    val settingsFlow: StateFlow<CsiLocalSettings> = cryptoPrefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CsiLocalSettings())

    val historyFlow: StateFlow<List<VoiceHistoryEntity>> = localDb.voiceHistoryDao().observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routinesFlow: StateFlow<List<CustomRoutineEntity>> = localDb.customRoutineDao().observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notesRemindersFlow: StateFlow<List<LocalNoteReminderEntity>> = localDb.noteReminderDao().observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notificationRulesFlow: StateFlow<List<NotificationRuleEntity>> = localDb.notificationRuleDao().observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val errorLogsFlow: StateFlow<List<ErrorLogEntity>> = localDb.errorLogDao().observeLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentNotificationsFlow: StateFlow<List<CapturedNotification>> =
        CsiNotificationListenerService.recentNotifications

    private val baseState = combine(
        settingsFlow,
        _orbState,
        VoiceListeningService.isListeningActive,
        VoiceListeningService.rmsLevel,
        _liveTranscript
    ) { settings, orb, listening, rms, transcript ->
        AssistantUiState(
            settings = settings,
            orbState = when {
                ttsManager.isSpeaking.value -> OrbState.SPEAKING
                listening && orb == OrbState.IDLE -> OrbState.LISTENING
                else -> orb
            },
            isListening = listening,
            rmsLevel = rms,
            liveTranscript = transcript,
            keystoreHardwareBacked = cryptoPrefs.isKeystoreHardwareBacked()
        )
    }

    private val extraState = combine(
        _lastStatusBanner,
        _chatTurns,
        _pendingCall,
        _ambiguousContacts,
        _pendingSms
    ) { banner, turns, call, contacts, sms ->
        Triple(banner to turns, call to contacts, sms)
    }

    val uiState: StateFlow<AssistantUiState> = combine(
        baseState,
        extraState,
        _needsDeviceAdminPrompt,
        _appConfig,
        _isPinLocked
    ) { base, extra, adminPrompt, config, pinLocked ->
        base.copy(
            lastStatusBanner = extra.first.first,
            chatTurns = extra.first.second,
            pendingCall = extra.second.first,
            ambiguousContacts = extra.second.second,
            pendingSms = extra.third,
            needsDeviceAdminPrompt = adminPrompt,
            appConfig = config,
            cloudSyncError = _cloudSyncError.value,
            isPinLocked = pinLocked
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantUiState())

    init {
        seedDefaultRoutinesAndObserve()
    }

    fun attachActivity(activity: Activity?) {
        currentActivityRef = activity
    }

    private fun seedDefaultRoutinesAndObserve() {
        viewModelScope.launch {
            val initialSettings = cryptoPrefs.settingsFlow.first()
            if (initialSettings.appPinEnabled) {
                _isPinLocked.value = true
            }
            ttsManager.updateSettings(
                languageCode = initialSettings.preferredLanguageCode,
                gender = initialSettings.voiceGender,
                speed = initialSettings.speechSpeed,
                volume = initialSettings.assistantVolume
            )
            aiRepo.updateContext(initialSettings.personality, initialSettings.preferredLanguageCode)

            val existing = localDb.customRoutineDao().getEnabledRoutines()
            if (existing.isEmpty()) {
                val defaultMorning = CustomRoutineEntity(
                    id = "routine_good_morning",
                    triggerPhrase = "Good morning",
                    actionsCsv = "READ_TIME,READ_WEATHER,READ_REMINDERS,READ_BATTERY",
                    customReply = "Good morning S Irfan.",
                    enabled = true
                )
                localDb.customRoutineDao().upsert(defaultMorning)
            }
        }

        // Sync settings -> TTS & AI
        viewModelScope.launch {
            settingsFlow.collect { s ->
                ttsManager.updateSettings(s.preferredLanguageCode, s.voiceGender, s.speechSpeed, s.assistantVolume)
                aiRepo.updateContext(s.personality, s.preferredLanguageCode)
            }
        }

        // Observe TTS speaking state for orb feedback
        viewModelScope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                if (speaking) {
                    _orbState.value = OrbState.SPEAKING
                } else if (_orbState.value == OrbState.SPEAKING) {
                    _orbState.value = if (VoiceListeningService.isListeningActive.value) OrbState.LISTENING else OrbState.IDLE
                }
            }
        }

        // Observe speech partial transcript
        viewModelScope.launch {
            VoiceListeningService.partialTranscript.collect { partial ->
                if (partial.isNotBlank()) _liveTranscript.value = partial
            }
        }

        // Observe final spoken transcripts from foreground SpeechRecognizer
        viewModelScope.launch {
            VoiceListeningService.finalTranscripts.collect { spoken ->
                handleSpokenTranscript(spoken)
            }
        }

        // Observe speakable incoming notifications
        viewModelScope.launch {
            CsiNotificationListenerService.speakableAlerts.collect { alert ->
                ttsManager.speak(alert)
            }
        }

        // Observe Firestore remote routines (two-tier Flow error handling)
        viewModelScope.launch {
            cloudRepo.observeRoutines(currentUserId)
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val root = fsError ?: error
                    Log.w(TAG, "Cloud routines sync warning", root)
                    _cloudSyncError.value = root.localizedMessage
                }
                .collect { remoteList ->
                    if (settingsFlow.value.cloudSyncEnabled) {
                        remoteList.forEach { doc ->
                            localDb.customRoutineDao().upsert(
                                CustomRoutineEntity(
                                    id = doc.id,
                                    triggerPhrase = doc.triggerPhrase,
                                    actionsCsv = doc.actions.joinToString(","),
                                    customReply = doc.customReply,
                                    enabled = doc.enabled
                                )
                            )
                        }
                    }
                }
        }

        // Fetch App Version Config from Firestore
        viewModelScope.launch {
            cloudRepo.getAppConfig().onSuccess { config ->
                _appConfig.value = config
            }
        }
    }

    fun startListening(context: Context, continuous: Boolean) {
        awaitWakeWordInContinuous = continuous
        val lang = settingsFlow.value.preferredLanguageCode
        _orbState.value = OrbState.LISTENING
        _lastStatusBanner.value = if (continuous) {
            "Listening for \"${settingsFlow.value.activeWakeWord}\""
        } else {
            "Listening for your command..."
        }
        VoiceListeningService.startService(context, continuous = continuous, languageCode = lang)
    }

    fun stopListening(context: Context) {
        VoiceListeningService.stopService(context)
        ttsManager.stop()
        _orbState.value = OrbState.IDLE
        _lastStatusBanner.value = "Listening paused"
    }

    private fun handleSpokenTranscript(transcript: String) {
        val s = settingsFlow.value
        if (awaitWakeWordInContinuous) {
            val match = WakeWordDetector.matchWakeWord(
                transcript = transcript,
                primaryWakeWord = s.activeWakeWord,
                secondaryWakeWordsCsv = s.secondaryWakeWordsCsv
            )
            if (!match.detected) {
                // Also allow direct stop/pause listening without wake word
                val lower = transcript.trim().lowercase()
                if (lower == "stop listening" || lower == "pause listening") {
                    submitCommand(transcript)
                } else {
                    _lastStatusBanner.value = "Heard \"$transcript\" (awaiting wake word \"${s.activeWakeWord}\")"
                }
                return
            }
            if (match.remainderCommand.isBlank()) {
                _lastStatusBanner.value = "Wake word \"${match.matchedProfile}\" detected! Listening..."
                ttsManager.speak("Yes Irfan?")
                return
            }
            submitCommand(match.remainderCommand)
        } else {
            submitCommand(transcript)
        }
    }

    fun submitCommand(rawInput: String) {
        val text = rawInput.trim()
        if (text.isBlank()) return
        val s = settingsFlow.value
        val startedAt = System.currentTimeMillis()
        _liveTranscript.value = text
        _orbState.value = OrbState.THINKING

        viewModelScope.launch {
            try {
                val routines = localDb.customRoutineDao().getEnabledRoutines()
                when (val outcome = commandProcessor.process(text, s, routines, currentActivityRef)) {
                    is CommandOutcome.Handled -> {
                        _pendingCall.value = outcome.pendingCall
                        _ambiguousContacts.value = outcome.ambiguousContacts
                        _pendingSms.value = outcome.pendingSms
                        _needsDeviceAdminPrompt.value = outcome.requestDeviceAdminPrompt

                        if (outcome.stopListeningRequested) {
                            currentActivityRef?.let { stopListening(it) }
                        } else if (outcome.startListeningRequested) {
                            currentActivityRef?.let { startListening(it, continuous = true) }
                        }

                        recordCompletedTurn(
                            userText = text,
                            assistantText = outcome.reply,
                            modeLabel = outcome.executionMode,
                            category = outcome.category,
                            languageCode = s.preferredLanguageCode,
                            offlineHandled = true,
                            latencyMs = System.currentTimeMillis() - startedAt
                        )
                        _lastStatusBanner.value = outcome.reply
                        _orbState.value = OrbState.SPEAKING
                        ttsManager.speak(outcome.reply)
                    }
                    is CommandOutcome.RequiresOnlineAi -> {
                        streamOnlineGemini(text, s.preferredLanguageCode, startedAt)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logDiagnosticError("CommandProcessor", e.localizedMessage ?: "Command failure", e)
                _orbState.value = OrbState.ERROR
                _lastStatusBanner.value = "Error: ${e.localizedMessage ?: "Unable to process command"}"
            }
        }
    }

    private suspend fun streamOnlineGemini(prompt: String, languageCode: String, startedAt: Long) {
        val turnId = UUID.randomUUID().toString()
        val initialTurn = ChatTurn(
            id = turnId,
            userText = prompt,
            assistantText = "",
            modeLabel = "GEMINI_AI",
            languageCode = languageCode,
            isStreaming = true
        )
        _chatTurns.value = listOf(initialTurn) + _chatTurns.value

        aiRepo.streamConversation(prompt).collect { event ->
            when (event) {
                is AiStreamEvent.Chunk -> {
                    _chatTurns.value = _chatTurns.value.map {
                        if (it.id == turnId) it.copy(assistantText = event.accumulated) else it
                    }
                }
                is AiStreamEvent.Completed -> {
                    _chatTurns.value = _chatTurns.value.map {
                        if (it.id == turnId) it.copy(assistantText = event.fullText, isStreaming = false) else it
                    }
                    localDb.voiceHistoryDao().insert(
                        VoiceHistoryEntity(
                            transcript = prompt,
                            response = event.fullText,
                            languageCode = languageCode,
                            executionMode = "GEMINI_AI"
                        )
                    )
                    if (settingsFlow.value.analyticsConsent) {
                        cloudRepo.logTelemetryEvent(
                            commandCategory = "ai_chat",
                            language = languageCode,
                            offlineHandled = false,
                            latencyMs = System.currentTimeMillis() - startedAt
                        )
                    }
                    _lastStatusBanner.value = "Gemini AI replied"
                    _orbState.value = OrbState.SPEAKING
                    ttsManager.speak(event.fullText)
                }
                is AiStreamEvent.Error -> {
                    _chatTurns.value = _chatTurns.value.map {
                        if (it.id == turnId) it.copy(assistantText = event.message, isStreaming = false) else it
                    }
                    logDiagnosticError("FirebaseAI", event.message, null)
                    _orbState.value = OrbState.ERROR
                    _lastStatusBanner.value = event.message
                }
            }
        }
    }

    private suspend fun recordCompletedTurn(
        userText: String,
        assistantText: String,
        modeLabel: String,
        category: String,
        languageCode: String,
        offlineHandled: Boolean,
        latencyMs: Long
    ) {
        val turn = ChatTurn(
            id = UUID.randomUUID().toString(),
            userText = userText,
            assistantText = assistantText,
            modeLabel = modeLabel,
            languageCode = languageCode
        )
        _chatTurns.value = listOf(turn) + _chatTurns.value.take(40)
        localDb.voiceHistoryDao().insert(
            VoiceHistoryEntity(
                transcript = userText,
                response = assistantText,
                languageCode = languageCode,
                executionMode = modeLabel
            )
        )
        if (settingsFlow.value.analyticsConsent) {
            cloudRepo.logTelemetryEvent(
                commandCategory = category,
                language = languageCode,
                offlineHandled = offlineHandled,
                latencyMs = latencyMs
            )
        }
    }

    fun confirmPendingCall(call: PendingCallConfirmation) {
        _pendingCall.value = null
        val (_, msg) = deviceController.placeCall(call.phoneNumber, call.simSlotIndex)
        _lastStatusBanner.value = msg
        ttsManager.speak(msg)
    }

    fun cancelPendingCall() {
        _pendingCall.value = null
        _ambiguousContacts.value = emptyList()
        _lastStatusBanner.value = "Call cancelled."
    }

    fun selectAmbiguousContact(contact: ContactMatch) {
        _ambiguousContacts.value = emptyList()
        val s = settingsFlow.value
        if (s.confirmCallBeforeDialing) {
            _pendingCall.value = PendingCallConfirmation(contact.name, contact.phoneNumber, s.preferredSimSlot)
        } else {
            val (_, msg) = deviceController.placeCall(contact.phoneNumber, s.preferredSimSlot)
            _lastStatusBanner.value = msg
            ttsManager.speak(msg)
        }
    }

    fun confirmAndSendSms(number: String, body: String) {
        _pendingSms.value = null
        val (_, msg) = deviceController.sendSmsMessage(number, body)
        _lastStatusBanner.value = msg
        ttsManager.speak(msg)
    }

    fun cancelPendingSms() {
        _pendingSms.value = null
        _lastStatusBanner.value = "SMS cancelled."
    }

    fun dismissDeviceAdminPrompt() {
        _needsDeviceAdminPrompt.value = false
    }

    fun updateSettings(transform: (CsiLocalSettings) -> CsiLocalSettings) {
        viewModelScope.launch {
            cryptoPrefs.updateSettings(transform)
            val s = cryptoPrefs.settingsFlow.first()
            if (s.cloudSyncEnabled) {
                cloudRepo.upsertProfile(
                    UserProfileDoc(
                        userId = currentUserId,
                        displayName = "S Irfan",
                        wakeWord = s.activeWakeWord,
                        assistantPersonality = s.personality,
                        preferredLanguage = s.preferredLanguageCode,
                        offlineModeOnly = s.offlineModeOnly,
                        analyticsConsent = s.analyticsConsent,
                        cloudSyncEnabled = s.cloudSyncEnabled
                    )
                )
            }
        }
    }

    fun completeOnboarding(context: Context) {
        RoutineCheckWorker.schedule(context)
        updateSettings { it.copy(setupCompleted = true) }
    }

    fun saveCustomRoutine(id: String?, triggerPhrase: String, actions: List<String>, customReply: String) {
        val routineId = id?.takeIf { it.isNotBlank() } ?: "routine_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val entity = CustomRoutineEntity(
            id = routineId,
            triggerPhrase = triggerPhrase.trim(),
            actionsCsv = actions.joinToString(","),
            customReply = customReply.trim(),
            enabled = true
        )
        viewModelScope.launch {
            localDb.customRoutineDao().upsert(entity)
            if (settingsFlow.value.cloudSyncEnabled) {
                cloudRepo.createOrUpdateRoutine(
                    CustomRoutineDoc(
                        id = routineId,
                        userId = currentUserId,
                        triggerPhrase = entity.triggerPhrase,
                        actions = actions,
                        customReply = entity.customReply,
                        enabled = true
                    )
                )
            }
        }
    }

    fun toggleRoutineEnabled(routine: CustomRoutineEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = routine.copy(enabled = enabled, updatedAtMillis = System.currentTimeMillis())
            localDb.customRoutineDao().upsert(updated)
            if (settingsFlow.value.cloudSyncEnabled) {
                cloudRepo.createOrUpdateRoutine(
                    CustomRoutineDoc(
                        id = updated.id,
                        userId = currentUserId,
                        triggerPhrase = updated.triggerPhrase,
                        actions = updated.actionsCsv.split(",").filter { it.isNotBlank() },
                        customReply = updated.customReply,
                        enabled = enabled
                    )
                )
            }
        }
    }

    fun deleteCustomRoutine(id: String) {
        viewModelScope.launch {
            localDb.customRoutineDao().deleteById(id)
            if (settingsFlow.value.cloudSyncEnabled) {
                cloudRepo.deleteRoutine(id)
            }
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch { localDb.voiceHistoryDao().deleteById(id) }
    }

    fun clearAllHistory() {
        viewModelScope.launch { localDb.voiceHistoryDao().clearAll() }
    }

    fun toggleNotificationRule(rule: NotificationRuleEntity, allowRead: Boolean, muted: Boolean) {
        viewModelScope.launch {
            localDb.notificationRuleDao().upsert(
                rule.copy(allowReadAloud = allowRead, muted = muted)
            )
        }
    }

    fun toggleNoteCompleted(item: LocalNoteReminderEntity) {
        viewModelScope.launch {
            localDb.noteReminderDao().upsert(item.copy(completed = !item.completed))
        }
    }

    fun deleteNoteReminder(id: String) {
        viewModelScope.launch { localDb.noteReminderDao().deleteById(id) }
    }

    fun setAppPin(pin: String?) {
        viewModelScope.launch {
            cryptoPrefs.setAppPin(pin)
            if (pin.isNullOrBlank()) _isPinLocked.value = false
        }
    }

    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = cryptoPrefs.verifyAppPin(pin)
            if (ok) _isPinLocked.value = false
            onResult(ok)
        }
    }

    fun updateAdminAppConfig(
        minSupportedVersion: String,
        latestVersion: String,
        maintenanceMode: Boolean,
        announcement: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = cloudRepo.updateAppConfig(
                minSupportedVersion = minSupportedVersion,
                latestVersion = latestVersion,
                maintenanceMode = maintenanceMode,
                announcement = announcement
            )
            if (res.isSuccess) {
                cloudRepo.getAppConfig().onSuccess { _appConfig.value = it }
            }
            onResult(res)
        }
    }

    fun clearDiagnosticLogs() {
        viewModelScope.launch { localDb.errorLogDao().clearAll() }
    }

    fun speakPreview(text: String) {
        ttsManager.speak(text)
    }

    private suspend fun logDiagnosticError(tag: String, message: String, throwable: Throwable?) {
        localDb.errorLogDao().insert(
            ErrorLogEntity(
                tag = tag,
                message = message,
                stackSummary = throwable?.stackTraceToString()?.take(600).orEmpty()
            )
        )
    }

    override fun onCleared() {
        ttsManager.shutdown()
        super.onCleared()
    }

    private companion object {
        const val TAG = "AssistantVM"
    }
}
