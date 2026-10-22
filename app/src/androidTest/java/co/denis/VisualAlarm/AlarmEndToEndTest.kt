package co.denis.VisualAlarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end verification that the alarm still works after [AlarmActivity] and
 * [AlarmReceiver] were made non-exported.
 *
 * This is the important counterpart to [AlarmActivitySecurityTest]: the security
 * fix must not break the feature. A PendingIntent executes with the identity of
 * the app that created it, so a non-exported component must still be launchable
 * by that same app. These tests exercise exactly that path, using the app's own
 * identity rather than an external caller.
 */
@RunWith(AndroidJUnit4::class)
class AlarmEndToEndTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    /**
     * The full-screen intent must still be able to launch the non-exported
     * [AlarmActivity]. This is the exact mechanism the alarm relies on.
     */
    @Test
    fun alarmPendingIntent_launchesNonExportedAlarmActivity() {
        val pendingIntent = NotificationHelper.getAlarmPendingIntent(context)

        // Send the PendingIntent ourselves: it executes with *our* identity,
        // which is what allows it to reach the non-exported activity.
        pendingIntent.send()

        val deadline = System.currentTimeMillis() + 10_000
        var launched = false
        while (System.currentTimeMillis() < deadline && !launched) {
            launched = isAlarmActivityRunning()
            if (!launched) Thread.sleep(250)
        }

        assertTrue(
            "The app's own PendingIntent must still be able to launch the non-exported AlarmActivity",
            launched
        )
    }

    /**
     * The non-exported [AlarmReceiver] must still react to the alarm action and
     * post the alarm notification when the broadcast comes from this app.
     */
    @Test
    fun alarmReceiver_postsNotificationForAlarmAction() {
        // POST_NOTIFICATIONS is a runtime permission and is not granted on a
        // fresh install, so the notification would be silently dropped.
        grantPostNotificationsIfPossible()

        val intent = Intent(AlarmReceiver.ACTION_ALARM_FIRED).apply {
            setClass(context, AlarmReceiver::class.java)
        }
        context.sendBroadcast(intent)

        val deadline = System.currentTimeMillis() + 10_000
        var posted = false
        while (System.currentTimeMillis() < deadline && !posted) {
            posted = isAlarmNotificationPosted()
            if (!posted) Thread.sleep(250)
        }

        assertTrue(
            "The non-exported AlarmReceiver did not post the alarm notification",
            posted
        )
    }

    /**
     * The receiver must ignore the protected BOOT_COMPLETED broadcast instead of
     * ringing a false alarm on every reboot.
     *
     * Note: the platform forbids apps from sending BOOT_COMPLETED at all
     * ("Permission Denial: not allowed to send broadcast"), so the receiver is
     * invoked directly to exercise its action-handling logic.
     */
    @Test
    fun alarmReceiver_ignoresBootCompleted() {
        grantPostNotificationsIfPossible()

        val intent = Intent(Intent.ACTION_BOOT_COMPLETED).apply {
            setClass(context, AlarmReceiver::class.java)
        }
        AlarmReceiver().onReceive(context, intent)

        // Give any (incorrect) notification time to appear.
        Thread.sleep(2_000)

        assertTrue(
            "BOOT_COMPLETED must not trigger the alarm",
            !isAlarmNotificationPosted()
        )
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

    private fun isAlarmNotificationPosted(): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        return notificationManager.activeNotifications.any { it.id == 1 }
    }

    private fun isAlarmActivityRunning(): Boolean {
        val activityManager = context.getSystemService(android.app.ActivityManager::class.java)
        return activityManager.appTasks.any { task ->
            task.taskInfo?.baseIntent?.component?.className == AlarmActivity::class.java.name
        }
    }
}
