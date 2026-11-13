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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.CsiLocalSettings
import com.example.data.local.NotificationRuleEntity
import com.example.system.CapturedNotification
import com.example.system.CsiNotificationListenerService
import com.example.system.DeviceActionController
import com.example.voice.ContactMatch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallingSettingsScreen(
    settings: CsiLocalSettings,
    deviceController: DeviceActionController,
    onUpdateSettings: ((CsiLocalSettings) -> CsiLocalSettings) -> Unit,
    onPlaceCallTest: (String) -> Unit
) {
    val context = LocalContext.current
    var contactsGranted by remember { mutableStateOf(deviceController.hasPermission(Manifest.permission.READ_CONTACTS)) }
    var callGranted by remember { mutableStateOf(deviceController.hasPermission(Manifest.permission.CALL_PHONE)) }
    var callLogGranted by remember { mutableStateOf(deviceController.hasPermission(Manifest.permission.READ_CALL_LOG)) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        contactsGranted = deviceController.hasPermission(Manifest.permission.READ_CONTACTS)
        callGranted = deviceController.hasPermission(Manifest.permission.CALL_PHONE)
        callLogGranted = deviceController.hasPermission(Manifest.permission.READ_CALL_LOG)
    }

    var fatherAlias by remember(settings.fatherPhoneAlias) { mutableStateOf(settings.fatherPhoneAlias) }
    var motherAlias by remember(settings.motherPhoneAlias) { mutableStateOf(settings.motherPhoneAlias) }
    var contactQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ContactMatch>>(emptyList()) }
    val simCount = remember(callGranted) { deviceController.getAvailableSimCount() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Contact & Calling Settings", style = MaterialTheme.typography.headlineSmall)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calling Permissions", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Contacts: ${if (contactsGranted) "Granted" else "Denied"} • Phone: ${if (callGranted) "Granted" else "Denied"} • Call Log: ${if (callLogGranted) "Granted" else "Denied"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        permLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_CALL_LOG,
                                Manifest.permission.READ_PHONE_STATE
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("grant_calling_permissions_button")
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Authorize Contacts & Calling")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Confirm before dialing", style = MaterialTheme.typography.titleMedium)
                        Text("Show recipient card before placing any call", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.confirmCallBeforeDialing,
                        onCheckedChange = { checked -> onUpdateSettings { it.copy(confirmCallBeforeDialing = checked) } },
                        modifier = Modifier.testTag("confirm_call_switch")
                    )
                }

                Text("Preferred SIM Card ($simCount detected)", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-1 to "System Default", 0 to "SIM 1", 1 to "SIM 2").forEach { (slot, label) ->
                        FilterChip(
                            selected = settings.preferredSimSlot == slot,
                            onClick = { onUpdateSettings { it.copy(preferredSimSlot = slot) } },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Encrypted Relationship Aliases", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Stored encrypted with Android Keystore for \"Call my father\" or \"Send a message to my mother\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = fatherAlias,
                    onValueChange = { fatherAlias = it },
                    label = { Text("Father phone number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("father_alias_input")
                )
                OutlinedTextField(
                    value = motherAlias,
                    onValueChange = { motherAlias = it },
                    label = { Text("Mother phone number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("mother_alias_input")
                )
                Button(
                    onClick = {
                        onUpdateSettings {
                            it.copy(
                                fatherPhoneAlias = fatherAlias.trim(),
                                motherPhoneAlias = motherAlias.trim()
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_aliases_button")
                ) {
                    Text("Save Encrypted Aliases")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Live Contact Lookup Test", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = contactQuery,
                        onValueChange = { contactQuery = it },
                        label = { Text("Name (e.g. Irfan)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { searchResults = deviceController.searchContacts(contactQuery) }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search contacts")
                    }
                }
                searchResults.forEach { contact ->
                    OutlinedButton(
                        onClick = { onPlaceCallTest("Call ${contact.name}") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${contact.name} • ${contact.phoneNumber}")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageSettingsScreen(
    settings: CsiLocalSettings,
    deviceController: DeviceActionController,
    onUpdateSettings: ((CsiLocalSettings) -> CsiLocalSettings) -> Unit
) {
    var smsReadGranted by remember { mutableStateOf(deviceController.hasPermission(Manifest.permission.READ_SMS)) }
    var smsSendGranted by remember { mutableStateOf(deviceController.hasPermission(Manifest.permission.SEND_SMS)) }
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        smsReadGranted = deviceController.hasPermission(Manifest.permission.READ_SMS)
        smsSendGranted = deviceController.hasPermission(Manifest.permission.SEND_SMS)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Message Reading & SMS Settings", style = MaterialTheme.typography.headlineSmall)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SMS Permissions & Android Policy", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Read SMS: ${if (smsReadGranted) "Granted" else "Not granted"} • Send SMS: ${if (smsSendGranted) "Granted" else "Not granted"}. If SMS permissions are restricted on your device, CSI automatically uses Notification-based reading and your default SMS app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        smsLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.SEND_SMS,
                                Manifest.permission.RECEIVE_SMS
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("grant_sms_permissions_button")
                ) {
                    Icon(Icons.Rounded.Message, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Request SMS Permissions")
                }
            }
        }

        SettingsToggleCard(
            title = "Block OTP & Banking Codes Aloud",
            subtitle = "Never speak verification codes, CVVs, or banking PINs out loud (recommended ON).",
            checked = settings.blockOtpAloud,
            tag = "switch_block_otp",
            onCheckedChange = { v -> onUpdateSettings { it.copy(blockOtpAloud = v) } }
        )

        SettingsToggleCard(
            title = "Confirm Recipient & Text Before Sending",
            subtitle = "Always allow editing and final confirmation before sending an SMS.",
            checked = settings.confirmSmsBeforeSending,
            tag = "switch_confirm_sms",
            onCheckedChange = { v -> onUpdateSettings { it.copy(confirmSmsBeforeSending = v) } }
        )

        SettingsToggleCard(
            title = "Notification-Based Message Reading Fallback",
            subtitle = "Read messaging notifications when direct SMS access is unavailable.",
            checked = settings.useNotificationFallbackForMessages,
            tag = "switch_notif_msg_fallback",
            onCheckedChange = { v -> onUpdateSettings { it.copy(useNotificationFallbackForMessages = v) } }
        )

        SettingsToggleCard(
            title = "Auto-Read New Incoming Messages",
            subtitle = "Read approved message alerts aloud automatically while listening.",
            checked = settings.autoReadMessages,
            tag = "switch_auto_read_msg",
            onCheckedChange = { v -> onUpdateSettings { it.copy(autoReadMessages = v) } }
        )
    }
}

@Composable
fun NotificationSettingsScreen(
    settings: CsiLocalSettings,
    rules: List<NotificationRuleEntity>,
    recentNotifications: List<CapturedNotification>,
    onUpdateSettings: ((CsiLocalSettings) -> CsiLocalSettings) -> Unit,
    onToggleRule: (NotificationRuleEntity, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val listenerEnabled = CsiNotificationListenerService.isNotificationAccessEnabled(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Notification Assistant & Permissions", style = MaterialTheme.typography.headlineSmall)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Notification Listener Status: ${if (listenerEnabled) "Authorized" else "Disabled"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (listenerEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
                Text(
                    "CSI only reads notifications from apps you explicitly enable below and hides sensitive content by default.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("open_notification_listener_settings_button")
                ) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Android Notification Access")
                }
            }
        }

        SettingsToggleCard(
            title = "Hide Sensitive Notification Content",
            subtitle = "Mask private lock-screen alerts and OTP codes by default.",
            checked = settings.hideSensitiveNotificationContent,
            tag = "switch_hide_sensitive_notif",
            onCheckedChange = { v -> onUpdateSettings { it.copy(hideSensitiveNotificationContent = v) } }
        )

        SettingsToggleCard(
            title = "Announce Caller Names",
            subtitle = "Speak incoming call notifications when permitted.",
            checked = settings.announceCallerEnabled,
            tag = "switch_announce_caller",
            onCheckedChange = { v -> onUpdateSettings { it.copy(announceCallerEnabled = v) } }
        )

        Text("Allowed Apps (${rules.size})", style = MaterialTheme.typography.titleMedium)
        if (rules.isEmpty()) {
            Text(
                "Apps will appear here automatically as notifications arrive once Notification Access is granted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            rules.forEach { rule ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.appLabel, style = MaterialTheme.typography.titleMedium)
                            Text(rule.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Read", style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = rule.allowReadAloud && !rule.muted,
                                onCheckedChange = { allow -> onToggleRule(rule, allow, !allow) }
                            )
                        }
                    }
                }
            }
        }

        if (recentNotifications.isNotEmpty()) {
            Text("Recent Captured Alerts", style = MaterialTheme.typography.titleMedium)
            recentNotifications.take(5).forEach { notif ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${notif.appLabel} • ${notif.title}", style = MaterialTheme.typography.titleMedium)
                        Text(notif.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(tag)
            )
        }
    }
}
