package com.example.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.csiDataStore: DataStore<Preferences> by preferencesDataStore(name = "csi_settings")

data class CsiLocalSettings(
    val setupCompleted: Boolean = false,
    val activeWakeWord: String = "Hey Voice Master",
    val secondaryWakeWordsCsv: String = "Hey CSI,Voice Master",
    val preferredLanguageCode: String = "en-US", // en-US, te-IN, hi-IN, auto
    val voiceGender: String = "female", // female, male
    val speechSpeed: Float = 1.0f,
    val assistantVolume: Float = 0.85f,
    val personality: String = "professional", // professional, friendly, concise, technical
    val confirmCallBeforeDialing: Boolean = true,
    val preferredSimSlot: Int = -1, // -1 = system default, 0 = SIM 1, 1 = SIM 2
    val fatherPhoneAlias: String = "",
    val motherPhoneAlias: String = "",
    val confirmSmsBeforeSending: Boolean = true,
    val blockOtpAloud: Boolean = true,
    val useNotificationFallbackForMessages: Boolean = true,
    val autoReadMessages: Boolean = false,
    val announceCallerEnabled: Boolean = true,
    val hideSensitiveNotificationContent: Boolean = true,
    val aiCloudConsent: Boolean = true,
    val offlineModeOnly: Boolean = false,
    val cloudSyncEnabled: Boolean = true,
    val analyticsConsent: Boolean = false,
    val batteryListeningMode: String = "FOREGROUND_WAKE_WORD", // PUSH_TO_TALK, SCREEN_ON_ONLY, FOREGROUND_WAKE_WORD
    val appPinEnabled: Boolean = false,
    val noiseResistantThreshold: Float = 0.35f
)

class CryptoPreferences(private val context: Context) {

    private object Keys {
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val ACTIVE_WAKE_WORD = stringPreferencesKey("active_wake_word")
        val SECONDARY_WAKE_WORDS = stringPreferencesKey("secondary_wake_words")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val VOICE_GENDER = stringPreferencesKey("voice_gender")
        val SPEECH_SPEED = floatPreferencesKey("speech_speed")
        val ASSISTANT_VOLUME = floatPreferencesKey("assistant_volume")
        val PERSONALITY = stringPreferencesKey("personality")
        val CONFIRM_CALL = booleanPreferencesKey("confirm_call")
        val PREFERRED_SIM = stringPreferencesKey("preferred_sim")
        val FATHER_ALIAS = stringPreferencesKey("father_alias_enc")
        val MOTHER_ALIAS = stringPreferencesKey("mother_alias_enc")
        val CONFIRM_SMS = booleanPreferencesKey("confirm_sms")
        val BLOCK_OTP = booleanPreferencesKey("block_otp")
        val NOTIF_FALLBACK_MSG = booleanPreferencesKey("notif_fallback_msg")
        val AUTO_READ_MSG = booleanPreferencesKey("auto_read_msg")
        val ANNOUNCE_CALLER = booleanPreferencesKey("announce_caller")
        val HIDE_SENSITIVE_NOTIF = booleanPreferencesKey("hide_sensitive_notif")
        val AI_CLOUD_CONSENT = booleanPreferencesKey("ai_cloud_consent")
        val OFFLINE_MODE_ONLY = booleanPreferencesKey("offline_mode_only")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val ANALYTICS_CONSENT = booleanPreferencesKey("analytics_consent")
        val BATTERY_LISTENING_MODE = stringPreferencesKey("battery_listening_mode")
        val APP_PIN_ENABLED = booleanPreferencesKey("app_pin_enabled")
        val APP_PIN_HASH = stringPreferencesKey("app_pin_hash_enc")
        val NOISE_THRESHOLD = floatPreferencesKey("noise_threshold")
    }

    val settingsFlow: Flow<CsiLocalSettings> = context.csiDataStore.data.map { prefs ->
        CsiLocalSettings(
            setupCompleted = prefs[Keys.SETUP_COMPLETED] ?: false,
            activeWakeWord = prefs[Keys.ACTIVE_WAKE_WORD] ?: "Hey Voice Master",
            secondaryWakeWordsCsv = prefs[Keys.SECONDARY_WAKE_WORDS] ?: "Hey CSI,Voice Master",
            preferredLanguageCode = prefs[Keys.PREFERRED_LANGUAGE] ?: "en-US",
            voiceGender = prefs[Keys.VOICE_GENDER] ?: "female",
            speechSpeed = prefs[Keys.SPEECH_SPEED] ?: 1.0f,
            assistantVolume = prefs[Keys.ASSISTANT_VOLUME] ?: 0.85f,
            personality = prefs[Keys.PERSONALITY] ?: "professional",
            confirmCallBeforeDialing = prefs[Keys.CONFIRM_CALL] ?: true,
            preferredSimSlot = prefs[Keys.PREFERRED_SIM]?.toIntOrNull() ?: -1,
            fatherPhoneAlias = prefs[Keys.FATHER_ALIAS]?.let { decryptString(it) } ?: "",
            motherPhoneAlias = prefs[Keys.MOTHER_ALIAS]?.let { decryptString(it) } ?: "",
            confirmSmsBeforeSending = prefs[Keys.CONFIRM_SMS] ?: true,
            blockOtpAloud = prefs[Keys.BLOCK_OTP] ?: true,
            useNotificationFallbackForMessages = prefs[Keys.NOTIF_FALLBACK_MSG] ?: true,
            autoReadMessages = prefs[Keys.AUTO_READ_MSG] ?: false,
            announceCallerEnabled = prefs[Keys.ANNOUNCE_CALLER] ?: true,
            hideSensitiveNotificationContent = prefs[Keys.HIDE_SENSITIVE_NOTIF] ?: true,
            aiCloudConsent = prefs[Keys.AI_CLOUD_CONSENT] ?: true,
            offlineModeOnly = prefs[Keys.OFFLINE_MODE_ONLY] ?: false,
            cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: true,
            analyticsConsent = prefs[Keys.ANALYTICS_CONSENT] ?: false,
            batteryListeningMode = prefs[Keys.BATTERY_LISTENING_MODE] ?: "FOREGROUND_WAKE_WORD",
            appPinEnabled = prefs[Keys.APP_PIN_ENABLED] ?: false,
            noiseResistantThreshold = prefs[Keys.NOISE_THRESHOLD] ?: 0.35f
        )
    }

    suspend fun updateSettings(transform: (CsiLocalSettings) -> CsiLocalSettings) {
        context.csiDataStore.edit { prefs ->
            val current = CsiLocalSettings(
                setupCompleted = prefs[Keys.SETUP_COMPLETED] ?: false,
                activeWakeWord = prefs[Keys.ACTIVE_WAKE_WORD] ?: "Hey Voice Master",
                secondaryWakeWordsCsv = prefs[Keys.SECONDARY_WAKE_WORDS] ?: "Hey CSI,Voice Master",
                preferredLanguageCode = prefs[Keys.PREFERRED_LANGUAGE] ?: "en-US",
                voiceGender = prefs[Keys.VOICE_GENDER] ?: "female",
                speechSpeed = prefs[Keys.SPEECH_SPEED] ?: 1.0f,
                assistantVolume = prefs[Keys.ASSISTANT_VOLUME] ?: 0.85f,
                personality = prefs[Keys.PERSONALITY] ?: "professional",
                confirmCallBeforeDialing = prefs[Keys.CONFIRM_CALL] ?: true,
                preferredSimSlot = prefs[Keys.PREFERRED_SIM]?.toIntOrNull() ?: -1,
                fatherPhoneAlias = prefs[Keys.FATHER_ALIAS]?.let { decryptString(it) } ?: "",
                motherPhoneAlias = prefs[Keys.MOTHER_ALIAS]?.let { decryptString(it) } ?: "",
                confirmSmsBeforeSending = prefs[Keys.CONFIRM_SMS] ?: true,
                blockOtpAloud = prefs[Keys.BLOCK_OTP] ?: true,
                useNotificationFallbackForMessages = prefs[Keys.NOTIF_FALLBACK_MSG] ?: true,
                autoReadMessages = prefs[Keys.AUTO_READ_MSG] ?: false,
                announceCallerEnabled = prefs[Keys.ANNOUNCE_CALLER] ?: true,
                hideSensitiveNotificationContent = prefs[Keys.HIDE_SENSITIVE_NOTIF] ?: true,
                aiCloudConsent = prefs[Keys.AI_CLOUD_CONSENT] ?: true,
                offlineModeOnly = prefs[Keys.OFFLINE_MODE_ONLY] ?: false,
                cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: true,
                analyticsConsent = prefs[Keys.ANALYTICS_CONSENT] ?: false,
                batteryListeningMode = prefs[Keys.BATTERY_LISTENING_MODE] ?: "FOREGROUND_WAKE_WORD",
                appPinEnabled = prefs[Keys.APP_PIN_ENABLED] ?: false,
                noiseResistantThreshold = prefs[Keys.NOISE_THRESHOLD] ?: 0.35f
            )
            val updated = transform(current)
            prefs[Keys.SETUP_COMPLETED] = updated.setupCompleted
            prefs[Keys.ACTIVE_WAKE_WORD] = updated.activeWakeWord
            prefs[Keys.SECONDARY_WAKE_WORDS] = updated.secondaryWakeWordsCsv
            prefs[Keys.PREFERRED_LANGUAGE] = updated.preferredLanguageCode
            prefs[Keys.VOICE_GENDER] = updated.voiceGender
            prefs[Keys.SPEECH_SPEED] = updated.speechSpeed
            prefs[Keys.ASSISTANT_VOLUME] = updated.assistantVolume
            prefs[Keys.PERSONALITY] = updated.personality
            prefs[Keys.CONFIRM_CALL] = updated.confirmCallBeforeDialing
            prefs[Keys.PREFERRED_SIM] = updated.preferredSimSlot.toString()
            prefs[Keys.FATHER_ALIAS] = encryptString(updated.fatherPhoneAlias)
            prefs[Keys.MOTHER_ALIAS] = encryptString(updated.motherPhoneAlias)
            prefs[Keys.CONFIRM_SMS] = updated.confirmSmsBeforeSending
            prefs[Keys.BLOCK_OTP] = updated.blockOtpAloud
            prefs[Keys.NOTIF_FALLBACK_MSG] = updated.useNotificationFallbackForMessages
            prefs[Keys.AUTO_READ_MSG] = updated.autoReadMessages
            prefs[Keys.ANNOUNCE_CALLER] = updated.announceCallerEnabled
            prefs[Keys.HIDE_SENSITIVE_NOTIF] = updated.hideSensitiveNotificationContent
            prefs[Keys.AI_CLOUD_CONSENT] = updated.aiCloudConsent
            prefs[Keys.OFFLINE_MODE_ONLY] = updated.offlineModeOnly
            prefs[Keys.CLOUD_SYNC_ENABLED] = updated.cloudSyncEnabled
            prefs[Keys.ANALYTICS_CONSENT] = updated.analyticsConsent
            prefs[Keys.BATTERY_LISTENING_MODE] = updated.batteryListeningMode
            prefs[Keys.APP_PIN_ENABLED] = updated.appPinEnabled
            prefs[Keys.NOISE_THRESHOLD] = updated.noiseResistantThreshold
        }
    }

    suspend fun setAppPin(pin: String?) {
        context.csiDataStore.edit { prefs ->
            if (pin.isNullOrBlank()) {
                prefs[Keys.APP_PIN_ENABLED] = false
                prefs.remove(Keys.APP_PIN_HASH)
            } else {
                val hash = sha256(pin)
                prefs[Keys.APP_PIN_ENABLED] = true
                prefs[Keys.APP_PIN_HASH] = encryptString(hash)
            }
        }
    }

    suspend fun verifyAppPin(pin: String): Boolean {
        var matched = false
        context.csiDataStore.edit { prefs ->
            val storedEncrypted = prefs[Keys.APP_PIN_HASH]
            if (storedEncrypted != null) {
                val storedHash = decryptString(storedEncrypted)
                matched = storedHash == sha256(pin)
            }
        }
        return matched
    }

    fun isKeystoreHardwareBacked(): Boolean {
        return try {
            getOrCreateSecretKey() != null
        } catch (_: Exception) {
            false
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("csi_salt_$input".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptString(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val key = getOrCreateSecretKey() ?: return Base64.encodeToString(plain.toByteArray(), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            "ENC:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            Base64.encodeToString(plain.toByteArray(), Base64.NO_WRAP)
        }
    }

    private fun decryptString(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            if (!cipherText.startsWith("ENC:")) {
                return String(Base64.decode(cipherText, Base64.NO_WRAP), Charsets.UTF_8)
            }
            val key = getOrCreateSecretKey() ?: return ""
            val combined = Base64.decode(cipherText.removePrefix("ENC:"), Base64.NO_WRAP)
            val ivLen = combined[0].toInt()
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val data = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private companion object {
        const val KEY_ALIAS = "csi_voice_master_aes_key"
    }
}
