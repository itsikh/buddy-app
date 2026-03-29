package com.template.app.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ChildProfile::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId"), Index("sessionId")]
)
data class Message(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val sessionId: String,

    /** "user" or "assistant" — matches the role field expected by both Claude and Gemini APIs. */
    val role: String,

    val text: String,
    val timestamp: Long = System.currentTimeMillis(),

    /** "EN" or "HE" — detected language of the message, null if unknown. */
    val languageDetected: String? = null
)
