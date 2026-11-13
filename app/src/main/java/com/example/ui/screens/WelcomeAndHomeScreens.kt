package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.AssistantUiState
import com.example.ui.components.AmbiguousContactsCard
import com.example.ui.components.AnimatedAiOrb
import com.example.ui.components.CallConfirmationCard
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.SmsConfirmationCard
import com.example.voice.ChatTurn
import com.example.voice.OrbState
import com.example.voice.VoiceLanguage

@Composable
fun AuthSignInScreen(
    onAuthSuccess: () -> Unit
) {
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_csi_hero_1794588560370),
                contentDescription = "CSI neural acoustic waveform artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(Modifier.height(20.dp))

            AnimatedAiOrb(
                orbState = OrbState.IDLE,
                rmsLevel = 0.2f,
                onClick = {},
                size = 136.dp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.welcome_irfan),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "CSI • Voice Master AI",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Hands-free personal voice assistant with local wake-word detection, multilingual Gemini AI, calling & SMS safeguards, custom routines, and privacy-first offline execution.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(10.dp))
                        Text("Zero-Trust Privacy Architecture", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Sign in with Google to sync your custom routines, preferences, and encrypted assistant profile securely.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (errorMsg != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(24.dp))

            GoogleSignInButton(
                onAuthSuccess = onAuthSuccess,
                onAuthError = { errorMsg = it }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeSetupScreen(
    uiState: AssistantUiState,
    onUpdateWakeWord: (String) -> Unit,
    onSelectLanguage: (VoiceLanguage) -> Unit,
    onToggleOffline: (Boolean) -> Unit,
    onCompleteSetup: () -> Unit
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasMicPermission = grants[Manifest.permission.RECORD_AUDIO] == true
    }

    var wakeInput by remember(uiState.settings.activeWakeWord) { mutableStateOf(uiState.settings.activeWakeWord) }
    val selectedLang = VoiceLanguage.fromCode(uiState.settings.preferredLanguageCode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_csi_hero_1794588560370),
                contentDescription = "CSI setup banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.welcome_irfan),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "CSI Voice Master AI • Quick Setup",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. Voice & Microphone Access", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Microphone access is used strictly when you activate listening. A permanent notification is always visible while active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        micLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("setup_grant_mic_button")
                ) {
                    Icon(
                        imageVector = if (hasMicPermission) Icons.Rounded.CheckCircle else Icons.Rounded.Mic,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasMicPermission) "Microphone Authorized" else "Grant Microphone & Notification Access")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("2. Custom Wake Word", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = wakeInput,
                    onValueChange = {
                        wakeInput = it
                        onUpdateWakeWord(it)
                    },
                    label = { Text("Activation phrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("setup_wake_word_input")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Hey Voice Master", "Hey CSI", "Voice Master").forEach { preset ->
                        AssistChip(
                            onClick = {
                                wakeInput = preset
                                onUpdateWakeWord(preset)
                            },
                            label = { Text(preset) }
                        )
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("3. Assistant Language", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLang == lang,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang.nativeLabel) },
                            modifier = Modifier.testTag("setup_lang_${lang.name.lowercase()}")
                        )
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("4. Offline Local Processing Only", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Keep every command 100% on-device without sending prompts to Gemini AI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.settings.offlineModeOnly,
                    onCheckedChange = onToggleOffline,
                    modifier = Modifier.testTag("setup_offline_switch")
                )
            }
        }

        Button(
            onClick = onCompleteSetup,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("complete_setup_button")
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Enter CSI Voice Master AI")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: AssistantUiState,
    onToggleContinuousMic: () -> Unit,
    onOneShotListen: () -> Unit,
    onOpenFullOrb: () -> Unit,
    onSubmitCommand: (String) -> Unit,
    onConfirmCall: () -> Unit,
    onCancelCall: () -> Unit,
    onSelectContact: (com.example.voice.ContactMatch) -> Unit,
    onSendSms: (String, String) -> Unit,
    onCancelSms: () -> Unit,
    onRequestDeviceAdmin: () -> Unit,
    onDismissDeviceAdmin: () -> Unit,
    onOpenSetup: () -> Unit
) {
    val context = LocalContext.current
    var commandInput by remember { mutableStateOf("") }
    val micGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            onToggleContinuousMic()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // Welcome S Irfan Banner Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.welcome_irfan),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "CSI • ${if (uiState.settings.offlineModeOnly) "Offline Local Core" else "Hybrid Local + Gemini AI"}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onOpenSetup,
                                modifier = Modifier.testTag("reopen_setup_button")
                            ) {
                                Text("Setup")
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AnimatedAiOrb(
                                orbState = uiState.orbState,
                                rmsLevel = uiState.rmsLevel,
                                onClick = {
                                    if (!micGranted) {
                                        micPermLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.RECORD_AUDIO,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        )
                                    } else {
                                        onOpenFullOrb()
                                    }
                                },
                                size = 112.dp
                            )

                            Spacer(Modifier.width(14.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Wake word: \"${uiState.settings.activeWakeWord}\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = uiState.lastStatusBanner,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (!micGranted) {
                                                micPermLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.RECORD_AUDIO,
                                                        Manifest.permission.POST_NOTIFICATIONS
                                                    )
                                                )
                                            } else {
                                                onToggleContinuousMic()
                                            }
                                        },
                                        colors = if (uiState.isListening) {
                                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        } else {
                                            ButtonDefaults.buttonColors()
                                        },
                                        modifier = Modifier.weight(1f).testTag("home_mic_toggle_button")
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (uiState.isListening) "Mic Off" else "Mic On")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (!micGranted) {
                                                micPermLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                            } else {
                                                onOneShotListen()
                                            }
                                        },
                                        modifier = Modifier.testTag("home_speak_once_button")
                                    ) {
                                        Icon(Icons.Rounded.GraphicEq, contentDescription = "Speak one command", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.pendingCall != null) {
                item {
                    CallConfirmationCard(
                        pendingCall = uiState.pendingCall,
                        onConfirm = { onConfirmCall() },
                        onCancel = onCancelCall
                    )
                }
            }

            if (uiState.ambiguousContacts.isNotEmpty()) {
                item {
                    AmbiguousContactsCard(
                        contacts = uiState.ambiguousContacts,
                        onSelectContact = onSelectContact,
                        onDismiss = onCancelCall
                    )
                }
            }

            if (uiState.pendingSms != null) {
                item {
                    SmsConfirmationCard(
                        pendingSms = uiState.pendingSms,
                        onSend = onSendSms,
                        onCancel = onCancelSms
                    )
                }
            }

            if (uiState.needsDeviceAdminPrompt) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Lock, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Authorize Voice Screen Lock", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "Android requires explicit Device Admin activation before CSI can lock your phone by voice.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = onRequestDeviceAdmin) { Text("Enable Lock Admin") }
                                OutlinedButton(onClick = onDismissDeviceAdmin) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }

            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { onSubmitCommand("Good morning") },
                        leadingIcon = { Icon(Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Good morning") },
                        modifier = Modifier.testTag("chip_good_morning")
                    )
                    AssistChip(
                        onClick = { onSubmitCommand("Turn flashlight on") },
                        leadingIcon = { Icon(Icons.Rounded.FlashlightOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Flashlight On") },
                        modifier = Modifier.testTag("chip_flashlight")
                    )
                    AssistChip(
                        onClick = { onSubmitCommand("Check battery level") },
                        leadingIcon = { Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Battery") },
                        modifier = Modifier.testTag("chip_battery")
                    )
                    AssistChip(
                        onClick = { onSubmitCommand("Lock my phone") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Lock Phone") },
                        modifier = Modifier.testTag("chip_lock_phone")
                    )
                }
            }

            if (uiState.chatTurns.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Ready for voice or text commands", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Try saying \"${uiState.settings.activeWakeWord}, Good morning\", \"Call Irfan\", \"Read my latest messages\", or ask any question in English, Telugu, or Hindi.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.chatTurns, key = { it.id }) { turn ->
                    ChatTurnCard(turn)
                }
            }
        }

        // Bottom command input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = { Text("Ask CSI or type a voice command...") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("home_command_input")
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        onSubmitCommand(commandInput)
                        commandInput = ""
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .testTag("home_send_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send command",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ChatTurnCard(turn: ChatTurn) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "YOU • ${turn.languageCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = turn.modeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(turn.userText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (turn.isStreaming) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = turn.assistantText.ifBlank { "Thinking..." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListeningOrbScreen(
    uiState: AssistantUiState,
    onToggleListening: () -> Unit,
    onStopImmediately: () -> Unit,
    onSelectLanguage: (VoiceLanguage) -> Unit,
    onBack: () -> Unit
) {
    val selectedLang = VoiceLanguage.fromCode(uiState.settings.preferredLanguageCode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "CSI VOICE STUDIO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (uiState.orbState) {
                    OrbState.LISTENING -> "Listening..."
                    OrbState.THINKING -> "Processing..."
                    OrbState.SPEAKING -> "Speaking..."
                    OrbState.ERROR -> "Attention Needed"
                    OrbState.IDLE -> "Standing By"
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Wake word: \"${uiState.settings.activeWakeWord}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedAiOrb(
            orbState = uiState.orbState,
            rmsLevel = uiState.rmsLevel,
            onClick = onToggleListening,
            size = 240.dp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = uiState.liveTranscript.ifBlank { "Say \"${uiState.settings.activeWakeWord}\" followed by your command..." },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = selectedLang == lang,
                    onClick = { onSelectLanguage(lang) },
                    label = { Text(lang.label) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStopImmediately,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f).height(50.dp).testTag("orb_pause_listening_button")
            ) {
                Icon(Icons.Rounded.MicOff, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Pause Now")
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(50.dp).testTag("orb_close_button")
            ) {
                Text("Back to Home")
            }
        }
    }
}
