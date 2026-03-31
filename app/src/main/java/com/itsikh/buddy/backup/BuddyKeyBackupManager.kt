package com.itsikh.buddy.backup

import android.content.Context
import com.google.gson.JsonObject
import com.itsikh.buddy.AppConfig
import com.itsikh.buddy.security.SecureKeyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete backup manager that exports/imports the app's API keys
 * (Gemini, Claude, Google TTS, GitHub token) using encrypted settings backup.
 *
 * Keys are serialised as a flat JSON object and encrypted with AES-256-GCM
 * by [BaseSettingsBackupManager]. Missing keys are skipped on export and
 * silently ignored on import.
 */
@Singleton
class BuddyKeyBackupManager @Inject constructor(
    @ApplicationContext context: Context,
    private val secureKeyManager: SecureKeyManager
) : BaseSettingsBackupManager(context, AppConfig.APP_NAME) {

    override suspend fun collectSettingsData(): SettingsData {
        val data = JsonObject()
        listOf(
            AppConfig.KEY_GEMINI_API,
            AppConfig.KEY_CLAUDE_API,
            AppConfig.KEY_GOOGLE_TTS,
            AppConfig.KEY_GITHUB_TOKEN
        ).forEach { key ->
            secureKeyManager.getKey(key)?.let { data.addProperty(key, it) }
        }
        return SettingsData(data = data)
    }

    override suspend fun restoreSettingsData(data: JsonObject) {
        listOf(
            AppConfig.KEY_GEMINI_API,
            AppConfig.KEY_CLAUDE_API,
            AppConfig.KEY_GOOGLE_TTS,
            AppConfig.KEY_GITHUB_TOKEN
        ).forEach { key ->
            data.get(key)?.asString?.takeIf { it.isNotBlank() }?.let {
                secureKeyManager.saveKey(key, it)
            }
        }
    }
}
