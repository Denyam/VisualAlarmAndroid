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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.denis.VisualAlarm.AlarmStore
import co.denis.VisualAlarm.StoredAlarm
import co.denis.VisualAlarm.R
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    // Bottom sheet state for edit screen
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val editingAlarmId = remember { mutableStateOf<String?>(null) }
    val isSheetVisible = remember { mutableStateOf<Boolean>(false) }

    fun showEditSheet(alarm: StoredAlarm?) {
        editingAlarmId.value = alarm?.id
        isSheetVisible.value = true
        scope.launch { sheetState.show() }
    }

    fun hideSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                isSheetVisible.value = false
                editingAlarmId.value = null
            }
        }
    }

    val sheetContent = @Composable {
        val alarm = editingAlarmId.value?.let { alarmStore.getAlarmById(it) }
        AlarmEditSheetContent(
            alarm = alarm,
            onSave = { hour, minute, snoozeEnabled, snoozeDurationMinutes ->
                onSaveAlarm(editingAlarmId.value, hour, minute, snoozeEnabled, snoozeDurationMinutes)
                hideSheet()
            },
            onDismiss = { hideSheet() },
            onDelete = editingAlarmId.value?.let { id ->
                {
                    onDeleteAlarm(id)
                    hideSheet()
                }
            }
        )
    }

    NavHost(navController, startDestination = "list") {
        composable("list") {
            AlarmListContent(
                alarms = persisted,
                onToggleAlarm = onToggleAlarm,
                onDeleteAlarm = onDeleteAlarm,
                onAddAlarm = { showEditSheet(null) },
                onAlarmClick = { alarm -> showEditSheet(alarm) },
                isPermissionFlowRunning = isPermissionFlowRunning
            )
        }
    }

    // Modal bottom sheet for editing alarms - overlay on top of the list (only when visible)
    if (isSheetVisible.value) {
        ModalBottomSheet(
            onDismissRequest = { hideSheet() },
            sheetState = sheetState,
        ) {
            sheetContent()
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
                    stringResource(R.string.toast_setting_up_alarm),
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
                text = stringResource(R.string.alarms_screen_title),
                style = MaterialTheme.typography.headlineLarge
            )

//            Text(
//                text = "For Sophie",
//                style = MaterialTheme.typography.headlineMedium
//            )

            if (alarms.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_alarms_text),
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
                Text(text = stringResource(R.string.add_alarm_button), fontSize = 18.sp)
            }
        }
    }
}