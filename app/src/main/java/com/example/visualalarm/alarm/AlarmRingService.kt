package com.example.visualalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.visualalarm.R
import java.io.IOException

class AlarmRingService : Service() {

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "alarm_channel"
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "com.example.visualalarm.ALARM_TRIGGER"
        
        // Start foreground with notification
        val notification = buildNotification()
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= 34) {
            // FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK = 32
            32
        } else {
            0
        }
        startForeground(NOTIFICATION_ID, notification, foregroundServiceType)

        // Play alarm sound and vibrate
        playAlarmSound()
        startVibration()

        // Safety: stop after 10 minutes
        stopSelfAfter(10 * 60 * 1000)

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_alarms),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_alarms_desc)
                // Allow sound to play even in silent mode
                setSound(null, null)
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val alarmIntent = Intent(this, AlarmActivity::class.java)
        alarmIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alarm)
            .setContentTitle(getString(R.string.alarm_ringing))
            .setContentText(getString(R.string.app_name))
            .setFullScreenIntent(pendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun playAlarmSound() {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
            )
            setLooping(true)
            try {
                // Use system default alarm alert sound
                val uri = Settings.System.DEFAULT_ALARM_ALERT_URI
                    ?: Settings.System.DEFAULT_NOTIFICATION_URI
                    ?: Uri.parse("content://settings/system/alarm_alert")
                setDataSource(this@AlarmRingService, uri)
                prepare()
                start()
            } catch (e: IOException) {
                e.printStackTrace()
                // Fallback to a simple beep if we can't load the system sound
                try {
                    setDataSource(this@AlarmRingService, Settings.System.DEFAULT_NOTIFICATION_URI)
                    prepare()
                    start()
                } catch (e2: IOException) {
                    e2.printStackTrace()
                }
            }
        }
    }

    private fun startVibration() {
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 1000, 500, 1000),
                        1 // repeat index 1 (the 500ms on)
                    ),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
            } else {
                // Pre-Oreo fallback
                v.vibrate(longArrayOf(0, 500, 1000, 500, 1000), 1)
            }
        }
    }

    private fun stopSelfAfter(delayMs: Long) {
        Thread {
            Thread.sleep(delayMs)
            stopSelf()
        }.start()
    }

    override fun onDestroy() {
        // Clean up media player
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null

        // Stop vibration
        vibrator?.cancel()

        // Cancel notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
