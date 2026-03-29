package com.itsikh.buddy.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single fact that Buddy has learned about the child from conversation.
 *
 * Examples:
 *   category=FAMILY,   key="sister",  value="Maya"
 *   category=INTERESTS, key="sport",  value="soccer"
 *   category=SCHOOL,   key="grade",   value="5th grade"
 *
 * These facts are injected into the system prompt of every session so Buddy
 * can reference them naturally ("How was soccer practice this week?").
 * They are also synced to Google Drive AppData.
 */
@Entity(
    tableName = "memory_facts",
    foreignKeys = [ForeignKey(
        entity = ChildProfile::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId")]
)
data class MemoryFact(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val category: MemoryCategory,
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

enum class MemoryCategory {
    FAMILY,
    INTERESTS,
    ACTIVITIES,
    SCHOOL,
    OTHER
}
