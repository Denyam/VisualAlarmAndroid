package co.denis.VisualAlarm

import android.app.Application

class AlarmApplication : Application() {
    @Volatile
    private var _alarmStore: AlarmStore? = null
    val alarmStore: AlarmStore
        get() = _alarmStore ?: synchronized(this) {
            _alarmStore ?: AlarmStore(this).also { _alarmStore = it }
        }
}