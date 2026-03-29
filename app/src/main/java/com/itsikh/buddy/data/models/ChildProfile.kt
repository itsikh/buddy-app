package com.itsikh.buddy.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents the child using this device.
 *
 * Only one profile exists per device (single-child design).
 * [displayName] is shown in the UI and injected into the AI system prompt, but is never
 * sent to analytics or logged — privacy by design.
 *
 * CEFR levels are tracked independently per skill area so the AI can pitch each dimension
 * at the right difficulty (e.g. a child may be A2 in vocabulary but A1 in speaking fluency).
 */
@Entity(tableName = "child_profiles")
data class ChildProfile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    /** Name used to address the child in conversation. Stored only locally. */
    val displayName: String,

    val age: Int,

    /** Overall CEFR level — updated when all skill areas advance. */
    val cefrLevel: String = "A1",

    /** Skill-specific CEFR levels for adaptive difficulty. */
    val speakingLevel: String  = "A1",
    val vocabularyLevel: String = "A1",
    val grammarLevel: String   = "A1",

    val totalSessionMinutes: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSessionAt: Long? = null,

    /** True once the parent consent + initial voice assessment are complete. */
    val onboardingComplete: Boolean = false,

    /** Parent confirmed privacy consent. Required before any data is sent to AI. */
    val parentConsentGiven: Boolean = false,

    // ---- Gamification ----
    val streakDays: Int = 0,
    val lastStreakDate: String? = null,   // "yyyy-MM-dd"
    val longestStreak: Int = 0,
    val streakShieldsAvailable: Int = 1,
    val xpTotal: Int = 0,
    val vocabularyMastered: Int = 0,

    // ---- Drive sync ----
    val driveAccountEmail: String? = null,
    val lastDriveSyncAt: Long? = null
)
