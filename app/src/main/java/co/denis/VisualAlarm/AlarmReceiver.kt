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

    override fun onReceive(context: Context, broadcastIntent: Intent) {
        when (broadcastIntent.action) {
            // Protected broadcast: only the system can send it. Nothing to ring for.
            Intent.ACTION_BOOT_COMPLETED -> rearmAlarmsAfterBoot(context)

            // The scheduled alarm firing.
            ACTION_ALARM_FIRED -> startAlarmServiceAndClear(context, broadcastIntent)

            // Legacy pending intents were created without an action; keep ringing for them.
            null -> startAlarmService(context)
        }
    }

    /**
     * Re-arms every enabled, saved alarm after a reboot. Alarms must be stored in
     * [AlarmStore] to survive process death anyway, so this keeps them working
     * past `BOOT_COMPLETED`.
     */
    private fun rearmAlarmsAfterBoot(context: Context) {
        val store = (context.applicationContext as AlarmApplication).alarmStore
        val alarms = store.loadAll().filter { it.enabled }
        for (alarm in alarms) {
            val success = AlarmScheduler(context).schedule(alarm)
            if (!success) continue
        }
    }

    /**
     * The scheduled alarm has fired. Start the ring service then update the
     * stored alarm to disabled (enabled = false) so it survives reboots but
     * won't re-arm automatically.
     */
    private fun startAlarmServiceAndClear(context: Context, broadcastIntent: Intent) {
        startAlarmService(context)
        val alarmId = broadcastIntent.getStringExtra(EXTRA_ALARM_ID)
        if (alarmId != null) {
            val store = (context.applicationContext as AlarmApplication).alarmStore
            store.loadAll().firstOrNull { it.id == alarmId }?.let { alarm ->
                store.save(alarm.copy(enabled = false))
            }
        }
    }

    private fun startAlarmService(context: Context) {
        val intent = AlarmRingingService.createStartIntent(context)
        ContextCompat.startForegroundService(context, intent)
    }

    companion object {
        /** Action broadcast by [AlarmScheduler] when the alarm fires. */
        const val ACTION_ALARM_FIRED = "co.denis.VisualAlarm.action.ALARM_FIRED"
        private const val EXTRA_ALARM_ID = "alarm_id"
    }
}
