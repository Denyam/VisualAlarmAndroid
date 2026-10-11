package com.example.visualalarm.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visualalarm.R
import com.example.visualalarm.data.Alarm
import com.example.visualalarm.data.AlarmRepository
import kotlinx.coroutines.launch

@Composable
fun AlarmScreen(
    repository: AlarmRepository,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val alarmState by repository.alarmFlow.collectAsState(initial = Alarm())
    val alarm: Alarm = alarmState
    val showTimePicker = remember { mutableStateOf(false) }
    val showPermissionDialog = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val daysOfWeek = listOf(
        Pair(context.getString(R.string.monday), 1),
        Pair(context.getString(R.string.tuesday), 2),
        Pair(context.getString(R.string.wednesday), 3),
        Pair(context.getString(R.string.thursday), 4),
        Pair(context.getString(R.string.friday), 5),
        Pair(context.getString(R.string.saturday), 6),
        Pair(context.getString(R.string.sunday), 7),
    )

    // Create vertical arrangement with spacing
    val verticalArrangement = Arrangement.spacedBy(24.dp)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Time display
        Text(
            text = String.format("%02d:%02d", alarm.hour, alarm.minute),
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
        )

        // Enable switch
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (alarm.enabled) context.getString(R.string.alarm_enabled) else context.getString(R.string.alarm_disabled),
                fontSize = 18.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
            androidx.compose.material3.Switch(
                checked = alarm.enabled,
                onCheckedChange = { enabled ->
                    coroutineScope.launch { repository.setEnabled(enabled) }
                    if (enabled && !notificationPermissionGranted) {
                        showPermissionDialog.value = true
                    }
                }
            )
        }

        // Set time button
        Button(
            onClick = { showTimePicker.value = true },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)
        ) {
            Text(text = context.getString(R.string.set_time), fontSize = 18.sp)
        }

        // Repeat days label
        Text(
            text = context.getString(R.string.repeat_days),
            fontSize = 16.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        // Repeat days row with manual spacing
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            daysOfWeek.forEach { (label, dayIndex) ->
                val isSelected = (alarm.repeatDays and (1 shl (dayIndex - 1))) != 0
                Text(
                    text = label,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth(1f / 7f)
                        .padding(16.dp)
                        .background(
                            if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                            else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { coroutineScope.launch { repository.toggleDay(dayIndex) } }
                )
            }
        }

        // Snooze indicator (always on in v1)
        if (alarm.snoozeEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = context.getString(R.string.snooze),
                    fontSize = 16.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = context.getString(R.string.snooze_duration),
                    fontSize = 16.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // Permission warning if needed
        if (!notificationPermissionGranted && alarm.enabled) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = context.getString(R.string.notification_permission_denied),
                    fontSize = 14.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    // Time Picker placeholder
    if (showTimePicker.value) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Time picker - TODO: implement proper time picker",
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { showTimePicker.value = false }) {
                        Text(text = context.getString(android.R.string.cancel))
                    }
                    Button(onClick = { showTimePicker.value = false }) {
                        Text(text = context.getString(android.R.string.ok))
                    }
                }
            }
        }
    }

    // Permission dialog placeholder
    if (showPermissionDialog.value) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = context.getString(R.string.permission_notification_rationale),
                    fontSize = 16.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { showPermissionDialog.value = false }) {
                        Text(text = context.getString(android.R.string.cancel))
                    }
                    Button(onClick = {
                        onRequestNotificationPermission()
                        showPermissionDialog.value = false
                    }) {
                        Text(text = context.getString(android.R.string.ok))
                    }
                }
            }
        }
    }
}
