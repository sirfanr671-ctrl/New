package com.example.ui

import android.app.Activity
import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.core.CryptoPreferences
import com.example.data.local.CsiDatabase
import com.example.data.remote.CsiCloudRepository
import com.example.data.remote.FirebaseAiRepository
import com.example.data.remote.WeatherRepository
import com.example.system.DeviceActionController
import com.example.ui.components.AppPinLockOverlay
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.BatteryOptimizationScreen
import com.example.ui.screens.CallingSettingsScreen
import com.example.ui.screens.CustomCommandsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LanguageVoiceSettingsScreen
import com.example.ui.screens.ListeningOrbScreen
import com.example.ui.screens.MessageSettingsScreen
import com.example.ui.screens.NotificationSettingsScreen
import com.example.ui.screens.OfflineModeScreen
import com.example.ui.screens.PrivacySecurityScreen
import com.example.ui.screens.WelcomeSetupScreen
import com.example.voice.BatteryListeningMode
import com.example.voice.OfflineCommandProcessor
import com.example.voice.VoiceTtsManager
import com.google.firebase.firestore.FirebaseFirestore

enum class CsiRoute(val title: String) {
    HOME("CSI Voice Master"),
    ROUTINES("Custom Commands"),
    HISTORY("History & Notes"),
    SETTINGS_HUB("Settings & Privacy"),
    WELCOME_SETUP("Welcome & Setup"),
    LISTENING_ORB("Voice Studio"),
    CALLING_SETTINGS("Calling Settings"),
    MESSAGE_SETTINGS("Message Settings"),
    NOTIFICATION_SETTINGS("Notifications"),
    LANGUAGE_VOICE_SETTINGS("Language & Voice"),
    PRIVACY_SECURITY("Privacy & Security"),
    OFFLINE_MODE("Offline Mode"),
    BATTERY_OPTIMIZATION("Battery Optimization"),
    ADMIN_DASHBOARD("Admin & Diagnostics")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedCsiShell(currentUserId: String) {
    val context = LocalContext.current
    val viewModel: AssistantViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val dbId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(dbId)
                val localDb = CsiDatabase.getInstance(app)
                val deviceCtrl = DeviceActionController(app)
                val weatherRepo = WeatherRepository()
                AssistantViewModel(
                    currentUserId = currentUserId,
                    cloudRepo = CsiCloudRepository(firestore),
                    localDb = localDb,
                    cryptoPrefs = CryptoPreferences(app),
                    aiRepo = FirebaseAiRepository(),
                    commandProcessor = OfflineCommandProcessor(
                        deviceController = deviceCtrl,
                        noteReminderDao = localDb.noteReminderDao(),
                        weatherRepository = weatherRepo
                    ),
                    ttsManager = VoiceTtsManager(app),
                    deviceController = deviceCtrl
                )
            }
        }
    )

    DisposableEffect(context) {
        viewModel.attachActivity(context as? Activity)
        onDispose { viewModel.attachActivity(null) }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.historyFlow.collectAsStateWithLifecycle()
    val routines by viewModel.routinesFlow.collectAsStateWithLifecycle()
    val notesReminders by viewModel.notesRemindersFlow.collectAsStateWithLifecycle()
    val notifRules by viewModel.notificationRulesFlow.collectAsStateWithLifecycle()
    val errorLogs by viewModel.errorLogsFlow.collectAsStateWithLifecycle()
    val recentNotifs by viewModel.recentNotificationsFlow.collectAsStateWithLifecycle()

    var currentRoute by rememberSaveable { mutableStateOf(CsiRoute.HOME) }
    val primaryTabs = listOf(
        Triple(CsiRoute.HOME, "Home", Icons.Rounded.Home),
        Triple(CsiRoute.ROUTINES, "Routines", Icons.Rounded.AutoAwesome),
        Triple(CsiRoute.HISTORY, "History", Icons.Rounded.History),
        Triple(CsiRoute.SETTINGS_HUB, "Controls", Icons.Rounded.Tune)
    )

    val isSubScreen = primaryTabs.none { it.first == currentRoute }
    if (isSubScreen) {
        BackHandler {
            currentRoute = when (currentRoute) {
                CsiRoute.LISTENING_ORB, CsiRoute.WELCOME_SETUP -> CsiRoute.HOME
                CsiRoute.ADMIN_DASHBOARD -> CsiRoute.PRIVACY_SECURITY
                else -> CsiRoute.SETTINGS_HUB
            }
        }
    }

    if (uiState.isPinLocked) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppPinLockOverlay(
                onUnlockAttempt = { pin, cb -> viewModel.verifyPin(pin, cb) }
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (currentRoute != CsiRoute.LISTENING_ORB) {
                    TopAppBar(
                        title = { Text(currentRoute.title) },
                        navigationIcon = {
                            if (isSubScreen) {
                                IconButton(
                                    onClick = {
                                        currentRoute = when (currentRoute) {
                                            CsiRoute.WELCOME_SETUP -> CsiRoute.HOME
                                            CsiRoute.ADMIN_DASHBOARD -> CsiRoute.PRIVACY_SECURITY
                                            else -> CsiRoute.SETTINGS_HUB
                                        }
                                    },
                                    modifier = Modifier.testTag("top_bar_back_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { currentRoute = CsiRoute.LISTENING_ORB },
                                modifier = Modifier.testTag("top_bar_orb_button")
                            ) {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = "Open voice listening studio")
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!useRail && !isSubScreen) {
                    NavigationBar {
                        primaryTabs.forEach { (route, label, icon) ->
                            NavigationBarItem(
                                selected = currentRoute == route,
                                onClick = { currentRoute = route },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${route.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isSubScreen) {
                    NavigationRail {
                        primaryTabs.forEach { (route, label, icon) ->
                            NavigationRailItem(
                                selected = currentRoute == route,
                                onClick = { currentRoute = route },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentRoute) {
                        CsiRoute.WELCOME_SETUP -> WelcomeSetupScreen(
                            uiState = uiState,
                            onUpdateWakeWord = { phrase -> viewModel.updateSettings { it.copy(activeWakeWord = phrase) } },
                            onSelectLanguage = { lang -> viewModel.updateSettings { it.copy(preferredLanguageCode = lang.code) } },
                            onToggleOffline = { off -> viewModel.updateSettings { it.copy(offlineModeOnly = off) } },
                            onCompleteSetup = {
                                viewModel.completeOnboarding(context)
                                currentRoute = CsiRoute.HOME
                            }
                        )
                        CsiRoute.HOME -> HomeScreen(
                            uiState = uiState,
                            onToggleContinuousMic = {
                                if (uiState.isListening) {
                                    viewModel.stopListening(context)
                                } else {
                                    val continuous = uiState.settings.batteryListeningMode != BatteryListeningMode.PUSH_TO_TALK.key
                                    viewModel.startListening(context, continuous = continuous)
                                }
                            },
                            onOneShotListen = {
                                viewModel.startListening(context, continuous = false)
                            },
                            onOpenFullOrb = { currentRoute = CsiRoute.LISTENING_ORB },
                            onSubmitCommand = { cmd -> viewModel.submitCommand(cmd) },
                            onConfirmCall = { uiState.pendingCall?.let { viewModel.confirmPendingCall(it) } },
                            onCancelCall = { viewModel.cancelPendingCall() },
                            onSelectContact = { viewModel.selectAmbiguousContact(it) },
                            onSendSms = { num, body -> viewModel.confirmAndSendSms(num, body) },
                            onCancelSms = { viewModel.cancelPendingSms() },
                            onRequestDeviceAdmin = {
                                viewModel.dismissDeviceAdminPrompt()
                                context.startActivity(viewModel.deviceController.buildEnableDeviceAdminIntent())
                            },
                            onDismissDeviceAdmin = { viewModel.dismissDeviceAdminPrompt() },
                            onOpenSetup = { currentRoute = CsiRoute.WELCOME_SETUP }
                        )
                        CsiRoute.LISTENING_ORB -> ListeningOrbScreen(
                            uiState = uiState,
                            onToggleListening = {
                                if (uiState.isListening) viewModel.stopListening(context)
                                else viewModel.startListening(context, continuous = true)
                            },
                            onStopImmediately = { viewModel.stopListening(context) },
                            onSelectLanguage = { lang -> viewModel.updateSettings { it.copy(preferredLanguageCode = lang.code) } },
                            onBack = { currentRoute = CsiRoute.HOME }
                        )
                        CsiRoute.HISTORY -> HistoryScreen(
                            history = history,
                            notesReminders = notesReminders,
                            onReplayCommand = { cmd ->
                                viewModel.submitCommand(cmd)
                                currentRoute = CsiRoute.HOME
                            },
                            onDeleteItem = { viewModel.deleteHistoryItem(it) },
                            onClearAll = { viewModel.clearAllHistory() },
                            onToggleNoteDone = { viewModel.toggleNoteCompleted(it) },
                            onDeleteNote = { viewModel.deleteNoteReminder(it) }
                        )
                        CsiRoute.ROUTINES -> CustomCommandsScreen(
                            routines = routines,
                            onSaveRoutine = { id, phrase, actions, reply ->
                                viewModel.saveCustomRoutine(id, phrase, actions, reply)
                            },
                            onToggleRoutine = { routine, enabled ->
                                viewModel.toggleRoutineEnabled(routine, enabled)
                            },
                            onTestRoutine = { routine ->
                                viewModel.submitCommand(routine.triggerPhrase)
                                currentRoute = CsiRoute.HOME
                            },
                            onDeleteRoutine = { viewModel.deleteCustomRoutine(it) }
                        )
                        CsiRoute.SETTINGS_HUB -> ControlsSettingsHubScreen(
                            onNavigate = { currentRoute = it }
                        )
                        CsiRoute.CALLING_SETTINGS -> CallingSettingsScreen(
                            settings = uiState.settings,
                            deviceController = viewModel.deviceController,
                            onUpdateSettings = { viewModel.updateSettings(it) },
                            onPlaceCallTest = { cmd ->
                                viewModel.submitCommand(cmd)
                                currentRoute = CsiRoute.HOME
                            }
                        )
                        CsiRoute.MESSAGE_SETTINGS -> MessageSettingsScreen(
                            settings = uiState.settings,
                            deviceController = viewModel.deviceController,
                            onUpdateSettings = { viewModel.updateSettings(it) }
                        )
                        CsiRoute.NOTIFICATION_SETTINGS -> NotificationSettingsScreen(
                            settings = uiState.settings,
                            rules = notifRules,
                            recentNotifications = recentNotifs,
                            onUpdateSettings = { viewModel.updateSettings(it) },
                            onToggleRule = { r, allow, muted -> viewModel.toggleNotificationRule(r, allow, muted) }
                        )
                        CsiRoute.LANGUAGE_VOICE_SETTINGS -> LanguageVoiceSettingsScreen(
                            settings = uiState.settings,
                            onUpdateSettings = { viewModel.updateSettings(it) },
                            onTestVoice = { viewModel.speakPreview(it) }
                        )
                        CsiRoute.PRIVACY_SECURITY -> PrivacySecurityScreen(
                            settings = uiState.settings,
                            isListening = uiState.isListening,
                            keystoreHardwareBacked = uiState.keystoreHardwareBacked,
                            deviceController = viewModel.deviceController,
                            onPauseListeningNow = { viewModel.stopListening(context) },
                            onUpdateSettings = { viewModel.updateSettings(it) },
                            onSetAppPin = { viewModel.setAppPin(it) },
                            onOpenAdminDashboard = { currentRoute = CsiRoute.ADMIN_DASHBOARD }
                        )
                        CsiRoute.OFFLINE_MODE -> OfflineModeScreen(
                            settings = uiState.settings,
                            historyCount = history.size,
                            routineCount = routines.count { it.enabled },
                            noteCount = notesReminders.size,
                            onUpdateSettings = { viewModel.updateSettings(it) }
                        )
                        CsiRoute.BATTERY_OPTIMIZATION -> BatteryOptimizationScreen(
                            settings = uiState.settings,
                            onUpdateSettings = { viewModel.updateSettings(it) }
                        )
                        CsiRoute.ADMIN_DASHBOARD -> AdminDashboardScreen(
                            settings = uiState.settings,
                            appConfig = uiState.appConfig,
                            errorLogs = errorLogs,
                            cloudSyncError = uiState.cloudSyncError,
                            onUpdateSettings = { viewModel.updateSettings(it) },
                            onUpdateAppConfig = { minV, latV, maint, ann, cb ->
                                viewModel.updateAdminAppConfig(minV, latV, maint, ann, cb)
                            },
                            onClearLogs = { viewModel.clearDiagnosticLogs() },
                            onBack = { currentRoute = CsiRoute.PRIVACY_SECURITY }
                        )
                    }
                }
            }
        }
    }
}

private data class SettingsHubEntry(
    val route: CsiRoute,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
private fun ControlsSettingsHubScreen(
    onNavigate: (CsiRoute) -> Unit
) {
    val entries = listOf(
        SettingsHubEntry(CsiRoute.WELCOME_SETUP, "Welcome & Setup Wizard", "Onboarding, microphone setup & primary wake word", Icons.Rounded.Settings),
        SettingsHubEntry(CsiRoute.LISTENING_ORB, "Animated Voice Studio", "Full-screen reactive AI orb & hands-free controls", Icons.Rounded.GraphicEq),
        SettingsHubEntry(CsiRoute.CALLING_SETTINGS, "Contact & Calling Settings", "SIM selection, call confirmation & relationship aliases", Icons.Rounded.Call),
        SettingsHubEntry(CsiRoute.MESSAGE_SETTINGS, "Message Reading Settings", "SMS access, notification fallback & OTP privacy blocker", Icons.Rounded.Message),
        SettingsHubEntry(CsiRoute.NOTIFICATION_SETTINGS, "Notification Permissions", "Allowed apps, caller announcement & privacy masking", Icons.Rounded.Notifications),
        SettingsHubEntry(CsiRoute.LANGUAGE_VOICE_SETTINGS, "Language & Voice Settings", "English, Telugu, Hindi, Male/Female voice & wake profiles", Icons.Rounded.Language),
        SettingsHubEntry(CsiRoute.PRIVACY_SECURITY, "Privacy & Security Dashboard", "Permissions, Device Lock admin, Keystore & App PIN", Icons.Rounded.Security),
        SettingsHubEntry(CsiRoute.OFFLINE_MODE, "Offline Mode Settings", "100% local processing switch & Room storage stats", Icons.Rounded.CloudOff),
        SettingsHubEntry(CsiRoute.BATTERY_OPTIMIZATION, "Battery Optimization Settings", "Power-efficient listening modes & Android limits", Icons.Rounded.BatterySaver),
        SettingsHubEntry(CsiRoute.ADMIN_DASHBOARD, "Admin, Telemetry & Diagnostics", "Account, App Version config, opt-in telemetry & error logs", Icons.Rounded.AdminPanelSettings)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("CSI Control Center", style = MaterialTheme.typography.headlineSmall)
        entries.forEach { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(item.route) }
                    .testTag("hub_${item.route.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
