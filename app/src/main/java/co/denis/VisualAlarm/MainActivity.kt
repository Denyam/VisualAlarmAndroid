package co.denis.VisualAlarm

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import co.denis.VisualAlarm.AlarmScheduler
import co.denis.VisualAlarm.AlarmStore
import co.denis.VisualAlarm.Hour
import co.denis.VisualAlarm.Minute
import co.denis.VisualAlarm.StoredAlarm
import co.denis.VisualAlarm.ui.alarm.AlarmItem
import co.denis.VisualAlarm.ui.alarm.AlarmSetScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private lateinit var permissions: AlarmPermissions
    private val alarmStore: AlarmStore by lazy { (applicationContext as AlarmApplication).alarmStore }

    // Alarm pending to be scheduled via the permission flow (when user sets a new alarm or toggles switch ON)
    private var pendingAlarmToEnable: StoredAlarm? = null

    // Loop guard: settings screens auto-opened since the last explicit user
    // action (launch or SET ALARM tap). Each step gets one automatic attempt;
    // if the user comes back without granting, we stop instead of
    // ping-ponging back to the same screen.
    private val autoLaunchedSettings = mutableSetOf<String>()
    private var awaitingSettingsReturn = false
    private var launchFlowDone = false

    // Tracks if permission flow is running (for UI loading indicator)
    var isPermissionFlowRunning = mutableStateOf(false)
        private set

    private val postNotificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            continuePermissionFlow()
        } else {
            // Don't re-prompt; wait for the next explicit "SET ALARM" tap.
            finishFlowIncomplete(getString(R.string.notification_permission_denied))
        }
    }

    // CAMERA powers the torch strobe but is best-effort: we ask at most once
    // per explicit user action, and a denial never blocks the alarm flow.
    private var cameraPermissionRequested = false

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        continuePermissionFlow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions = AlarmPermissions(this)
        setContent {
            MaterialTheme {
                AlarmSetScreen(
                    alarmStore = alarmStore,
                    onToggleAlarm = { alarmId, enabled -> onToggleAlarm(alarmId, enabled) },
                    onAlarmUpdate = { alarmId, enabled, hour, minute -> onAlarmUpdate(alarmId, enabled, hour, minute) },
                    isPermissionFlowRunning = isPermissionFlowRunning.value
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!launchFlowDone) {
            // First launch: walk the user through the permission wizard.
            launchFlowDone = true
            continuePermissionFlow()
        } else if (awaitingSettingsReturn) {
            awaitingSettingsReturn = false
            continuePermissionFlow()
        } else if (permissions.allRequiredPermissionsGranted()) {
            // App resumed with permissions already granted - ensure all enabled alarms are scheduled
            scheduleAllEnabledAlarms()
        }
    }

    private fun continuePermissionFlow() {
        if (!permissions.hasPostNotificationsPermission()) {
            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!permissions.canScheduleExactAlarms()) {
            launchSettings(permissions.exactAlarmSettingsIntent())
            return
        }
        if (!permissions.canUseFullScreenIntent()) {
            launchSettings(permissions.fullScreenIntentSettingsIntent())
            return
        }
        if (!permissions.hasCameraPermission() && !cameraPermissionRequested) {
            // Best-effort: the callback continues the flow whether granted or not.
            cameraPermissionRequested = true
            cameraLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        // All permissions granted - schedule the pending alarm
        schedulePendingAlarm()
        // Also schedule any other enabled alarms from store (e.g. after process death)
        scheduleAllEnabledAlarms()
    }

    /**
     * Schedules all enabled alarms from the store.
     * Used to re-arm alarms after process death or when permissions become granted.
     */
    private fun scheduleAllEnabledAlarms() {
        val alarms = alarmStore.loadAll().filter { it.enabled }
        for (alarm in alarms) {
            tryScheduleAlarm(alarm)
        }
    }

    private fun launchSettings(intent: Intent) {
        val action = intent.action ?: ""
        if (autoLaunchedSettings.contains(action)) {
            // We already opened this exact screen once and it's still missing;
            // stop instead of ping-ponging back to Settings.
            finishFlowIncomplete(getString(R.string.permission_still_needed))
            return
        }
        try {
            autoLaunchedSettings.add(action)
            awaitingSettingsReturn = true
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // OEM skin without this settings screen: skip this step.
            autoLaunchedSettings.remove(action)
            awaitingSettingsReturn = false
            continuePermissionFlow()
        }
    }

    private fun finishFlowIncomplete(message: String) {
        autoLaunchedSettings.clear()
        clearPendingAlarm()
        // If we were trying to enable an alarm, revert it to disabled
        pendingAlarmToEnable?.let { alarm ->
            alarmStore.save(alarm.copy(enabled = false))
        }
        pendingAlarmToEnable = null
        lifecycleScope.launch {
            isPermissionFlowRunning.value = false
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Schedules the alarm in [pendingAlarmToEnable] via [AlarmScheduler] and persists
     * the result so it survives reboots and process death.
     * On failure, reverts the alarm to disabled state.
     */
    private fun schedulePendingAlarm() {
        val alarm = pendingAlarmToEnable ?: return
        pendingAlarmToEnable = null

        val success = tryScheduleAlarm(alarm)
        if (success) {
            alarmStore.save(alarm)
        } else {
            // Revert to disabled state
            alarmStore.save(alarm.copy(enabled = false))
            Toast.makeText(
                this,
                "Failed to set alarm. Check permissions.",
                Toast.LENGTH_SHORT
            ).show()
        }
        lifecycleScope.launch {
            isPermissionFlowRunning.value = false
        }
    }

    private fun clearPendingAlarm() {
        pendingAlarmToEnable = null
    }

    /**
     * Attempts to schedule an alarm.
     * Returns true if successful.
     */
    private fun tryScheduleAlarm(alarm: StoredAlarm): Boolean =
        AlarmScheduler(this).schedule(alarm)

    /**
     * Unified method to update alarm state (enabled, hour, minute) by ID.
     * Handles permission flow when enabling, cancels when disabling.
     */
    private fun onAlarmUpdate(
        alarmId: String,
        newEnabled: Boolean? = null,
        newHour: Int? = null,
        newMinute: Int? = null
    ) {
        val existingAlarm = alarmStore.getAlarmById(alarmId)
            ?: return // Alarm not found, nothing to update

        val updatedAlarm = existingAlarm.copy(
            enabled = if (newEnabled != null) newEnabled else existingAlarm.enabled,
            hour = if (newHour != null) Hour.require(newHour) else existingAlarm.hour,
            minute = if (newMinute != null) Minute.require(newMinute) else existingAlarm.minute
        )
        alarmStore.save(updatedAlarm)

        // If alarm was enabled before, cancel old schedule
        if (existingAlarm.enabled) {
            AlarmScheduler(this).cancel(existingAlarm)
        }

        // If now enabled, run permission flow to schedule
        if (updatedAlarm.enabled) {
            pendingAlarmToEnable = updatedAlarm
            autoLaunchedSettings.clear()
            cameraPermissionRequested = false
            isPermissionFlowRunning.value = true
            lifecycleScope.launch { continuePermissionFlow() }
        } else {
            // Just disabled - no permissions needed
            pendingAlarmToEnable = null
            isPermissionFlowRunning.value = false
        }
    }

    private fun onToggleAlarm(alarmId: String, enabled: Boolean) {
        onAlarmUpdate(alarmId, newEnabled = enabled)
    }

}
