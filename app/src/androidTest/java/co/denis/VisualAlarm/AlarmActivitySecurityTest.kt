package co.denis.VisualAlarm

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression guard for the security finding:
 *
 *   "AlarmActivity exported without permission guard"
 *
 * `AlarmActivity` is only ever launched through a [android.app.PendingIntent]
 * that this app creates itself, so it does not need to be reachable by other
 * applications. A PendingIntent executes with the identity of its creator, so
 * the platform can still launch a non-exported activity on our behalf.
 */
@RunWith(AndroidJUnit4::class)
class AlarmActivitySecurityTest {

    @Test
    fun alarmActivity_isNotExported() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, AlarmActivity::class.java),
            0,
        )

        assertFalse(
            "AlarmActivity must not be exported: it is only launched via the app's own PendingIntent",
            activityInfo.exported
        )
    }

    @Test
    fun alarmReceiver_isNotExported() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receiverInfo = context.packageManager.getReceiverInfo(
            ComponentName(context, AlarmReceiver::class.java),
            0
        )

        assertFalse(
            "AlarmReceiver must not be exported: BOOT_COMPLETED is a protected broadcast",
            receiverInfo.exported
        )
    }
}
