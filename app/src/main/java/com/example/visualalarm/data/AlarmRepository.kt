package com.example.visualalarm.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Alarm(
    val hour: Int = 7,
    val minute: Int = 0,
    val repeatDays: Int = 0,
    val enabled: Boolean = false,
    val snoozeEnabled: Boolean = true,
)

private const val PREFS_NAME = "alarm_prefs"
private object Keys {
    const val HOUR = "alarm_hour"
    const val MINUTE = "alarm_minute"
    const val REPEAT_DAYS = "alarm_repeat_days"
    const val ENABLED = "alarm_enabled"
    const val SNOOZE_ENABLED = "alarm_snooze_enabled"
}

private val defaultAlarm = Alarm()

class AlarmRepository private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    private val _alarm = MutableStateFlow(defaultAlarm)
    val alarmFlow: Flow<Alarm> = _alarm

    companion object {
        @Volatile
        private var INSTANCE: AlarmRepository? = null

        fun getInstance(context: Context): AlarmRepository {
            return INSTANCE ?: synchronized(this) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                INSTANCE = AlarmRepository(context, prefs)
                INSTANCE!!.loadFromPrefs()
                INSTANCE!!
            }
        }
    }

    private fun loadFromPrefs() {
        val alarm = Alarm(
            hour = prefs.getInt(Keys.HOUR, 7),
            minute = prefs.getInt(Keys.MINUTE, 0),
            repeatDays = prefs.getInt(Keys.REPEAT_DAYS, 0),
            enabled = prefs.getBoolean(Keys.ENABLED, false),
            snoozeEnabled = prefs.getBoolean(Keys.SNOOZE_ENABLED, true)
        )
        _alarm.value = alarm
    }

    suspend fun getAlarm(): Alarm = _alarm.first()

    private fun saveToPrefs(alarm: Alarm) {
        prefs.edit()
            .putInt(Keys.HOUR, alarm.hour)
            .putInt(Keys.MINUTE, alarm.minute)
            .putInt(Keys.REPEAT_DAYS, alarm.repeatDays)
            .putBoolean(Keys.ENABLED, alarm.enabled)
            .putBoolean(Keys.SNOOZE_ENABLED, alarm.snoozeEnabled)
            .apply()
        _alarm.value = alarm
    }

    suspend fun saveAlarm(alarm: Alarm) {
        saveToPrefs(alarm)
    }

    suspend fun setEnabled(enabled: Boolean) {
        saveToPrefs(_alarm.value.copy(enabled = enabled))
    }

    suspend fun setTime(hour: Int, minute: Int) {
        saveToPrefs(_alarm.value.copy(hour = hour, minute = minute))
    }

    suspend fun setRepeatDays(repeatDays: Int) {
        saveToPrefs(_alarm.value.copy(repeatDays = repeatDays))
    }

    suspend fun toggleDay(dayOfWeek: Int) {
        val current = _alarm.value.repeatDays
        val bit = (dayOfWeek - 1)
        val newMask = if (current and (1 shl bit) != 0) {
            current and (1 shl bit).inv()
        } else {
            current or (1 shl bit)
        }
        setRepeatDays(newMask)
    }
}
