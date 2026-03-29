package com.template.app.ai

import com.template.app.AppConfig
import com.template.app.data.models.Message
import com.template.app.logging.AppLogger
import com.template.app.security.SecureKeyManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes conversational AI requests to the configured default provider,
 * falling back to the other provider automatically on failure.
 *
 * The parent configures the default provider in Settings. Both API keys must be
 * set for the fallback to work — if only one key is present, that provider is used
 * regardless of the default setting.
 *
 * Provider selection:
 *   1. Try the configured default (Gemini or Claude)
 *   2. On any exception, try the other provider
 *   3. If both fail, rethrow the last exception
 */
@Singleton
class AiRouter @Inject constructor(
    private val geminiClient: GeminiApiClient,
    private val claudeClient: ClaudeApiClient,
    private val keyManager: SecureKeyManager
) {
    companion object {
        private const val TAG = "AiRouter"
    }

    /**
     * Sends a chat message and returns the AI's response.
     * Automatically uses default provider with fallback.
     */
    suspend fun chat(
        systemPrompt: String,
        history: List<Message>,
        userMessage: String
    ): String {
        val geminiKey = keyManager.getKey(AppConfig.KEY_GEMINI_API)
        val claudeKey = keyManager.getKey(AppConfig.KEY_CLAUDE_API)
        val defaultProvider = keyManager.getKey(AppConfig.PREF_AI_DEFAULT_PROVIDER)
            ?: AppConfig.AI_PROVIDER_GEMINI

        val primaryProvider  = if (defaultProvider == AppConfig.AI_PROVIDER_CLAUDE) "claude" else "gemini"
        val fallbackProvider = if (primaryProvider == "gemini") "claude" else "gemini"

        suspend fun tryProvider(provider: String): String? {
            return when (provider) {
                "gemini" -> {
                    if (geminiKey.isNullOrBlank()) null
                    else runCatching {
                        geminiClient.chat(geminiKey, systemPrompt, history, userMessage)
                    }.onFailure { AppLogger.e(TAG, "Gemini failed: ${it.message}") }.getOrNull()
                }
                "claude" -> {
                    if (claudeKey.isNullOrBlank()) null
                    else runCatching {
                        claudeClient.chat(claudeKey, systemPrompt, history, userMessage)
                    }.onFailure { AppLogger.e(TAG, "Claude failed: ${it.message}") }.getOrNull()
                }
                else -> null
            }
        }

        return tryProvider(primaryProvider)
            ?: tryProvider(fallbackProvider)
            ?: throw IllegalStateException(
                "Both AI providers failed or no API keys configured. " +
                "Please set API keys in Settings → API Configuration."
            )
    }

    /**
     * Runs a structured analysis task (memory extraction, lesson planning, parent report).
     * Always uses Claude for these tasks — better structured output adherence.
     * Falls back to Gemini only if no Claude key is available.
     */
    suspend fun analyze(systemPrompt: String, userPrompt: String): String {
        val claudeKey = keyManager.getKey(AppConfig.KEY_CLAUDE_API)
        val geminiKey = keyManager.getKey(AppConfig.KEY_GEMINI_API)

        if (!claudeKey.isNullOrBlank()) {
            runCatching {
                return claudeClient.analyze(claudeKey, systemPrompt, userPrompt)
            }.onFailure { AppLogger.e(TAG, "Claude analysis failed: ${it.message}") }
        }

        // Gemini fallback for structured tasks
        if (!geminiKey.isNullOrBlank()) {
            val model = com.google.ai.client.generativeai.GenerativeModel(
                modelName = "gemini-2.0-flash-exp",
                apiKey    = geminiKey,
                systemInstruction = com.google.ai.client.generativeai.type.content { text(systemPrompt) }
            )
            return model.generateContent(userPrompt).text
                ?: throw IllegalStateException("Gemini returned null for analysis")
        }

        throw IllegalStateException("No API keys configured for analysis tasks.")
    }

    /** Returns true if at least one provider is configured and usable. */
    fun hasAnyApiKey(): Boolean {
        val gemini = keyManager.getKey(AppConfig.KEY_GEMINI_API)
        val claude = keyManager.getKey(AppConfig.KEY_CLAUDE_API)
        return !gemini.isNullOrBlank() || !claude.isNullOrBlank()
    }
}
