package com.example.alarm

import android.content.Context
import com.example.data.db.AppDatabase
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.*

class AlarmStateManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("alarm_settings", Context.MODE_PRIVATE)
    private val db = AppDatabase.getDatabase(context)

    companion object {
        private const val KEY_HOUR = "alarm_hour"
        private const val KEY_MINUTE = "alarm_minute"
        private const val KEY_ENABLED = "alarm_enabled"
        private const val KEY_RINGING = "alarm_ringing"
        private const val KEY_SNOOZED_UNTIL = "snoozed_until"
    }

    var alarmHour: Int
        get() = prefs.getInt(KEY_HOUR, 8)
        set(value) = prefs.edit().putInt(KEY_HOUR, value).apply()

    var alarmMinute: Int
        get() = prefs.getInt(KEY_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_MINUTE, value).apply()

    var isAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var isAlarmRinging: Boolean
        get() = prefs.getBoolean(KEY_RINGING, false)
        set(value) = prefs.edit().putBoolean(KEY_RINGING, value).apply()

    var snoozedUntil: Long
        get() = prefs.getLong(KEY_SNOOZED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_SNOOZED_UNTIL, value).apply()

    fun snooze(minutes: Int = 5) {
        snoozedUntil = System.currentTimeMillis() + (minutes * 60 * 1000)
        isAlarmRinging = false
    }

    fun isSnoozed(): Boolean {
        return System.currentTimeMillis() < snoozedUntil
    }

    suspend fun isJournalWrittenForToday(): Boolean {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val entry = db.journalEntryDao().getJournalEntryByDate(todayStr)
        return entry != null
    }

    suspend fun shouldRingNow(): Boolean {
        if (!isAlarmEnabled) return false
        if (isSnoozed()) return false
        if (isJournalWrittenForToday()) return false
        return true
    }
}
