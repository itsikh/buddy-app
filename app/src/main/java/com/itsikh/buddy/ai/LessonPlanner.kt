package com.itsikh.buddy.ai

import com.itsikh.buddy.data.models.ChildProfile
import com.itsikh.buddy.data.models.ChatMode
import com.itsikh.buddy.data.repository.VocabularyRepository
import com.itsikh.buddy.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Determines the learning goal for each session based on the child's current CEFR level
 * and grammar/vocabulary progression.
 *
 * Goals are injected into the AI system prompt so Buddy weaves them naturally into
 * conversation — the child never sees a "lesson plan", it just feels like a fun chat
 * where they happen to practice something useful.
 *
 * Each session targets:
 * 1. A grammar structure appropriate for the child's level
 * 2. A conversation topic with a rich, specific vocabulary bank
 *
 * Topics rotate daily so every session feels fresh and varied.
 */
@Singleton
class LessonPlanner @Inject constructor(
    private val vocabularyRepository: VocabularyRepository
) {
    companion object {
        private const val TAG = "LessonPlanner"
    }

    /**
     * Returns a concise session goal string to inject into the system prompt.
     * Rotates through grammar targets and vocabulary topics appropriate for the
     * child's current CEFR level.
     */
    suspend fun buildSessionGoal(profile: ChildProfile, mode: ChatMode): String {
        AppLogger.d(TAG, "buildSessionGoal() for ${profile.displayName}, level=${profile.speakingLevel}")
        return try {
            val dueWords = vocabularyRepository.getDueForReview(profile.id)
            val dayOfYear = java.time.LocalDate.now().dayOfYear

            val grammarFocus   = getGrammarFocus(profile, dayOfYear)
            val topicFocus     = getTopicWithVocabulary(profile, dayOfYear)
            val vocabReview    = if (dueWords.isNotEmpty()) {
                "REVIEW WORDS — reintroduce naturally in context: ${dueWords.take(3).joinToString(", ") { it.word }}"
            } else {
                ""
            }

            buildString {
                appendLine(grammarFocus)
                appendLine(topicFocus)
                if (vocabReview.isNotBlank()) appendLine(vocabReview)
                if (mode == ChatMode.FREE_CHAT) {
                    appendLine("In Free Chat: guide ${profile.displayName} toward producing full sentences, not single words.")
                }
            }.trimEnd()
        } catch (e: Exception) {
            AppLogger.e(TAG, "buildSessionGoal() failed, using fallback: ${e.message}", e)
            "Encourage natural, engaging conversation at ${profile.displayName}'s comfortable level."
        }
    }

    private fun getGrammarFocus(profile: ChildProfile, dayOfYear: Int): String {
        return when (profile.speakingLevel) {
            "A1" -> {
                val targets = listOf(
                    "Grammar: simple present — 'I like', 'I have', 'I want', 'I see'.",
                    "Grammar: basic 'to be' — 'I am happy', 'It is huge', 'They are funny'.",
                    "Grammar: simple yes/no questions — 'Do you like...?', 'Is it...?'.",
                    "Grammar: 'I want / I don't want' and simple preferences.",
                    "Grammar: 'There is / There are' — 'There is a dog in my house!'",
                    "Grammar: present continuous — 'I am playing', 'It is running'."
                )
                targets[dayOfYear % targets.size]
            }
            "A2" -> {
                val targets = listOf(
                    "Grammar: simple past — 'I went', 'I saw', 'I played', 'It was amazing'.",
                    "Grammar: adjectives + intensifiers — 'really big', 'so funny', 'incredibly fast'.",
                    "Grammar: 'because' for reasons — 'I love it because it is exciting'.",
                    "Grammar: connectors — 'and then', 'but', 'so', 'suddenly', 'after that'.",
                    "Grammar: 'going to' for plans — 'I am going to...'",
                    "Grammar: questions with 'what', 'where', 'when', 'why', 'how'."
                )
                targets[dayOfYear % targets.size]
            }
            "B1" -> {
                val targets = listOf(
                    "Grammar: present perfect — 'Have you ever...?', 'I have never...'",
                    "Grammar: comparatives + superlatives — 'bigger than', 'the most exciting'.",
                    "Grammar: expressing opinions — 'I think', 'In my opinion', 'I believe'.",
                    "Grammar: 'would like / would love' for wishes and polite requests.",
                    "Grammar: conditionals — 'If I could..., I would...'",
                    "Grammar: 'used to' — 'I used to be scared of dogs, but now I love them!'"
                )
                targets[dayOfYear % targets.size]
            }
            else -> "Encourage natural, varied language at the child's comfortable level."
        }
    }

    /**
     * Returns a conversation topic with a rich vocabulary bank for this session.
     * Topics and vocabulary rotate daily so each session introduces different words.
     */
    private fun getTopicWithVocabulary(profile: ChildProfile, dayOfYear: Int): String {
        val age = profile.age
        val level = profile.speakingLevel

        // Topics are age-sensitive — younger kids get more concrete, older kids get richer topics
        val topics = buildTopicList(age, level)
        val topic = topics[dayOfYear % topics.size]

        return buildString {
            appendLine("CONVERSATION TOPIC TODAY: ${topic.name}")
            appendLine("Drive the conversation naturally toward this topic.")
            appendLine("VOCABULARY BANK — choose 2-3 of these to teach today:")
            appendLine(topic.words.joinToString(", ") { "\"${it}\"" })
            appendLine("Teach each word in context — not as a list. Connect to what ${profile.displayName} actually says.")
        }.trimEnd()
    }

    private data class ConversationTopic(val name: String, val words: List<String>)

    private fun buildTopicList(age: Int, level: String): List<ConversationTopic> {
        // Core topics for all ages (concrete, relatable)
        val coreTopics = listOf(
            ConversationTopic(
                "Animals & Pets",
                if (level == "A1")
                    listOf("puppy", "kitten", "fluffy", "bark", "scratch", "enormous", "tiny", "fierce")
                else
                    listOf("enormous", "fierce", "creature", "whiskers", "snuggle", "wild", "extinct", "predator")
            ),
            ConversationTopic(
                "Food & Eating",
                if (level == "A1")
                    listOf("delicious", "yucky", "crunchy", "sweet", "spicy", "favourite", "hungry", "taste")
                else
                    listOf("delicious", "disgusting", "crunchy", "spicy", "starving", "recipe", "ingredients", "craving")
            ),
            ConversationTopic(
                "Sports & Games",
                if (level == "A1")
                    listOf("score", "win", "team", "practice", "fun", "champion", "cheer", "exciting")
                else
                    listOf("champion", "exhausted", "competition", "strategy", "incredible", "determined", "defeat", "victory")
            ),
            ConversationTopic(
                "Family & Friends",
                if (level == "A1")
                    listOf("funny", "kind", "silly", "together", "miss", "hug", "favourite", "proud")
                else
                    listOf("hilarious", "annoying", "proud", "celebrate", "memory", "adventure", "argument", "forgive")
            ),
            ConversationTopic(
                "School & Learning",
                if (level == "A1")
                    listOf("boring", "exciting", "difficult", "easy", "learn", "funny", "smart", "practice")
                else
                    listOf("confusing", "fascinating", "struggle", "improve", "curious", "achieve", "concentrate", "discover")
            ),
            ConversationTopic(
                "Imagination & Superpowers",
                if (level == "A1")
                    listOf("invisible", "fly", "magic", "powerful", "wish", "dream", "imagine", "incredible")
                else
                    listOf("invisible", "telepathy", "transform", "invincible", "incredible", "mysterious", "legend", "magical")
            ),
            ConversationTopic(
                "Funny & Scary Moments",
                if (level == "A1")
                    listOf("scared", "surprised", "nervous", "laugh", "shocked", "funny", "silly", "brave")
                else
                    listOf("terrifying", "hilarious", "embarrassed", "relieved", "panicked", "courageous", "unexpected", "shocked")
            ),
            ConversationTopic(
                "Adventures & Travel",
                if (level == "A1")
                    listOf("explore", "discover", "journey", "amazing", "different", "beautiful", "exciting", "far away")
                else
                    listOf("explore", "discover", "breathtaking", "ancient", "mysterious", "adventure", "exotic", "incredible")
            )
        )

        // Additional topics for older kids (age 9+)
        val olderTopics = if (age >= 9) listOf(
            ConversationTopic(
                "Movies, Shows & Characters",
                listOf("hilarious", "thrilling", "emotional", "plot twist", "villain", "heroic", "sequel", "recommend")
            ),
            ConversationTopic(
                "Dreams & Goals",
                listOf("ambitious", "determined", "achieve", "inspire", "challenge", "improve", "motivated", "extraordinary")
            ),
            ConversationTopic(
                "Nature & The World",
                listOf("enormous", "breathtaking", "extinct", "creature", "eruption", "hurricane", "incredible", "ecosystem")
            )
        ) else emptyList()

        return coreTopics + olderTopics
    }
}
