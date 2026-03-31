package com.itsikh.buddy.ai

import com.google.gson.Gson
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
        if (messages.isEmpty()) {
            AppLogger.i(TAG, "No messages to extract from — skipping")
            return ExtractionResult(emptyList(), emptyList(), "")
        }

        // Only include turns where the child actually said something meaningful
        val childMessages = messages.filter { it.role == "user" && it.text.trim().length > 2 }
        if (childMessages.isEmpty()) {
            AppLogger.i(TAG, "No child messages found (${messages.size} total msgs) — skipping extraction")
            return ExtractionResult(emptyList(), emptyList(), "")
        }

        val conversationText = messages.joinToString("\n") { msg ->
            val speaker = if (msg.role == "user") childName else "Buddy"
            "$speaker: ${msg.text}"
        }

        AppLogger.i(TAG, "Starting extraction: ${messages.size} messages, ${childMessages.size} child turns")

        val systemPrompt = """
            You analyze conversations between an AI called Buddy and a Hebrew-speaking child learning English.
            Extract ALL personal information the child revealed — even small mentions count.

            Return ONLY valid JSON in this exact structure:
            {
              "facts": [
                {"category": "FAMILY|INTERESTS|ACTIVITIES|SCHOOL|OTHER", "key": "short label in English", "value": "what was mentioned in English"}
              ],
              "new_english_words": [
                {"word": "elephant", "hebrew": "פיל"},
                {"word": "delicious", "hebrew": "טעים מאוד"}
              ],
              "hebrew_summary": "2-3 sentence summary in Hebrew for parents"
            }

            CATEGORY GUIDE:
            - FAMILY: parents, siblings, grandparents, pets, family activities, family members mentioned
            - INTERESTS: hobbies, favourite things, games, music, movies, characters, books, food preferences
            - ACTIVITIES: sports, lessons, clubs, after-school activities, weekend activities
            - SCHOOL: grade level, subjects liked or disliked, teachers mentioned, school events
            - OTHER: anything else personal — dreams, fears, funny stories, places visited, achievements

            EXTRACTION RULES — be generous, extract everything:
            - Extract EVERY personal detail, even if mentioned briefly or casually.
            - If the child mentioned a pet → extract it. A sibling name → extract it. A favourite food → extract it.
            - Also extract things the child DOESN'T like (key: "dislikes food", value: "broccoli").
            - Use short, clear English keys: "pet type", "sibling name", "favourite sport", "school grade".
            - Values should be brief but complete: "a dog named Bobo" not just "dog".
            - If nothing personal was mentioned at all, return facts as an empty array [].

            FOR new_english_words:
            - Include English words the child USED or TRIED TO USE during the session.
            - Include words they learned during this session that were new to them.
            - Skip extremely basic words (I, you, the, is, a, and).
            - Limit to the 5 most significant words.
            - Each entry must be an object with "word" (English) and "hebrew" (a short Hebrew definition, 1-3 words).
            - If no notable English words, return [].
            - Example: [{"word": "elephant", "hebrew": "פיל"}, {"word": "delicious", "hebrew": "טעים מאוד"}]

            FOR hebrew_summary:
            - 2-3 sentences in natural Hebrew.
            - Mention topics discussed, something the child shared about themselves, and any English progress.
            - Example: "היום דיברנו על חיות מחמד ועל הכלב של דני שנקרא בובו. דני שיתף שהוא אוהב כדורגל ומשחק כל יום. הצליח לומר 'my favourite sport is football' בצורה מושלמת!"

            Return ONLY the JSON object. No explanation. No markdown. No code blocks. Just the raw JSON.
        """.trimIndent()

        return try {
            val response = aiRouter.analyze(systemPrompt, conversationText)
            AppLogger.i(TAG, "AI extraction response (${response.length} chars): ${response.take(300)}")

            // Strip markdown code blocks if present (```json ... ``` or ``` ... ```)
            val cleaned = response
                .replace(Regex("^```(?:json)?\\s*", RegexOption.MULTILINE), "")
                .replace(Regex("```\\s*$", RegexOption.MULTILINE), "")
                .trim()

            val jsonStart = cleaned.indexOf('{')
            val jsonEnd   = cleaned.lastIndexOf('}') + 1
            if (jsonStart < 0 || jsonEnd <= jsonStart) {
                AppLogger.w(TAG, "Could not find JSON in extraction response: ${cleaned.take(100)}")
                return ExtractionResult(emptyList(), emptyList(), "")
            }

            val jsonStr = cleaned.substring(jsonStart, jsonEnd)
            AppLogger.d(TAG, "Parsed JSON: ${jsonStr.take(300)}")

            @Suppress("UNCHECKED_CAST")
            val parsed = gson.fromJson(jsonStr, Map::class.java) as Map<String, Any>

            // Parse facts — Gson returns List<LinkedTreeMap<String,Any>> for JSON arrays of objects
            @Suppress("UNCHECKED_CAST")
            val factsRaw = parsed["facts"] as? List<*> ?: emptyList<Any>()
            val facts = factsRaw.mapNotNull { item ->
                val f = item as? Map<*, *> ?: return@mapNotNull null
                val cat   = f["category"]?.toString() ?: return@mapNotNull null
                val key   = f["key"]?.toString()      ?: return@mapNotNull null
                val value = f["value"]?.toString()    ?: return@mapNotNull null
                if (key.isBlank() || value.isBlank()) return@mapNotNull null
                ExtractedFact(cat, key, value)
            }

            // Parse new English words — new format: [{word, hebrew}]; old format: ["word"]
            @Suppress("UNCHECKED_CAST")
            val wordsRaw = parsed["new_english_words"] as? List<*> ?: emptyList<Any>()
            // wordEntries: list of (word, hebrewDefinition?) pairs
            data class WordEntry(val word: String, val hebrew: String?)
            val wordEntries = wordsRaw.mapNotNull { item ->
                when (item) {
                    is Map<*, *> -> {
                        val w = item["word"]?.toString()?.trim()?.lowercase() ?: return@mapNotNull null
                        val h = item["hebrew"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
                        if (w.isBlank()) null else WordEntry(w, h)
                    }
                    else -> item?.toString()?.trim()?.lowercase()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { WordEntry(it, null) }
                }
            }
            val words = wordEntries.map { it.word }

            val summary = parsed["hebrew_summary"]?.toString() ?: ""

            // Persist facts to DB
            var savedFacts = 0
            facts.forEach { fact ->
                val category = runCatching { MemoryCategory.valueOf(fact.category) }
                    .getOrDefault(MemoryCategory.OTHER)
                memoryRepository.saveFact(profileId, category, fact.key, fact.value)
                savedFacts++
            }

            // Persist new vocabulary (with Hebrew definitions when available)
            var savedWords = 0
            wordEntries.forEach { entry ->
                vocabularyRepository.addWordIfNew(profileId, entry.word, entry.hebrew)
                savedWords++
            }

            AppLogger.i(TAG, "Extraction done: $savedFacts facts saved, $savedWords words saved. Facts: ${facts.map { "${it.key}=${it.value}" }}")
            ExtractionResult(facts, words, summary)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Memory extraction failed: ${e.javaClass.simpleName}: ${e.message}", e)
            ExtractionResult(emptyList(), emptyList(), "")
        }
    }
}
