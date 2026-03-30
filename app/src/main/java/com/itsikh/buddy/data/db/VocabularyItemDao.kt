package com.itsikh.buddy.data.db

import androidx.room.*
import com.itsikh.buddy.data.models.VocabularyItem
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyItemDao {

    @Query("SELECT * FROM vocabulary_items WHERE profileId = :profileId ORDER BY masteryLevel ASC, word ASC")
    fun observeAll(profileId: String): Flow<List<VocabularyItem>>

    @Query("SELECT * FROM vocabulary_items WHERE profileId = :profileId ORDER BY masteryLevel ASC, word ASC")
    suspend fun getAll(profileId: String): List<VocabularyItem>

    /** Words due for review today — injected into the AI session as vocabulary to reintroduce. */
    @Query("SELECT * FROM vocabulary_items WHERE profileId = :profileId AND nextReviewDue <= :now ORDER BY nextReviewDue ASC LIMIT :limit")
    suspend fun getDueForReview(profileId: String, now: Long = System.currentTimeMillis(), limit: Int = 5): List<VocabularyItem>

    /** Count of words at masteryLevel >= 3 (shown in Vocabulary Garden and parent dashboard). */
    @Query("SELECT COUNT(*) FROM vocabulary_items WHERE profileId = :profileId AND masteryLevel >= 3")
    suspend fun countMastered(profileId: String): Int

    @Query("SELECT COUNT(*) FROM vocabulary_items WHERE profileId = :profileId")
    suspend fun countTotal(profileId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(item: VocabularyItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VocabularyItem>)

    @Update
    suspend fun update(item: VocabularyItem)

    @Query("SELECT * FROM vocabulary_items WHERE profileId = :profileId AND word = :word LIMIT 1")
    suspend fun findByWord(profileId: String, word: String): VocabularyItem?

    @Query("UPDATE vocabulary_items SET masteryLevel = :level, easeFactor = :ease, nextReviewDue = :nextDue, lastReviewed = :now, successfulRecalls = successfulRecalls + 1 WHERE id = :id")
    suspend fun recordSuccess(id: String, level: Int, ease: Float, nextDue: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE vocabulary_items SET easeFactor = :ease, nextReviewDue = :nextDue, lastReviewed = :now, failedRecalls = failedRecalls + 1 WHERE id = :id")
    suspend fun recordFailure(id: String, ease: Float, nextDue: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM vocabulary_items WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)
}
