package com.example.visualalarm.alarm

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visualalarm.R
import com.example.visualalarm.ui.theme.VisualAlarmTheme

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show on lock screen, turn screen on, keep screen on
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setContent {
            VisualAlarmTheme {
                AlarmScreen(onDismiss = { finish() }, onSnooze = { snoozeAndFinish() })
            }
        }
    }

    private fun snoozeAndFinish() {
        // Start snooze via AlarmScheduler
        val scheduler = AlarmScheduler(this)
        scheduler.scheduleSnooze()
        finish()
    }
}

@Composable
fun AlarmScreen(
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val context = LocalContext.current
    val time = remember { mutableStateOf("") }
    
    // Update time display
    androidx.compose.runtime.LaunchedEffect(true) {
        while (true) {
            val cal = java.util.Calendar.getInstance()
            time.value = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            kotlinx.coroutines.delay(1000)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(32.dp)
        ) {
            Text(
                text = context.getString(R.string.alarm_ringing),
                fontSize = 24.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = time.value,
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )

            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)
            ) {
                Button(
                    onClick = onSnooze,
                    modifier = Modifier.width(140.dp).height(56.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(text = context.getString(R.string.snooze), fontSize = 18.sp)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.width(140.dp).height(56.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(text = context.getString(R.string.dismiss), fontSize = 18.sp)
                }
            }
        }
    }
}
