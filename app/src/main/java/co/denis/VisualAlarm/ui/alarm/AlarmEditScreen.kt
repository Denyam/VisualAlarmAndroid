package co.denis.VisualAlarm.ui.alarm

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.denis.VisualAlarm.R
import co.denis.VisualAlarm.StoredAlarm
import co.denis.VisualAlarm.Hour
import co.denis.VisualAlarm.Minute
import co.denis.VisualAlarm.util.TimeFormatUtil
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditSheetContent(
    alarm: StoredAlarm?,
    onSave: (Int, Int, Boolean, Int) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var showTimePickerDialog by remember { mutableStateOf(false) }

    // Initial values from alarm or defaults
    val currentTime = Calendar.getInstance()
    var selectedHour by rememberSaveable {
        mutableStateOf(alarm?.hour?.value ?: currentTime.get(Calendar.HOUR_OF_DAY))
    }

    var selectedMinute by rememberSaveable {
        mutableStateOf(alarm?.minute?.value ?: currentTime.get(Calendar.MINUTE))
    }

    var snoozeEnabled by rememberSaveable { mutableStateOf(alarm?.snoozeEnabled ?: true) }
    var sliderPosition by remember {
        mutableStateOf(
            (alarm?.snoozeDurationMinutes ?: StoredAlarm.DEFAULT_SNOOZE_MINUTES) .toFloat()
        )
    }

    fun snoozeDuration() = sliderPosition.roundToInt()

    fun clampedSnoozeDuration(enteredDuration: String) = max(
        1,
        min(
            enteredDuration.toIntOrNull() ?: StoredAlarm.DEFAULT_SNOOZE_MINUTES,
            20)
    )

    fun snoozeString() = snoozeDuration().toString()

    val context = LocalContext.current
    // Use system 12/24 hour format setting
    val is24Hour = TimeFormatUtil.is24HourFormat(context)

    // TimePicker state for the dialog
    val timePickerState = rememberTimePickerState(
        initialHour = selectedHour,
        initialMinute = selectedMinute,
        is24Hour = is24Hour
    )

    // Sync timePickerState with selectedHour/selectedMinute
    DisposableEffect(selectedHour, selectedMinute) {
        timePickerState.hour = selectedHour
        timePickerState.minute = selectedMinute
        onDispose { }
    }

    // Use a Column with fixed button section at bottom
    // Wrap in Box with bottom alignment to pin buttons
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Content column - sits above buttons, scrollable if needed
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 88.dp) // Space for buttons (approx 56dp + padding)
                .padding(top = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = if (alarm == null) stringResource(R.string.add_alarm_sheet_title) else stringResource(R.string.edit_alarm_sheet_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            // Time picker button
            Button(
                onClick = { showTimePickerDialog = true },
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
                    Text(text = stringResource(R.string.time_label), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = TimeFormatUtil.formatTime(context, selectedHour, selectedMinute),
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
                Text(text = stringResource(R.string.snooze_label), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = snoozeEnabled,
                    onCheckedChange = { snoozeEnabled = it }
                )
            }

            // Snooze duration - animated visibility preserves layout space during animation
            AnimatedVisibility(visible = snoozeEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = stringResource(R.string.snooze_duration_label), style = MaterialTheme.typography.bodyMedium)
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
                            }
                        )
                        androidx.compose.material3.TextField(
                            value = snoozeString(),
                            onValueChange = { newValue ->
                                sliderPosition = clampedSnoozeDuration(newValue).toFloat()
                            },
                            modifier = Modifier.width(60.dp),
                            singleLine = true
                        )
                        Text(text = stringResource(R.string.snooze_duration_minutes_suffix), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Fixed action buttons at bottom - always visible, never overlap
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        Text(text = stringResource(R.string.delete_button), fontSize = 16.sp)
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
                    Text(text = stringResource(R.string.cancel_button), fontSize = 16.sp)
                }

                // Save button
                Button(
                    onClick = {
                        onSave(
                            selectedHour,
                            selectedMinute,
                            snoozeEnabled,
                            snoozeDuration()
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = if (alarm == null) stringResource(R.string.add_button) else stringResource(R.string.save_button), fontSize = 16.sp)
                }
            }
        }
    }

    // Time Picker Dialog
    if (showTimePickerDialog) {
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = { Text(stringResource(R.string.select_time_dialog_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp)) },
            text = {
                TimePicker(
                    state = timePickerState,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedHour = timePickerState.hour
                    selectedMinute = timePickerState.minute
                    showTimePickerDialog = false
                }) {
                    Text(stringResource(R.string.ok_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }
}

@Preview(
    name = "Add Alarm Sheet",
    showBackground = true,
    showSystemUi = true,
    widthDp = 360,
    heightDp = 500
)
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditSheetAddPreview() {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(true) }

    if (isVisible) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) isVisible = false } }
        ) {
            AlarmEditSheetContent(
                alarm = null,
                onSave = { _, _, _, _ -> },
                onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) isVisible = false } },
                onDelete = null
            )
        }
    }
    // Auto-show the sheet
    DisposableEffect(Unit) {
        scope.launch { sheetState.show() }
        onDispose {}
    }
}

@Preview(
    name = "Edit Alarm Sheet",
    showBackground = true,
    showSystemUi = true,
    widthDp = 360,
    heightDp = 500
)
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditSheetEditPreview() {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(true) }
    val mockAlarm = StoredAlarm(
        id = "alarm-1",
        requestCode = 123,
        hour = Hour.require(7),
        minute = Minute.require(30),
        enabled = true,
        snoozeEnabled = true,
        snoozeDurationMinutes = 10
    )

    if (isVisible) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) isVisible = false } }
        ) {
            AlarmEditSheetContent(
                alarm = mockAlarm,
                onSave = { _, _, _, _ -> },
                onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) isVisible = false } },
                onDelete = { }
            )
        }
    }
    DisposableEffect(Unit) {
        scope.launch { sheetState.show() }
        onDispose {}
    }
}

@Preview(
    name = "Edit Alarm - Snooze Disabled",
    showBackground = true,
    showSystemUi = true,
    widthDp = 360,
    heightDp = 500
)
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditSheetSnoozeDisabledPreview() {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(true) }
    val mockAlarm = StoredAlarm(
        id = "alarm-2",
        requestCode = 456,
        hour = Hour.require(22),
        minute = Minute.require(0),
        enabled = true,
        snoozeEnabled = false,
        snoozeDurationMinutes = 9
    )

    if (isVisible) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) isVisible = false } }
        ) {
            AlarmEditSheetContent(
                alarm = mockAlarm,
                onSave = { _, _, _, _ -> },
                onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) isVisible = false } },
                onDelete = { }
            )
        }
    }
    DisposableEffect(Unit) {
        scope.launch { sheetState.show() }
        onDispose {}
    }
}

@Preview(
    name = "Add Alarm - Dark Theme",
    showBackground = true,
    showSystemUi = true,
    widthDp = 360,
    heightDp = 500,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditSheetAddDarkPreview() {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(true) }

    if (isVisible) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) isVisible = false } }
        ) {
            AlarmEditSheetContent(
                alarm = null,
                onSave = { _, _, _, _ -> },
                onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) isVisible = false } },
                onDelete = null
            )
        }
    }
    DisposableEffect(Unit) {
        scope.launch { sheetState.show() }
        onDispose {}
    }
}
