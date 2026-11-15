package co.denis.VisualAlarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

class AlarmPermissions(private val context: Context) {

    companion object {
        /** Notification channel ID used by the alarm. Must match NotificationHelper and AlarmRingingService. */
        const val ALARM_CHANNEL_ID = "alarm_channel"
    }

    /** POST_NOTIFICATIONS is a runtime permission only from API 33. */
    fun hasPostNotificationsPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /** SCHEDULE_EXACT_ALARM is user-granted (settings toggle) only from API 31. */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** USE_FULL_SCREEN_INTENT is user-granted only from API 34. */
    fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** CAMERA is a runtime (dangerous) permission; needed for the alarm torch strobe. */
    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    /** SYSTEM_ALERT_WINDOW is a special permission (not runtime) needed to launch
     *  full-screen alarm activity when notifications are blocked. Granted via Settings. */
    fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(context)

    /** Intent to open the "Display over other apps" settings for this app. */
    fun drawOverlaysSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    /**
     * Checks if the alarm notification channel is enabled (not blocked by user).
     * User can disable a specific channel in Settings > Notifications without disabling all notifications.
     * Returns true if channel exists and importance != IMPORTANCE_NONE.
     */
    fun isAlarmChannelEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(ALARM_CHANNEL_ID)
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Intent to open the app's notification channel settings. */
    fun alarmChannelSettingsIntent(): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, ALARM_CHANNEL_ID)
        }

    /** Resolves to Settings$AlarmsAndRemindersAppActivity on API 31+. */
    fun exactAlarmSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}")
        )

    /** Resolves to Settings$AppManageFullScreenIntent on API 34+. */
    fun fullScreenIntentSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${context.packageName}")
        )

    /** Checks if all required permissions for alarm scheduling are granted. */
    fun allRequiredPermissionsGranted(): Boolean =
        hasPostNotificationsPermission() &&
        isAlarmChannelEnabled() &&
        canScheduleExactAlarms() &&
        canUseFullScreenIntent() &&
        canDrawOverlays()
}
