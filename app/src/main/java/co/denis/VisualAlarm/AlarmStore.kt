package co.denis.VisualAlarm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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

@JvmInline
value class Hour private constructor(val value: Int) {
    companion object {
        fun create(hour: Int): Hour? = if (hour in 0..23) Hour(hour) else null
        fun require(hour: Int): Hour = create(hour) ?: throw IllegalArgumentException("Invalid hour: $hour")
    }
}

@JvmInline
value class Minute private constructor(val value: Int) {
    companion object {
        fun create(minute: Int): Minute? = if (minute in 0..59) Minute(minute) else null
        fun require(minute: Int): Minute = create(minute) ?: throw IllegalArgumentException("Invalid minute: $minute")
    }
}

data class StoredAlarm(
    val id: String,
    val hour: Hour,
    val minute: Minute,
    val enabled: Boolean = true
)

sealed interface LoadAlarmsResult {
    data class Success(val alarms: List<StoredAlarm>) : LoadAlarmsResult
    data class Failure(val error: String, val cause: Exception?, val rawJson: String?) : LoadAlarmsResult
}

class AlarmStore(private val context: Context) {

    private val prefs =
        context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    private val _alarms = MutableStateFlow(emptyList<StoredAlarm>())
    val alarms: StateFlow<List<StoredAlarm>> = _alarms
    private val lock = ReentrantLock()

    // Actor for serialized async persistence
    private val persistChannel = Channel<List<StoredAlarm>>(Channel.UNLIMITED)
    private val persistJob = CoroutineScope(Dispatchers.IO).launch {
        for (alarms in persistChannel) {
            persistToDisk(alarms)
        }
    }

    init {
        _alarms.value = when (val result = loadAllSync()) {
            is LoadAlarmsResult.Success -> result.alarms
            is LoadAlarmsResult.Failure -> emptyList()
        }
    }

    fun save(alarm: StoredAlarm) {
        lock.lock()
        try {
            val all = _alarms.value.toMutableList()
            all.removeIf { it.id == alarm.id }
            all.add(alarm)
            _alarms.value = all
            persistChannel.trySend(all)
        } finally {
            lock.unlock()
        }
    }

    fun delete(id: String) {
        lock.lock()
        try {
            val filtered = _alarms.value.filterNot { it.id == id }
            _alarms.value = filtered
            persistChannel.trySend(filtered)
        } finally {
            lock.unlock()
        }
    }

    fun clear() {
        lock.lock()
        try {
            _alarms.value = emptyList()
            persistChannel.trySend(emptyList())
        } finally {
            lock.unlock()
        }
    }

    /** Synchronous load for initialisation or non-reactive callers. */
    fun loadAll(): List<StoredAlarm> = _alarms.value

    /** Load alarms from disk, returning a Result to surface parse errors. */
    fun loadAllSync(): LoadAlarmsResult {
        val jsonString = prefs.getString(KEY_ALARMS_JSON, null) ?: return LoadAlarmsResult.Success(emptyList())
        return try {
            val array = JSONArray(jsonString)
            val alarms = mutableListOf<StoredAlarm>()
            for (idx in 0 until array.length()) {
                val obj = array.getJSONObject(idx)
                // Safe extraction with validation - skip invalid entries
                val id = obj.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                val hour = Hour.create(obj.optInt("hour", -1))
                val minute = Minute.create(obj.optInt("minute", -1))
                val enabled = obj.optBoolean("enabled", true)

                if (hour != null && minute != null) {
                    alarms.add(StoredAlarm(id, hour, minute, enabled))
                } else {
                    Log.w("AlarmStore", "Skipping invalid alarm at index $idx: hour=${obj.optInt("hour", -1)}, minute=${obj.optInt("minute", -1)}")
                }
            }
            LoadAlarmsResult.Success(alarms)
        } catch (e: JSONException) {
            Log.e("AlarmStore", "Failed to parse alarms JSON: $jsonString", e)
            LoadAlarmsResult.Failure("JSON parse error", e, jsonString)
        }
    }

    // Runs on Dispatchers.IO, sequentially, in send order
    private suspend fun persistToDisk(alarms: List<StoredAlarm>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            val obj = JSONObject().apply {
                put("id", alarm.id)
                put("hour", alarm.hour.value)
                put("minute", alarm.minute.value)
                put("enabled", alarm.enabled)
            }
            array.put(obj)
        }
        val success = prefs.edit().putString(KEY_ALARMS_JSON, array.toString()).commit()
        if (!success) {
            Log.e("AlarmStore", "Failed to persist alarms")
        }
    }

    /** Call when the store is no longer needed to release resources. */
    fun shutdown() {
        persistJob.cancel()
        persistChannel.close()
    }
}

private const val PREFERENCES_FILE = "visual_alarm_store"
private const val KEY_ALARMS_JSON = "alarms_json"