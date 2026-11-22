package co.denis.VisualAlarm.util

import android.content.Context
import android.text.format.DateFormat

object TimeFormatUtil {
    /**
     * Returns whether the system is configured for 24-hour time format.
     */
    fun is24HourFormat(context: Context): Boolean {
        return DateFormat.is24HourFormat(context)
    }

    /**
     * Formats the given hour and minute according to the system's time format preference.
     * Uses the system's default time format which automatically respects 12/24 hour setting.
     */
    fun formatTime(context: Context, hour: Int, minute: Int): String {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
        }
        // getTimeFormat returns a formatter that automatically respects the system 12/24 hour setting
        val timeFormat = DateFormat.getTimeFormat(context)
        return timeFormat.format(calendar.time)
    }
}
