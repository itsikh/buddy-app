package com.template.app.data.repository

import com.template.app.data.db.MessageDao
import com.template.app.data.db.SessionLogDao
import com.template.app.data.models.ChatMode
import com.template.app.data.models.Message
import com.template.app.data.models.SessionLog
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val sessionLogDao: SessionLogDao
) {
    fun observeSessionMessages(sessionId: String): Flow<List<Message>> =
        messageDao.observeSessionMessages(sessionId)

    /** Returns the last [limit] messages reversed to chronological order for the AI context. */
    suspend fun getRecentMessages(profileId: String, limit: Int = 20): List<Message> =
        messageDao.getRecentMessages(profileId, limit).reversed()

    suspend fun getSessionMessages(sessionId: String): List<Message> =
        messageDao.getSessionMessages(sessionId)

    suspend fun addMessage(message: Message) = messageDao.insert(message)

    suspend fun startSession(profileId: String, mode: ChatMode): SessionLog {
        val log = SessionLog(
            id        = UUID.randomUUID().toString(),
            profileId = profileId,
            mode      = mode
        )
        sessionLogDao.upsert(log)
        return log
    }

    suspend fun closeSession(
        sessionId: String,
        durationMinutes: Int,
        turnCount: Int,
        newWords: Int,
        corrections: Int,
        summary: String?,
        xp: Int
    ) {
        sessionLogDao.closeSession(
            id          = sessionId,
            endedAt     = System.currentTimeMillis(),
            minutes     = durationMinutes,
            turns       = turnCount,
            newWords    = newWords,
            corrections = corrections,
            summary     = summary,
            xp          = xp
        )
    }

    suspend fun getSessionLog(sessionId: String): SessionLog? =
        sessionLogDao.getById(sessionId)

    fun observeAllSessions(profileId: String): Flow<List<SessionLog>> =
        sessionLogDao.observeAll(profileId)

    suspend fun getRecentSessions(profileId: String, limit: Int = 10): List<SessionLog> =
        sessionLogDao.getRecent(profileId, limit)

    suspend fun totalSessionCount(profileId: String): Int =
        sessionLogDao.countCompleted(profileId)

    suspend fun pruneOldMessages(profileId: String) =
        messageDao.pruneOldMessages(profileId)

    suspend fun deleteAllForProfile(profileId: String) {
        messageDao.deleteAllForProfile(profileId)
        sessionLogDao.deleteAllForProfile(profileId)
    }
}
