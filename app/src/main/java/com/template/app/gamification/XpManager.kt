package com.template.app.gamification

import com.template.app.AppConfig
import com.template.app.data.models.ChildProfile
import com.template.app.data.repository.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates and awards XP for sessions and achievements.
 *
 * XP is awarded for:
 * - Each minute of session time
 * - Each new vocabulary word introduced
 * - Streak bonus (when streak >= 3 days)
 */
@Singleton
class XpManager @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    /** Calculates XP earned for a session and saves it to the profile. */
    suspend fun awardSessionXp(
        profile: ChildProfile,
        durationMinutes: Int,
        newWordsCount: Int
    ): Int {
        val baseXp    = durationMinutes * AppConfig.XP_PER_SESSION_MINUTE
        val wordXp    = newWordsCount * AppConfig.XP_PER_NEW_WORD
        val streakXp  = if (profile.streakDays >= 3) AppConfig.XP_STREAK_BONUS else 0

        val totalXp = baseXp + wordXp + streakXp
        if (totalXp > 0) {
            profileRepository.addXp(profile.id, totalXp)
        }
        return totalXp
    }

    /** Returns the human-readable level name for a given total XP. */
    fun levelName(xpTotal: Int): String = when {
        xpTotal >= 5000 -> "English Star ⭐"
        xpTotal >= 2000 -> "English Pro 📘"
        xpTotal >= 500  -> "English Explorer 🧭"
        xpTotal >= 100  -> "English Starter 🌱"
        else            -> "Beginner 🐣"
    }

    /** Returns XP needed for the next level. */
    fun xpToNextLevel(xpTotal: Int): Int = when {
        xpTotal >= 5000 -> 0
        xpTotal >= 2000 -> 5000 - xpTotal
        xpTotal >= 500  -> 2000 - xpTotal
        xpTotal >= 100  -> 500  - xpTotal
        else            -> 100  - xpTotal
    }
}
