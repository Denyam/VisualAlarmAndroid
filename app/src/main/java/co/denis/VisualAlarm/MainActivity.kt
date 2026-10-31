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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var permissions: AlarmPermissions

    // Time of the alarm the user asked for; non-null while the permission
    // chain is still running for a "SET ALARM" tap.
    private var pendingHour: Int? = null
    private var pendingMinute: Int? = null

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
                    onSetAlarm = { hour, minute ->
                        onSetAlarmClicked(hour, minute)
                    }
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

    private fun onSetAlarmClicked(hour: Int, minute: Int) {
        pendingHour = hour
        pendingMinute = minute
        // An explicit tap re-arms every settings step for one more automatic attempt.
        autoLaunchedSettings.clear()
        cameraPermissionRequested = false
        continuePermissionFlow()
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
        schedulePendingAlarmIfAny()
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
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun schedulePendingAlarmIfAny() {
        val hour = pendingHour ?: return
        val minute = pendingMinute ?: return
        clearPendingAlarm()

        val success = AlarmScheduler(this).schedule(hour, minute)
        val text = if (success)
            "Alarm set for $hour:$minute"
        else
            "Failed to set alarm. Check permissions."

        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun clearPendingAlarm() {
        pendingHour = null
        pendingMinute = null
    }
}

@Composable
fun AlarmSetScreen(onSetAlarm: (Int, Int) -> Unit) {
    val context = LocalContext.current
    var selectedHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Set Alarm",
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
                },
                selectedHour,
                selectedMinute,
                true
            ).show()
        }) {
            Text(text = "Change Time")
        }
        Button(onClick = { onSetAlarm(selectedHour, selectedMinute) }) {
            Text(text = "SET ALARM")
        }
    }
}
