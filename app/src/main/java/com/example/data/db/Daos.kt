package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY date DESC")
    fun getAllJournalEntries(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE date = :date LIMIT 1")
    suspend fun getJournalEntryByDate(date: String): JournalEntry?

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getJournalEntryById(id: Int): JournalEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntry): Long

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteJournalEntryById(id: Int)
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY name ASC")
    fun getAllSkills(): Flow<List<Skill>>

    @Query("SELECT * FROM skills WHERE name = :name LIMIT 1")
    suspend fun getSkillByName(name: String): Skill?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: Skill): Long

    @Query("SELECT * FROM skills ORDER BY level DESC")
    fun getSkillsByLevelDesc(): Flow<List<Skill>>
}

@Dao
interface SkillProgressHistoryDao {
    @Query("SELECT * FROM skill_progress_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<SkillProgressHistory>>

    @Query("SELECT * FROM skill_progress_history WHERE skillName = :skillName ORDER BY timestamp DESC")
    fun getHistoryForSkill(skillName: String): Flow<List<SkillProgressHistory>>

    @Query("SELECT * FROM skill_progress_history WHERE journalEntryId = :journalEntryId")
    suspend fun getHistoryForJournalEntry(journalEntryId: Int): List<SkillProgressHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: SkillProgressHistory): Long

    @Query("DELETE FROM skill_progress_history WHERE journalEntryId = :journalEntryId")
    suspend fun deleteHistoryByJournalEntryId(journalEntryId: Int)
}

@Dao
interface BuffDao {
    @Query("SELECT * FROM buffs ORDER BY date DESC, id DESC")
    fun getAllBuffs(): Flow<List<Buff>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuff(buff: Buff): Long

    @Query("DELETE FROM buffs WHERE id = :id")
    suspend fun deleteBuffById(id: Int)

    @Query("DELETE FROM buffs WHERE date = :date")
    suspend fun deleteBuffsByDate(date: String)

    @Query("DELETE FROM buffs WHERE journalEntryId = :journalEntryId")
    suspend fun deleteBuffsByJournalEntryId(journalEntryId: Int)
}

@Dao
interface InventoryItemDao {
    @Query("SELECT * FROM inventory_items ORDER BY status ASC, name ASC")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE name = :name LIMIT 1")
    suspend fun getItemByName(name: String): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItemById(id: Int)

    @Query("SELECT * FROM inventory_items WHERE dateAdded = :date")
    suspend fun getItemsAddedOnDate(date: String): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE lastUpdatedDate = :date")
    suspend fun getItemsUpdatedOnDate(date: String): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE journalEntryId = :journalEntryId")
    suspend fun getItemsByJournalEntryId(journalEntryId: Int): List<InventoryItem>
}

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY affinityLevel DESC, name ASC")
    fun getAllConnections(): Flow<List<Connection>>

    @Query("SELECT * FROM connections WHERE name = :name LIMIT 1")
    suspend fun getConnectionByName(name: String): Connection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: Connection): Long

    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun deleteConnectionById(id: Int)

    @Query("DELETE FROM connections WHERE journalEntryId = :journalEntryId")
    suspend fun deleteConnectionsByJournalEntryId(journalEntryId: Int)

    @Query("SELECT * FROM connections WHERE journalEntryId = :journalEntryId")
    suspend fun getConnectionsByJournalEntryId(journalEntryId: Int): List<Connection>
}

@Dao
interface AiQuestionDao {
    @Query("SELECT * FROM ai_questions")
    suspend fun getAllQuestions(): List<AiQuestion>

    @Query("SELECT * FROM ai_questions WHERE date = :date ORDER BY id ASC")
    suspend fun getQuestionsForDate(date: String): List<AiQuestion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<AiQuestion>)

    @Query("UPDATE ai_questions SET isAnswered = :isAnswered WHERE id = :id")
    suspend fun updateQuestionAnswered(id: Int, isAnswered: Boolean)

    @Query("DELETE FROM ai_questions WHERE date = :date")
    suspend fun deleteQuestionsForDate(date: String)
}

@Dao
interface TodoTaskDao {
    @Query("SELECT * FROM todo_tasks ORDER BY timestamp ASC")
    fun getAllTasksFlow(): Flow<List<TodoTask>>

    @Query("SELECT * FROM todo_tasks WHERE date = :date ORDER BY timestamp ASC")
    fun getTasksForDateFlow(date: String): Flow<List<TodoTask>>

    @Query("SELECT * FROM todo_tasks WHERE date = :date ORDER BY timestamp ASC")
    suspend fun getTasksForDate(date: String): List<TodoTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TodoTask): Long

    @Query("UPDATE todo_tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateTaskStatus(id: Int, isCompleted: Boolean)

    @Query("DELETE FROM todo_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    @Query("DELETE FROM todo_tasks WHERE date = :date")
    suspend fun deleteTasksByDate(date: String)
}

