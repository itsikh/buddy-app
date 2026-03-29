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
            return """
                Generate the very FIRST greeting between Buddy and $name (age ${profile.age}).
                They have never spoken before. This is the first meeting.

                TIME: It is ${time.period}. Start with "${time.hebrewGreeting}".

                RULES:
                - MAX 3 short sentences total. SHORT = 5-8 words each.
                - Mostly Hebrew (80%), one English phrase.
                - Introduce yourself as Buddy, $friendWord האנגלי${if (childIsGirl) "ת" else ""} של $name.
                - End by asking $name to say their first English word: "say: 'Hello!'"
                - Be ENERGETIC. Use 1-2 emojis.
                - YOU (Buddy) are ${if (buddyIsGirl) "a GIRL" else "a BOY"}: use "אני $buddyHappy", "אני $buddyReady"

                EXAMPLE of style (don't copy — create your own):
                "${time.hebrewGreeting} $name! 🎉 אני Buddy — $friendWord האנגלי${if (childIsGirl) "ת" else ""} שלך! בוא${if (childIsGirl) "י" else ""} נגיד ביחד: 'Hello!'"
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

        return """
            Generate a greeting for $name returning to Buddy. Style: $styleSeed/6.

            $styleDescription

            $continuationHint

            HARD RULES — DO NOT BREAK:
            - MAX 2-3 sentences. SHORT sentences (5-8 words each).
            - 80% Hebrew, 20% English. The English part = what you want them to SAY.
            - Do NOT use the exact same opening as the style example — create your own variation.
            - End with asking $name to say one English phrase.
            - 1-2 emojis only.
            - YOU (Buddy) are ${if (buddyIsGirl) "a GIRL — use: אני $buddyHappy, אני $buddyReady" else "a BOY — use: אני $buddyHappy, אני $buddyReady"}.
            - $name is ${if (childIsGirl) "a GIRL — use feminine address" else "a BOY — use masculine address"}.
        """.trimIndent()
    }
}
