package com.example.visualalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == "com.example.visualalarm.ALARM_TRIGGER" ||
            action == "com.example.visualalarm.SNOOZE_TRIGGER") {
            Log.d("AlarmReceiver", "Alarm triggered: $action")
            // Start the foreground service to handle ringing
            val serviceIntent = Intent(context, AlarmRingService::class.java)
            serviceIntent.action = action
            context.startForegroundService(serviceIntent)
        }
    }
}