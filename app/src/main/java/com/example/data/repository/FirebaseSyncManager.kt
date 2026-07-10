package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.db.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"

    /**
     * Initializes FirebaseApp manually if needed. This is critical for environments 
     * where the developer doesn't bundle a pre-configured google-services.json file.
     */
    fun getDatabaseInstance(context: Context, customUrl: String): FirebaseDatabase {
        val trimmedUrl = customUrl.trim()
        
        // Ensure default FirebaseApp is initialized
        try {
            FirebaseApp.getInstance()
        } catch (e: Exception) {
            Log.d(TAG, "Initializing manual FirebaseApp fallback")
            val builder = FirebaseOptions.Builder()
                .setApplicationId("com.aistudio.skilljournal.app")
                .setApiKey("manual_initialization_placeholder")
            
            if (trimmedUrl.isNotEmpty()) {
                builder.setDatabaseUrl(trimmedUrl)
            }
            
            try {
                FirebaseApp.initializeApp(context.applicationContext, builder.build())
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to initialize manual FirebaseApp", ex)
            }
        }

        return if (trimmedUrl.isNotEmpty()) {
            FirebaseDatabase.getInstance(trimmedUrl)
        } else {
            FirebaseDatabase.getInstance()
        }
    }

    /**
     * Pushes all local Room Database tables up to the Firebase cloud under the specified Sync Key.
     */
    suspend fun backupToCloud(
        context: Context,
        database: AppDatabase,
        syncKey: String,
        customUrl: String
    ): Unit = withContext(Dispatchers.IO) {
        val cleanSyncKey = syncKey.trim().replace(".", "_").replace("#", "_")
            .replace("$", "_").replace("[", "_").replace("]", "_")
        if (cleanSyncKey.isEmpty()) {
            throw IllegalArgumentException("Sync Key cannot be empty.")
        }

        val journalEntryDao = database.journalEntryDao()
        val skillDao = database.skillDao()
        val skillProgressHistoryDao = database.skillProgressHistoryDao()
        val buffDao = database.buffDao()
        val inventoryItemDao = database.inventoryItemDao()
        val connectionDao = database.connectionDao()
        val aiQuestionDao = database.aiQuestionDao()

        // Fetch all local records synchronously
        val journalEntries = journalEntryDao.getAllJournalEntries().first()
        val skills = skillDao.getAllSkills().first()
        val history = skillProgressHistoryDao.getAllHistory().first()
        val buffs = buffDao.getAllBuffs().first()
        val inventoryItems = inventoryItemDao.getAllItems().first()
        val connections = connectionDao.getAllConnections().first()
        val aiQuestions = aiQuestionDao.getAllQuestions()

        val backupData = mapOf(
            "journal_entries" to journalEntries,
            "skills" to skills,
            "history" to history,
            "buffs" to buffs,
            "inventory_items" to inventoryItems,
            "connections" to connections,
            "ai_questions" to aiQuestions,
            "metadata" to mapOf(
                "last_backup_timestamp" to System.currentTimeMillis(),
                "device_model" to android.os.Build.MODEL,
                "version" to 1
            )
        )

        val dbInstance = getDatabaseInstance(context, customUrl)
        val userRef = dbInstance.reference.child("users").child(cleanSyncKey)

        suspendCancellableCoroutine<Unit> { continuation ->
            userRef.setValue(backupData)
                .addOnSuccessListener {
                    Log.d(TAG, "Cloud backup completed successfully.")
                    continuation.resume(Unit)
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Cloud backup failed: ${error.message}", error)
                    continuation.resumeWithException(error)
                }
        }
    }

    /**
     * Pulls the backup from Firebase, wipes the local Room database, and restores the cloud records.
     */
    suspend fun restoreFromCloud(
        context: Context,
        database: AppDatabase,
        syncKey: String,
        customUrl: String
    ): Unit = withContext(Dispatchers.IO) {
        val cleanSyncKey = syncKey.trim().replace(".", "_").replace("#", "_")
            .replace("$", "_").replace("[", "_").replace("]", "_")
        if (cleanSyncKey.isEmpty()) {
            throw IllegalArgumentException("Sync Key cannot be empty.")
        }

        val dbInstance = getDatabaseInstance(context, customUrl)
        val userRef = dbInstance.reference.child("users").child(cleanSyncKey)

        // Retrieve data from Firebase
        val dataSnapshot = suspendCancellableCoroutine<DataSnapshot> { continuation ->
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    continuation.resume(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    continuation.resumeWithException(error.toException())
                }
            }
            userRef.addListenerForSingleValueEvent(listener)
        }

        if (!dataSnapshot.exists()) {
            throw IllegalStateException("No backup found in the cloud for Sync Key '$cleanSyncKey'. Please make sure the Sync Key is correct and that you've backed up first.")
        }

        // Deserialize lists safely
        val journalEntries = dataSnapshot.child("journal_entries").children.mapNotNull { 
            it.getValue(JournalEntry::class.java) 
        }
        val skills = dataSnapshot.child("skills").children.mapNotNull { 
            it.getValue(Skill::class.java) 
        }
        val history = dataSnapshot.child("history").children.mapNotNull { 
            it.getValue(SkillProgressHistory::class.java) 
        }
        val buffs = dataSnapshot.child("buffs").children.mapNotNull { 
            it.getValue(Buff::class.java) 
        }
        val inventoryItems = dataSnapshot.child("inventory_items").children.mapNotNull { 
            it.getValue(InventoryItem::class.java) 
        }
        val connections = dataSnapshot.child("connections").children.mapNotNull { 
            it.getValue(Connection::class.java) 
        }
        val aiQuestions = dataSnapshot.child("ai_questions").children.mapNotNull { 
            it.getValue(AiQuestion::class.java) 
        }

        // Clear local database and perform atomic insert
        database.runInTransaction {
            database.clearAllTables()

            // Run insertions on DAOs
            // Note: Since runInTransaction runs synchronously inside Room, we use blocking execution or run blocking helpers on DAOs
            // Let's use runBlocking if we need to execute suspend functions on Room inside a transaction
            kotlinx.coroutines.runBlocking {
                val journalEntryDao = database.journalEntryDao()
                val skillDao = database.skillDao()
                val skillProgressHistoryDao = database.skillProgressHistoryDao()
                val buffDao = database.buffDao()
                val inventoryItemDao = database.inventoryItemDao()
                val connectionDao = database.connectionDao()
                val aiQuestionDao = database.aiQuestionDao()

                for (entry in journalEntries) {
                    journalEntryDao.insertJournalEntry(entry)
                }
                for (skill in skills) {
                    skillDao.insertSkill(skill)
                }
                for (item in history) {
                    skillProgressHistoryDao.insertHistory(item)
                }
                for (buff in buffs) {
                    buffDao.insertBuff(buff)
                }
                for (item in inventoryItems) {
                    inventoryItemDao.insertItem(item)
                }
                for (conn in connections) {
                    connectionDao.insertConnection(conn)
                }
                if (aiQuestions.isNotEmpty()) {
                    aiQuestionDao.insertQuestions(aiQuestions)
                }
            }
        }
        Log.d(TAG, "Local database successfully restored from cloud backup.")
    }
}
