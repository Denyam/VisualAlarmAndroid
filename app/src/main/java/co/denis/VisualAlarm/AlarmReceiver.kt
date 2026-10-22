package co.denis.VisualAlarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Receives the alarm broadcast scheduled by [AlarmScheduler].
 *
 * The component is declared `android:exported="false"`, so it can only be
 * triggered by this app (or the system). Because it also declares a filter for
 * `BOOT_COMPLETED` — a protected broadcast — the received action must be
 * validated before doing anything.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Protected broadcast: only the system can send it. Nothing to ring for.
            // TODO: reschedule pending alarms here once alarm persistence is added.
            Intent.ACTION_BOOT_COMPLETED -> return

            // The scheduled alarm firing.
            ACTION_ALARM_FIRED -> startAlarmService(context)

            // Legacy pending intents were created without an action; keep ringing for them.
            null -> startAlarmService(context)
        }
    }

    private fun startAlarmService(context: Context) {
        val intent = AlarmRingingService.createStartIntent(context)
        ContextCompat.startForegroundService(context, intent)
    }

    companion object {
        /** Action broadcast by [AlarmScheduler] when the alarm fires. */
        const val ACTION_ALARM_FIRED = "co.denis.VisualAlarm.action.ALARM_FIRED"
    }
}