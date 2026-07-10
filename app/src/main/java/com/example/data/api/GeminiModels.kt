package com.example.data.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: ResponseSchema? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class ResponseSchema(
    val type: String,
    val description: String? = null,
    val properties: Map<String, SchemaProperty>? = null,
    val required: List<String>? = null,
    val items: ResponseSchema? = null
)

@JsonClass(generateAdapter = true)
data class SchemaProperty(
    val type: String,
    val description: String? = null,
    val items: ResponseSchema? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

@JsonClass(generateAdapter = true)
data class InferredSkill(
    val name: String,
    val description: String,
    val category: String,
    val level: Int, // 1 to 10
    val progressExplanation: String
)

@JsonClass(generateAdapter = true)
data class InferredItem(
    val name: String,                  // e.g. "Laptop / PC", "Running Shoes"
    val specification: String,          // e.g. "Intel Core i5 CPU", "Nike Pegasus 40"
    val buffRating: Int,               // 1 to 10 scale based on how good/advanced the spec is
    val specificationBenefit: String,  // how this spec benefits the user, e.g. "faster coding compile times"
    val actionType: String,            // "ACQUIRED" (new), "UPGRADED" (upgraded previous), "SOLD", "DESTROYED", "UNUSABLE"
    val targetOfAction: String? = null // if UPGRADED/SOLD/etc, what it acts on (e.g. "Intel Core i3 CPU")
)

@JsonClass(generateAdapter = true)
data class InferredBuff(
    val name: String,                  // e.g. "8-Hour Deep Sleep", "Insomnia", "Loud Neighbors"
    val description: String,           // e.g. "Provided immense physical and mental recovery"
    val type: String,                  // "BENEFIT" or "HARM"
    val intensity: Int,                // 1 to 10 scale of impact
    val aspectAffected: String         // e.g. "Mental Focus", "Physical Energy", "Stress"
)

@JsonClass(generateAdapter = true)
data class InferredConnection(
    val name: String,                  // e.g. "Alice", "Bob"
    val relationType: String,          // e.g. "Coworker", "Friend", "Family", "Mentor", "Mentee", "Rival"
    val affinityLevel: Int,            // 1 to 10 scale of connection strength
    val specification: String          // brief context of interaction
)

@JsonClass(generateAdapter = true)
data class JournalAnalysisResult(
    val summary: String,
    val progressAnalysis: String,
    val skillsInferred: List<InferredSkill>,
    val itemsInferred: List<InferredItem>? = null,
    val buffsInferred: List<InferredBuff>? = null,
    val connectionsInferred: List<InferredConnection>? = null
)

@JsonClass(generateAdapter = true)
data class QuestionListResponse(
    val questions: List<String>
)

@JsonClass(generateAdapter = true)
data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Float = 0.4f,
    val response_format: GroqResponseFormat? = null
)

@JsonClass(generateAdapter = true)
data class GroqMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class GroqResponseFormat(
    val type: String // "json_object"
)

@JsonClass(generateAdapter = true)
data class GroqChatResponse(
    val choices: List<GroqChoice>
)

@JsonClass(generateAdapter = true)
data class GroqChoice(
    val message: GroqMessage
)
