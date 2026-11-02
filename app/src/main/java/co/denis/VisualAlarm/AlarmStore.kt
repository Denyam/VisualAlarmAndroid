package co.denis.VisualAlarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted alarm metadata stored on disk so alarms survive process death and
 * reboots. [loadAll] returns the full list; [save] replaces (adds or updates) the
 * entry identified by [StoredAlarm.id].
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

    fun save(alarm: StoredAlarm) {
        val all = loadAll().toMutableList()
        // Remove any existing alarm with the same id
        all.removeIf { it.id == alarm.id }
        all.add(alarm)
        persist(all)
    }

    fun delete(id: String) {
        persist(loadAll().filterNot { it.id == id })
    }

    fun clear() {
        prefs.edit().remove(KEY_ALARMS_JSON).apply()
    }

    fun loadAll(): List<StoredAlarm> {
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(alarms: List<StoredAlarm>) {
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
        prefs.edit().putString(KEY_ALARMS_JSON, array.toString()).apply()
    }
}

private const val PREFERENCES_FILE = "visual_alarm_store"
private const val KEY_ALARMS_JSON = "alarms_json"
