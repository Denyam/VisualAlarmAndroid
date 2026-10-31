package co.denis.VisualAlarm

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Foreground service that handles the alarm ringing.
 *
 * This service runs with FOREGROUND_SERVICE_MEDIA_PLAYBACK type to ensure
 * the ringtone continues playing even when the app is in the background or
 * the device is in Doze mode.
 *
 * The service posts an ongoing notification with a full-screen intent to
 * launch AlarmActivity, plays the default alarm ringtone, and flashes the
 * camera torch (best-effort) in sync with the screen strobe.
 */
class AlarmRingingService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "alarm_channel"
        private const val START_ACTION = "co.denis.VisualAlarm.action.START_ALARM"
        private const val STOP_ACTION = "co.denis.VisualAlarm.action.STOP_ALARM"
        private const val SAFETY_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

        /**
         * Broadcast (app-internal) sent when the alarm is fully over, so the
         * strobing AlarmActivity can finish instead of blinking forever after
         * the ringtone/torch have already stopped.
         */
        const val ACTION_ALARM_STOPPED = "co.denis.VisualAlarm.action.ALARM_STOPPED"

        /** Creates an Intent to start the alarm ringing service. */
        fun createStartIntent(context: Context): Intent {
            return Intent(context, AlarmRingingService::class.java).apply {
                action = START_ACTION
            }
        }

        /** Creates an Intent to stop the alarm ringing service. */
        fun createStopIntent(context: Context): Intent {
            return Intent(context, AlarmRingingService::class.java).apply {
                action = STOP_ACTION
            }
        }
    }

    private var ringtone: Ringtone? = null
    private var torchFlasher: TorchFlasher? = null
    private var safetyTimeoutRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP_ACTION || intent == null) {
            stopAlarm()
            return START_NOT_STICKY
        }

        // Start foreground with notification
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start ringtone
        startRingtone()

        // Start torch flashing (best-effort; the alarm rings regardless)
        startTorch()

        // Safety timeout: auto-stop after 5 minutes
        safetyTimeoutRunnable = Runnable { stopSelf() }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed(safetyTimeoutRunnable!!, SAFETY_TIMEOUT_MS)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRingtone()
        stopTorch()
        safetyTimeoutRunnable?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }
        notifyAlarmStopped()
        super.onDestroy()
    }

    /** Tells the ringing screen (if any) that the alarm is over. */
    private fun notifyAlarmStopped() {
        try {
            sendBroadcast(Intent(ACTION_ALARM_STOPPED).setPackage(packageName))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Stops the alarm and the service. */
    private fun stopAlarm() {
        stopRingtone()
        stopTorch()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val pendingIntent = NotificationHelper.getAlarmPendingIntent(this)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText("Your alarm is ringing!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(false) // Don't auto-cancel; we manage it
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Alarm Notifications",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Alarm notifications"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startRingtone() {
        stopRingtone()
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(this, notification)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
    }

    private fun startTorch() {
        stopTorch()
        try {
            torchFlasher = TorchFlasher(this).also { it.start() }
        } catch (e: Exception) {
            // Flashing is best-effort: never let it break the ringing alarm.
            e.printStackTrace()
            torchFlasher = null
        }
    }

    private fun stopTorch() {
        try {
            torchFlasher?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        torchFlasher = null
    }
}