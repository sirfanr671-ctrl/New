package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class VoiceTtsManager(context: Context) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var speechRate: Float = 1.0f
    private var volume: Float = 0.85f
    private var voiceGender: String = "female"
    private var languageCode: String = "en-US"

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _isReady.value = true
                applyConfiguration()
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
            }
        })
    }

    fun updateSettings(
        languageCode: String,
        gender: String,
        speed: Float,
        volume: Float
    ) {
        this.languageCode = languageCode
        this.voiceGender = gender
        this.speechRate = speed.coerceIn(0.5f, 2.0f)
        this.volume = volume.coerceIn(0f, 1f)
        if (_isReady.value) {
            applyConfiguration()
        }
    }

    private fun applyConfiguration() {
        val engine = tts ?: return
        val locale = when (languageCode) {
            "te-IN" -> Locale("te", "IN")
            "hi-IN" -> Locale("hi", "IN")
            else -> Locale.US
        }
        val langResult = engine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        engine.setSpeechRate(speechRate)
        engine.setPitch(if (voiceGender == "male") 0.88f else 1.08f)

        try {
            val targetVoices = engine.voices?.filter { v ->
                v.locale.language == locale.language && !v.isNetworkConnectionRequired
            } ?: emptyList()
            val genderMatch = targetVoices.firstOrNull { v ->
                val n = v.name.lowercase(Locale.ROOT)
                if (voiceGender == "male") {
                    n.contains("male") || n.contains("-b-") || n.contains("-d-") || n.contains("iom") || n.contains("iob")
                } else {
                    n.contains("female") || n.contains("-a-") || n.contains("-c-") || n.contains("sfg") || n.contains("iog")
                }
            }
            if (genderMatch != null) {
                engine.voice = genderMatch
            }
        } catch (_: Exception) {
        }
    }

    fun speak(text: String) {
        val engine = tts ?: return
        if (!_isReady.value || text.isBlank()) return
        val cleanText = text
            .replace(Regex("[*_`#]+"), "")
            .take(1500)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
