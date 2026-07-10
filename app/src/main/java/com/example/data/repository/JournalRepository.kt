package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.*
import com.example.data.db.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class JournalRepository(private val db: AppDatabase) {

    private val journalEntryDao = db.journalEntryDao()
    private val skillDao = db.skillDao()
    private val skillProgressHistoryDao = db.skillProgressHistoryDao()
    private val buffDao = db.buffDao()
    private val inventoryItemDao = db.inventoryItemDao()
    private val connectionDao = db.connectionDao()
    private val aiQuestionDao = db.aiQuestionDao()
    private val todoTaskDao = db.todoTaskDao()

    val allJournalEntries: Flow<List<JournalEntry>> = journalEntryDao.getAllJournalEntries()
    val allSkills: Flow<List<Skill>> = skillDao.getAllSkills()
    val allSkillsByLevel: Flow<List<Skill>> = skillDao.getSkillsByLevelDesc()
    val allHistory: Flow<List<SkillProgressHistory>> = skillProgressHistoryDao.getAllHistory()
    val allConnections: Flow<List<Connection>> = connectionDao.getAllConnections()
    val allTasks: Flow<List<TodoTask>> = todoTaskDao.getAllTasksFlow()

    fun getTasksForDateFlow(date: String): Flow<List<TodoTask>> = todoTaskDao.getTasksForDateFlow(date)
    suspend fun getTasksForDate(date: String): List<TodoTask> = withContext(Dispatchers.IO) { todoTaskDao.getTasksForDate(date) }
    suspend fun insertTask(task: TodoTask) = withContext(Dispatchers.IO) { todoTaskDao.insertTask(task) }
    suspend fun updateTaskStatus(id: Int, isCompleted: Boolean) = withContext(Dispatchers.IO) { todoTaskDao.updateTaskStatus(id, isCompleted) }
    suspend fun deleteTaskById(id: Int) = withContext(Dispatchers.IO) { todoTaskDao.deleteTaskById(id) }
    suspend fun deleteTasksByDate(date: String) = withContext(Dispatchers.IO) { todoTaskDao.deleteTasksByDate(date) }

    // Connections
    suspend fun deleteConnectionById(id: Int) = withContext(Dispatchers.IO) { connectionDao.deleteConnectionById(id) }

    // Buffs
    val allBuffs: Flow<List<Buff>> = buffDao.getAllBuffs()
    suspend fun insertBuff(buff: Buff) = withContext(Dispatchers.IO) { buffDao.insertBuff(buff) }
    suspend fun deleteBuffById(id: Int) = withContext(Dispatchers.IO) { buffDao.deleteBuffById(id) }

    // Inventory
    val allInventoryItems: Flow<List<InventoryItem>> = inventoryItemDao.getAllItems()
    suspend fun insertInventoryItem(item: InventoryItem) = withContext(Dispatchers.IO) { inventoryItemDao.insertItem(item) }
    suspend fun deleteInventoryItemById(id: Int) = withContext(Dispatchers.IO) { inventoryItemDao.deleteItemById(id) }
    suspend fun getItemById(id: Int): InventoryItem? = withContext(Dispatchers.IO) { inventoryItemDao.getItemById(id) }
    suspend fun getItemByName(name: String): InventoryItem? = withContext(Dispatchers.IO) { inventoryItemDao.getItemByName(name) }

    // AI Questions
    suspend fun getQuestionsForDate(date: String): List<AiQuestion> = withContext(Dispatchers.IO) {
        aiQuestionDao.getQuestionsForDate(date)
    }
    suspend fun saveQuestions(questions: List<AiQuestion>) = withContext(Dispatchers.IO) {
        aiQuestionDao.insertQuestions(questions)
    }

    suspend fun seedInventoryIfEmpty() = withContext(Dispatchers.IO) {
        val items = inventoryItemDao.getAllItems().firstOrNull() ?: emptyList()
        if (items.isEmpty()) {
            val defaults = listOf(
                InventoryItem(
                    name = "Workstation Computer",
                    specification = "Intel Core i3 CPU",
                    buffRating = 3,
                    specificationBenefit = "Standard baseline compiling and office productivity.",
                    status = "ACTIVE",
                    statusDetails = "Initial baseline computer.",
                    dateAdded = "2026-01-01",
                    lastUpdatedDate = "2026-01-01",
                    timestamp = System.currentTimeMillis()
                ),
                InventoryItem(
                    name = "Smart Phone",
                    specification = "Standard Android Phone",
                    buffRating = 4,
                    specificationBenefit = "Baseline communications and organizer apps.",
                    status = "ACTIVE",
                    statusDetails = "Initial standard phone.",
                    dateAdded = "2026-01-01",
                    lastUpdatedDate = "2026-01-01",
                    timestamp = System.currentTimeMillis()
                )
            )
            for (item in defaults) {
                inventoryItemDao.insertItem(item)
            }
        }
    }

    suspend fun getJournalEntryByDate(date: String): JournalEntry? = withContext(Dispatchers.IO) {
        journalEntryDao.getJournalEntryByDate(date)
    }

    private suspend fun revertJournalImpacts(entry: JournalEntry) {
        // 1. Revert skill levels using history
        val historyList = skillProgressHistoryDao.getHistoryForJournalEntry(entry.id)
        for (history in historyList) {
            val skill = skillDao.getSkillByName(history.skillName)
            if (skill != null) {
                val levelGain = history.newLevel - history.previousLevel
                val restoredLevel = maxOf(0, skill.level - levelGain)
                skillDao.insertSkill(skill.copy(level = restoredLevel))
            }
        }

        // 2. Delete progress history for this journal entry
        skillProgressHistoryDao.deleteHistoryByJournalEntryId(entry.id)

        // 3. Delete buffs for this journal entry ID
        buffDao.deleteBuffsByJournalEntryId(entry.id)

        // 4. Delete inventory items added specifically via this journal entry
        val itemsInvolved = inventoryItemDao.getItemsByJournalEntryId(entry.id)
        for (item in itemsInvolved) {
            if (item.dateAdded == entry.date) {
                inventoryItemDao.deleteItemById(item.id)
            } else {
                // If the item was updated (e.g. status changed/upgraded), revert its status back to active baseline
                val restoredItem = item.copy(
                    status = "ACTIVE",
                    statusDetails = "Restored after entry update",
                    lastUpdatedDate = item.dateAdded,
                    journalEntryId = 0
                )
                inventoryItemDao.insertItem(restoredItem)
            }
        }

        // 5. Revert or delete connections extracted via this journal entry
        val connectionsInvolved = connectionDao.getConnectionsByJournalEntryId(entry.id)
        for (conn in connectionsInvolved) {
            if (conn.affinityLevel > 1) {
                // Restore previous affinity level
                connectionDao.insertConnection(
                    conn.copy(
                        affinityLevel = conn.affinityLevel - 1,
                        journalEntryId = 0
                    )
                )
            } else {
                // Was newly created, delete it
                connectionDao.deleteConnectionById(conn.id)
            }
        }
    }

    suspend fun deleteJournalEntryById(id: Int) = withContext(Dispatchers.IO) {
        val entry = journalEntryDao.getJournalEntryById(id)
        if (entry != null) {
            revertJournalImpacts(entry)
        }
        journalEntryDao.deleteJournalEntryById(id)
    }

    fun getHistoryForSkill(skillName: String): Flow<List<SkillProgressHistory>> {
        return skillProgressHistoryDao.getHistoryForSkill(skillName)
    }

    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                val isTransient = when (e) {
                    is retrofit2.HttpException -> {
                        val code = e.code()
                        code == 429 || code == 503 || code == 502 || code == 504 || code == 408
                    }
                    is java.io.IOException -> true
                    else -> false
                }
                if (!isTransient || attempt == maxAttempts) {
                    throw e
                }
                Log.w("JournalRepository", "Gemini API failed with transient error: ${e.message}. Retrying in ${currentDelay}ms (Attempt $attempt/$maxAttempts)...")
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        throw IllegalStateException("Should not be reached")
    }

    private fun isKeyConfigured(key: String, placeholder: String): Boolean {
        return key.isNotEmpty() && key != placeholder
    }

    suspend fun addJournalEntry(
        date: String,
        content: String,
        isSimulationMode: Boolean = false,
        editingEntryId: Int? = null
    ): JournalAnalysisResult = withContext(Dispatchers.IO) {
        // 1. Fetch historical context for prompt
        val previousEntries = journalEntryDao.getAllJournalEntries().firstOrNull()?.take(5) ?: emptyList()
        val existingSkills = skillDao.getAllSkills().firstOrNull() ?: emptyList()

        val hasGemini = isKeyConfigured(BuildConfig.GEMINI_API_KEY, "MY_GEMINI_API_KEY")
        val hasGroq = isKeyConfigured(BuildConfig.GROQ_API_KEY, "MY_GROQ_API_KEY")
        val hasOpenCode = isKeyConfigured(BuildConfig.OPENCODE_API_KEY, "MY_OPENCODE_API_KEY")

        val useSimulation = isSimulationMode || (!hasGemini && !hasGroq && !hasOpenCode)

        val analysisResult = if (useSimulation) {
            simulateAnalysis(content, previousEntries, existingSkills)
        } else {
            var result: JournalAnalysisResult? = null
            val errors = mutableListOf<String>()

            // 1. Attempt Google Gemini API
            if (hasGemini) {
                try {
                    result = retryWithBackoff {
                        callGeminiAPIWithKey(BuildConfig.GEMINI_API_KEY, content, previousEntries, existingSkills, date)
                    }
                    Log.d("JournalRepository", "Successfully analyzed journal using Google Gemini API")
                } catch (e: Exception) {
                    Log.e("JournalRepository", "Google Gemini API call failed, trying next fallback", e)
                    errors.add("Google Gemini: ${e.localizedMessage}")
                }
            }

            // 2. Attempt Groq API
            if (result == null && hasGroq) {
                try {
                    result = retryWithBackoff {
                        callGroqAPI(BuildConfig.GROQ_API_KEY, content, previousEntries, existingSkills, date)
                    }
                    Log.d("JournalRepository", "Successfully analyzed journal using Groq API")
                } catch (e: Exception) {
                    Log.e("JournalRepository", "Groq API call failed, trying next fallback", e)
                    errors.add("Groq: ${e.localizedMessage}")
                }
            }

            // 3. Attempt OpenCode Zen API
            if (result == null && hasOpenCode) {
                try {
                    result = retryWithBackoff {
                        callGeminiAPIWithKey(BuildConfig.OPENCODE_API_KEY, content, previousEntries, existingSkills, date)
                    }
                    Log.d("JournalRepository", "Successfully analyzed journal using OpenCode Zen API")
                } catch (e: Exception) {
                    Log.e("JournalRepository", "OpenCode Zen API call failed", e)
                    errors.add("OpenCode Zen: ${e.localizedMessage}")
                }
            }

            result ?: run {
                val combinedError = errors.joinToString("; ")
                Log.e("JournalRepository", "All AI APIs failed completely. Falling back to offline simulation. Errors: $combinedError")
                simulateAnalysis(content, previousEntries, existingSkills, errorMsg = combinedError)
            }
        }

        // 2. Write analyzed results to Local DB
        val existingEntry = if (editingEntryId != null) {
            journalEntryDao.getJournalEntryById(editingEntryId)
        } else {
            null
        }
        
        if (existingEntry != null) {
            revertJournalImpacts(existingEntry)
        }

        val journalEntry = JournalEntry(
            id = existingEntry?.id ?: 0,
            date = date,
            content = content,
            summary = analysisResult.summary,
            progressAnalysis = analysisResult.progressAnalysis,
            timestamp = existingEntry?.timestamp ?: System.currentTimeMillis()
        )
        val journalId = journalEntryDao.insertJournalEntry(journalEntry).toInt()

        // 3. Process skills and write skill history
        for (inferred in analysisResult.skillsInferred) {
            val existingSkill = skillDao.getSkillByName(inferred.name)
            val previousLevel = existingSkill?.level ?: 0
            val newLevel = inferred.level

            // Update or Insert Skill
            val updatedSkill = Skill(
                id = existingSkill?.id ?: 0,
                name = inferred.name,
                description = inferred.description,
                category = inferred.category,
                level = newLevel,
                lastUpdated = System.currentTimeMillis()
            )
            skillDao.insertSkill(updatedSkill)

            // Insert progress history
            val history = SkillProgressHistory(
                skillName = inferred.name,
                journalEntryId = journalId,
                date = date,
                previousLevel = previousLevel,
                newLevel = newLevel,
                explanation = inferred.progressExplanation,
                timestamp = System.currentTimeMillis()
            )
            skillProgressHistoryDao.insertHistory(history)
        }

        // 4. Process inferred inventory items
        analysisResult.itemsInferred?.forEach { inferredItem ->
            val existingItem = inventoryItemDao.getItemByName(inferredItem.name)
            
            when (inferredItem.actionType.uppercase()) {
                "UPGRADED" -> {
                    if (existingItem != null) {
                        // Mark old item as UPGRADED
                        val upgradedOldItem = existingItem.copy(
                            status = "UPGRADED",
                            statusDetails = "Upgraded to ${inferredItem.specification} on $date",
                            lastUpdatedDate = date,
                            timestamp = System.currentTimeMillis(),
                            journalEntryId = journalId
                        )
                        inventoryItemDao.insertItem(upgradedOldItem)
                    }
                    // Insert new active item
                    val newItem = InventoryItem(
                        name = inferredItem.name,
                        specification = inferredItem.specification,
                        buffRating = inferredItem.buffRating,
                        specificationBenefit = inferredItem.specificationBenefit,
                        status = "ACTIVE",
                        statusDetails = "Upgraded from ${existingItem?.specification ?: "previous version"}",
                        dateAdded = date,
                        lastUpdatedDate = date,
                        timestamp = System.currentTimeMillis(),
                        journalEntryId = journalId
                    )
                    inventoryItemDao.insertItem(newItem)
                }
                "SOLD", "DESTROYED", "UNUSABLE" -> {
                    if (existingItem != null) {
                        val deactivatedItem = existingItem.copy(
                            status = inferredItem.actionType.uppercase(),
                            statusDetails = "Marked as ${inferredItem.actionType.uppercase()} in journal on $date",
                            lastUpdatedDate = date,
                            timestamp = System.currentTimeMillis(),
                            journalEntryId = journalId
                        )
                        inventoryItemDao.insertItem(deactivatedItem)
                    }
                }
                else -> { // ACQUIRED or any default
                    if (existingItem != null) {
                        // If it has a higher buff rating, treat it as an upgrade automatically
                        if (inferredItem.buffRating > existingItem.buffRating) {
                            val supersededItem = existingItem.copy(
                                status = "UPGRADED",
                                statusDetails = "Upgraded to ${inferredItem.specification} on $date",
                                lastUpdatedDate = date,
                                timestamp = System.currentTimeMillis(),
                                journalEntryId = journalId
                            )
                            inventoryItemDao.insertItem(supersededItem)
                        }
                        
                        // Update active item details
                        val updatedItem = existingItem.copy(
                            specification = inferredItem.specification,
                            buffRating = maxOf(existingItem.buffRating, inferredItem.buffRating),
                            specificationBenefit = inferredItem.specificationBenefit,
                            status = "ACTIVE",
                            lastUpdatedDate = date,
                            timestamp = System.currentTimeMillis(),
                            journalEntryId = journalId
                        )
                        inventoryItemDao.insertItem(updatedItem)
                    } else {
                        // Create brand new item
                        val newItem = InventoryItem(
                            name = inferredItem.name,
                            specification = inferredItem.specification,
                            buffRating = inferredItem.buffRating,
                            specificationBenefit = inferredItem.specificationBenefit,
                            status = "ACTIVE",
                            statusDetails = "Acquired on $date",
                            dateAdded = date,
                            lastUpdatedDate = date,
                            timestamp = System.currentTimeMillis(),
                            journalEntryId = journalId
                        )
                        inventoryItemDao.insertItem(newItem)
                    }
                }
            }
        }

        // 5. Process inferred buffs and status effects
        analysisResult.buffsInferred?.forEach { inferredBuff ->
            val newBuff = Buff(
                name = inferredBuff.name,
                description = inferredBuff.description,
                type = inferredBuff.type,
                intensity = inferredBuff.intensity,
                aspectAffected = inferredBuff.aspectAffected,
                date = date,
                timestamp = System.currentTimeMillis(),
                journalEntryId = journalId
            )
            buffDao.insertBuff(newBuff)
        }

        // 6. Process inferred connections and social dynamics
        analysisResult.connectionsInferred?.forEach { inferredConn ->
            val existingConn = connectionDao.getConnectionByName(inferredConn.name)
            val updatedAffinity = if (existingConn != null) {
                minOf(10, existingConn.affinityLevel + 1)
            } else {
                inferredConn.affinityLevel
            }
            val newConn = Connection(
                id = existingConn?.id ?: 0,
                name = inferredConn.name,
                relationType = inferredConn.relationType,
                affinityLevel = updatedAffinity,
                lastInteractionDate = date,
                specification = inferredConn.specification,
                journalEntryId = journalId,
                timestamp = System.currentTimeMillis()
            )
            connectionDao.insertConnection(newConn)
        }

        analysisResult
    }

    private suspend fun callGeminiAPIWithKey(
        apiKey: String,
        content: String,
        previousEntries: List<JournalEntry>,
        existingSkills: List<Skill>,
        todayDate: String
    ): JournalAnalysisResult {
        val pastSummariesText = previousEntries.joinToString("\n") { "- ${it.date}: ${it.summary}" }
            .ifEmpty { "No previous entries." }

        val currentSkillsText = existingSkills.joinToString("\n") { "- ${it.name} (Level ${it.level}/10): ${it.description}" }
            .ifEmpty { "No skills registered yet." }

        val prompt = """
            Analyze today's journal entry and compare it with the previous history.

            Today's Date: $todayDate
            Today's Journal Entry:
            "$content"

            --- Previous History ---
            Last 5 daily journal summaries:
            $pastSummariesText

            Current known skills and their levels:
            $currentSkillsText

            Instructions:
            1. Extract 1 to 4 skills demonstrated, improved, or exercised in today's entry. For each skill:
               - Give it a name (capitalize, e.g. "Kotlin Programming", "Public Speaking", "Stress Management", "Problem Solving"). Keep names consistent with existing skills if they match.
               - Give a clear description.
               - Assign a category (e.g. Technical, Creative, Communication, Fitness, Mindset, etc.).
               - Estimate a proficiency level from 1 to 10. If the user already has this skill, determine if today's entry shows an improvement, stable practice, or new level.
               - Write a brief progress explanation explaining how today's journal demonstrates this level or growth.
            2. Write a concise daily summary of today's journal entry (2-3 sentences).
            3. Analyze if they have improved or grown compared to their previous entries. Be specific, highlight any milestones or emotional/practical improvements. If this is their first entry, welcome them and state their initial baseline.
            4. Infer any real-world physical or technical items owned, bought, upgraded, or mentioned as sold/destroyed/unusable today. For example, if they bought/got a computer (e.g. i5 or i7 PC), running shoes, smartphone, or mentioned sold/destroyed items. Each item must have:
               - name: Generic category name (e.g. "Laptop / PC", "Running Shoes", "Smartphone", "Car", "Desk Chair")
               - specification: Technical spec or model details (e.g. "Intel Core i5 CPU", "Nike Pegasus 40", "iPhone 15 Pro", "Aeron Chair")
               - buffRating: 1 to 10 scale of spec quality (higher specs like i9/Pro models get higher ratings, e.g., i3 CPU is 3/10, i5 is 5/10, i7 is 7/10, i9 is 9/10, M3 Max is 10/10)
               - specificationBenefit: Clear real-world benefit of this specification (e.g. "reduces compile times and increases programming efficiency", "superior cushion reduces joint pain during runs")
               - actionType: "ACQUIRED" (if brand new or mentioned first time), "UPGRADED" (if they replaced an older spec with this one), "SOLD" (if they sold it), "DESTROYED" (if it got broken), or "UNUSABLE" (if it's broken beyond use)
               - targetOfAction: If UPGRADED, SOLD, or DESTROYED, mention which previous specification is affected (e.g., "Intel Core i3 CPU")
            5. Infer any real-world benefit or harm factors experienced today. This is not some app-only game state, but analyzing the real world based on the journal entry:
               - name: e.g. "8-Hour Deep Sleep", "Insomnia", "Loud Roommate", "Sugar Crash", "Warm Chamomile Tea"
               - description: how it affected the user (e.g. "Slept peacefully for 8.5 hours, waking up with full cognitive rest and alertness", "Noisy roommates disrupted deep sleep cycle, leading to afternoon fatigue")
               - type: "BENEFIT" or "HARM"
               - intensity: 1 to 10 scale of impact
               - aspectAffected: Aspect of life affected (e.g., "Mental Focus", "Physical Energy", "Sleep Quality", "Stress Level", "Mood")

            Return the response strictly as a JSON object matching this schema. Do not write markdown tags or other texts.
        """.trimIndent()

        val systemInstruction = "You are a supportive, insightful personal growth coach. Analyze journal entries and return a structured JSON response tracking skills, physical inventory specifications/upgrades, real-world benefit or harm factors, and professional/social connections affecting their productivity and life."

        val schema = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "summary" to SchemaProperty(type = "STRING", description = "Concise summary of today's journal entry"),
                "progressAnalysis" to SchemaProperty(type = "STRING", description = "Growth and progress analysis compared to previous entries"),
                "skillsInferred" to SchemaProperty(
                    type = "ARRAY",
                    items = ResponseSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "name" to SchemaProperty(type = "STRING", description = "Name of the skill"),
                            "description" to SchemaProperty(type = "STRING", description = "General description of the skill"),
                             "category" to SchemaProperty(type = "STRING", description = "Category of the skill"),
                            "level" to SchemaProperty(type = "INTEGER", description = "Rating level from 1 to 10"),
                            "progressExplanation" to SchemaProperty(type = "STRING", description = "Explanation of how they grew or demonstrated this skill today")
                        ),
                        required = listOf("name", "description", "category", "level", "progressExplanation")
                    )
                ),
                "itemsInferred" to SchemaProperty(
                    type = "ARRAY",
                    items = ResponseSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "name" to SchemaProperty(type = "STRING", description = "Generic name of the item category"),
                            "specification" to SchemaProperty(type = "STRING", description = "Technical specs or model name"),
                            "buffRating" to SchemaProperty(type = "INTEGER", description = "1 to 10 rating based on specification quality"),
                            "specificationBenefit" to SchemaProperty(type = "STRING", description = "Explanation of how the spec benefits the user"),
                            "actionType" to SchemaProperty(type = "STRING", description = "ACQUIRED, UPGRADED, SOLD, DESTROYED, or UNUSABLE"),
                            "targetOfAction" to SchemaProperty(type = "STRING", description = "Specification affected by upgrade or deactivation")
                        ),
                        required = listOf("name", "specification", "buffRating", "specificationBenefit", "actionType")
                    )
                ),
                "buffsInferred" to SchemaProperty(
                    type = "ARRAY",
                    items = ResponseSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "name" to SchemaProperty(type = "STRING", description = "Name of the benefit or harm factor"),
                            "description" to SchemaProperty(type = "STRING", description = "Explanation of impact"),
                            "type" to SchemaProperty(type = "STRING", description = "BENEFIT or HARM"),
                            "intensity" to SchemaProperty(type = "INTEGER", description = "Impact intensity from 1 to 10"),
                            "aspectAffected" to SchemaProperty(type = "STRING", description = "Aspect affected: Mental Focus, Physical Energy, Sleep Quality, Stress, etc.")
                        ),
                        required = listOf("name", "description", "type", "intensity", "aspectAffected")
                    )
                ),
                "connectionsInferred" to SchemaProperty(
                    type = "ARRAY",
                    items = ResponseSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "name" to SchemaProperty(type = "STRING", description = "Name of the person"),
                            "relationType" to SchemaProperty(type = "STRING", description = "Coworker, Friend, Family, Mentor, Mentee, Rival, etc."),
                            "affinityLevel" to SchemaProperty(type = "INTEGER", description = "Interaction quality/closeness rating from 1 to 10"),
                            "specification" to SchemaProperty(type = "STRING", description = "Short summary of the interaction context")
                        ),
                        required = listOf("name", "relationType", "affinityLevel", "specification")
                    )
                )
            ),
            required = listOf("summary", "progressAnalysis", "skillsInferred")
        )

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.4f
            )
        )

        val response = RetrofitClient.service.generateContent(apiKey, request)
        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini API")

        return try {
            val adapter = RetrofitClient.apiMoshi.adapter(JournalAnalysisResult::class.java)
            adapter.fromJson(responseText) ?: throw Exception("Failed to parse analysis result JSON")
        } catch (e: Exception) {
            Log.e("JournalRepository", "JSON parsing failed for response: $responseText", e)
            throw e
        }
    }

    private suspend fun callGroqAPI(
        apiKey: String,
        content: String,
        previousEntries: List<JournalEntry>,
        existingSkills: List<Skill>,
        todayDate: String
    ): JournalAnalysisResult {
        val pastSummariesText = previousEntries.joinToString("\n") { "- ${it.date}: ${it.summary}" }
            .ifEmpty { "No previous entries." }

        val currentSkillsText = existingSkills.joinToString("\n") { "- ${it.name} (Level ${it.level}/10): ${it.description}" }
            .ifEmpty { "No skills registered yet." }

        val systemInstruction = """
            You are a supportive, insightful personal growth coach. Analyze journal entries and return a structured JSON response tracking skills, physical inventory specifications/upgrades, and real-world benefit or harm factors affecting their productivity and life.
            
            Return the response strictly as a JSON object matching this schema:
            {
              "summary": "Concise summary of today's journal entry (2-3 sentences)",
              "progressAnalysis": "Growth and progress analysis compared to previous entries",
              "skillsInferred": [
                {
                  "name": "Name of the skill (Capitalized, e.g. Kotlin Programming, Stress Management)",
                  "description": "General description of the skill",
                  "category": "Category of the skill (e.g. Technical, Fitness, Mindset)",
                  "level": 5,
                  "progressExplanation": "Explanation of how they grew or demonstrated this skill today"
                }
              ],
              "itemsInferred": [
                {
                  "name": "Generic category name (e.g. Laptop / PC, Running Shoes, Smartphone, Car, Desk Chair)",
                  "specification": "Technical spec or model details (e.g. Intel Core i5 CPU, Nike Pegasus 40, iPhone 15 Pro, Aeron Chair)",
                  "buffRating": 5,
                  "specificationBenefit": "Clear real-world benefit of this specification (e.g. reduces compile times and increases programming efficiency)",
                  "actionType": "ACQUIRED, UPGRADED, SOLD, DESTROYED, or UNUSABLE",
                  "targetOfAction": "If UPGRADED, SOLD, or DESTROYED, mention which previous specification is affected (e.g. Intel Core i3 CPU)"
                }
              ],
              "buffsInferred": [
                {
                  "name": "Name of the benefit or harm factor (e.g. 8-Hour Deep Sleep, Insomnia, Loud Roommate, Sugar Crash)",
                  "description": "How it affected the user",
                  "type": "BENEFIT or HARM",
                  "intensity": 8,
                  "aspectAffected": "Aspect of life affected (e.g., Mental Focus, Physical Energy, Sleep Quality, Stress Level, Mood)"
                }
              ]
            }
            Do not write markdown tags or other texts. Only output a valid JSON object.
        """.trimIndent()

        val prompt = """
            Analyze today's journal entry and compare it with the previous history.

            Today's Date: $todayDate
            Today's Journal Entry:
            "$content"

            --- Previous History ---
            Last 5 daily journal summaries:
            $pastSummariesText

            Current known skills and their levels:
            $currentSkillsText
        """.trimIndent()

        val messages = listOf(
            com.example.data.api.GroqMessage(role = "system", content = systemInstruction),
            com.example.data.api.GroqMessage(role = "user", content = prompt)
        )

        val request = com.example.data.api.GroqChatRequest(
            model = "llama-3.3-70b-versatile",
            messages = messages,
            temperature = 0.4f,
            response_format = com.example.data.api.GroqResponseFormat(type = "json_object")
        )

        val response = RetrofitClient.groqService.chatCompletions("Bearer $apiKey", request)
        val responseText = response.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from Groq API")

        return try {
            val adapter = RetrofitClient.apiMoshi.adapter(JournalAnalysisResult::class.java)
            adapter.fromJson(responseText) ?: throw Exception("Failed to parse analysis result JSON from Groq")
        } catch (e: Exception) {
            Log.e("JournalRepository", "JSON parsing failed for Groq response: $responseText", e)
            throw e
        }
    }

    suspend fun generateQuestionsForDate(date: String, forceOffline: Boolean = false): List<AiQuestion> = withContext(Dispatchers.IO) {
        val hasGemini = isKeyConfigured(BuildConfig.GEMINI_API_KEY, "MY_GEMINI_API_KEY")
        val hasGroq = isKeyConfigured(BuildConfig.GROQ_API_KEY, "MY_GROQ_API_KEY")
        val hasOpenCode = isKeyConfigured(BuildConfig.OPENCODE_API_KEY, "MY_OPENCODE_API_KEY")

        val useSimulation = forceOffline || (!hasGemini && !hasGroq && !hasOpenCode)

        if (useSimulation) {
            return@withContext generateOfflineQuestions(date)
        }

        var result: List<AiQuestion>? = null
        val errors = mutableListOf<String>()

        // 1. Attempt Google Gemini API
        if (hasGemini) {
            try {
                result = retryWithBackoff {
                    callGeminiForQuestionsWithKey(BuildConfig.GEMINI_API_KEY, date)
                }
                Log.d("JournalRepository", "Successfully generated questions using Google Gemini API")
            } catch (e: Exception) {
                Log.e("JournalRepository", "Google Gemini API questions generation failed, trying next fallback", e)
                errors.add("Google Gemini: ${e.localizedMessage}")
            }
        }

        // 2. Attempt Groq API
        if (result == null && hasGroq) {
            try {
                result = retryWithBackoff {
                    callGroqForQuestions(BuildConfig.GROQ_API_KEY, date)
                }
                Log.d("JournalRepository", "Successfully generated questions using Groq API")
            } catch (e: Exception) {
                Log.e("JournalRepository", "Groq API questions generation failed, trying next fallback", e)
                errors.add("Groq: ${e.localizedMessage}")
            }
        }

        // 3. Attempt OpenCode Zen API
        if (result == null && hasOpenCode) {
            try {
                result = retryWithBackoff {
                    callGeminiForQuestionsWithKey(BuildConfig.OPENCODE_API_KEY, date)
                }
                Log.d("JournalRepository", "Successfully generated questions using OpenCode Zen API")
            } catch (e: Exception) {
                Log.e("JournalRepository", "OpenCode Zen API questions generation failed", e)
                errors.add("OpenCode Zen: ${e.localizedMessage}")
            }
        }

        result ?: run {
            val combinedError = errors.joinToString("; ")
            Log.e("JournalRepository", "All AI APIs failed to generate questions, falling back to offline questions. Errors: $combinedError")
            generateOfflineQuestions(date)
        }
    }

    private suspend fun callGeminiForQuestionsWithKey(apiKey: String, date: String): List<AiQuestion> {
        val existingSkills = skillDao.getAllSkills().firstOrNull() ?: emptyList()
        val skillsText = existingSkills.joinToString(", ") { it.name }.ifEmpty { "no skills yet" }

        val prompt = """
            Generate exactly 3 personalized growth, reflective, or RPG-themed questions for the user to answer in their growth journal today.
            Today's Date: $date
            User's Current Skills: $skillsText

            Instructions:
            1. Make the questions highly engaging, specific, and reflective.
            2. Tailor them slightly toward the user's current skills if possible (e.g. if they have "Kotlin Programming", ask a coding question; if they have no skills, ask open-ended personal growth questions).
            3. Frame them in a motivating personal coach or RPG quest style (e.g., "What was your main quest today?", "What mental state did you experience?").
            4. Keep questions concise (1 sentence each).

            Return the response strictly as a JSON object matching this schema. Do not write markdown tags or other texts.
        """.trimIndent()

        val systemInstruction = "You are an inspiring, helpful growth coach. Generate 3 engaging daily questions for a user's skills and accomplishments journal."

        val schema = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "questions" to SchemaProperty(
                    type = "ARRAY",
                    items = ResponseSchema(type = "STRING")
                )
            ),
            required = listOf("questions")
        )

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.7f
            )
        )

        val response = RetrofitClient.service.generateContent(apiKey, request)
        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini API for questions")

        return try {
            val adapter = RetrofitClient.apiMoshi.adapter(QuestionListResponse::class.java)
            val result = adapter.fromJson(responseText) ?: throw Exception("Failed to parse questions JSON")
            result.questions.map { AiQuestion(question = it, date = date) }
        } catch (e: Exception) {
            Log.e("JournalRepository", "JSON parsing failed for questions: $responseText", e)
            throw e
        }
    }

    private suspend fun callGroqForQuestions(apiKey: String, date: String): List<AiQuestion> {
        val existingSkills = skillDao.getAllSkills().firstOrNull() ?: emptyList()
        val skillsText = existingSkills.joinToString(", ") { it.name }.ifEmpty { "no skills yet" }

        val systemInstruction = """
            You are an inspiring, helpful growth coach. Generate 3 engaging daily questions for a user's skills and accomplishments journal.
            
            Return the response strictly as a JSON object matching this schema:
            {
              "questions": [
                "concise question 1",
                "concise question 2",
                "concise question 3"
              ]
            }
            Do not write markdown tags or other texts. Only output a valid JSON object.
        """.trimIndent()

        val prompt = """
            Generate exactly 3 personalized growth, reflective, or RPG-themed questions for the user to answer in their growth journal today.
            Today's Date: $date
            User's Current Skills: $skillsText

            Instructions:
            1. Make the questions highly engaging, specific, and reflective.
            2. Tailor them slightly toward the user's current skills if possible (e.g. if they have "Kotlin Programming", ask a coding question; if they have no skills, ask open-ended personal growth questions).
            3. Frame them in a motivating personal coach or RPG quest style (e.g., "What was your main quest today?", "What mental state did you experience?").
            4. Keep questions concise (1 sentence each).
        """.trimIndent()

        val messages = listOf(
            com.example.data.api.GroqMessage(role = "system", content = systemInstruction),
            com.example.data.api.GroqMessage(role = "user", content = prompt)
        )

        val request = com.example.data.api.GroqChatRequest(
            model = "llama-3.3-70b-versatile",
            messages = messages,
            temperature = 0.7f,
            response_format = com.example.data.api.GroqResponseFormat(type = "json_object")
        )

        val response = RetrofitClient.groqService.chatCompletions("Bearer $apiKey", request)
        val responseText = response.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from Groq API for questions")

        return try {
            val adapter = RetrofitClient.apiMoshi.adapter(QuestionListResponse::class.java)
            val result = adapter.fromJson(responseText) ?: throw Exception("Failed to parse questions JSON from Groq")
            result.questions.map { AiQuestion(question = it, date = date) }
        } catch (e: Exception) {
            Log.e("JournalRepository", "JSON parsing failed for Groq questions: $responseText", e)
            throw e
        }
    }

    private fun generateOfflineQuestions(date: String): List<AiQuestion> {
        val allOptions = listOf(
            "What was the main quest (important goal) you conquered today?",
            "Did you battle any major bugs or setbacks? How did you defeat them?",
            "What new knowledge or skill did you acquire in your 'inventory' today?",
            "Which of your current skills did you feel most confident practicing today?",
            "How did you manage your mental energy or focus levels during your tasks?",
            "Did you unlock any minor milestones or make a breakthrough today?",
            "What did you learn today that you want to apply to tomorrow's quests?"
        )
        // Shuffle or pick based on the day's hash so they change daily but remain consistent for the day
        val seed = date.hashCode()
        val random = kotlin.random.Random(seed.toLong())
        val shuffled = allOptions.shuffled(random)
        return shuffled.take(3).map { AiQuestion(question = it, date = date) }
    }

    private fun simulateAnalysis(
        content: String,
        previousEntries: List<JournalEntry>,
        existingSkills: List<Skill>,
        errorMsg: String? = null
    ): JournalAnalysisResult {
        // Simple heuristic rules to make simulation feel smart!
        val skills = mutableListOf<InferredSkill>()

        val lowerContent = content.lowercase(Locale.getDefault())

        // Heuristic analysis
        if (lowerContent.contains("program") || lowerContent.contains("code") || lowerContent.contains("develop") || lowerContent.contains("kotlin") || lowerContent.contains("java") || lowerContent.contains("android")) {
            val name = "Software Engineering"
            val existing = existingSkills.find { it.name.lowercase() == name.lowercase() }
            val nextLevel = minOf(10, (existing?.level ?: 2) + 1)
            skills.add(
                InferredSkill(
                    name = "Software Engineering",
                    description = "Writing, designing, and maintaining code for apps and systems.",
                    category = "Technical",
                    level = nextLevel,
                    progressExplanation = "Extracted from coding journal. Demonstrated continuous learning and development in software crafting."
                )
            )
        }

        if (lowerContent.contains("learn") || lowerContent.contains("read") || lowerContent.contains("study") || lowerContent.contains("skill")) {
            val name = "Continuous Learning"
            val existing = existingSkills.find { it.name.lowercase() == name.lowercase() }
            val nextLevel = minOf(10, (existing?.level ?: 3) + 1)
            skills.add(
                InferredSkill(
                    name = "Continuous Learning",
                    description = "Active acquisition of new skills, knowledge, and capabilities.",
                    category = "Mindset",
                    level = nextLevel,
                    progressExplanation = "Demonstrated high interest in studying and acquiring new competencies today."
                )
            )
        }

        if (lowerContent.contains("exercise") || lowerContent.contains("run") || lowerContent.contains("gym") || lowerContent.contains("workout") || lowerContent.contains("health") || lowerContent.contains("sport")) {
            val name = "Physical Fitness"
            val existing = existingSkills.find { it.name.lowercase() == name.lowercase() }
            val nextLevel = minOf(10, (existing?.level ?: 2) + 1)
            skills.add(
                InferredSkill(
                    name = "Physical Fitness",
                    description = "Maintaining cardiovascular strength, flexibility, and muscle tone through regular workouts.",
                    category = "Fitness",
                    level = nextLevel,
                    progressExplanation = "Reflected a healthy workout routine or physical exercise mentioned in journal."
                )
            )
        }

        if (lowerContent.contains("feel") || lowerContent.contains("happy") || lowerContent.contains("sad") || lowerContent.contains("anxious") || lowerContent.contains("stress") || lowerContent.contains("calm") || lowerContent.contains("meditat")) {
            val name = "Emotional Intelligence"
            val existing = existingSkills.find { it.name.lowercase() == name.lowercase() }
            val nextLevel = minOf(10, (existing?.level ?: 4) + 1)
            skills.add(
                InferredSkill(
                    name = "Emotional Intelligence",
                    description = "Awareness, processing, and regulation of complex emotions and stress.",
                    category = "Mindset",
                    level = nextLevel,
                    progressExplanation = "Presents deep reflection on inner feelings, self-awareness, and emotional processes."
                )
            )
        }

        if (lowerContent.contains("work") || lowerContent.contains("project") || lowerContent.contains("task") || lowerContent.contains("manage")) {
            val name = "Productivity & Execution"
            val existing = existingSkills.find { it.name.lowercase() == name.lowercase() }
            val nextLevel = minOf(10, (existing?.level ?: 3) + 1)
            skills.add(
                InferredSkill(
                    name = "Productivity & Execution",
                    description = "Managing workloads, executing daily actions, and getting tasks finished.",
                    category = "Technical",
                    level = nextLevel,
                    progressExplanation = "Extracted from task completion or work project details. Shows structured goal focus."
                )
            )
        }

        // Fallback skill if none found
        if (skills.isEmpty()) {
            val name = "Self-Reflection"
            val existing = existingSkills.find { it.name.lowercase() == name.lowercase() }
            val nextLevel = minOf(10, (existing?.level ?: 1) + 1)
            skills.add(
                InferredSkill(
                    name = "Self-Reflection",
                    description = "Analyzing one's thoughts, experiences, and actions to promote personal growth.",
                    category = "Mindset",
                    level = nextLevel,
                    progressExplanation = "By writing in this skill journal, you exercised self-reflection and personal baseline measurement."
                )
            )
        }

        val itemsInferred = mutableListOf<InferredItem>()
        val buffsInferred = mutableListOf<InferredBuff>()

        // Specific Heuristics for physical specifications
        if (lowerContent.contains("i3")) {
            itemsInferred.add(
                InferredItem(
                    name = "Workstation Computer",
                    specification = "Intel Core i3 CPU",
                    buffRating = 3,
                    specificationBenefit = "Standard daily tasks, light web browsing, and writing baseline text.",
                    actionType = "ACQUIRED"
                )
            )
        } else if (lowerContent.contains("i5")) {
            itemsInferred.add(
                InferredItem(
                    name = "Workstation Computer",
                    specification = "Intel Core i5 CPU",
                    buffRating = 5,
                    specificationBenefit = "Moderate multitasking, smooth IDE execution, and comfortable development.",
                    actionType = "ACQUIRED"
                )
            )
        } else if (lowerContent.contains("i7")) {
            itemsInferred.add(
                InferredItem(
                    name = "Workstation Computer",
                    specification = "Intel Core i7 CPU",
                    buffRating = 7,
                    specificationBenefit = "High-speed multi-threaded compilation and seamless emulation environments.",
                    actionType = "ACQUIRED"
                )
            )
        } else if (lowerContent.contains("i9")) {
            itemsInferred.add(
                InferredItem(
                    name = "Workstation Computer",
                    specification = "Intel Core i9 CPU",
                    buffRating = 9,
                    specificationBenefit = "Elite workstation speed, hyper-threaded multitasking, cutting compilation times in half.",
                    actionType = "UPGRADED",
                    targetOfAction = "Intel Core i7 CPU"
                )
            )
        } else if (lowerContent.contains("macbook") || lowerContent.contains("m3")) {
            itemsInferred.add(
                InferredItem(
                    name = "Workstation Computer",
                    specification = "Apple M3 Max CPU",
                    buffRating = 10,
                    specificationBenefit = "Ultra-premium performance, instant project indexing, and outstanding silent battery endurance.",
                    actionType = "ACQUIRED"
                )
            )
        }

        if (lowerContent.contains("shoe") || lowerContent.contains("nike") || lowerContent.contains("pegasus")) {
            itemsInferred.add(
                InferredItem(
                    name = "Running Shoes",
                    specification = "Nike Pegasus 40",
                    buffRating = 8,
                    specificationBenefit = "Advanced cushion protects heels and knees, reducing running stress fatigue by 15%.",
                    actionType = "ACQUIRED"
                )
            )
        }

        if (lowerContent.contains("phone") || lowerContent.contains("iphone")) {
            itemsInferred.add(
                InferredItem(
                    name = "Smart Phone",
                    specification = "iPhone 15 Pro Max",
                    buffRating = 9,
                    specificationBenefit = "Fluid communication, instant search indexing, and reliable planning organization.",
                    actionType = "ACQUIRED"
                )
            )
        }

        // Sell or destroy triggers
        if (lowerContent.contains("sold my pc") || lowerContent.contains("sold my old computer")) {
            itemsInferred.add(
                InferredItem(
                    name = "Workstation Computer",
                    specification = "Intel Core i3 CPU",
                    buffRating = 3,
                    specificationBenefit = "None",
                    actionType = "SOLD"
                )
            )
        }
        if (lowerContent.contains("broke my phone") || lowerContent.contains("phone cracked")) {
            itemsInferred.add(
                InferredItem(
                    name = "Smart Phone",
                    specification = "iPhone 15 Pro Max",
                    buffRating = 9,
                    specificationBenefit = "None",
                    actionType = "UNUSABLE"
                )
            )
        }

        // Real-world benefits / harms analysis
        if (lowerContent.contains("sleep") || lowerContent.contains("slept")) {
            if (lowerContent.contains("good") || lowerContent.contains("great") || lowerContent.contains("rested") || lowerContent.contains("8 hour") || lowerContent.contains("deep")) {
                buffsInferred.add(
                    InferredBuff(
                        name = "8-Hour Deep Sleep",
                        description = "Slept fully and peacefully, resetting physical energy and clearing morning brain fog.",
                        type = "BENEFIT",
                        intensity = 8,
                        aspectAffected = "Mental Focus & Energy"
                    )
                )
            } else {
                buffsInferred.add(
                    InferredBuff(
                        name = "Sleep Deprivation",
                        description = "Interrupted sleep or late bedtime causing grogginess and low cognitive endurance.",
                        type = "HARM",
                        intensity = 6,
                        aspectAffected = "Cognitive Focus"
                    )
                )
            }
        }

        if (lowerContent.contains("tea") || lowerContent.contains("tee")) {
            buffsInferred.add(
                InferredBuff(
                    name = "Chamomile Tea Calm",
                    description = "Warm herbal infusion reducing baseline stress, soothing digestion, and calming anxiety.",
                    type = "BENEFIT",
                    intensity = 5,
                    aspectAffected = "Stress Relief"
                )
            )
        }

        if (lowerContent.contains("coffee") || lowerContent.contains("caffeine")) {
            buffsInferred.add(
                InferredBuff(
                    name = "Caffeine Focus Spike",
                    description = "Temporary espresso-driven dopamine surge, boosting writing speed and instant task motivation.",
                    type = "BENEFIT",
                    intensity = 7,
                    aspectAffected = "Mental Alertness"
                )
            )
        }

        if (lowerContent.contains("headache") || lowerContent.contains("head ache") || lowerContent.contains("pain")) {
            buffsInferred.add(
                InferredBuff(
                    name = "Tension Headache",
                    description = "Painful dull pressure around the temples making long stretches of screen reading extremely uncomfortable.",
                    type = "HARM",
                    intensity = 5,
                    aspectAffected = "Focus & Comfort"
                )
            )
        }

        if (lowerContent.contains("noise") || lowerContent.contains("noisy") || lowerContent.contains("disturb")) {
            buffsInferred.add(
                InferredBuff(
                    name = "Noisy Background Distraction",
                    description = "Loud decibels in the surrounding room breaking logical thoughts during critical code writing.",
                    type = "HARM",
                    intensity = 4,
                    aspectAffected = "Focus & Attention"
                )
            )
        }

        val wordCount = content.split("\\s+".toRegex()).size
        val summary = "A daily journal reflection covering recent events and emotional thoughts, totaling $wordCount words."

        val baseAnalysis = if (previousEntries.isEmpty()) {
            "Welcome! This is your very first entry. We have recorded your baseline skills: ${skills.joinToString { it.name }}. Write every day to track your progress and see how these levels change over time!"
        } else {
            val improvedSkillsText = skills.joinToString { "${it.name} (+1)" }
            "Progress analyzed! Compared to your past records, you've shown active engagement in: $improvedSkillsText. Your consistent journal practice is helping you solidify these habits."
        }

        val progressAnalysis = if (errorMsg != null) {
            val friendlyError = when {
                errorMsg.contains("503") || errorMsg.contains("Service Unavailable", ignoreCase = true) ->
                    "The Gemini AI service is temporarily busy (503 Service Unavailable). We successfully performed local analysis so your skills were updated and recorded, and your alarm was safely dismissed!"
                errorMsg.contains("429") || errorMsg.contains("Too Many Requests", ignoreCase = true) ->
                    "Gemini AI rate limits were exceeded. We've used offline analysis to save your skills and dismiss your alarm without any interruption."
                else ->
                    "Gemini AI is currently offline or unreachable ($errorMsg). Local analysis took over to safeguard your skills and stop your alarm."
            }
            "ℹ️ $friendlyError\n\n$baseAnalysis"
        } else {
            "[Local Offline Mode (Key Unconfigured)]\n\n$baseAnalysis"
        }

        val connectionsInferred = mutableListOf<InferredConnection>()
        if (lowerContent.contains("alice") || lowerContent.contains("coworker") || lowerContent.contains("collaborat")) {
            connectionsInferred.add(
                InferredConnection(
                    name = "Alice",
                    relationType = "Coworker",
                    affinityLevel = 5,
                    specification = "Collaborated on technical tasks, code reviews, or engineering discussions."
                )
            )
        }
        if (lowerContent.contains("bob") || lowerContent.contains("mentor") || lowerContent.contains("mentee") || lowerContent.contains("apprentice")) {
            connectionsInferred.add(
                InferredConnection(
                    name = "Bob",
                    relationType = "Mentee",
                    affinityLevel = 6,
                    specification = "Shared knowledge in coding, pair programming, and engineering best practices."
                )
            )
        }
        if (lowerContent.contains("mom") || lowerContent.contains("dad") || lowerContent.contains("family") || lowerContent.contains("sister") || lowerContent.contains("brother") || lowerContent.contains("parent")) {
            val relationName = if (lowerContent.contains("mom")) "Mom" else if (lowerContent.contains("dad")) "Dad" else "Family Member"
            connectionsInferred.add(
                InferredConnection(
                    name = relationName,
                    relationType = "Family",
                    affinityLevel = 8,
                    specification = "Had a deeply supportive personal check-in or shared family quality time."
                )
            )
        }
        if (lowerContent.contains("charlie") || lowerContent.contains("friend") || lowerContent.contains("pal") || lowerContent.contains("buddy")) {
            connectionsInferred.add(
                InferredConnection(
                    name = "Charlie",
                    relationType = "Friend",
                    affinityLevel = 7,
                    specification = "Caught up on social topics, mental health wellness, or shared recreational hobbies."
                )
            )
        }

        return JournalAnalysisResult(
            summary = summary,
            progressAnalysis = progressAnalysis,
            skillsInferred = skills,
            itemsInferred = itemsInferred,
            buffsInferred = buffsInferred,
            connectionsInferred = connectionsInferred
        )
    }
}
