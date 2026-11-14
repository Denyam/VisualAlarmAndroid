package co.denis.VisualAlarm.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.denis.VisualAlarm.AlarmStore
import co.denis.VisualAlarm.StoredAlarm
import co.denis.VisualAlarm.ui.alarm.AlarmItem

@Composable
fun AlarmSetScreen(
    alarmStore: AlarmStore,
    onToggleAlarm: (String, Boolean) -> Unit,
    onAlarmUpdate: (String, Boolean?, Int?, Int?) -> Unit,
    isPermissionFlowRunning: Boolean
) {
    // Observe alarms from StateFlow - triggers recomposition on change.
    val persisted by alarmStore.alarms.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Alarms",
                style = MaterialTheme.typography.headlineLarge
            )

            if (persisted.isEmpty()) {
                Text(
                    text = "No alarms set",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(persisted, key = { it.id }) { alarm ->
                        AlarmItem(
                            alarm = alarm,
                            onToggle = onToggleAlarm,
                            onTimeChange = { alarmId, hour, minute ->
                                onAlarmUpdate(alarmId, null, hour, minute)
                            },
                            isPermissionFlowRunning = isPermissionFlowRunning
                        )
                    }
                }
            }
        }

        // Loading overlay during permission flow
        if (isPermissionFlowRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
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