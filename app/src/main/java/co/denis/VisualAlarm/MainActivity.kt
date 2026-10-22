package co.denis.VisualAlarm

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AlarmSetScreen(
                    onSetAlarm = { hour, minute ->
                        val scheduler = AlarmScheduler(this)
                        val success = scheduler.schedule(hour, minute)

                        val text = if (success)
                            "Alarm set for $hour:$minute"
                            else
                                "Failed to set alarm. Check permissions."

                        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
//                        if (success) {
//
//                        } else {
//                            Toast.makeText(this, , Toast.LENGTH_SHORT).show()
//                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AlarmSetScreen(onSetAlarm: (Int, Int) -> Unit) {
    val context = LocalContext.current
    var selectedHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Set Alarm", 
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
            style = MaterialTheme.typography.displayLarge
        )
        Button(onClick = {
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
        }) {
            Text(text = "Change Time")
        }
        Button(onClick = { onSetAlarm(selectedHour, selectedMinute) }) {
            Text(text = "SET ALARM")
        }
    }
}
