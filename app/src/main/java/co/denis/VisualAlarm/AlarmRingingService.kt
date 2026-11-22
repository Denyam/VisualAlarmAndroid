package co.denis.VisualAlarm

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.denis.VisualAlarm.AlarmPermissions

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
        private const val START_ACTION = "co.denis.VisualAlarm.action.START_ALARM"
        private const val SNOOZE_ACTION = "co.denis.VisualAlarm.action.SNOOZE"
        private const val SAFETY_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

        /**
         * Broadcast (app-internal) sent when the alarm is fully over, so the
         * strobing AlarmActivity can finish instead of blinking forever after
         * the ringtone/torch have already stopped.
         */
        const val ACTION_ALARM_STOPPED = "co.denis.VisualAlarm.action.ALARM_STOPPED"

        /** Creates an Intent to start the alarm ringing service. */
        fun createStartIntent(context: Context, alarmId: String = "") = createIntent(
            context = context,
            alarmId = alarmId,
            action = START_ACTION
        )

        /** Creates an Intent to trigger snooze from AlarmActivity. */
        fun createSnoozeIntent(context: Context, alarmId: String) = createIntent(
            context = context,
            alarmId = alarmId,
            action = SNOOZE_ACTION
        )

        private fun createIntent(context: Context, alarmId: String, action: String) = Intent(
            context,
            AlarmRingingService::class.java
        ).apply {
            this.action = action
            putExtra(NotificationHelper.EXTRA_ALARM_ID, alarmId)
        }
    }

    private var ringtone: Ringtone? = null
    private var torchFlasher: TorchFlasher? = null
    private var safetyTimeoutRunnable: Runnable? = null
    private var currentAlarmId: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private val permissions = AlarmPermissions(this)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentAlarmId = intent?.getStringExtra(NotificationHelper.EXTRA_ALARM_ID) ?: ""
        val action = intent?.action ?: START_ACTION

        when (action) {
            SNOOZE_ACTION -> {
                handleSnoozeAction(currentAlarmId)
                return START_NOT_STICKY
            }
            else -> { /* START_ACTION or legacy */ }
        }

        // Check if notification will actually show (permission + channel enabled).
        // If not, launch AlarmActivity directly since fullScreenIntent won't fire.
        val canShowNotification = permissions.hasPostNotificationsPermission() && permissions.isAlarmChannelEnabled()
        if (!canShowNotification) {
            launchAlarmActivityDirectly()
            Toast.makeText(
                this,
                getString(R.string.toast_notifications_blocked),
                Toast.LENGTH_LONG
            ).show()
        }

        // Start foreground with notification (still post as fallback if user re-enables channel)
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

        safetyTimeoutRunnable?.let {
            handler.removeCallbacks(it)
        }

        // Safety timeout: auto-stop after 5 minutes, with snooze logic
        safetyTimeoutRunnable = Runnable { handleSafetyTimeout(currentAlarmId) }
        safetyTimeoutRunnable?.let {
            handler.postDelayed(it, SAFETY_TIMEOUT_MS)
        }

        return START_NOT_STICKY
    }

    private fun handleSnoozeAction(alarmId: String) {
        if (alarmId.isBlank()) return
        val store = (applicationContext as AlarmApplication).alarmStore
        store.getAlarmById(alarmId)?.let { alarm ->
            if (alarm.snoozeEnabled) {
                val success = AlarmScheduler(this).scheduleSnooze(alarm)
                if (success) {
                    stopSelf()
                }
            } else {
                stopSelf()
            }
        }
    }

    private fun handleSafetyTimeout(alarmId: String) {
        if (alarmId.isBlank()) {
            stopSelf()
            return
        }
        val store = (applicationContext as AlarmApplication).alarmStore
        store.getAlarmById(alarmId)?.let { alarm ->
            if (alarm.snoozeEnabled) {
                val success = AlarmScheduler(this).scheduleSnooze(alarm)
            }
        }

        stopSelf()
    }

    /**
     * Launches AlarmActivity directly with full-screen intent flags.
     * Used when notification channel is disabled (so fullScreenIntent won't fire).
     */
    private fun launchAlarmActivityDirectly() {
        val intent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopRingtone()
        stopTorch()
        safetyTimeoutRunnable?.let {
            handler.removeCallbacks(it)
        }
        // Explicit notification teardown: this is the single shutdown path for
        // every way the service can end (STOP tap, safety timeout, system call
        // to stopService, ...).
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        notifyAlarmStopped(currentAlarmId)
        super.onDestroy()
    }

    /** Tells the ringing screen (if any) that the alarm is over. */
    private fun notifyAlarmStopped(alarmId: String = "") {
        try {
            val intent = Intent(ACTION_ALARM_STOPPED).setPackage(packageName)
            if (alarmId.isNotBlank()) {
                intent.putExtra(NotificationHelper.EXTRA_ALARM_ID, alarmId)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pendingIntent = NotificationHelper.getAlarmPendingIntent(this, currentAlarmId)

        return NotificationCompat.Builder(this, AlarmPermissions.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.notification_title_alarm))
            .setContentText(getString(R.string.notification_text_alarm_ringing))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false) // Don't auto-cancel; we manage it
            .setOngoing(true)
            .build()
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
