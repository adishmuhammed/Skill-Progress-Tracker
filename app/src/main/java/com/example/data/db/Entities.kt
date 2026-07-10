package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "journal_entries"
)
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String = "", // yyyy-MM-dd
    val content: String = "",
    val summary: String = "",
    val progressAnalysis: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "skills",
    indices = [Index(value = ["name"], unique = true)]
)
data class Skill(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val level: Int = 1, // 1 to 10 scale or level index
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "skill_progress_history"
)
data class SkillProgressHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val skillName: String = "",
    val journalEntryId: Int = 0,
    val date: String = "", // yyyy-MM-dd
    val previousLevel: Int = 0,
    val newLevel: Int = 0,
    val explanation: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "buffs")
data class Buff(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",                  // e.g. "8-Hour Deep Sleep", "Continuous Screen Time"
    val description: String = "",           // e.g. "Provided immense physical and mental recovery"
    val type: String = "",                  // "BENEFIT" or "HARM"
    val intensity: Int = 1,                // 1 to 10 scale of impact
    val aspectAffected: String = "",         // e.g. "Mental Focus", "Physical Energy", "Stress"
    val date: String = "",                  // yyyy-MM-dd of occurrence
    val timestamp: Long = System.currentTimeMillis(),
    val journalEntryId: Int = 0        // Associated journal entry ID for precise rollback
)

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",                  // e.g. "Laptop / PC", "Running Shoes"
    val specification: String = "",          // e.g. "Intel Core i5 CPU", "Nike Pegasus 40"
    val buffRating: Int = 1,               // 1 to 10 scale calculated based on specification quality
    val specificationBenefit: String = "",  // e.g. "speeds up compile time by 30%"
    val status: String = "",                // "ACTIVE", "UPGRADED", "SOLD", "DESTROYED", "UNUSABLE"
    val statusDetails: String = "",    // e.g. "Upgraded to i7", "Screen cracked"
    val dateAdded: String = "",             // yyyy-MM-dd
    val lastUpdatedDate: String = "",       // yyyy-MM-dd
    val timestamp: Long = System.currentTimeMillis(),
    val journalEntryId: Int = 0        // Associated journal entry ID for precise rollback
)

@Entity(tableName = "connections")
data class Connection(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",                  // e.g. "Alice", "Bob"
    val relationType: String = "",          // e.g. "Coworker", "Friend", "Family", "Mentor", "Mentee", "Rival"
    val affinityLevel: Int = 1,            // 1 to 10 scale of connection strength
    val lastInteractionDate: String = "",    // yyyy-MM-dd
    val specification: String = "",          // Context or summary of interaction
    val journalEntryId: Int = 0,        // Associated journal entry ID
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_questions")
data class AiQuestion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val question: String = "",
    val isAnswered: Boolean = false,
    val date: String = "" // yyyy-MM-dd
)

@Entity(tableName = "todo_tasks")
data class TodoTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String = "",
    val isCompleted: Boolean = false,
    val date: String = "", // yyyy-MM-dd
    val timestamp: Long = System.currentTimeMillis()
)
