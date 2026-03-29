package com.itsikh.buddy.ai

import com.itsikh.buddy.AppConfig
import com.itsikh.buddy.data.models.ChildProfile
import com.itsikh.buddy.data.models.ChatMode
import com.itsikh.buddy.data.repository.ConversationRepository
import com.itsikh.buddy.data.repository.MemoryRepository
import com.itsikh.buddy.data.repository.VocabularyRepository
import com.itsikh.buddy.security.SecureKeyManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the context window for each AI conversation.
 *
 * Hebrew gender consistency strategy (multi-layer):
 *
 * Layer 1 — System prompt: gender declaration is the very first section,
 *           with full conjugation tables and WRONG vs. CORRECT examples.
 *
 * Layer 2 — Per-turn injection: every user message is prefixed with a short
 *           gender reminder. This fights "context drift" — the known problem
 *           where LLMs revert to masculine defaults after 10-20 turns because
 *           the system prompt loses attention weight as the conversation grows.
 *
 * Research basis: Microsoft multilingual misgendering study (EMNLP 2025) and
 * context drift analysis show that per-turn reinforcement is critical for
 * gender consistency in long Hebrew conversations.
 */
@Singleton
class ConversationManager @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val systemPromptBuilder: SystemPromptBuilder,
    private val lessonPlanner: LessonPlanner,
    private val aiRouter: AiRouter,
    private val secureKeyManager: SecureKeyManager
) {

    private fun buddyGender(): String =
        secureKeyManager.getKey(AppConfig.PREF_BUDDY_GENDER) ?: AppConfig.BUDDY_GENDER_GIRL

    /**
     * Prepends a short gender reminder to every user message.
     * This is Layer 2 of the gender enforcement strategy — keeps gender
     * anchored near the top of the most recent context window on every turn,
     * preventing drift back to masculine defaults in long conversations.
     */
    private fun withGenderReminder(userMessage: String, profile: ChildProfile): String {
        val reminder = systemPromptBuilder.buildTurnReminder(profile.gender, buddyGender())
        return "$reminder\n\n$userMessage"
    }

    /**
     * Sends a user message and returns Buddy's response.
     */
    suspend fun sendMessage(
        profile: ChildProfile,
        sessionId: String,
        mode: ChatMode,
        userMessage: String
    ): String {
        val memoryContext  = memoryRepository.toPromptString(profile.id)
        val reviewWords    = vocabularyRepository.getDueForReview(profile.id)
        val sessionGoal    = lessonPlanner.buildSessionGoal(profile, mode)
        val recentMessages = conversationRepository.getRecentMessages(
            profile.id, AppConfig.MAX_CONTEXT_TURNS
        )

        val systemPrompt = systemPromptBuilder.build(
            profile       = profile,
            memoryContext = memoryContext,
            sessionGoal   = sessionGoal,
            reviewWords   = reviewWords,
            mode          = mode,
            buddyGender   = buddyGender()
        )

        // Layer 2: inject gender reminder before the user's message
        return aiRouter.chat(
            systemPrompt = systemPrompt,
            history      = recentMessages,
            userMessage  = withGenderReminder(userMessage, profile)
        )
    }

    /**
     * Generates the opening greeting at session start.
     */
    suspend fun generateGreeting(profile: ChildProfile, mode: ChatMode): String {
        val memoryContext  = memoryRepository.toPromptString(profile.id)
        val reviewWords    = vocabularyRepository.getDueForReview(profile.id)
        val sessionGoal    = lessonPlanner.buildSessionGoal(profile, mode)
        val buddy          = buddyGender()
        val buddyIsGirl    = buddy == AppConfig.BUDDY_GENDER_GIRL
        val friendWord     = if (buddyIsGirl) "החברה" else "החבר"
        val buddyReady     = if (buddyIsGirl) "מוכנה" else "מוכן"

        val systemPrompt = systemPromptBuilder.build(
            profile       = profile,
            memoryContext = memoryContext,
            sessionGoal   = sessionGoal,
            reviewWords   = reviewWords,
            mode          = mode,
            buddyGender   = buddy
        )

        val childIsGirl = profile.gender == "GIRL"
        val verbTell    = if (childIsGirl) "ספרי"  else "ספר"
        val verbSay     = if (childIsGirl) "אמרי"  else "אמור"

        // The greeting prompt itself also carries the gender reminder
        val genderReminder = systemPromptBuilder.buildTurnReminder(profile.gender, buddy)

        val greetingContent = when {
            memoryContext.isNotBlank() -> """
                Generate a warm bilingual greeting for ${profile.displayName} — mix Hebrew and English naturally.
                Structure:
                1. Hebrew welcome back (1 sentence).
                2. English phrase referencing something you know about them (short, at their level).
                3. Hebrew instruction + English prompt: "$verbTell לי — say: 'I am happy to be here!'"
                Keep it to 3-4 short sentences. Use emojis.
                GENDER REMINDER: YOU (Buddy) are ${if (buddyIsGirl) "a GIRL (ילדה)" else "a BOY (ילד)"}.
                ${profile.displayName} is ${if (childIsGirl) "a GIRL (ילדה)" else "a BOY (ילד)"}.
            """.trimIndent()
            else -> """
                Generate a warm bilingual first meeting greeting for ${profile.displayName} (age ${profile.age}).
                Structure:
                1. Hebrew intro: "שלום ${profile.displayName}! אני Buddy — $friendWord האנגלי${if (childIsGirl) "ת" else ""} שלך! 🤖"
                2. English self-intro: "My name is Buddy and I love to chat!"
                3. Hebrew: "ביחד נדבר אנגלית — זה כיף, אני מבטיח${if (buddyIsGirl) "ה" else ""}! אני $buddyReady!"
                4. Hebrew prompt: "עכשיו $verbSay לי — say: 'Hello Buddy!'"
                Keep it warm, fun, short. Use emojis.
                GENDER REMINDER: YOU (Buddy) are ${if (buddyIsGirl) "a GIRL (ילדה)" else "a BOY (ילד)"}.
                ${profile.displayName} is ${if (childIsGirl) "a GIRL (ילדה)" else "a BOY (ילד)"}.
            """.trimIndent()
        }

        return aiRouter.chat(
            systemPrompt = systemPrompt,
            history      = emptyList(),
            userMessage  = "$genderReminder\n\n$greetingContent"
        )
    }
}
