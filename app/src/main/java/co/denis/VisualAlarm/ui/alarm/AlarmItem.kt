package co.denis.VisualAlarm.ui.alarm

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.denis.VisualAlarm.StoredAlarm
import java.util.Locale

/** Width of the delete button (24dp icon + 2 x 16dp content padding), mirrored by the leading spacer. */
private val DeleteButtonWidth = 56.dp

@Composable
fun AlarmItem(
    alarm: StoredAlarm,
    onToggle: (String, Boolean) -> Unit,
    onTimeChange: (String, Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    isPermissionFlowRunning: Boolean
) {
    val context = LocalContext.current
    var selectedHour by rememberSaveable { mutableIntStateOf(alarm.hour.value) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(alarm.minute.value) }

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
                enabled = !isPermissionFlowRunning,
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
            enabled = !isPermissionFlowRunning
        ) {
            Text(text = "Change Time", fontSize = 18.sp)
        }

        // Enable/Disable switch
        Switch(
            checked = alarm.enabled,
            onCheckedChange = if (isPermissionFlowRunning) null else { enabled ->
                onToggle(alarm.id, enabled)
            },
            enabled = !isPermissionFlowRunning
        )
    }
}