package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.core.CsiLocalSettings
import com.example.data.local.ErrorLogEntity
import com.example.data.remote.AppConfigDoc
import com.example.system.CsiNotificationListenerService
import com.example.system.DeviceActionController
import com.example.ui.components.signOutUser
import com.example.voice.AssistantPersonality
import com.example.voice.BatteryListeningMode
import com.example.voice.VoiceLanguage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageVoiceSettingsScreen(
    settings: CsiLocalSettings,
    onUpdateSettings: ((CsiLocalSettings) -> CsiLocalSettings) -> Unit,
    onTestVoice: (String) -> Unit
) {
    val selectedLang = VoiceLanguage.fromCode(settings.preferredLanguageCode)
    val selectedPersonality = AssistantPersonality.fromKey(settings.personality)
    var primaryWake by remember(settings.activeWakeWord) { mutableStateOf(settings.activeWakeWord) }
    var secondaryWake by remember(settings.secondaryWakeWordsCsv) { mutableStateOf(settings.secondaryWakeWordsCsv) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Language, Voice & Wake Words", style = MaterialTheme.typography.headlineSmall)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Spoken Language & Auto-Recognition", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLang == lang,
                            onClick = { onUpdateSettings { it.copy(preferredLanguageCode = lang.code) } },
                            label = { Text(lang.nativeLabel) },
                            modifier = Modifier.testTag("voice_lang_${lang.name.lowercase()}")
                        )
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Assistant Voice Profile", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("female" to "Female Voice", "male" to "Male Voice").forEach { (key, label) ->
                        FilterChip(
                            selected = settings.voiceGender == key,
                            onClick = { onUpdateSettings { it.copy(voiceGender = key) } },
                            label = { Text(label) },
                            modifier = Modifier.testTag("voice_gender_$key")
                        )
                    }
                }
                Text("Speaking Speed: ${"%.2f".format(settings.speechSpeed)}x", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.speechSpeed,
                    onValueChange = { speed -> onUpdateSettings { it.copy(speechSpeed = speed) } },
                    valueRange = 0.6f..1.8f,
                    modifier = Modifier.testTag("speech_speed_slider")
                )
                Text("Assistant Volume: ${(settings.assistantVolume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.assistantVolume,
                    onValueChange = { vol -> onUpdateSettings { it.copy(assistantVolume = vol) } },
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier.testTag("assistant_volume_slider")
                )
                Button(
                    onClick = {
                        onTestVoice("Welcome S Irfan. CSI Voice Master AI is calibrated and ready.")
                    },
                    modifier = Modifier.fillMaxWidth().testTag("test_tts_voice_button")
                ) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Test Assistant Voice")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Custom Assistant Personality", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantPersonality.entries.forEach { p ->
                        FilterChip(
                            selected = selectedPersonality == p,
                            onClick = { onUpdateSettings { it.copy(personality = p.key) } },
                            label = { Text(p.label) }
                        )
                    }
                }
                Text(selectedPersonality.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Multiple Wake-Word Profiles", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = primaryWake,
                    onValueChange = { primaryWake = it },
                    label = { Text("Primary Wake Word") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secondaryWake,
                    onValueChange = { secondaryWake = it },
                    label = { Text("Additional Wake Profiles (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onUpdateSettings {
                            it.copy(
                                activeWakeWord = primaryWake.trim().ifBlank { "Hey Voice Master" },
                                secondaryWakeWordsCsv = secondaryWake.trim()
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_wake_profiles_button")
                ) {
                    Text("Save Wake-Word Profiles")
                }
            }
        }
    }
}

@Composable
fun PrivacySecurityScreen(
    settings: CsiLocalSettings,
    isListening: Boolean,
    keystoreHardwareBacked: Boolean,
    deviceController: DeviceActionController,
    onPauseListeningNow: () -> Unit,
    onUpdateSettings: ((CsiLocalSettings) -> CsiLocalSettings) -> Unit,
    onSetAppPin: (String?) -> Unit,
    onOpenAdminDashboard: () -> Unit
) {
    val context = LocalContext.current
    var refreshCounter by remember { mutableStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshCounter++ }

    var pinDraft by remember { mutableStateOf("") }
    val micGranted = remember(refreshCounter) { deviceController.hasPermission(Manifest.permission.RECORD_AUDIO) }
    val contactsGranted = remember(refreshCounter) { deviceController.hasPermission(Manifest.permission.READ_CONTACTS) }
    val phoneGranted = remember(refreshCounter) { deviceController.hasPermission(Manifest.permission.CALL_PHONE) }
    val smsGranted = remember(refreshCounter) { deviceController.hasPermission(Manifest.permission.READ_SMS) }
    val notifEnabled = remember(refreshCounter) { CsiNotificationListenerService.isNotificationAccessEnabled(context) }
    val deviceAdminEnabled = remember(refreshCounter) { deviceController.isDeviceAdminEnabled() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Privacy & Security Dashboard", style = MaterialTheme.typography.headlineSmall)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Microphone Indicator: ${if (isListening) "ACTIVE" else "PAUSED"}", style = MaterialTheme.typography.titleMedium)
                    Text("Instant hardware-safe listening cutoff.", style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = onPauseListeningNow,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("privacy_pause_listening_button")
                ) {
                    Icon(Icons.Rounded.MicOff, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Pause")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Permission Dashboard", style = MaterialTheme.typography.titleMedium)
                PermissionRow("Microphone (Voice)", micGranted)
                PermissionRow("Contacts (Lookup)", contactsGranted)
                PermissionRow("Phone (Direct Dial)", phoneGranted)
                PermissionRow("SMS (Read/Send)", smsGranted)
                PermissionRow("Notification Listener", notifEnabled)
                PermissionRow("Device Admin (Voice Lock)", deviceAdminEnabled)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            permLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.CALL_PHONE,
                                    Manifest.permission.READ_CALL_LOG
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Grant Core")
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(deviceController.buildEnableDeviceAdminIntent())
                        },
                        modifier = Modifier.weight(1f).testTag("enable_device_admin_button")
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Voice Lock")
                    }
                }
            }
        }

        SettingsToggleCard(
            title = "AI Cloud Data Consent (Gemini)",
            subtitle = "Allow sending open-ended questions to Gemini AI when not handled locally offline.",
            checked = settings.aiCloudConsent,
            tag = "switch_ai_cloud_consent",
            onCheckedChange = { v -> onUpdateSettings { it.copy(aiCloudConsent = v) } }
        )

        SettingsToggleCard(
            title = "Cloud Sync (Firestore)",
            subtitle = "Sync custom routines and preferences to your signed-in Google account.",
            checked = settings.cloudSyncEnabled,
            tag = "switch_cloud_sync",
            onCheckedChange = { v -> onUpdateSettings { it.copy(cloudSyncEnabled = v) } }
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text("Encrypted Storage & Optional App PIN", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Android Keystore AES-256 GCM: ${if (keystoreHardwareBacked) "Active" else "Fallback Mode"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = pinDraft,
                    onValueChange = { if (it.length <= 8) pinDraft = it },
                    label = { Text(if (settings.appPinEnabled) "New PIN (4-8 digits)" else "Set Optional App PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("set_app_pin_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSetAppPin(pinDraft)
                            pinDraft = ""
                        },
                        enabled = pinDraft.length >= 4,
                        modifier = Modifier.weight(1f).testTag("save_app_pin_button")
                    ) {
                        Text("Enable PIN")
                    }
                    if (settings.appPinEnabled) {
                        OutlinedButton(
                            onClick = { onSetAppPin(null) },
                            modifier = Modifier.weight(1f).testTag("disable_app_pin_button")
                        ) {
                            Text("Remove PIN")
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onOpenAdminDashboard,
            modifier = Modifier.fillMaxWidth().testTag("open_admin_dashboard_button")
        ) {
            Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Admin, Telemetry & Diagnostics")
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (granted) "GRANTED" else "OFF",
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun OfflineModeScreen(
    settings: CsiLocalSettings,
    historyCount: Int,
    routineCount: Int,
    noteCount: Int,
    onUpdateSettings: ((CsiLocalSettings) -> CsiLocalSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Offline Mode & Local Engine", style = MaterialTheme.typography.headlineSmall)

        SettingsToggleCard(
            title = "100% Offline Local Mode",
            subtitle = "Block all cloud AI calls and weather fetches. Process commands strictly on-device.",
            checked = settings.offlineModeOnly,
            tag = "switch_offline_only",
            onCheckedChange = { v -> onUpdateSettings { it.copy(offlineModeOnly = v) } }
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Local Room Storage Stats", style = MaterialTheme.typography.titleMedium)
                }
                Text("• Cached Voice History: $historyCount items", style = MaterialTheme.typography.bodyMedium)
                Text("• Offline Custom Routines: $routineCount active routines", style = MaterialTheme.typography.bodyMedium)
                Text("• Local Notes & Reminders: $noteCount saved items", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What Works 100% Offline", style = MaterialTheme.typography.titleMedium)
                Text("✓ Flashlight, Volume, Brightness, Media Playback", style = MaterialTheme.typography.bodyMedium)
                Text("✓ Calling, Contacts, Recent Calls & SMS Intents", style = MaterialTheme.typography.bodyMedium)
                Text("✓ Alarms, Timers, App Launch, Date/Time, Battery, Math", style = MaterialTheme.typography.bodyMedium)
                Text("✓ Custom Routines, Local Notes & Reminders, Phone Lock", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("Requires Internet", style = MaterialTheme.typography.titleMedium)
                Text("• Gemini AI natural Q&A, complex explanations & translations", style = MaterialTheme.typography.bodyMedium)
                Text("• Live Open-Meteo weather forecasts & Firestore cloud sync", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun BatteryOptimizationScreen(
    settings: CsiLocalSettings,
    onUpdateSettings: ((CsiLocalSettings) -> CsiLocalSettings) -> Unit
) {
    val context = LocalContext.current
    val currentMode = BatteryListeningMode.fromKey(settings.batteryListeningMode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Battery Optimization Settings", style = MaterialTheme.typography.headlineSmall)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Listening Power Profile", style = MaterialTheme.typography.titleMedium)
                BatteryListeningMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = { onUpdateSettings { it.copy(batteryListeningMode = mode.key) } }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.label, style = MaterialTheme.typography.titleMedium)
                            Text(mode.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.BatterySaver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Android Background Mic Limitations", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Modern Android enforces strict microphone and battery Doze limits. No third-party app can guarantee unrestricted 24/7 background speech recognition across every OEM skin. Use Foreground Wake-Word mode while actively needing hands-free control, or Ultra Saver (Tap to Talk) for all-day battery life.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("open_battery_settings_button")
                ) {
                    Text("Open Android Battery Optimization Settings")
                }
            }
        }
    }
}

@Composable
fun AdminDashboardScreen(
    settings: CsiLocalSettings,
    appConfig: AppConfigDoc?,
    errorLogs: List<ErrorLogEntity>,
    cloudSyncError: String?,
    onUpdateSettings: ((CsiLocalSettings) -> CsiLocalSettings) -> Unit,
    onUpdateAppConfig: (String, String, Boolean, String, (Result<Unit>) -> Unit) -> Unit,
    onClearLogs: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val user = Firebase.auth.currentUser

    var minVer by remember(appConfig) { mutableStateOf(appConfig?.minSupportedVersion ?: "1.0") }
    var latestVer by remember(appConfig) { mutableStateOf(appConfig?.latestVersion ?: "1.0") }
    var maintMode by remember(appConfig) { mutableStateOf(appConfig?.maintenanceMode ?: false) }
    var announcement by remember(appConfig) { mutableStateOf(appConfig?.announcement ?: "Voice Master AI operational") }
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Admin & Diagnostics", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Authenticated Account", style = MaterialTheme.typography.titleMedium)
                Text("Name: ${user?.displayName ?: "S Irfan"}", style = MaterialTheme.typography.bodyMedium)
                Text("Email: ${user?.email ?: "Unknown"}", style = MaterialTheme.typography.bodyMedium)
                Text("UID: ${user?.uid ?: "-"}", style = MaterialTheme.typography.labelSmall)
                if (cloudSyncError != null) {
                    Text("Sync note: $cloudSyncError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(
                    onClick = { signOutUser(credentialManager, scope) },
                    modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                ) {
                    Text("Sign Out")
                }
            }
        }

        SettingsToggleCard(
            title = "Usage Analytics (Opt-In)",
            subtitle = "Log anonymized command category & latency metrics to Firestore.",
            checked = settings.analyticsConsent,
            tag = "switch_analytics_consent",
            onCheckedChange = { v -> onUpdateSettings { it.copy(analyticsConsent = v) } }
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App Version Management (Firestore /app_config)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = minVer, onValueChange = { minVer = it }, label = { Text("Min Supported Version") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = latestVer, onValueChange = { latestVer = it }, label = { Text("Latest Version") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = announcement, onValueChange = { announcement = it }, label = { Text("System Announcement") }, modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Maintenance Mode")
                    Switch(checked = maintMode, onCheckedChange = { maintMode = it })
                }
                if (statusFeedback != null) {
                    Text(statusFeedback!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Button(
                    onClick = {
                        onUpdateAppConfig(minVer, latestVer, maintMode, announcement) { res ->
                            statusFeedback = if (res.isSuccess) "App config published." else "Admin write rejected: ${res.exceptionOrNull()?.localizedMessage}"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("publish_app_config_button")
                ) {
                    Text("Publish Version Config")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Error & Crash Logs (${errorLogs.size})", style = MaterialTheme.typography.titleMedium)
                    if (errorLogs.isNotEmpty()) {
                        OutlinedButton(onClick = onClearLogs) {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }
                if (errorLogs.isEmpty()) {
                    Text("No client errors recorded.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    errorLogs.take(10).forEach { log ->
                        Text("[${log.tag}] ${log.message}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
