package co.denis.VisualAlarm

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
import android.media.RingtoneManager
import android.media.Ringtone

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                RingingScreen(onStop = {
                    stopRingtone()
                    finish()
                })
            }
        }
    }

    private fun stopRingtone() {
        NotificationHelper.stopRingtone()
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
