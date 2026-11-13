package co.denis.VisualAlarm

import android.Manifest
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import co.denis.VisualAlarm.AlarmScheduler
import co.denis.VisualAlarm.Hour
import co.denis.VisualAlarm.Minute
import co.denis.VisualAlarm.StoredAlarm
import java.util.Calendar
import java.util.Locale

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
                    onToggleAlarm = { enabled -> onToggleAlarm(enabled) }
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

        val success = AlarmScheduler(this).schedule(alarm)
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
    }

    private fun clearPendingAlarm() {
        pendingAlarmToEnable = null
    }

    private fun onToggleAlarm(enabled: Boolean) {
        val alarm = alarmStore.loadAll().firstOrNull() ?: return
        val updatedAlarm = alarm.copy(enabled = enabled)

        // Save first (persists the new state)
        alarmStore.save(updatedAlarm)

        if (enabled) {
            // Need permissions to schedule - store alarm and run permission flow
            pendingAlarmToEnable = updatedAlarm
            autoLaunchedSettings.clear()
            cameraPermissionRequested = false
            continuePermissionFlow()
        } else {
            // Disabling doesn't need permissions, just cancel
            AlarmScheduler(this).cancel(alarm)
            pendingAlarmToEnable = null
        }
    }
}

@Composable
private fun AlarmSetScreen(
    alarmStore: AlarmStore,
    onToggleAlarm: (Boolean) -> Unit
) {
    // Observe alarms from StateFlow - triggers recomposition on change.
    val persisted by alarmStore.alarms.collectAsState()

    // Get context for system dialogs and string resources
    val context = LocalContext.current

    // If we have a saved alarm, pre-fill the UI with its time.
    val hasAlarm = persisted.isNotEmpty()

    // Read-only derived display time from persisted alarm (or current time as fallback)
    val displayTime = derivedStateOf {
        persisted.firstOrNull()?.let { it.hour.value to it.minute.value }
            ?: (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) to Calendar.getInstance().get(Calendar.MINUTE))
    }

    // Current enabled state from persisted alarm
    val isEnabled = persisted.firstOrNull()?.enabled ?: false

    // Mutable editor state — initialized once from persisted, then preserved across recompositions
    var selectedHour by rememberSaveable { mutableIntStateOf(displayTime.value.first) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(displayTime.value.second) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (hasAlarm) {
                if (isEnabled) "Alarm ON" else "Alarm OFF"
            } else {
                "Alarm OFF"
            },
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
            style = MaterialTheme.typography.displayLarge
        )

        Button(onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    selectedHour = hour
                    selectedMinute = minute
                    // Update the stored alarm with new time
                    persisted.firstOrNull()?.let { alarm ->
                        // Disarm former alarm (cancel system alarm)
                        AlarmScheduler(context).cancel(alarm)
                        // Save with new time, preserving enabled state
                        val updatedAlarm = alarm.copy(
                            hour = Hour.require(hour),
                            minute = Minute.require(minute)
                        )
                        alarmStore.save(updatedAlarm)
                        // If alarm was enabled, re-arm with new time
                        if (updatedAlarm.enabled) {
                            val success = AlarmScheduler(context).schedule(updatedAlarm)
                            if (!success) {
                                // Revert to disabled on failure
                                alarmStore.save(updatedAlarm.copy(enabled = false))
                                Toast.makeText(
                                    context,
                                    "Failed to set alarm. Check permissions.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                },
                selectedHour,
                selectedMinute,
                true
            ).show()
        }) {
            Text(text = "Change Time")
        }

        androidx.compose.material3.Switch(
            checked = isEnabled,
            onCheckedChange = { enabled ->
                if (enabled && !hasAlarm) {
                    // Create new alarm with current time and enable it
                    val alarm = StoredAlarm(
                        id = java.util.UUID.randomUUID().toString(),
                        hour = Hour.require(selectedHour),
                        minute = Minute.require(selectedMinute),
                        enabled = true
                    )
                    alarmStore.save(alarm)
                    onToggleAlarm(true)
                } else {
                    onToggleAlarm(enabled)
                }
            }
        )
    }
}