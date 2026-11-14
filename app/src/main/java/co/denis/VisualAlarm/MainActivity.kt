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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import co.denis.VisualAlarm.AlarmScheduler
import co.denis.VisualAlarm.Hour
import co.denis.VisualAlarm.Minute
import co.denis.VisualAlarm.StoredAlarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext
import java.time.Duration
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
                    onToggleAlarm = { enabled -> onToggleAlarm(enabled) },
                    onAlarmUpdate = { enabled, hour, minute -> onAlarmUpdate(enabled, hour, minute) },
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
        lifecycleScope.launch {
            isPermissionFlowRunning.value = false
        }
    }

    private fun clearPendingAlarm() {
        pendingAlarmToEnable = null
    }

    /**
     * Unified method to update alarm state (enabled, hour, minute).
     * Handles permission flow when enabling, cancels when disabling.
     */
    private fun onAlarmUpdate(
        newEnabled: Boolean? = null,
        newHour: Int? = null,
        newMinute: Int? = null
    ) {
        val alarm = alarmStore.loadAll().firstOrNull() ?: return

        // Build updated alarm
        val updatedAlarm = alarm.copy(
            enabled = if (newEnabled != null) newEnabled else alarm.enabled,
            hour = if (newHour != null) Hour.require(newHour) else alarm.hour,
            minute = if (newMinute != null) Minute.require(newMinute) else alarm.minute
        )

        // Save immediately
        alarmStore.save(updatedAlarm)

        // If alarm was enabled before, cancel old schedule
        if (alarm.enabled) {
            AlarmScheduler(this).cancel(alarm)
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

    private fun onToggleAlarm(enabled: Boolean) {
        onAlarmUpdate(newEnabled = enabled)
    }
}

@Composable
private fun AlarmSetScreen(
    alarmStore: AlarmStore,
    onToggleAlarm: (Boolean) -> Unit,
    onAlarmUpdate: (Boolean?, Int?, Int?) -> Unit,
    isPermissionFlowRunning: Boolean
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

    Box(modifier = Modifier.fillMaxSize()) {
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

            Button(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            selectedHour = hour
                            selectedMinute = minute
                            onAlarmUpdate(null, hour, minute)
                        },
                        selectedHour,
                        selectedMinute,
                        true
                    ).show()
                },
                enabled = !isPermissionFlowRunning
            ) {
                Text(text = "Change Time")
            }

            androidx.compose.material3.Switch(
                checked = isEnabled,
                onCheckedChange = if (isPermissionFlowRunning) null else { enabled ->
                    if (enabled && !hasAlarm) {
                        // Create new alarm with current time and enable it
                        onAlarmUpdate(true, selectedHour, selectedMinute)
                    } else {
                        onAlarmUpdate(enabled, null, null)
                    }
                },
                enabled = !isPermissionFlowRunning
            )
        }

        // Loading overlay during permission flow
        if (isPermissionFlowRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Setting up alarm...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}