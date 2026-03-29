package com.itsikh.buddy.ai

import com.itsikh.buddy.data.models.ChildProfile
import com.itsikh.buddy.data.models.ChatMode
import com.itsikh.buddy.data.repository.VocabularyRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Determines the learning goal for each session based on the child's current CEFR level
 * and grammar progression.
 *
 * Goals are injected into the AI system prompt so Buddy weaves them naturally into
 * conversation — the child never sees a "lesson plan", it just feels like a fun chat
 * where they happen to practice something useful.
 */
@Singleton
class LessonPlanner @Inject constructor(
    private val vocabularyRepository: VocabularyRepository
) {

    /**
     * Returns a concise session goal string to inject into the system prompt.
     * Rotates through grammar targets appropriate for the child's current CEFR level.
     */
    suspend fun buildSessionGoal(profile: ChildProfile, mode: ChatMode): String {
        val dueWords = vocabularyRepository.getDueForReview(profile.id)

        val grammarFocus = getGrammarFocus(profile)
        val vocabFocus   = if (dueWords.isNotEmpty()) {
            "Naturally reintroduce these words in conversation: ${dueWords.take(3).joinToString(", ") { it.word }}"
        } else {
            "Introduce 1-2 new words appropriate for ${profile.vocabularyLevel} level naturally in the conversation."
        }

        return buildString {
            appendLine(grammarFocus)
            appendLine(vocabFocus)
            if (mode == ChatMode.FREE_CHAT) {
                appendLine("Encourage ${profile.displayName} to produce full sentences, not just single words.")
            }
        }.trimEnd()
    }

    private fun getGrammarFocus(profile: ChildProfile): String {
        // Rotate through grammar targets based on level + session count proxy (timestamp modulo)
        val dayOfYear = java.time.LocalDate.now().dayOfYear
        return when (profile.speakingLevel) {
            "A1" -> {
                val targets = listOf(
                    "Focus on simple present tense: 'I like', 'I have', 'I go'.",
                    "Focus on basic 'to be': 'I am', 'you are', 'it is'.",
                    "Focus on simple yes/no questions: 'Do you like...?', 'Is it...?'.",
                    "Focus on 'I want / I don't want' expressions.",
                    "Focus on colors, numbers, and everyday objects vocabulary."
                )
                targets[dayOfYear % targets.size]
            }
            "A2" -> {
                val targets = listOf(
                    "Focus on simple past tense: 'I went', 'I saw', 'I played'.",
                    "Focus on describing with adjectives: 'The big dog was really funny'.",
                    "Focus on talking about daily routines with time expressions.",
                    "Focus on 'because' — giving reasons: 'I like it because...'.",
                    "Focus on 'and', 'but', 'so' to connect ideas in longer sentences."
                )
                targets[dayOfYear % targets.size]
            }
            "B1" -> {
                val targets = listOf(
                    "Focus on present perfect: 'Have you ever...?', 'I have never...'.",
                    "Focus on comparing things: 'bigger than', 'the most exciting'.",
                    "Focus on expressing opinions: 'I think...', 'In my opinion...'.",
                    "Focus on 'would like' for polite requests and future wishes.",
                    "Focus on talking about hypotheticals: 'If I could... I would...'"
                )
                targets[dayOfYear % targets.size]
            }
            else -> "Encourage natural conversation at the child's comfortable level."
        }
    }
}
