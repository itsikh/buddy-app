package com.itsikh.buddy.data.repository

import com.itsikh.buddy.AppConfig
import com.itsikh.buddy.data.db.VocabularyItemDao
import com.itsikh.buddy.data.models.VocabularyItem
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

@Singleton
class VocabularyRepository @Inject constructor(
    private val dao: VocabularyItemDao
) {
    fun observeAll(profileId: String): Flow<List<VocabularyItem>> = dao.observeAll(profileId)

    suspend fun getAll(profileId: String): List<VocabularyItem> = dao.getAll(profileId)

    suspend fun getDueForReview(profileId: String): List<VocabularyItem> =
        dao.getDueForReview(profileId)

    suspend fun countMastered(profileId: String): Int = dao.countMastered(profileId)

    suspend fun addWordIfNew(profileId: String, word: String, definition: String? = null) {
        val existing = dao.findByWord(profileId, word.lowercase().trim())
        if (existing == null) {
            dao.insertIfAbsent(
                VocabularyItem(
                    id         = UUID.randomUUID().toString(),
                    profileId  = profileId,
                    word       = word.lowercase().trim(),
                    definition = definition
                )
            )
        }
    }

    /**
     * Records a successful recall using the SM-2 algorithm.
     * Quality 5 = perfect, 4 = correct after hesitation, 3 = correct with difficulty.
     */
    suspend fun recordRecall(item: VocabularyItem, quality: Int) {
        val newEase = max(1.3f, item.easeFactor + 0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f))
        val newLevel = min(5, item.masteryLevel + 1)

        // SM-2 interval calculation (days)
        val intervalDays = when (item.successfulRecalls) {
            0    -> 1L
            1    -> 6L
            else -> ((item.successfulRecalls * newEase).roundToLong()).coerceAtLeast(1L)
        }
        val nextDue = System.currentTimeMillis() + intervalDays * 86_400_000L
        dao.recordSuccess(item.id, newLevel, newEase, nextDue)
    }

    /** Records a failed recall — resets interval, reduces ease factor. */
    suspend fun recordFailure(item: VocabularyItem) {
        val newEase = max(AppConfig.SRS_EASE_THRESHOLD, item.easeFactor - 0.2f)
        val nextDue = System.currentTimeMillis() + 86_400_000L // retry tomorrow
        dao.recordFailure(item.id, newEase, nextDue)
    }

    suspend fun deleteAllForProfile(profileId: String) = dao.deleteAllForProfile(profileId)
}
