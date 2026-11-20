package co.denis.VisualAlarm.ui.alarm

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import co.denis.VisualAlarm.StoredAlarm
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarm: StoredAlarm?,
    onSave: (Int, Int, Boolean, Int) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Initial values from alarm or defaults
    var selectedHour by rememberSaveable { mutableIntStateOf(alarm?.hour?.value ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(alarm?.minute?.value ?: Calendar.getInstance().get(Calendar.MINUTE)) }
    var snoozeEnabled by rememberSaveable { mutableStateOf(alarm?.snoozeEnabled ?: true) }
    var snoozeDuration by rememberSaveable { mutableStateOf(alarm?.snoozeDurationMinutes?.toString() ?: "9") }
    var sliderPosition by remember { mutableStateOf(alarm?.snoozeDurationMinutes?.toFloat() ?: 9f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp, 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                )
            }

            // Title
            Text(
                text = if (alarm == null) "Add Alarm" else "Edit Alarm",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            // Time picker button
            Button(
                onClick = {
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
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Time", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            // Snooze toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Snooze", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = snoozeEnabled,
                    onCheckedChange = { snoozeEnabled = it }
                )
            }

            // Snooze duration - slider with editable text field
            if (snoozeEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
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
                            }
                        )
                        TextField(
                            value = snoozeDuration,
                            onValueChange = { newValue ->
                                val minutes = newValue.toIntOrNull() ?: sliderPosition.roundToInt()
                                val clampedMinutes = max(1, min(minutes, 20))

                                snoozeDuration = clampedMinutes.toString()
                                sliderPosition = clampedMinutes.toFloat()
                            },
                            modifier = Modifier.width(60.dp),
                            singleLine = true
                        )
                        Text(text = " min", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Delete button (only for editing existing alarm)
                onDelete?.let { deleteAction ->
                    Button(
                        onClick = {
                            deleteAction()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(text = "Delete", fontSize = 16.sp)
                    }
                }

                // Cancel button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(text = "Cancel", fontSize = 16.sp)
                }

                // Save button
                Button(
                    onClick = {
                        onSave(
                            selectedHour,
                            selectedMinute,
                            snoozeEnabled,
                            snoozeDuration.toIntOrNull() ?: sliderPosition.roundToInt()
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = if (alarm == null) "Add" else "Save", fontSize = 16.sp)
                }
            }
        }
    }
}