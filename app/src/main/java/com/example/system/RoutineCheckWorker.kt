package com.example.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.local.CsiDatabase
import java.util.concurrent.TimeUnit

class RoutineCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = CsiDatabase.getInstance(applicationContext)
            val activeReminders = db.noteReminderDao().getActiveReminders()
            val now = System.currentTimeMillis()
            val due = activeReminders.filter { it.dueAtMillis in 1..now }
            if (due.isNotEmpty()) {
                postReminderNotification(due.first().title, due.size)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun postReminderNotification(title: String, count: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "CSI Scheduled Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
        val text = if (count == 1) title else "$title (+${count - 1} more)"
        val notif = NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("CSI Voice Master Reminder")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(2042, notif)
    }

    companion object {
        private const val REMINDER_CHANNEL_ID = "csi_reminders"
        private const val WORK_NAME = "csi_periodic_reminder_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RoutineCheckWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
