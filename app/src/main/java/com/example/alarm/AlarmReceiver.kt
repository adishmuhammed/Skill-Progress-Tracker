package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm triggered!")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SkillJournal:AlarmWakeLock").apply {
            acquire(15 * 1000L) // Safe 15-second timeout to prevent leaks
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val stateManager = AlarmStateManager(context.applicationContext)
                val shouldRing = stateManager.shouldRingNow()

                Log.d("AlarmReceiver", "shouldRing: $shouldRing")
                if (shouldRing) {
                    val serviceIntent = Intent(context, AlarmSoundService::class.java).apply {
                        action = AlarmSoundService.ACTION_START
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }

                // Always reschedule next daily alarm when one fires
                AlarmScheduler.scheduleDailyAlarm(context.applicationContext)
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error in AlarmReceiver onReceive", e)
            } finally {
                try {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                } catch (we: Exception) {
                    Log.e("AlarmReceiver", "Error releasing wake lock", we)
                }
                pendingResult.finish()
            }
        }
    }
}
