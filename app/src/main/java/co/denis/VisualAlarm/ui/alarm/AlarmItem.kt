package co.denis.VisualAlarm.ui.alarm

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.denis.VisualAlarm.StoredAlarm
import java.util.Locale

@Composable
fun AlarmItem(
    alarm: StoredAlarm,
    onToggle: (String, Boolean) -> Unit,
    onTimeChange: (String, Int, Int) -> Unit,
    isPermissionFlowRunning: Boolean
) {
    val context = LocalContext.current
    var selectedHour by rememberSaveable { mutableIntStateOf(alarm.hour.value) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(alarm.minute.value) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
            style = MaterialTheme.typography.displayLarge
        )

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