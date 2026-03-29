package com.itsikh.buddy.ai

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.itsikh.buddy.data.models.MemoryCategory
import com.itsikh.buddy.data.models.Message
import com.itsikh.buddy.data.repository.MemoryRepository
import com.itsikh.buddy.data.repository.VocabularyRepository
import com.itsikh.buddy.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs silently after each session to:
 *   1. Extract new facts the child mentioned → saved to MemoryFact table
 *   2. Identify new English words used → added to VocabularyItem table
 *   3. Generate a brief Hebrew summary for the parent dashboard
 *
 * Uses Claude (via AiRouter.analyze) for reliable structured JSON output.
 * Runs on a background coroutine — never blocks the UI.
 */
@Singleton
class MemoryExtractor @Inject constructor(
    private val aiRouter: AiRouter,
    private val memoryRepository: MemoryRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "MemoryExtractor"
    }

    data class ExtractionResult(
        val newFacts: List<ExtractedFact>,
        val newWords: List<String>,
        val hebrewSummary: String
    )

    data class ExtractedFact(
        val category: String,
        val key: String,
        val value: String
    )

    /**
     * Analyzes the session's messages and returns extracted facts, words, and summary.
     * Call this AFTER the session ends, on a background coroutine.
     */
    suspend fun extractFromSession(
        profileId: String,
        childName: String,
        messages: List<Message>
    ): ExtractionResult {
        if (messages.isEmpty()) return ExtractionResult(emptyList(), emptyList(), "")

        val conversationText = messages.joinToString("\n") { msg ->
            val speaker = if (msg.role == "user") childName else "Buddy"
            "$speaker: ${msg.text}"
        }

        val systemPrompt = """
            You are an assistant that analyzes English-learning conversations.
            You will receive a conversation between an AI (Buddy) and a Hebrew-speaking child learning English.

            Extract the following in valid JSON format:
            {
              "facts": [
                {"category": "FAMILY|INTERESTS|ACTIVITIES|SCHOOL|OTHER", "key": "short label", "value": "what was mentioned"}
              ],
              "new_english_words": ["word1", "word2"],
              "hebrew_summary": "2-3 sentence summary in Hebrew for the parent describing what was discussed and any progress"
            }

            Categories:
            - FAMILY: family members, pets
            - INTERESTS: hobbies, likes, dislikes, favorite things
            - ACTIVITIES: sports, lessons, regular activities
            - SCHOOL: grade, subjects, teachers, school events
            - OTHER: anything else personal

            For new_english_words: only include English words the child used or tried to use that seem new or challenging for them.
            Limit to 5 most significant words. Skip extremely common words (I, you, the, is, etc.).

            For hebrew_summary: write in simple Hebrew. Mention specific topics discussed and any positive moments.

            Return ONLY valid JSON, no explanation.
        """.trimIndent()

        return try {
            val response = aiRouter.analyze(systemPrompt, conversationText)

            // Parse the JSON response
            val jsonStart = response.indexOf('{')
            val jsonEnd   = response.lastIndexOf('}') + 1
            if (jsonStart < 0 || jsonEnd <= jsonStart) {
                AppLogger.w(TAG, "Could not find JSON in extraction response")
                return ExtractionResult(emptyList(), emptyList(), "")
            }

            val jsonStr = response.substring(jsonStart, jsonEnd)

            @Suppress("UNCHECKED_CAST")
            val parsed = gson.fromJson(jsonStr, Map::class.java) as Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val factsJson = parsed["facts"] as? List<Map<String, String>> ?: emptyList()
            val facts = factsJson.mapNotNull { f ->
                val cat = f["category"] ?: return@mapNotNull null
                val key = f["key"] ?: return@mapNotNull null
                val value = f["value"] ?: return@mapNotNull null
                ExtractedFact(cat, key, value)
            }

            @Suppress("UNCHECKED_CAST")
            val words = parsed["new_english_words"] as? List<String> ?: emptyList()
            val summary = parsed["hebrew_summary"] as? String ?: ""

            // Persist facts to DB
            facts.forEach { fact ->
                val category = runCatching { MemoryCategory.valueOf(fact.category) }
                    .getOrDefault(MemoryCategory.OTHER)
                memoryRepository.saveFact(profileId, category, fact.key, fact.value)
            }

            // Persist new vocabulary
            words.forEach { word ->
                vocabularyRepository.addWordIfNew(profileId, word)
            }

            AppLogger.i(TAG, "Extracted ${facts.size} facts, ${words.size} new words")
            ExtractionResult(facts, words, summary)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Memory extraction failed: ${e.message}")
            ExtractionResult(emptyList(), emptyList(), "")
        }
    }
}
