package co.denis.VisualAlarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                RingingScreen(onStop = {
                    stopAlarmService()
                    finish()
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep the active Intent reference current.
        setIntent(intent)
        // The system may reuse an existing instance instead of creating a new one,
        // so re-arm the ringtone to make sure the alarm is still audible.
        // The service handles the ringtone now, so no need to restart it here.
    }

    private fun stopAlarmService() {
        val intent = AlarmRingingService.createStopIntent(this)
        stopService(intent)
    }
}

@Composable
fun RingingScreen(onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "ALARM RINGING!", style = MaterialTheme.typography.headlineLarge)
        Button(onClick = onStop) {
            Text(text = "STOP")
        }
    }
}