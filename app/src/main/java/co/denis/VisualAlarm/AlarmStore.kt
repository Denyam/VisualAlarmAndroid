package co.denis.VisualAlarm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock

/**
 * Persisted alarm metadata stored on disk so alarms survive process death and
 * reboots. [alarms] exposes the current list as a StateFlow for reactive UI.
 */
data class StoredAlarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
)

class AlarmStore(private val context: Context) {

    private val prefs =
        context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    private val _alarms = MutableStateFlow(emptyList<StoredAlarm>())
    val alarms: StateFlow<List<StoredAlarm>> = _alarms
    private val lock = ReentrantLock()
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    init {
        _alarms.value = loadAllSync()
    }

    fun save(alarm: StoredAlarm) {
        lock.lock()
        try {
            val all = _alarms.value.toMutableList()
            all.removeIf { it.id == alarm.id }
            all.add(alarm)
            _alarms.value = all
            persist(all)
        } finally {
            lock.unlock()
        }
    }

    fun delete(id: String) {
        lock.lock()
        try {
            val filtered = _alarms.value.filterNot { it.id == id }
            _alarms.value = filtered
            persist(filtered)
        } finally {
            lock.unlock()
        }
    }

    fun clear() {
        lock.lock()
        try {
            _alarms.value = emptyList()
            persist(emptyList())
        } finally {
            lock.unlock()
        }
    }

    /** Synchronous load for initialisation or non-reactive callers. */
    fun loadAll(): List<StoredAlarm> = _alarms.value

    private fun loadAllSync(): List<StoredAlarm> {
        val jsonString = prefs.getString(KEY_ALARMS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonString)
            (0 until array.length()).map { idx ->
                val obj = array.getJSONObject(idx)
                StoredAlarm(
                    id = obj.getString("id"),
                    hour = obj.getInt("hour"),
                    minute = obj.getInt("minute"),
                    enabled = obj.optBoolean("enabled", true)
                )
            }
        } catch (e: JSONException) {
            // Log the error for debugging but don't crash - return empty list
            // to avoid persisting corrupted data
            e.printStackTrace()
            emptyList()
        }
    }

    private fun persist(alarms: List<StoredAlarm>) {
        persistScope.launch {
            val array = JSONArray()
            alarms.forEach { alarm ->
                val obj = JSONObject().apply {
                    put("id", alarm.id)
                    put("hour", alarm.hour)
                    put("minute", alarm.minute)
                    put("enabled", alarm.enabled)
                }
                array.put(obj)
            }
            val success = prefs.edit().putString(KEY_ALARMS_JSON, array.toString()).commit()
            if (!success) {
                Log.e("AlarmStore", "Failed to persist alarms")
            }
        }
    }
}

private const val PREFERENCES_FILE = "visual_alarm_store"
private const val KEY_ALARMS_JSON = "alarms_json"
