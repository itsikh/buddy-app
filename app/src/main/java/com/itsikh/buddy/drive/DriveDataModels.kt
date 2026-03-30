package com.itsikh.buddy.drive

/**
 * JSON-serializable data models for Google Drive AppData storage.
 *
 * These are separate from the Room entities — they are the Drive-persisted representation
 * of the child's learning data. They intentionally omit Room-specific fields (like
 * foreign keys) and add Drive-specific metadata like schema version.
 *
 * File layout in Drive AppData folder:
 *   buddy_profile.json          ← ChildProfile + gamification state
 *   buddy_vocabulary.json       ← All VocabularyItems
 *   buddy_memory.json           ← All MemoryFacts
 *   buddy_sessions.json         ← Recent session summaries (last 30)
 *   buddy_parent_policy.json    ← Parental control settings (writable by future parent app)
 */

data class DriveProfile(
    val schemaVersion: Int = 2,
    val lastSyncAt: Long = System.currentTimeMillis(),
    val displayName: String,
    val age: Int,
    val gender: String = "BOY",
    val namePhonetic: String = "",
    val cefrLevel: String,
    val speakingLevel: String,
    val vocabularyLevel: String,
    val grammarLevel: String,
    val totalSessionMinutes: Int,
    val createdAt: Long,
    val lastSessionAt: Long?,
    val streakDays: Int,
    val lastStreakDate: String? = null,
    val longestStreak: Int,
    val streakShieldsAvailable: Int = 1,
    val xpTotal: Int,
    val vocabularyMastered: Int,
    val coins: Int = 0,
    val onboardingComplete: Boolean = true,
    val parentConsentGiven: Boolean = true
)

data class DriveVocabularyItem(
    val word: String,
    val definition: String?,
    val firstSeen: Long,
    val lastReviewed: Long,
    val nextReviewDue: Long,
    val easeFactor: Float,
    val successfulRecalls: Int,
    val failedRecalls: Int,
    val masteryLevel: Int
)

data class DriveVocabularyStore(
    val schemaVersion: Int = 1,
    val lastSyncAt: Long = System.currentTimeMillis(),
    val items: List<DriveVocabularyItem>
)

data class DriveMemoryFact(
    val category: String,
    val key: String,
    val value: String,
    val updatedAt: Long
)

data class DriveMemoryStore(
    val schemaVersion: Int = 1,
    val lastSyncAt: Long = System.currentTimeMillis(),
    val facts: List<DriveMemoryFact>
)

data class DriveSessionEntry(
    val id: String,
    val mode: String,
    val startedAt: Long,
    val durationMinutes: Int,
    val turnCount: Int,
    val newWordsIntroduced: Int,
    val sessionSummary: String?
)

data class DriveSessionStore(
    val schemaVersion: Int = 1,
    val lastSyncAt: Long = System.currentTimeMillis(),
    val sessions: List<DriveSessionEntry>
)

/**
 * Parental control policy stored in Drive.
 * Currently only read on-device, but the schema is designed to be writable
 * by a future parent companion app — enabling "central" parental control.
 */
data class DriveParentPolicy(
    val schemaVersion: Int = 1,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val maxDailyMinutes: Int = 60,
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderHour: Int = 18,   // 6 PM default
    val dailyReminderMinute: Int = 0,
    val allowedModes: List<String> = listOf("FREE_CHAT", "STORY_TIME", "ROLE_PLAY"),
    val contentSafetyLevel: String = "STANDARD"  // STANDARD or STRICT
)

object DriveFileNames {
    const val PROFILE    = "buddy_profile.json"
    const val VOCABULARY = "buddy_vocabulary.json"
    const val MEMORY     = "buddy_memory.json"
    const val SESSIONS   = "buddy_sessions.json"
    const val POLICY     = "buddy_parent_policy.json"
    const val KEYS       = "buddy_keys.enc"   // AES-256-GCM encrypted API keys
}
