package com.itsikh.buddy.gamification

import com.itsikh.buddy.data.models.ChildProfile
import com.itsikh.buddy.data.models.ChatMode
import com.itsikh.buddy.data.models.SessionLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates which new badges a child has earned after a session.
 *
 * Called at the end of each session with the current profile state.
 * Returns the list of newly earned badge IDs (caller is responsible for
 * persisting them and showing congratulations UI).
 */
@Singleton
class BadgeEvaluator @Inject constructor() {

    /**
     * @param profile          The current (post-session) child profile
     * @param alreadyEarned    Set of badge IDs already awarded
     * @param sessionLog       The just-completed session
     * @param totalSessions    Total completed sessions count
     * @param vocabularyMastered Total mastered vocabulary count
     * @return List of newly earned badge IDs
     */
    fun evaluate(
        profile: ChildProfile,
        alreadyEarned: Set<String>,
        sessionLog: SessionLog,
        totalSessions: Int,
        vocabularyMastered: Int
    ): List<String> {
        val newBadges = mutableListOf<String>()

        fun maybeAward(id: String) {
            if (id !in alreadyEarned) newBadges.add(id)
        }

        // Streak badges
        if (profile.streakDays >= 3)  maybeAward("streak_3")
        if (profile.streakDays >= 7)  maybeAward("streak_7")
        if (profile.streakDays >= 14) maybeAward("streak_14")
        if (profile.streakDays >= 30) maybeAward("streak_30")

        // Vocabulary badges
        if (vocabularyMastered >= 10)  maybeAward("vocab_10")
        if (vocabularyMastered >= 25)  maybeAward("vocab_25")
        if (vocabularyMastered >= 50)  maybeAward("vocab_50")
        if (vocabularyMastered >= 100) maybeAward("vocab_100")

        // Conversation badges
        if (totalSessions >= 1)  maybeAward("first_chat")
        if (totalSessions >= 5)  maybeAward("chat_5")
        if (totalSessions >= 20) maybeAward("chat_20")

        // CEFR level badges
        when (profile.cefrLevel) {
            "A1" -> maybeAward("level_a1")
            "A2" -> { maybeAward("level_a1"); maybeAward("level_a2") }
            "B1" -> { maybeAward("level_a1"); maybeAward("level_a2"); maybeAward("level_b1") }
        }

        // Mode-specific badges
        if (sessionLog.mode == ChatMode.STORY_TIME)  maybeAward("first_story")
        if (sessionLog.mode == ChatMode.ROLE_PLAY)   maybeAward("first_rp")

        // Secret badges
        if (sessionLog.durationMinutes >= 3 && sessionLog.turnCount >= 6) maybeAward("talkative")

        return newBadges
    }
}
