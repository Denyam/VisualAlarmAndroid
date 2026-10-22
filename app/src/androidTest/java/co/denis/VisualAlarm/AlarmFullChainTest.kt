package co.denis.VisualAlarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the complete real-world alarm chain still works after [AlarmActivity]
 * and [AlarmReceiver] were made non-exported:
 *
 *  1. `AlarmManager` fires the alarm at the scheduled time.
 *  2. The broadcast reaches the **non-exported** [AlarmReceiver] (it is delivered
 *     with the identity of this app, so the platform permits it).
 *  3. The receiver posts the alarm notification with a full-screen intent.
 *  4. The system launches the **non-exported** [AlarmActivity] via that intent.
 *
 * This is the single most important test for the security change: it proves the
 * hardening did not break the feature it was protecting.
 */
@RunWith(AndroidJUnit4::class)
class AlarmFullChainTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        grantPostNotificationsIfPossible()
    }

    @Test
    fun fullAlarmChain_firesThroughNonExportedComponents() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        // Exact alarms require the SCHEDULE_EXACT_ALARM special permission, which
        // is not granted by default on a fresh install. The chain itself does not
        // depend on the alarm being *exact*, so fall back to an inexact alarm
        // when the permission is unavailable.
        val canScheduleExact = alarmManager.canScheduleExactAlarms()
        if (!canScheduleExact) {
            println("RUNNING: exact alarms not permitted, using an inexact alarm for the chain")
        } else {
            println("RUNNING: exact alarms are permitted, exercising the full chain")
        }

        // Cancel any leftover alarm notification from a previous run.
        notificationManager.cancel(1)

        val intent = Intent(AlarmReceiver.ACTION_ALARM_FIRED).apply {
            setClass(context, AlarmReceiver::class.java)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Fire in 3 seconds.
        val triggerAt = System.currentTimeMillis() + 3_000
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }

        val deadline = System.currentTimeMillis() + 30_000
        var notificationPosted = false
        var activityLaunched = false

        while (System.currentTimeMillis() < deadline) {
            if (!notificationPosted) {
                notificationPosted = notificationManager.activeNotifications.any { it.id == 1 }
            }
            if (!activityLaunched) {
                activityLaunched = isAlarmActivityRunning()
            }
            if (notificationPosted && activityLaunched) break
            Thread.sleep(500)
        }

        assertTrue(
            "The alarm notification was not posted: the non-exported AlarmReceiver did not run",
            notificationPosted
        )
        assertTrue(
            "The non-exported AlarmActivity was not launched by the full-screen intent",
            activityLaunched
        )
    }

    private fun isAlarmActivityRunning(): Boolean {
        val activityManager = context.getSystemService(android.app.ActivityManager::class.java)
        return activityManager.appTasks.any { task ->
            task.taskInfo?.baseIntent?.component?.className == AlarmActivity::class.java.name
        }
    }

    private fun grantPostNotificationsIfPossible() {
        try {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(
                    context.packageName,
                    "android.permission.POST_NOTIFICATIONS"
                )
        } catch (_: Exception) {
            // Permission is auto-granted below API 33; nothing to do.
        }
    }

}
