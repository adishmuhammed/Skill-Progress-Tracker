package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.alarm.AlarmSoundService
import com.example.alarm.AlarmStateManager
import com.example.data.api.JournalAnalysisResult
import com.example.data.db.AppDatabase
import com.example.data.db.JournalEntry
import com.example.data.db.Skill
import com.example.data.db.SkillProgressHistory
import com.example.data.db.Buff
import com.example.data.db.InventoryItem
import com.example.data.db.AiQuestion
import com.example.data.db.TodoTask
import com.example.data.repository.JournalRepository
import com.example.data.repository.FirebaseSyncManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = JournalRepository(db)
    private val stateManager = AlarmStateManager(application)

    // Firebase Sync State and configuration
    private val prefs = application.getSharedPreferences("firebase_sync_prefs", Context.MODE_PRIVATE)
    
    val firebaseDbUrl = MutableStateFlow(prefs.getString("db_url", "https://your-firebase-project-default-rtdb.firebaseio.com/") ?: "")
    val firebaseSyncKey = MutableStateFlow(prefs.getString("sync_key", "") ?: "")
    val isAutoSyncEnabled = MutableStateFlow(prefs.getBoolean("auto_sync", false))

    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        data class Success(val message: String) : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState = _syncState.asStateFlow()

    private fun triggerAutoBackup() {
        if (isAutoSyncEnabled.value && firebaseSyncKey.value.isNotBlank()) {
            backupDataToCloud()
        }
    }

    fun updateFirebaseConfig(url: String, key: String, autoSync: Boolean) {
        prefs.edit().apply {
            putString("db_url", url)
            putString("sync_key", key)
            putBoolean("auto_sync", autoSync)
            apply()
        }
        firebaseDbUrl.value = url
        firebaseSyncKey.value = key
        isAutoSyncEnabled.value = autoSync
    }

    fun backupDataToCloud() {
        val key = firebaseSyncKey.value
        val url = firebaseDbUrl.value
        if (key.isBlank()) {
            _syncState.value = SyncState.Error("Sync Key is empty. Please configure it in settings.")
            return
        }
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                FirebaseSyncManager.backupToCloud(getApplication(), db, key, url)
                _syncState.value = SyncState.Success("Cloud Backup Successful!")
            } catch (e: java.lang.Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Failed to backup data.")
            }
        }
    }

    fun restoreDataFromCloud() {
        val key = firebaseSyncKey.value
        val url = firebaseDbUrl.value
        if (key.isBlank()) {
            _syncState.value = SyncState.Error("Sync Key is empty. Please configure it in settings.")
            return
        }
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                FirebaseSyncManager.restoreFromCloud(getApplication(), db, key, url)
                _syncState.value = SyncState.Success("Cloud Restore Successful!")
                
                // Refresh states
                checkTodayStatus()
                loadTodayQuestions()
            } catch (e: java.lang.Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Failed to restore data.")
            }
        }
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    // Reactive streams from database
    val journalEntries: StateFlow<List<JournalEntry>> = repository.allJournalEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val skills: StateFlow<List<Skill>> = repository.allSkillsByLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<SkillProgressHistory>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBuffs: StateFlow<List<Buff>> = repository.allBuffs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryItems: StateFlow<List<InventoryItem>> = repository.allInventoryItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connections: StateFlow<List<com.example.data.db.Connection>> = repository.allConnections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiQuestions = MutableStateFlow<List<AiQuestion>>(emptyList())
    val aiQuestions = _aiQuestions.asStateFlow()

    val editingEntry = MutableStateFlow<JournalEntry?>(null)

    // Alarm state
    private val _alarmHour = MutableStateFlow(stateManager.alarmHour)
    val alarmHour = _alarmHour.asStateFlow()

    private val _alarmMinute = MutableStateFlow(stateManager.alarmMinute)
    val alarmMinute = _alarmMinute.asStateFlow()

    private val _isAlarmEnabled = MutableStateFlow(stateManager.isAlarmEnabled)
    val isAlarmEnabled = _isAlarmEnabled.asStateFlow()

    private val _isAlarmRinging = MutableStateFlow(stateManager.isAlarmRinging)
    val isAlarmRinging = _isAlarmRinging.asStateFlow()

    private val _isSnoozed = MutableStateFlow(stateManager.isSnoozed())
    val isSnoozed = _isSnoozed.asStateFlow()

    // Journal writing state
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing = _isAnalyzing.asStateFlow()

    private val _analysisResult = MutableStateFlow<JournalAnalysisResult?>(null)
    val analysisResult = _analysisResult.asStateFlow()

    // Checks if today's entry has been written
    private val _isTodayWritten = MutableStateFlow(false)
    val isTodayWritten = _isTodayWritten.asStateFlow()

    init {
        checkTodayStatus()
        viewModelScope.launch {
            repository.seedInventoryIfEmpty()
            loadTodayQuestions()
        }
        viewModelScope.launch {
            // Keep checking snooze/ringing state periodically
            while (true) {
                _isSnoozed.value = stateManager.isSnoozed()
                _isAlarmRinging.value = stateManager.isAlarmRinging
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    fun checkTodayStatus() {
        viewModelScope.launch {
            _isTodayWritten.value = stateManager.isJournalWrittenForToday()
        }
    }

    fun updateAlarmTime(hour: Int, minute: Int) {
        stateManager.alarmHour = hour
        stateManager.alarmMinute = minute
        _alarmHour.value = hour
        _alarmMinute.value = minute
        AlarmScheduler.scheduleDailyAlarm(getApplication())
    }

    fun setAlarmEnabled(enabled: Boolean) {
        stateManager.isAlarmEnabled = enabled
        _isAlarmEnabled.value = enabled
        if (enabled) {
            AlarmScheduler.scheduleDailyAlarm(getApplication())
        } else {
            AlarmScheduler.cancelAlarm(getApplication())
            stopAlarmSound()
        }
    }

    fun triggerTestAlarm() {
        viewModelScope.launch {
            val written = stateManager.isJournalWrittenForToday()
            if (written) {
                // For test purposes, let them experience the alarm even if they wrote today's journal!
                // Clear state temporarily or bypass the check
                stateManager.isAlarmRinging = true
                _isAlarmRinging.value = true
                startAlarmSoundDirectly()
            } else {
                stateManager.isAlarmRinging = true
                _isAlarmRinging.value = true
                startAlarmSoundDirectly()
            }
        }
    }

    private fun startAlarmSoundDirectly() {
        val intent = Intent(getApplication(), AlarmSoundService::class.java).apply {
            action = AlarmSoundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun snoozeAlarm() {
        stateManager.snooze(5)
        _isSnoozed.value = true
        _isAlarmRinging.value = false
        stopAlarmSound()
    }

    fun stopAlarmSound() {
        val intent = Intent(getApplication(), AlarmSoundService::class.java).apply {
            action = AlarmSoundService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
        stateManager.isAlarmRinging = false
        _isAlarmRinging.value = false
    }

    fun submitJournalEntry(content: String, dateStr: String, isSimulation: Boolean = false, editingEntryId: Int? = null) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val result = repository.addJournalEntry(dateStr, content, isSimulation, editingEntryId)
                _analysisResult.value = result
                _isTodayWritten.value = true

                // Clear editing state on success
                editingEntry.value = null

                // Dismiss alarm if it is ringing
                stopAlarmSound()

                // Trigger auto-backup
                triggerAutoBackup()
            } catch (e: Exception) {
                // error is already logged and fallback happens inside repository,
                // but if repository fails completely we catch it here.
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun clearAnalysisResult() {
        _analysisResult.value = null
    }

    fun deleteJournal(id: Int) {
        viewModelScope.launch {
            repository.deleteJournalEntryById(id)
            checkTodayStatus()
            if (editingEntry.value?.id == id) {
                editingEntry.value = null
            }
            triggerAutoBackup()
        }
    }

    fun loadTodayQuestions() {
        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            var questions = repository.getQuestionsForDate(todayStr)
            if (questions.isEmpty()) {
                questions = repository.generateQuestionsForDate(todayStr)
                repository.saveQuestions(questions)
            }
            _aiQuestions.value = questions
        }
    }

    fun removeBuff(id: Int) {
        viewModelScope.launch {
            repository.deleteBuffById(id)
            triggerAutoBackup()
        }
    }

    fun deleteInventoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteInventoryItemById(id)
            triggerAutoBackup()
        }
    }

    fun deleteConnection(id: Int) {
        viewModelScope.launch {
            repository.deleteConnectionById(id)
            triggerAutoBackup()
        }
    }

    fun startEditing(entry: JournalEntry) {
        editingEntry.value = entry
    }

    fun cancelEditing() {
        editingEntry.value = null
    }

    // Task Management
    val allTasks: StateFlow<List<TodoTask>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getTasksForDateFlow(date: String): Flow<List<TodoTask>> {
        return repository.getTasksForDateFlow(date)
    }

    fun addTask(title: String, date: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertTask(TodoTask(title = title, date = date))
            triggerAutoBackup()
        }
    }

    fun toggleTask(id: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateTaskStatus(id, isCompleted)
            triggerAutoBackup()
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch {
            repository.deleteTaskById(id)
            triggerAutoBackup()
        }
    }

    private suspend fun delay(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }
}
