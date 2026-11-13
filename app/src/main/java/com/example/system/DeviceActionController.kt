package com.example.system

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.voice.CallLogSummary
import com.example.voice.ContactMatch
import com.example.voice.SmsSummary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DeviceActionController(private val context: Context) {

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isDeviceAdminEnabled(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val admin = ComponentName(context, CsiDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(admin)
    }

    fun buildEnableDeviceAdminIntent(): Intent {
        val admin = ComponentName(context, CsiDeviceAdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Required only when you explicitly ask CSI to lock your screen by voice."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun lockScreenIfAuthorized(): Pair<Boolean, String> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false to "Device policy service is unavailable on this device."
        val admin = ComponentName(context, CsiDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            return false to "Device Lock permission is not enabled yet. Open Privacy & Security or tap Enable Voice Lock to authorize it."
        }
        return try {
            dpm.lockNow()
            true to "Screen locked. Unlock with your normal device PIN, password, or biometrics."
        } catch (e: SecurityException) {
            false to "Unable to lock screen: ${e.localizedMessage ?: "Admin permission missing."}"
        }
    }

    fun setFlashlight(enable: Boolean): Pair<Boolean, String> {
        val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return false to "Camera service unavailable."
        return try {
            val cameraId = camManager.cameraIdList.firstOrNull { id ->
                val chars = camManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false to "No flashlight hardware detected on this device."
            camManager.setTorchMode(cameraId, enable)
            true to if (enable) "Flashlight turned on." else "Flashlight turned off."
        } catch (e: Exception) {
            false to "Could not control flashlight: ${e.localizedMessage ?: "Hardware busy."}"
        }
    }

    fun adjustVolume(direction: Int, mute: Boolean = false): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "Audio service unavailable."
        return try {
            if (mute) {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
                "Media volume muted."
            } else {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    direction,
                    AudioManager.FLAG_SHOW_UI
                )
                val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val pct = (current * 100) / max
                "Media volume adjusted to $pct%."
            }
        } catch (e: Exception) {
            "Unable to adjust volume: ${e.localizedMessage ?: "Restricted."}"
        }
    }

    fun adjustAppBrightness(activity: Activity?, delta: Float): String {
        if (activity == null) return "Brightness can only be changed while the app window is active."
        return try {
            val attrs = activity.window.attributes
            val current = if (attrs.screenBrightness < 0f) 0.5f else attrs.screenBrightness
            val next = (current + delta).coerceIn(0.1f, 1.0f)
            attrs.screenBrightness = next
            activity.window.attributes = attrs
            val pct = (next * 100).toInt()
            val systemCanWrite = Settings.System.canWrite(context)
            if (systemCanWrite) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    (next * 255).toInt()
                )
                "System and screen brightness set to $pct%."
            } else {
                "Screen brightness set to $pct%. Grant Modify System Settings to apply system-wide."
            }
        } catch (e: Exception) {
            "Could not adjust brightness: ${e.localizedMessage}"
        }
    }

    fun getBatteryReport(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return "Battery telemetry unavailable."
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return if (level in 0..100) {
            if (charging) "Battery is at $level percent and currently charging."
            else "Battery is at $level percent."
        } else {
            "Battery level could not be read on this device."
        }
    }

    fun getBatteryPercentage(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return -1
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: -1
    }

    fun getCurrentDateTimeReport(): String {
        val now = LocalDateTime.now()
        val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
        return "It is ${now.format(timeFmt)} on ${now.format(dateFmt)}."
    }

    fun controlMediaPlayback(keyCode: Int, label: String): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "Audio service unavailable."
        return try {
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audio.dispatchMediaKeyEvent(down)
            audio.dispatchMediaKeyEvent(up)
            "Sent media $label command."
        } catch (e: Exception) {
            "Could not control media playback: ${e.localizedMessage}"
        }
    }

    fun openCamera(): Pair<Boolean, String> = launchIntent(
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        "Opening camera.",
        "No camera app found."
    )

    fun openSystemSettings(action: String, label: String): Pair<Boolean, String> =
        launchIntent(Intent(action), "Opening $label.", "Unable to open $label.")

    fun openCalculator(): Pair<Boolean, String> {
        val calcIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_CALCULATOR)
        }
        return launchIntent(calcIntent, "Opening calculator.", "No calculator app found on this device.")
    }

    fun openAppByName(appName: String): Pair<Boolean, String> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val normalized = appName.trim().lowercase(Locale.ROOT)
        val match = resolveInfos.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase(Locale.ROOT)
            label == normalized || label.contains(normalized)
        } ?: return false to "Could not find an installed app matching '$appName'."

        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return false to "App '${match.loadLabel(pm)}' cannot be launched directly."
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch)
            true to "Opening ${match.loadLabel(pm)}."
        } catch (e: Exception) {
            false to "Failed to launch ${match.loadLabel(pm)}: ${e.localizedMessage}"
        }
    }

    fun setAlarm(hour: Int, minute: Int, label: String = "CSI Alarm"): Pair<Boolean, String> {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return launchIntent(intent, "Opening clock to set alarm for %02d:%02d.".format(hour, minute), "No compatible clock app found.")
    }

    fun setTimer(seconds: Int, label: String = "CSI Timer"): Pair<Boolean, String> {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return launchIntent(intent, "Setting timer for $seconds seconds.", "No compatible timer app found.")
    }

    fun searchWeb(query: String): Pair<Boolean, String> {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
        }
        val launched = launchIntent(intent, "Searching the web for '$query'.", "")
        if (launched.first) return launched
        val browserIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
        )
        return launchIntent(browserIntent, "Searching the web for '$query'.", "No browser available for web search.")
    }

    fun navigateToLocation(destination: String): Pair<Boolean, String> {
        val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(destination))
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        val launched = launchIntent(mapIntent, "Starting navigation to $destination.", "")
        if (launched.first) return launched
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)))
        return launchIntent(geoIntent, "Opening maps for $destination.", "No navigation or maps app found.")
    }

    fun searchContacts(query: String): List<ContactMatch> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return emptyList()
        val results = mutableListOf<ContactMatch>()
        val normalized = query.trim().lowercase(Locale.ROOT)
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        try {
            context.contentResolver.query(
                uri,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$normalized%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val seen = HashSet<String>()
                while (cursor.moveToNext() && results.size < 10) {
                    val id = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val number = cursor.getString(numIdx)?.trim() ?: continue
                    val dedupKey = "$name|$number"
                    if (seen.add(dedupKey)) {
                        results.add(ContactMatch(id = id, name = name, phoneNumber = number))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    fun getRecentCalls(limit: Int = 5): List<CallLogSummary> {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val list = mutableListOf<CallLogSummary>()
        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE
        )
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val numIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                while (cursor.moveToNext() && list.size < limit) {
                    val number = cursor.getString(numIdx) ?: "Unknown"
                    val name = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: number
                    val typeInt = cursor.getInt(typeIdx)
                    val date = cursor.getLong(dateIdx)
                    val typeLabel = when (typeInt) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        else -> "Call"
                    }
                    list.add(CallLogSummary(name, number, typeLabel, date))
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun getAvailableSimCount(): Int {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) return 1
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return 1
            tm.callCapablePhoneAccounts.size.coerceAtLeast(1)
        } catch (_: Exception) {
            1
        }
    }

    fun placeCall(phoneNumber: String, simSlotIndex: Int = -1): Pair<Boolean, String> {
        val uri = Uri.parse("tel:${Uri.encode(phoneNumber)}")
        if (hasPermission(Manifest.permission.CALL_PHONE)) {
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (simSlotIndex >= 0 && hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                    try {
                        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                        val accounts = tm?.callCapablePhoneAccounts
                        if (accounts != null && simSlotIndex < accounts.size) {
                            putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accounts[simSlotIndex])
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            return try {
                context.startActivity(intent)
                true to "Calling $phoneNumber."
            } catch (e: Exception) {
                false to "Unable to place call: ${e.localizedMessage}"
            }
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, uri)
            return launchIntent(dialIntent, "Opening dialer for $phoneNumber (grant Phone permission for direct calling).", "No dialer app found.")
        }
    }

    fun readSmsMessages(senderFilter: String? = null, unreadOnly: Boolean = false, limit: Int = 5): List<SmsSummary> {
        if (!hasPermission(Manifest.permission.READ_SMS)) return emptyList()
        val list = mutableListOf<SmsSummary>()
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )
        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                while (cursor.moveToNext() && list.size < limit) {
                    val rawAddr = cursor.getString(addrIdx) ?: "Unknown"
                    val resolvedName = resolveContactName(rawAddr) ?: rawAddr
                    val body = cursor.getString(bodyIdx) ?: ""
                    val date = cursor.getLong(dateIdx)
                    val isUnread = cursor.getInt(readIdx) == 0
                    if (unreadOnly && !isUnread) continue
                    if (!senderFilter.isNullOrBlank()) {
                        val f = senderFilter.lowercase(Locale.ROOT)
                        if (!resolvedName.lowercase(Locale.ROOT).contains(f) &&
                            !rawAddr.lowercase(Locale.ROOT).contains(f)
                        ) continue
                    }
                    list.add(
                        SmsSummary(
                            sender = resolvedName,
                            body = body,
                            timestampMillis = date,
                            isUnread = isUnread,
                            containsOtpOrSensitiveCode = isSensitiveOtpText(body)
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun sendSmsMessage(phoneNumber: String, message: String): Pair<Boolean, String> {
        if (hasPermission(Manifest.permission.SEND_SMS)) {
            return try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                }
                true to "SMS sent to $phoneNumber."
            } catch (e: Exception) {
                false to "SMS send failed: ${e.localizedMessage ?: "Carrier error."}"
            }
        } else {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phoneNumber)}")).apply {
                putExtra("sms_body", message)
            }
            return launchIntent(
                intent,
                "Opening your SMS app to send to $phoneNumber (SMS send permission not granted).",
                "No SMS messaging app found."
            )
        }
    }

    fun isSensitiveOtpText(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        val keywords = listOf(
            "otp", "one time password", "one-time password", "verification code",
            "auth code", "security code", "cvv", "mpin", "upi pin", "do not share",
            "passcode", "debited", "credited", "a/c", "account ending", "txn"
        )
        if (keywords.any { lower.contains(it) }) return true
        val hasCodeDigits = Regex("\\b\\d{4,8}\\b").containsMatchIn(lower)
        val hasSensitiveContext = listOf("code", "pin", "bank", "verify", "login").any { lower.contains(it) }
        return hasCodeDigits && hasSensitiveContext
    }

    private fun resolveContactName(phoneNumber: String): String? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun launchIntent(intent: Intent, successMsg: String, failMsg: String): Pair<Boolean, String> {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true to successMsg
        } catch (_: Exception) {
            false to failMsg
        }
    }
}
