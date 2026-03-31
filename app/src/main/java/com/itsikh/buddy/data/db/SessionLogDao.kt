package com.itsikh.buddy.data.db

import androidx.room.*
import com.itsikh.buddy.data.models.SessionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionLogDao {

    @Query("SELECT * FROM session_logs WHERE profileId = :profileId ORDER BY startedAt DESC")
    fun observeAll(profileId: String): Flow<List<SessionLog>>

    @Query("SELECT * FROM session_logs WHERE profileId = :profileId ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecent(profileId: String, limit: Int = 30): List<SessionLog>

    @Query("SELECT * FROM session_logs WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): SessionLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: SessionLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<SessionLog>)

    @Query("""
        UPDATE session_logs
        SET endedAt = :endedAt, durationMinutes = :minutes, turnCount = :turns,
            newWordsIntroduced = :newWords, buddyCorrections = :corrections,
            sessionSummary = :summary, xpEarned = :xp
        WHERE id = :id
    """)
    suspend fun closeSession(
        id: String,
        endedAt: Long,
        minutes: Int,
        turns: Int,
        newWords: Int,
        corrections: Int,
        summary: String?,
        xp: Int
    )

    @Query("SELECT COUNT(*) FROM session_logs WHERE profileId = :profileId AND endedAt IS NOT NULL")
    suspend fun countCompleted(profileId: String): Int

    @Query("SELECT SUM(durationMinutes) FROM session_logs WHERE profileId = :profileId")
    suspend fun totalMinutes(profileId: String): Int?

    @Query("SELECT * FROM session_logs WHERE profileId = :profileId AND startedAt >= :since ORDER BY startedAt DESC")
    suspend fun getSessionsSince(profileId: String, since: Long): List<SessionLog>

    @Query("DELETE FROM session_logs WHERE profileId = :profileId AND startedAt < :before")
    suspend fun deleteSessionsBefore(profileId: String, before: Long)

    @Query("DELETE FROM session_logs WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)
}
