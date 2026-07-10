package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [JournalEntry::class, Skill::class, SkillProgressHistory::class, Buff::class, InventoryItem::class, Connection::class, AiQuestion::class, TodoTask::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun skillDao(): SkillDao
    abstract fun skillProgressHistoryDao(): SkillProgressHistoryDao
    abstract fun buffDao(): BuffDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun connectionDao(): ConnectionDao
    abstract fun aiQuestionDao(): AiQuestionDao
    abstract fun todoTaskDao(): TodoTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "skill_journal_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
