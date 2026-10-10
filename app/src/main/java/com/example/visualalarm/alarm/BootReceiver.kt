package com.example.visualalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.visualalarm.data.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received: $action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // Reschedule the alarm
                rescheduleAlarm(context)
            }
        }
    }

    private fun rescheduleAlarm(context: Context) {
        // Use a coroutine scope to access suspend functions
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val repository = AlarmRepository.getInstance(context)
                val alarm = repository.getAlarm()
                val scheduler = AlarmScheduler(context)
                scheduler.schedule(alarm)
                Log.d("BootReceiver", "Rescheduled alarm: $alarm")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to reschedule alarm", e)
            }
        }
    }
}
