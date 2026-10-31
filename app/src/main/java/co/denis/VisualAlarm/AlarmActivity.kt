package co.denis.VisualAlarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {

    // Fired when the ringing service fully stops (STOP tap, safety timeout, ...):
    // finish so the screen can't keep strobing after the alarm is over.
    private val alarmStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AlarmRingingService.ACTION_ALARM_STOPPED) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ContextCompat.registerReceiver(
            this,
            alarmStoppedReceiver,
            IntentFilter(AlarmRingingService.ACTION_ALARM_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
			setShowWhenLocked(true)
		} else {
			window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
			setTurnScreenOn(true)
		} else {
			window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
		}

        // Keep the screen on for the whole alarm so the strobe never times out.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Start at full brightness; the strobe loop pumps it from here.
        setScreenBrightness(1f)

        setContent {
            MaterialTheme {
                RingingScreen(
                    onBrightFrame = { bright ->
                        setScreenBrightness(if (bright) 1f else DIM_SCREEN_BRIGHTNESS)
                    },
                    onStop = {
                        stopAlarmService()
                        finish()
                    }
                )
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

    override fun onDestroy() {
        // Hand brightness control back to the system setting.
        setScreenBrightness(DEFAULT_SCREEN_BRIGHTNESS)
        try {
            unregisterReceiver(alarmStoppedReceiver)
        } catch (e: IllegalArgumentException) {
            // Never registered (activity finished very early) — nothing to do.
        }
        super.onDestroy()
    }

    private fun setScreenBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }

    private fun stopAlarmService() {
        val intent = AlarmRingingService.createStopIntent(this)
        stopService(intent)
    }

    companion object {
        /** Follows the system brightness slider instead of a fixed value. */
        private const val DEFAULT_SCREEN_BRIGHTNESS = -1f

        /**
         * Near-off on the "black" strobe frames. Not quite 0 so the display
         * stays technically on (0 can be treated as "don't change" or trigger
         * adaptive-brightness quirks on some devices).
         */
        private const val DIM_SCREEN_BRIGHTNESS = 0.02f
    }
}

/**
 * Full-screen alarm UI that strobes between white and black while the alarm
 * rings: every [STROBE_HALF_PERIOD_MS] the background hard-cuts to the opposite
 * color and [onBrightFrame] is invoked so the activity can pump the window
 * brightness in sync (max on white frames, near-off on black frames) for a
 * stronger strobe effect.
 *
 * @param onBrightFrame true on the white/full-brightness frame, false on the
 * black/dim frame.
 * @param onStop invoked when the user taps STOP.
 */
@Composable
fun RingingScreen(onBrightFrame: (Boolean) -> Unit, onStop: () -> Unit) {
    // Hard cut, no crossfade: a strobe must flip instantly between frames.
    var brightFrame by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            onBrightFrame(brightFrame)
            delay(STROBE_HALF_PERIOD_MS)
            brightFrame = !brightFrame
        }
    }

    val background = if (brightFrame) Color.White else Color.Black
    val contentColor = if (brightFrame) Color.Black else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ALARM RINGING!",
            style = MaterialTheme.typography.headlineLarge,
            color = contentColor
        )
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = contentColor,
                contentColor = background
            )
        ) {
            Text(text = "STOP")
        }
    }
}

/**
 * Half-period of both strobes, shared by the screen and the torch so they
 * blink together: the screen shows white for 2000 ms, black for 2000 ms, and
 * the torch fires at the same cadence (used by [TorchFlasher]).
 */
const val STROBE_HALF_PERIOD_MS = 2000L
