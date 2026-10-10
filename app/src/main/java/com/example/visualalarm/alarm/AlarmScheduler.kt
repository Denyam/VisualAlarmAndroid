package com.example.visualalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.visualalarm.data.Alarm
import java.util.Calendar
import java.util.TimeZone

/**
 * Schedules and cancels exact alarms using AlarmManager.
 * Pure next-trigger-time calculation for testability.
 */
class AlarmScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
) {

    private val ALARM_REQUEST_CODE = 1001
    private val ALARM_ACTION = "com.example.visualalarm.ALARM_TRIGGER"

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ALARM_ACTION
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
    }

    /**
     * Calculate the next trigger time for the given alarm.
     * Returns epoch milliseconds, or null if alarm is disabled.
     */
    fun calculateNextTrigger(alarm: Alarm): Long? {
        if (!alarm.enabled) return null

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
        cal.set(Calendar.MINUTE, alarm.minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val baseTime = cal.timeInMillis

        if (alarm.repeatDays == 0) {
            // One-shot: if time already passed today, trigger tomorrow
            return if (baseTime <= now) baseTime + 24 * 60 * 60 * 1000 else baseTime
        }

        // Repeating: find next enabled day
        val todayDayOfWeek = getDayOfWeekIndex(now) // 0=Mon...6=Sun
        val baseDayOfWeek = getDayOfWeekIndex(baseTime)

        // Check if today is enabled and time hasn't passed
        val todayBit = 1 shl todayDayOfWeek
        if ((alarm.repeatDays and todayBit) != 0 && baseTime > now) {
            return baseTime
        }

        // Search forward up to 7 days
        for (i in 1..7) {
            val checkDay = (todayDayOfWeek + i) % 7
            val checkBit = 1 shl checkDay
            if ((alarm.repeatDays and checkBit) != 0) {
                val daysToAdd = if (checkDay >= todayDayOfWeek) {
                    checkDay - todayDayOfWeek
                } else {
                    7 - todayDayOfWeek + checkDay
                }
                return baseTime + daysToAdd * 24L * 60 * 60 * 1000
            }
        }

        // No days selected (shouldn't happen if repeatDays != 0), fallback to tomorrow
        return baseTime + 24 * 60 * 60 * 1000
    }

    /**
     * Schedule the alarm if enabled, cancel if disabled.
     */
    fun schedule(alarm: Alarm) {
        if (!alarm.enabled) {
            cancel()
            return
        }

        val triggerAt = calculateNextTrigger(alarm) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    /**
     * Cancel any pending alarm.
     */
    fun cancel() {
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Schedule a one-shot snooze alarm (5 minutes from now).
     */
    fun scheduleSnooze() {
        val triggerAt = System.currentTimeMillis() + 5 * 60 * 1000
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.visualalarm.SNOOZE_TRIGGER"
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                snoozePendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                snoozePendingIntent
            )
        }
    }

    private fun getDayOfWeekIndex(timeMs: Long): Int {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = timeMs
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Convert: Calendar.SUNDAY=1...SATURDAY=7 -> 0=Mon...6=Sun
        return (dayOfWeek + 5) % 7
    }
}