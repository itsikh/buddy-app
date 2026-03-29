package com.template.app.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Tracks a single English word's mastery state using a simplified SM-2 spaced repetition algorithm.
 *
 * The AI introduces new words naturally in conversation. After each session, [MemoryExtractor]
 * identifies new words and adds them here. [LessonPlanner] uses [nextReviewDue] to decide
 * which words to weave back into the next session's conversation.
 *
 * [masteryLevel] 0-5 maps to garden stages: seed (0) → sprout (1) → growing (2) →
 *   flowering (3) → bloomed (4) → mastered (5).
 */
@Entity(
    tableName = "vocabulary_items",
    foreignKeys = [ForeignKey(
        entity = ChildProfile::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId"), Index("nextReviewDue")]
)
data class VocabularyItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val word: String,
    val definition: String? = null,

    val firstSeen: Long = System.currentTimeMillis(),
    val lastReviewed: Long = System.currentTimeMillis(),

    /** Unix millis. Words whose nextReviewDue <= now are candidates for today's session. */
    val nextReviewDue: Long = System.currentTimeMillis() + 86_400_000L, // +1 day default

    /** SM-2 ease factor. Starts at 2.5; decreases when child struggles. Min 1.3. */
    val easeFactor: Float = 2.5f,

    val successfulRecalls: Int = 0,
    val failedRecalls: Int = 0,

    /** 0–5. Words at level >= 3 count toward the Vocabulary Garden's "bloomed" count. */
    val masteryLevel: Int = 0
)
