package com.template.app.data.db

import androidx.room.*
import com.template.app.data.models.ChildProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildProfileDao {

    @Query("SELECT * FROM child_profiles LIMIT 1")
    fun observeProfile(): Flow<ChildProfile?>

    @Query("SELECT * FROM child_profiles LIMIT 1")
    suspend fun getProfile(): ChildProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ChildProfile)

    @Update
    suspend fun update(profile: ChildProfile)

    @Query("DELETE FROM child_profiles")
    suspend fun deleteAll()

    // Granular update methods to avoid full-row writes for hot-path updates

    @Query("UPDATE child_profiles SET lastSessionAt = :ts, totalSessionMinutes = totalSessionMinutes + :minutes WHERE id = :id")
    suspend fun recordSession(id: String, ts: Long, minutes: Int)

    @Query("UPDATE child_profiles SET streakDays = :days, lastStreakDate = :date, longestStreak = :longest, streakShieldsAvailable = :shields WHERE id = :id")
    suspend fun updateStreak(id: String, days: Int, date: String, longest: Int, shields: Int)

    @Query("UPDATE child_profiles SET xpTotal = xpTotal + :amount WHERE id = :id")
    suspend fun addXp(id: String, amount: Int)

    @Query("UPDATE child_profiles SET vocabularyMastered = :count WHERE id = :id")
    suspend fun updateVocabMastered(id: String, count: Int)

    @Query("UPDATE child_profiles SET cefrLevel = :level, speakingLevel = :speaking, vocabularyLevel = :vocab, grammarLevel = :grammar WHERE id = :id")
    suspend fun updateCefrLevels(id: String, level: String, speaking: String, vocab: String, grammar: String)

    @Query("UPDATE child_profiles SET driveAccountEmail = :email, lastDriveSyncAt = :syncAt WHERE id = :id")
    suspend fun updateDriveStatus(id: String, email: String?, syncAt: Long)

    @Query("UPDATE child_profiles SET onboardingComplete = 1 WHERE id = :id")
    suspend fun markOnboardingComplete(id: String)
}
