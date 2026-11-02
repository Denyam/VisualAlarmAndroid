package co.denis.VisualAlarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.*

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Schedule an alarm with a unique identifier.
     *
     * The action is part of the PendingIntent's identity, so schedule and
     * cancel must use the exact same identifier for the alarm to be cancellable.
     */
    fun schedule(alarmId: String, hour: Int, minute: Int): Boolean {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the time is in the past, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        val pendingIntent = buildPendingIntent(alarmId)

        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            true
        } catch (e: SecurityException) {
            // Handle case where SCHEDULE_EXACT_ALARM permission is not granted
            false
        }
    }

    /**
     * Cancel an alarm with the given identifier.
     */
    fun cancel(alarmId: String) {
        alarmManager.cancel(buildPendingIntent(alarmId))
    }

    private fun buildPendingIntent(alarmId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRED
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        // Use alarmId in the requestCode to differentiate alarms
        val requestCode = alarmId.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val EXTRA_ALARM_ID = "alarm_id"
    }
}