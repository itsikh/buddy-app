package com.itsikh.buddy

/**
 * Central per-app configuration for Buddy — AI English & Hebrew Friend.
 *
 * Update the constants in this file before first release. All infrastructure modules
 * read their configurable values from here.
 *
 * ## Setup checklist
 * 1. Set GitHub repo constants (bug reports + OTA updates)
 * 2. Configure Google Cloud project and add google-services.json to app/
 * 3. Register app SHA-1 in Google Cloud Console for Drive OAuth
 * 4. Set API keys via Settings → (admin mode) → API Configuration
 */
object AppConfig {

    // -----------------------------------------------------------------------------------------
    // Bug Reports — GitHub repository where issues and screenshots are submitted
    // -----------------------------------------------------------------------------------------

    const val GITHUB_ISSUES_REPO_OWNER = "itsikh"
    const val GITHUB_ISSUES_REPO_NAME  = "buddy-app"

    // -----------------------------------------------------------------------------------------
    // App Updates — GitHub repository where release APKs are published
    // -----------------------------------------------------------------------------------------

    const val GITHUB_RELEASES_REPO_OWNER = "itsikh"
    const val GITHUB_RELEASES_REPO_NAME  = "buddy-app"

    // -----------------------------------------------------------------------------------------
    // General
    // -----------------------------------------------------------------------------------------

    const val APP_NAME = "Buddy"

    /**
     * Filename for the encrypted SharedPreferences file managed by SecureKeyManager.
     * DO NOT change after first public release — existing users will lose stored keys.
     */
    const val SECURE_PREFS_FILENAME = "buddy_secure_keys"

    // -----------------------------------------------------------------------------------------
    // API Key identifiers stored in SecureKeyManager
    // These are the *keys* (lookup names), not the actual secret values.
    // -----------------------------------------------------------------------------------------

    const val KEY_GEMINI_API    = "gemini_api_key"
    const val KEY_CLAUDE_API    = "claude_api_key"
    const val KEY_GOOGLE_TTS    = "google_cloud_tts_key"
    const val KEY_GITHUB_TOKEN  = "github_token"

    /** "gemini" or "claude" — which AI is the default conversational engine */
    const val PREF_AI_DEFAULT_PROVIDER = "ai_default_provider"
    const val AI_PROVIDER_GEMINI = "gemini"
    const val AI_PROVIDER_CLAUDE = "claude"

    // -----------------------------------------------------------------------------------------
    // Google Drive
    // -----------------------------------------------------------------------------------------

    /**
     * Scope for Google Drive AppData folder.
     * This gives the app a private, app-specific folder in the user's Drive.
     * The user cannot see or modify these files in the Drive UI.
     *
     * SETUP: Register your app in Google Cloud Console, enable Drive API,
     * add your release SHA-1 fingerprint, and add google-services.json to app/.
     */
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val DRIVE_API_BASE_URL  = "https://www.googleapis.com/drive/v3"
    const val DRIVE_UPLOAD_URL    = "https://www.googleapis.com/upload/drive/v3"

    // -----------------------------------------------------------------------------------------
    // Notifications
    // -----------------------------------------------------------------------------------------

    const val NOTIFICATION_CHANNEL_BACKUP    = "channel_backup"
    const val NOTIFICATION_CHANNEL_REMINDERS = "channel_reminders"

    // -----------------------------------------------------------------------------------------
    // Learning configuration
    // -----------------------------------------------------------------------------------------

    /** CEFR levels supported. Progress moves A1 → A2 → B1. */
    val CEFR_LEVELS = listOf("A1", "A2", "B1")

    /** Maximum turns to include in context window for AI (beyond this, use summary). */
    const val MAX_CONTEXT_TURNS = 20

    /** Drive sync fires this many minutes after a session ends. */
    const val DRIVE_SYNC_DELAY_MINUTES = 1L

    /** Spaced repetition review threshold — words below this ease factor get extra practice. */
    const val SRS_EASE_THRESHOLD = 2.0f

    // -----------------------------------------------------------------------------------------
    // Gamification
    // -----------------------------------------------------------------------------------------

    const val XP_PER_SESSION_MINUTE = 5
    const val XP_PER_NEW_WORD       = 10
    const val XP_STREAK_BONUS       = 25   // bonus per session when streak >= 3
    const val STREAK_SHIELDS_DEFAULT = 1   // shields available per week
}
