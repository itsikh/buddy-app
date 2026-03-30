package com.itsikh.buddy.data.repository

import com.itsikh.buddy.data.db.ChildProfileDao
import com.itsikh.buddy.data.models.ChildProfile
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ChildProfileDao
) {
    val profile: Flow<ChildProfile?> = dao.observeProfile()

    suspend fun getProfile(): ChildProfile? = dao.getProfile()

    suspend fun saveProfile(profile: ChildProfile) = dao.upsert(profile)

    suspend fun markOnboardingComplete(id: String) = dao.markOnboardingComplete(id)

    suspend fun addXp(id: String, amount: Int) = dao.addXp(id, amount)

    suspend fun addCoins(id: String, amount: Int) = dao.addCoins(id, amount)

    suspend fun recordSessionEnd(id: String, durationMinutes: Int) {
        dao.recordSession(id, System.currentTimeMillis(), durationMinutes)
    }

    /**
     * Updates the streak for today's session.
     * - If last session was yesterday → increment streak
     * - If last session was today → no change (already counted)
     * - If gap > 1 day:
     *   - If shield available → use shield, keep streak, decrement shield
     *   - Otherwise → reset streak to 1
     */
    suspend fun updateStreak(profile: ChildProfile): ChildProfile {
        val today = LocalDate.now()
        val fmt   = DateTimeFormatter.ISO_LOCAL_DATE
        val lastDate = profile.lastStreakDate?.let { runCatching { LocalDate.parse(it, fmt) }.getOrNull() }

        val updatedProfile = when {
            lastDate == null || lastDate == today -> {
                // First session ever, or already counted today
                profile.copy(
                    lastStreakDate = today.format(fmt),
                    streakDays = maxOf(profile.streakDays, 1)
                )
            }
            lastDate == today.minusDays(1) -> {
                // Consecutive day
                val newStreak = profile.streakDays + 1
                profile.copy(
                    streakDays = newStreak,
                    longestStreak = maxOf(profile.longestStreak, newStreak),
                    lastStreakDate = today.format(fmt)
                )
            }
            profile.streakShieldsAvailable > 0 -> {
                // Missed a day but shield protects the streak
                profile.copy(
                    streakShieldsAvailable = profile.streakShieldsAvailable - 1,
                    lastStreakDate = today.format(fmt)
                )
            }
            else -> {
                // Streak broken
                profile.copy(
                    streakDays = 1,
                    lastStreakDate = today.format(fmt)
                )
            }
        }
        dao.updateStreak(
            id      = updatedProfile.id,
            days    = updatedProfile.streakDays,
            date    = updatedProfile.lastStreakDate ?: today.format(fmt),
            longest = updatedProfile.longestStreak,
            shields = updatedProfile.streakShieldsAvailable
        )
        return updatedProfile
    }

    suspend fun updateCefrLevels(id: String, speaking: String, vocab: String, grammar: String) {
        // Overall CEFR level is the minimum of the skill levels
        val levels = listOf(speaking, vocab, grammar)
        val overall = if (levels.all { it == "B1" }) "B1"
                      else if (levels.all { it >= "A2" }) "A2"
                      else "A1"
        dao.updateCefrLevels(id, overall, speaking, vocab, grammar)
    }

    suspend fun updateDriveStatus(id: String, email: String?) {
        dao.updateDriveStatus(id, email, System.currentTimeMillis())
    }

    suspend fun deleteAll() = dao.deleteAll()
}
