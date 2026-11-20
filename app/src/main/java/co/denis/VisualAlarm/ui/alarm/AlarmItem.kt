package co.denis.VisualAlarm.ui.alarm

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.denis.VisualAlarm.StoredAlarm
import java.util.Locale
import kotlin.math.roundToInt

/** Width of the delete button (24dp icon + 2 x 16dp content padding), mirrored by the leading spacer. */
private val DeleteButtonWidth = 56.dp

@Composable
fun AlarmItem(
    alarm: StoredAlarm,
    onToggle: (String, Boolean) -> Unit,
    onTimeChange: (String, Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    onSnoozeEnabledChange: (String, Boolean) -> Unit,
    onSnoozeDurationChange: (String, Int) -> Unit,
    isPermissionFlowRunning: MutableState<Boolean>
) {
    val context = LocalContext.current
    var selectedHour by rememberSaveable { mutableIntStateOf(alarm.hour.value) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(alarm.minute.value) }
    var snoozeDuration by rememberSaveable { mutableStateOf(alarm.snoozeDurationMinutes.toString()) }
    var sliderPosition by remember { mutableStateOf(alarm.snoozeDurationMinutes.toFloat()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Time label centred on the full screen width: the leading spacer
        // mirrors the delete button's width so the text's slot is symmetric.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(DeleteButtonWidth))

            Text(
                text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            // Delete alarm button
            Button(
                onClick = { onDelete(alarm.id) },
                enabled = !isPermissionFlowRunning.value,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete alarm"
                )
            }
        }

        // Time picker button
        Button(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        selectedHour = hour
                        selectedMinute = minute
                        onTimeChange(alarm.id, hour, minute)
                    },
                    selectedHour,
                    selectedMinute,
                    true
                ).show()
            },
            enabled = !isPermissionFlowRunning.value
        ) {
            Text(text = "Change Time", fontSize = 18.sp)
        }

        // Enable/Disable switch
        Switch(
            checked = alarm.enabled,
            onCheckedChange = if (isPermissionFlowRunning.value) null else { enabled ->
                onToggle(alarm.id, enabled)
            },
            enabled = !isPermissionFlowRunning.value
        )

        // Snooze toggle switch
        Switch(
            checked = alarm.snoozeEnabled,
            onCheckedChange = if (isPermissionFlowRunning.value) null else { enabled ->
                onSnoozeEnabledChange(alarm.id, enabled)
            },
            enabled = !isPermissionFlowRunning.value
        )

        // Snooze duration - slider with editable text field
        if (alarm.snoozeEnabled) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Snooze duration", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = sliderPosition,
                        valueRange = 1f..20f,
                        onValueChange = { newValue ->
                            sliderPosition = newValue
                            val minutes = newValue.roundToInt()
                            snoozeDuration = minutes.toString()
                            onSnoozeDurationChange(alarm.id, minutes)
                        },
                        enabled = !isPermissionFlowRunning.value
                    )
                    TextField(
                        value = snoozeDuration,
                        onValueChange = { newValue ->
                            snoozeDuration = newValue
                            val minutes = newValue.toIntOrNull() ?: sliderPosition.roundToInt()
                            if (minutes in 1..20) {
                                sliderPosition = minutes.toFloat()
                                onSnoozeDurationChange(alarm.id, minutes)
                            }
                        },
                        modifier = Modifier.width(60.dp),
                        singleLine = true,
                        enabled = !isPermissionFlowRunning.value
                    )
                    Text(text = " min", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}