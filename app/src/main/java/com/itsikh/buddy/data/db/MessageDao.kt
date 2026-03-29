package com.itsikh.buddy.data.db

import androidx.room.*
import com.itsikh.buddy.data.models.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Live stream of messages for the current session — drives the chat UI. */
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeSessionMessages(sessionId: String): Flow<List<Message>>

    /** Last N messages across all sessions — used to build the AI context window. */
    @Query("SELECT * FROM messages WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(profileId: String, limit: Int = 20): List<Message>

    /** All messages for a given session — used for memory extraction after session ends. */
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getSessionMessages(sessionId: String): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message)

    @Query("DELETE FROM messages WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)

    /** Keep only the most recent [keepCount] messages per profile to bound storage use. */
    @Query("""
        DELETE FROM messages
        WHERE profileId = :profileId
          AND id NOT IN (
            SELECT id FROM messages WHERE profileId = :profileId
            ORDER BY timestamp DESC LIMIT :keepCount
          )
    """)
    suspend fun pruneOldMessages(profileId: String, keepCount: Int = 500)
}
