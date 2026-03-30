package com.itsikh.buddy.ai

import com.itsikh.buddy.AppConfig
import com.itsikh.buddy.data.models.ChildProfile
import com.itsikh.buddy.data.models.ChatMode
import com.itsikh.buddy.data.repository.ConversationRepository
import com.itsikh.buddy.data.repository.MemoryRepository
import com.itsikh.buddy.data.repository.VocabularyRepository
import com.itsikh.buddy.logging.AppLogger
import com.itsikh.buddy.security.SecureKeyManager
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Manages the context window for each AI conversation.
 *
 * Gender consistency — two layers:
 *   Layer 1: full gender section first in system prompt (conjugation tables + examples)
 *   Layer 2: short gender reminder prepended to every user message (fights context drift)
 *
 * Greeting variety — time-aware + session continuation:
 *   - Reads device clock → morning/afternoon/evening greeting
 *   - Loads last completed session summary for continuation context
 *   - Random style seed (1–6) forces different opener each session
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

    companion object {
        private const val TAG = "ConversationManager"
    }

    private fun buddyGender(): String =
        secureKeyManager.getKey(AppConfig.PREF_BUDDY_GENDER) ?: AppConfig.BUDDY_GENDER_GIRL

    private fun withGenderReminder(userMessage: String, profile: ChildProfile): String {
        val reminder = systemPromptBuilder.buildTurnReminder(profile.gender, buddyGender())
        return "$reminder\n\n$userMessage"
    }

    // ── Time of day ────────────────────────────────────────────────────────────

    private data class TimeContext(
        val hebrewGreeting: String,   // e.g. "בוקר טוב"
        val period: String            // "morning" / "afternoon" / "evening" / "night"
    )

    private fun timeContext(): TimeContext {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 5..11  -> TimeContext("בוקר טוב", "morning")
            hour in 12..16 -> TimeContext("צהריים טובים", "afternoon")
            hour in 17..21 -> TimeContext("ערב טוב", "evening")
            else           -> TimeContext("לילה טוב", "night")
        }
    }

    // ── Send message ──────────────────────────────────────────────────────────

    suspend fun sendMessage(
        profile: ChildProfile,
        sessionId: String,
        mode: ChatMode,
        userMessage: String
    ): String {
        AppLogger.d(TAG, "sendMessage() for ${profile.displayName}, session=$sessionId")
        return try {
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

            val response = aiRouter.chat(
                systemPrompt = systemPrompt,
                history      = recentMessages,
                userMessage  = withGenderReminder(userMessage, profile)
            )
            AppLogger.d(TAG, "sendMessage() response length=${response.length}")
            response
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendMessage() failed: ${e.message}", e)
            throw e
        }
    }

    // ── Engagement evaluation ─────────────────────────────────────────────────

    /**
     * Asks the AI to judge whether the child's responses in this session were genuine
     * and engaged (worth awarding coins), or just noise/random input.
     *
     * Returns `true` if the session was a real conversation, `false` otherwise.
     * On any error defaults to `true` (benefit of the doubt).
     */
    suspend fun evaluateEngagement(childMessages: List<com.itsikh.buddy.data.models.Message>): Boolean {
        if (childMessages.isEmpty()) return false
        val sample = childMessages.takeLast(10).joinToString("\n") { "- ${it.text}" }
        return try {
            val result = aiRouter.chat(
                systemPrompt = "You evaluate whether a child genuinely engaged in a conversation. Answer only YES or NO.",
                history      = emptyList(),
                userMessage  = """
                    A child had a conversation with an English-learning AI.
                    Here are the child's last responses:
                    $sample

                    Did the child genuinely try to respond and participate (even if imperfect or short)?
                    Answer YES if it looks like real effort, NO if it is mostly gibberish, random keys, or single meaningless characters.
                    Answer only: YES or NO
                """.trimIndent()
            )
            result.trim().uppercase().startsWith("YES")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Engagement eval failed (${e.message}), defaulting to true")
            true // benefit of the doubt on error
        }
    }

    // ── Greeting ──────────────────────────────────────────────────────────────

    suspend fun generateGreeting(profile: ChildProfile, mode: ChatMode): String {
        AppLogger.d(TAG, "generateGreeting() for ${profile.displayName}")
        return try {
        generateGreetingInternal(profile, mode)
        } catch (e: Exception) {
            AppLogger.e(TAG, "generateGreeting() failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun generateGreetingInternal(profile: ChildProfile, mode: ChatMode): String {
        val memoryContext  = memoryRepository.toPromptString(profile.id)
        val reviewWords    = vocabularyRepository.getDueForReview(profile.id)
        val sessionGoal    = lessonPlanner.buildSessionGoal(profile, mode)
        val buddy          = buddyGender()
        val buddyIsGirl    = buddy == AppConfig.BUDDY_GENDER_GIRL
        val childIsGirl    = profile.gender == "GIRL"
        val time           = timeContext()
        val genderReminder = systemPromptBuilder.buildTurnReminder(profile.gender, buddy)

        // Gendered address forms
        val verbSay  = if (childIsGirl) "אמרי"  else "אמור"
        val verbTell = if (childIsGirl) "ספרי"  else "ספר"
        val pronoun  = if (childIsGirl) "את"    else "אתה"
        val friendWord = if (buddyIsGirl) "החברה" else "החבר"
        val buddyReady = if (buddyIsGirl) "מוכנה" else "מוכן"
        val buddyHappy = if (buddyIsGirl) "שמחה"  else "שמח"

        // Last completed session summary for continuation context
        val recentSessions = conversationRepository.getRecentSessions(profile.id, 5)
        val lastCompletedSession = recentSessions.firstOrNull { it.endedAt != null }
        val lastSummary = lastCompletedSession?.sessionSummary
        val isFirstEver = recentSessions.none { it.endedAt != null }

        val systemPrompt = systemPromptBuilder.build(
            profile       = profile,
            memoryContext = memoryContext,
            sessionGoal   = sessionGoal,
            reviewWords   = reviewWords,
            mode          = mode,
            buddyGender   = buddy
        )

        // Random style seed (1–6) — ensures greeting varies every session
        val styleSeed = Random.nextInt(1, 7)

        val greetingInstruction = buildGreetingInstruction(
            profile       = profile,
            isFirstEver   = isFirstEver,
            hasMemory     = memoryContext.isNotBlank(),
            lastSummary   = lastSummary,
            time          = time,
            styleSeed     = styleSeed,
            childIsGirl   = childIsGirl,
            buddyIsGirl   = buddyIsGirl,
            verbSay       = verbSay,
            verbTell      = verbTell,
            pronoun       = pronoun,
            friendWord    = friendWord,
            buddyReady    = buddyReady,
            buddyHappy    = buddyHappy,
            mode          = mode
        )

        val greeting = aiRouter.chat(
            systemPrompt = systemPrompt,
            history      = emptyList(),
            userMessage  = "$genderReminder\n\n$greetingInstruction"
        )
        AppLogger.d(TAG, "generateGreeting() style=$styleSeed, isFirstEver=$isFirstEver, response length=${greeting.length}")
        return greeting
    }

    private fun buildGreetingInstruction(
        profile: ChildProfile,
        isFirstEver: Boolean,
        hasMemory: Boolean,
        lastSummary: String?,
        time: TimeContext,
        styleSeed: Int,
        childIsGirl: Boolean,
        buddyIsGirl: Boolean,
        verbSay: String,
        verbTell: String,
        pronoun: String,
        friendWord: String,
        buddyReady: String,
        buddyHappy: String,
        mode: ChatMode
    ): String {
        val name = profile.displayName

        // ── First-ever session ──────────────────────────────────────────────
        if (isFirstEver) {
            val firstModeHint = when (mode) {
                ChatMode.FREE_CHAT -> "End by asking $name to say: 'Hello!'"
                ChatMode.STORY_TIME -> "After introducing yourself, immediately start a story: 'היה היה...' and ask 'מה קורה עכשיו?'"
                ChatMode.ROLE_PLAY -> "After introducing yourself, propose a role-play: 'בוא${if (childIsGirl) "י" else ""} נדמיין — אנחנו...' and open the scene in English."
            }
            return """
                Generate the very FIRST greeting between Buddy and $name (age ${profile.age}).
                They have never spoken before. This is the first meeting.

                TIME: It is ${time.period}. Start with "${time.hebrewGreeting}".

                RULES:
                - MAX 3 short sentences total. SHORT = 5-8 words each.
                - Mostly Hebrew (80%), one English phrase.
                - Introduce yourself as Buddy, $friendWord האנגלי${if (childIsGirl) "ת" else ""} של $name.
                - $firstModeHint
                - Be ENERGETIC. Use 1-2 emojis.
                - YOU (Buddy) are ${if (buddyIsGirl) "a GIRL" else "a BOY"}: use "אני $buddyHappy", "אני $buddyReady"
            """.trimIndent()
        }

        // ── Returning user — style varies by seed ──────────────────────────
        val continuationHint = if (lastSummary != null) {
            "LAST SESSION SUMMARY (use only if relevant to style ${styleSeed}): $lastSummary"
        } else ""

        val styleDescription = when (styleSeed) {
            1 -> """
                Style: TIME-AWARE ENERGY BURST.
                Lead with "${time.hebrewGreeting} $name!" then one punchy Hebrew line about the time of day.
                End with a quick English challenge.
                Example vibe: "${time.hebrewGreeting} $name! ☀️ מוכן${if (childIsGirl) "ה" else ""} לאנגלית? say: 'Good ${time.period}!'"
            """.trimIndent()

            2 -> """
                Style: CONTINUE THE STORY / PICK UP WHERE WE LEFT OFF.
                Reference something from the last session summary (if available).
                Act like you just remembered something exciting from last time.
                ${if (lastSummary != null) "Last session context: $lastSummary" else "No last session — just act like you're excited to be back."}
                Example vibe: "היי $name! זוכר${if (childIsGirl) "ת" else ""} שדיברנו על...? 😄 בוא${if (childIsGirl) "י" else ""} נמשיך!"
            """.trimIndent()

            3 -> """
                Style: PLAYFUL CHALLENGE OPENER.
                Skip pleasantries. Go straight to a fun English challenge.
                Tease them a little: "יש לי שאלה קשה בשבילך..."
                Example vibe: "היי! 🎯 יש לי אתגר קטן — can you say: 'I am ready'? $pronoun יכול${if (childIsGirl) "ה" else ""}?"
            """.trimIndent()

            4 -> """
                Style: CURIOUS QUESTION OPENER.
                Ask about their day / what happened since last time.
                Short question in Hebrew, then invite English.
                Example vibe: "${time.hebrewGreeting}! מה קרה היום? 🤔 $verbTell לי ב-English!"
            """.trimIndent()

            5 -> """
                Style: COMPLIMENT + CHALLENGE.
                Open with a genuine compliment about their last session (if known) or about the time of day.
                Then immediately give an English challenge.
                Example vibe: "$name! 🌟 אני $buddyHappy לראות אותך. Quick — say: 'I'm back!'"
            """.trimIndent()

            else -> """
                Style: SURPRISE / RANDOM FUN.
                Start with something unexpected — a fun fact, a joke setup, or a random English word challenge.
                Keep it silly and short.
                Example vibe: "היי $name! 🐧 ידעת שפינגווין באנגלית זה 'penguin'? $verbSay לי!"
            """.trimIndent()
        }

        val modeContext = when (mode) {
            ChatMode.FREE_CHAT -> ""
            ChatMode.STORY_TIME -> """

                MODE: STORY TIME session!
                After the greeting line, IMMEDIATELY open a story — give the first sentence of a new story.
                Use a dramatic hook: "היה היה..." / "פתאום..." / "בלילה אחד..."
                Keep it short — just ONE story-starter sentence to pull them in.
                Then ask: "מה קורה עכשיו?" or "מה הגיבור עושה?"
                Do NOT mention "mode" or "story time" explicitly — just start telling!
            """.trimIndent()

            ChatMode.ROLE_PLAY -> """

                MODE: ROLE PLAY session!
                After the brief greeting, IMMEDIATELY propose one specific scenario and assign roles.
                Example: "בוא${if (childIsGirl) "י" else ""} נדמיין — אנחנו במסעדה איטלקית. אני המלצר, $name הלקוח${if (childIsGirl) "ה" else ""}. Ready?"
                Then open the scene IN ENGLISH: "Hello! Welcome! What would you like today?"
                Pick ONE scenario and commit — pizza restaurant, space mission, pet shop, supermarket, or new kid at school.
                Do NOT explain the rules — just start the scene!
            """.trimIndent()
        }

        return """
            Generate a greeting for $name returning to Buddy. Style: $styleSeed/6.

            $styleDescription

            $continuationHint
            $modeContext

            HARD RULES — DO NOT BREAK:
            - MAX 2-3 sentences total (including story/scene opener if applicable).
            - SHORT sentences (5-8 words each).
            - 80% Hebrew, 20% English.
            - 1-2 emojis only.
            - YOU (Buddy) are ${if (buddyIsGirl) "a GIRL — use: אני $buddyHappy, אני $buddyReady" else "a BOY — use: אני $buddyHappy, אני $buddyReady"}.
            - $name is ${if (childIsGirl) "a GIRL — use feminine address" else "a BOY — use masculine address"}.
        """.trimIndent()
    }
}
