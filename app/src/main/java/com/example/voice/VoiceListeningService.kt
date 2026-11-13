package com.example.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceListeningService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var continuousMode = true
    private var activeLanguageCode = "en-US"
    private var isRestartScheduled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListeningAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START_ONE_SHOT -> {
                continuousMode = false
                activeLanguageCode = intent.getStringExtra(EXTRA_LANGUAGE) ?: activeLanguageCode
                promoteToForeground()
                startRecognizerCycle()
            }
            else -> {
                continuousMode = intent?.getBooleanExtra(EXTRA_CONTINUOUS, true) ?: true
                activeLanguageCode = intent?.getStringExtra(EXTRA_LANGUAGE) ?: activeLanguageCode
                promoteToForeground()
                startRecognizerCycle()
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        createNotificationChannel()
        val notification = buildNotification(
            "CSI Microphone Active",
            if (continuousMode) "Listening for your wake word & voice commands"
            else "Listening for a single voice command"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        _isListeningActive.value = true
    }

    private fun startRecognizerCycle() {
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasMic || !SpeechRecognizer.isRecognitionAvailable(this)) {
            _partialTranscript.value = "Speech recognition unavailable or microphone permission missing."
            stopListeningAndSelf()
            return
        }

        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _isListeningActive.value = true
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                            _rmsLevel.value = normalized
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _rmsLevel.value = 0.05f
                        }

                        override fun onError(error: Int) {
                            _rmsLevel.value = 0.0f
                            if (continuousMode && _isListeningActive.value) {
                                scheduleRestart(delayMs = 900L)
                            } else {
                                stopListeningAndSelf()
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val best = matches?.firstOrNull()?.trim().orEmpty()
                            if (best.isNotEmpty()) {
                                _partialTranscript.value = best
                                _finalTranscripts.tryEmit(best)
                            }
                            if (continuousMode && _isListeningActive.value) {
                                scheduleRestart(delayMs = 500L)
                            } else {
                                stopListeningAndSelf()
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (partial.isNotEmpty()) {
                                _partialTranscript.value = partial
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    if (activeLanguageCode != "auto") {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLanguageCode)
                    }
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                }
                speechRecognizer?.startListening(recognizerIntent)
            } catch (_: Exception) {
                if (continuousMode) scheduleRestart(1500L) else stopListeningAndSelf()
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (isRestartScheduled) return
        isRestartScheduled = true
        mainHandler.postDelayed({
            isRestartScheduled = false
            if (continuousMode && _isListeningActive.value) {
                startRecognizerCycle()
            }
        }, delayMs)
    }

    private fun stopListeningAndSelf() {
        continuousMode = false
        _isListeningActive.value = false
        _rmsLevel.value = 0f
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            speechRecognizer = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        _isListeningActive.value = false
        _rmsLevel.value = 0f
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.mic_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.mic_service_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceListeningService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.pause_listening), stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START_CONTINUOUS = "com.example.voice.START_CONTINUOUS"
        const val ACTION_START_ONE_SHOT = "com.example.voice.START_ONE_SHOT"
        const val ACTION_STOP = "com.example.voice.STOP"
        const val EXTRA_CONTINUOUS = "extra_continuous"
        const val EXTRA_LANGUAGE = "extra_language"

        private const val CHANNEL_ID = "csi_mic_listening_channel"
        private const val NOTIFICATION_ID = 1001

        private val _isListeningActive = MutableStateFlow(false)
        val isListeningActive: StateFlow<Boolean> = _isListeningActive.asStateFlow()

        private val _rmsLevel = MutableStateFlow(0f)
        val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

        private val _partialTranscript = MutableStateFlow("")
        val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

        private val _finalTranscripts = MutableSharedFlow<String>(extraBufferCapacity = 16)
        val finalTranscripts: SharedFlow<String> = _finalTranscripts.asSharedFlow()

        fun startService(context: Context, continuous: Boolean, languageCode: String) {
            val intent = Intent(context, VoiceListeningService::class.java).apply {
                action = if (continuous) ACTION_START_CONTINUOUS else ACTION_START_ONE_SHOT
                putExtra(EXTRA_CONTINUOUS, continuous)
                putExtra(EXTRA_LANGUAGE, languageCode)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VoiceListeningService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
