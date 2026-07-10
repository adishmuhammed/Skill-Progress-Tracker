package com.example.alarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.*

class AlarmSoundService : Service() {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        const val ACTION_START = "com.example.alarm.START"
        const val ACTION_STOP = "com.example.alarm.STOP"
        const val ACTION_SNOOZE = "com.example.alarm.SNOOZE"
        private const val NOTIFICATION_ID = 9988
        private const val CHANNEL_ID = "alarm_sound_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d("AlarmSoundService", "onStartCommand action: $action")

        when (action) {
            ACTION_START -> {
                serviceScope.launch {
                    val stateManager = AlarmStateManager(applicationContext)
                    if (stateManager.shouldRingNow()) {
                        startRinging()
                    } else {
                        stopSelf()
                    }
                }
            }
            ACTION_STOP -> {
                stopRinging()
                stopSelf()
            }
            ACTION_SNOOZE -> {
                snoozeAlarm()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        if (isPlaying) return
        isPlaying = true

        val stateManager = AlarmStateManager(applicationContext)
        stateManager.isAlarmRinging = true

        // 1. Play looping buzzer sound programmatically (highly reliable PCM buzzer)
        startBuzzer()

        // 2. Build Notification with actions
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, AlarmSoundService::class.java).apply {
            this.action = ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getService(
            this, 1, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mandatory Daily Alarm is Ringing!")
            .setContentText("Write in your Skill Journal now to stop the alarm and track your daily growth!")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze (5m)", snoozePendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startBuzzer() {
        try {
            val sampleRate = 8000
            val numSamples = sampleRate // 1 second loop
            val generatedSnd = ByteArray(2 * numSamples)

            // Make a classic dual-beep sound: beep (0.2s), rest (0.1s), beep (0.2s), rest (0.5s)
            val hz = 600.0 // crisp digital frequency
            for (i in 0 until numSamples) {
                // Determine if we should play sound during this millisecond
                val playSound = (i < sampleRate * 0.2) || (i > sampleRate * 0.3 && i < sampleRate * 0.5)
                val dVal = if (playSound) {
                    Math.sin(2 * Math.PI * i / (sampleRate / hz))
                } else {
                    0.0
                }
                val val16 = (dVal * 32767).toInt()
                generatedSnd[2 * i] = (val16 and 0x00ff).toByte()
                generatedSnd[2 * i + 1] = ((val16 and 0xff00) ushr 8).toByte()
            }

            audioTrack = AudioTrack(
                AudioManager.STREAM_ALARM,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                generatedSnd.size,
                AudioTrack.MODE_STATIC
            ).apply {
                write(generatedSnd, 0, generatedSnd.size)
                setLoopPoints(0, numSamples, -1) // loop infinitely
                play()
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundService", "Failed to start buzzer", e)
        }
    }

    private fun snoozeAlarm() {
        val stateManager = AlarmStateManager(applicationContext)
        stateManager.snooze(5) // Snooze for 5 minutes
        stopRinging()
        AlarmScheduler.scheduleDailyAlarm(applicationContext) // Reschedule tomorrow or after snooze
    }

    private fun stopRinging() {
        if (!isPlaying) return
        isPlaying = false

        val stateManager = AlarmStateManager(applicationContext)
        stateManager.isAlarmRinging = false

        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                    release()
                }
            }
            audioTrack = null
        } catch (e: Exception) {
            Log.e("AlarmSoundService", "Error stopping audioTrack", e)
        }
    }

    override fun onDestroy() {
        stopRinging()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Skill Journal Alarms"
            val descriptionText = "Channel for daily mandatory journal alarms"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(null, null) // Play sound via AudioTrack for perfect looping
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
