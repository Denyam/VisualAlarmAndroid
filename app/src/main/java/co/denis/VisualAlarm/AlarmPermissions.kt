package co.denis.VisualAlarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Checks and launches the system settings screens for the three permissions
 * needed for a reliable alarm: notifications, exact alarms, and full-screen intents.
 *
 * Each check treats permissions that don't exist on the running platform version
 * as already granted, so the flow can simply walk the three in order.
 */
class AlarmPermissions(private val context: Context) {

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

    fun hasAllPermissions(): Boolean =
        hasPostNotificationsPermission() && canScheduleExactAlarms() && canUseFullScreenIntent()

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
}
