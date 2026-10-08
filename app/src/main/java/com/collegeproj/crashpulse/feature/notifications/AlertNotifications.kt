package com.collegeproj.crashpulse.feature.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.collegeproj.crashpulse.R
import com.collegeproj.crashpulse.feature.alarm.AlarmActivity

object AlertNotifications {
    /**
     * v2 replaces v1. On Android 8+ a notification channel's sound, vibration and
     * importance are fixed at creation and the app cannot change them afterwards.
     * v1 was created without an explicit sound, so the alert played silently and
     * simply adding setSound() to v1 would have had no effect. A new identifier
     * is the only reliable way to adopt a new sound for an existing install.
     */
    const val CHANNEL_ID = "crashpulse_urgent_alerts_v2"

    /** Retired identifiers, silenced so they cannot still buzz on upgrade. */
    private val LEGACY_CHANNEL_IDS = listOf("crashpulse_urgent_alerts")
    private const val GROUP = "crashpulse_incidents"

    /** Alarm-grade audio: the device's own alarm sound, so no asset is bundled. */
    fun alarmSound(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun alarmUri() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            for (legacy in LEGACY_CHANNEL_IDS) runCatching { manager.deleteNotificationChannel(legacy) }
            val channel = NotificationChannel(CHANNEL_ID, "Urgent incident alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Audible human-review alerts from CrashPulse"
                setSound(alarmUri(), alarmSound())
                enableVibration(true)
                enableLights(true)
                // Repeating pattern so it keeps buzzing until the alert is acted on.
                vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun canUseFullScreen(context: Context): Boolean = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun showIncident(context: Context, incidentId: String, title: String, body: String) {
        ensureChannel(context)
        val open = Intent(context, AlarmActivity::class.java).putExtra(AlarmActivity.EXTRA_INCIDENT_ID, incidentId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, incidentId.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_crashpulse)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(GROUP)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, canUseFullScreen(context))
            // Vibration is owned by the channel on Android 8+; the builder-level
            // setSound only matters on older releases.
            .setSound(alarmUri())
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        NotificationManagerCompat.from(context).notify(incidentId.hashCode(), notification)
    }

    fun clear(context: Context, incidentId: String) {
        NotificationManagerCompat.from(context).cancel(incidentId.hashCode())
    }
}