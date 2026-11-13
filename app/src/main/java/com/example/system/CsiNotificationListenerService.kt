package com.example.system

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.example.core.CryptoPreferences
import com.example.data.local.CsiDatabase
import com.example.data.local.NotificationRuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CapturedNotification(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val timestampMillis: Long,
    val isSensitiveHidden: Boolean
)

class CsiNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        val pkg = item.packageName ?: return
        if (pkg == packageName) return

        val extras = item.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && body.isBlank()) return

        val pm = packageManager
        val appLabel = try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }

        scope.launch {
            val db = CsiDatabase.getInstance(applicationContext)
            val dao = db.notificationRuleDao()
            val existingRule = dao.getRule(pkg)
            if (existingRule == null) {
                dao.upsert(
                    NotificationRuleEntity(
                        packageName = pkg,
                        appLabel = appLabel,
                        allowReadAloud = false,
                        muted = false
                    )
                )
            }

            val prefs = CryptoPreferences(applicationContext).settingsFlow.first()
            val deviceCtrl = DeviceActionController(applicationContext)
            val isOtpOrSensitive = deviceCtrl.isSensitiveOtpText("$title $body") ||
                (item.notification?.visibility == Notification.VISIBILITY_PRIVATE && prefs.hideSensitiveNotificationContent)

            val captured = CapturedNotification(
                packageName = pkg,
                appLabel = appLabel,
                title = title,
                text = if (isOtpOrSensitive && prefs.hideSensitiveNotificationContent) {
                    "[Sensitive or OTP content hidden by CSI Privacy]"
                } else {
                    body
                },
                timestampMillis = item.postTime,
                isSensitiveHidden = isOtpOrSensitive && prefs.hideSensitiveNotificationContent
            )

            val current = _recentNotifications.value.toMutableList()
            current.add(0, captured)
            _recentNotifications.value = current.take(25)

            if (existingRule?.allowReadAloud == true && !existingRule.muted) {
                val spoken = if (captured.isSensitiveHidden) {
                    "New notification from $appLabel. Content hidden for privacy."
                } else {
                    "Notification from $appLabel. $title. $body"
                }
                _speakableAlerts.emit(spoken)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val _recentNotifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
        val recentNotifications: StateFlow<List<CapturedNotification>> = _recentNotifications.asStateFlow()

        private val _speakableAlerts = MutableSharedFlow<String>(extraBufferCapacity = 10)
        val speakableAlerts: SharedFlow<String> = _speakableAlerts.asSharedFlow()

        fun isNotificationAccessEnabled(context: Context): Boolean {
            val cn = ComponentName(context, CsiNotificationListenerService::class.java)
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return !TextUtils.isEmpty(flat) && flat.contains(cn.flattenToString())
        }
    }
}
