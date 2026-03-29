package com.itsikh.buddy.ai

import com.itsikh.buddy.AppConfig
import com.itsikh.buddy.data.models.ChildProfile
import com.itsikh.buddy.data.models.ChatMode
import com.itsikh.buddy.data.models.Message
import com.itsikh.buddy.data.repository.ConversationRepository
import com.itsikh.buddy.data.repository.MemoryRepository
import com.itsikh.buddy.data.repository.VocabularyRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the context window for each AI conversation.
 *
 * Responsibility: given a child profile and session state, builds the complete
 * input to the AI — system prompt + memory context + recent messages + new user turn.
 *
 * Also enforces the MAX_CONTEXT_TURNS limit so we don't send unbounded token counts.
 */
@Singleton
class ConversationManager @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val systemPromptBuilder: SystemPromptBuilder,
    private val lessonPlanner: LessonPlanner,
    private val aiRouter: AiRouter
) {

    /**
     * Sends a user message and returns Buddy's response.
     *
     * Builds the complete context (system prompt + memory + history) on every call.
     * The AI is stateless — full context is re-sent each time.
     *
     * @param profile     The child's profile (CEFR level, name, age)
     * @param sessionId   Current session ID (used to pull session-scoped history)
     * @param mode        Current chat mode (FREE_CHAT, STORY_TIME, ROLE_PLAY)
     * @param userMessage The new message from the child
     */
    suspend fun sendMessage(
        profile: ChildProfile,
        sessionId: String,
        mode: ChatMode,
        userMessage: String
    ): String {
        // 1. Gather context ingredients
        val memoryContext  = memoryRepository.toPromptString(profile.id)
        val reviewWords    = vocabularyRepository.getDueForReview(profile.id)
        val sessionGoal    = lessonPlanner.buildSessionGoal(profile, mode)
        val recentMessages = conversationRepository.getRecentMessages(
            profile.id, AppConfig.MAX_CONTEXT_TURNS
        )

        // 2. Build system prompt
        val systemPrompt = systemPromptBuilder.build(
            profile        = profile,
            memoryContext  = memoryContext,
            sessionGoal    = sessionGoal,
            reviewWords    = reviewWords,
            mode           = mode
        )

        // 3. Call AI with routing + fallback
        return aiRouter.chat(
            systemPrompt = systemPrompt,
            history      = recentMessages,
            userMessage  = userMessage
        )
    }

    /**
     * Generates the opening message Buddy says at the start of a session.
     * Buddy greets the child by name and references something it knows about them.
     */
    suspend fun generateGreeting(profile: ChildProfile, mode: ChatMode): String {
        val memoryContext  = memoryRepository.toPromptString(profile.id)
        val reviewWords    = vocabularyRepository.getDueForReview(profile.id)
        val sessionGoal    = lessonPlanner.buildSessionGoal(profile, mode)

        val systemPrompt = systemPromptBuilder.build(
            profile       = profile,
            memoryContext = memoryContext,
            sessionGoal   = sessionGoal,
            reviewWords   = reviewWords,
            mode          = mode
        )

        val greetingPrompt = when {
            memoryContext.isNotBlank() -> """
                Generate a warm greeting for ${profile.displayName}.
                START with exactly one short Hebrew sentence to welcome them back (e.g. "יאללה, בוא נדבר אנגלית!").
                Then switch to English for the rest. Reference one specific thing you know about them.
                Keep the whole greeting to 2-3 sentences. End with ONE simple English question.
            """.trimIndent()
            else -> """
                Generate a warm first-time greeting for ${profile.displayName} who is ${profile.age} years old.
                START with exactly one short Hebrew sentence so they feel safe (e.g. "שלום! אני Buddy, חבר האנגלית שלך!").
                Then switch to English: introduce yourself as Buddy and invite them to chat.
                Keep it to 2-3 sentences total. End with one very simple question in English.
            """.trimIndent()
        }

        return aiRouter.chat(
            systemPrompt = systemPrompt,
            history      = emptyList(),
            userMessage  = greetingPrompt
        )
    }
}
