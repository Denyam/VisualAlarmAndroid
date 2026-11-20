package co.denis.VisualAlarm.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.denis.VisualAlarm.AlarmStore
import co.denis.VisualAlarm.StoredAlarm
import androidx.compose.ui.platform.LocalContext

@Composable
fun AlarmListScreen(
    alarmStore: AlarmStore,
    onToggleAlarm: (String, Boolean) -> Unit,
    onDeleteAlarm: (String) -> Unit,
    onSaveAlarm: (String?, Int, Int, Boolean, Int) -> Unit,
    isPermissionFlowRunning: MutableState<Boolean>
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    // Observe alarms from StateFlow - triggers recomposition on change.
    val persisted by alarmStore.alarms.collectAsState()

    NavHost(navController, startDestination = "list") {
        composable("list") {
            AlarmListContent(
                alarms = persisted,
                onToggleAlarm = onToggleAlarm,
                onDeleteAlarm = onDeleteAlarm,
                onAddAlarm = {
                    navController.navigate("edit/null")
                },
                onAlarmClick = { alarm ->
                    navController.navigate("edit/${alarm.id}")
                },
                isPermissionFlowRunning = isPermissionFlowRunning
            )
        }
        composable(
            route = "edit/{alarmId}",
            arguments = listOf(navArgument("alarmId") { type = NavType.StringType })
        ) { backStackEntry ->
            val alarmId = backStackEntry.arguments?.getString("alarmId")?.takeIf { it != "null" }
            val alarm = alarmId?.let { alarmStore.getAlarmById(it) }
            AlarmEditScreen(
                alarm = alarm,
                onSave = { hour, minute, snoozeEnabled, snoozeDurationMinutes ->
                    onSaveAlarm(alarmId, hour, minute, snoozeEnabled, snoozeDurationMinutes)
                    navController.popBackStack()
                },
                onDismiss = { navController.popBackStack() },
                onDelete = alarmId?.let { id ->
                    {
                        onDeleteAlarm(id)
                        navController.popBackStack()
                    }
                }
            )
        }
    }

    // Loading overlay during permission flow
    if (isPermissionFlowRunning.value) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                // Consume all touch input so taps can't fall through to the
                // alarm list / buttons underneath while the flow is running.
                .pointerInput(Unit) { detectTapGestures { } },
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

@Composable
fun AlarmListContent(
    alarms: List<StoredAlarm>,
    onToggleAlarm: (String, Boolean) -> Unit,
    onDeleteAlarm: (String) -> Unit,
    onAddAlarm: () -> Unit,
    onAlarmClick: (StoredAlarm) -> Unit,
    isPermissionFlowRunning: MutableState<Boolean>
) {
    val context = LocalContext.current

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

            Text(
                text = "For Sophie",
                style = MaterialTheme.typography.headlineMedium
            )

            if (alarms.isEmpty()) {
                Text(
                    text = "No alarms set",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmItem(
                            alarm = alarm,
                            onToggle = onToggleAlarm,
                            onDelete = onDeleteAlarm,
                            onClick = { onAlarmClick(alarm) },
                            enabled = !isPermissionFlowRunning.value
                        )
                    }
                }
            }

            // Add a new alarm
            Button(
                onClick = onAddAlarm,
                enabled = !isPermissionFlowRunning.value,
                // Keep the button above the system gesture/navigation bar
                modifier = Modifier.navigationBarsPadding()
            ) {
                Text(text = "Add Alarm", fontSize = 18.sp)
            }
        }
    }
}