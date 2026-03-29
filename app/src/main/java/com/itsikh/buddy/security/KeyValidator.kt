package com.itsikh.buddy.security

import com.google.gson.Gson
import com.itsikh.buddy.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Result of validating an API key against its live endpoint. */
sealed class KeyValidation {
    /** No validation has been run yet (key was loaded from storage on startup). */
    object Idle : KeyValidation()
    /** Validation in progress. */
    object Validating : KeyValidation()
    /** Key is valid — [info] contains a brief human-readable confirmation. */
    data class Ok(val info: String) : KeyValidation()
    /** Key is invalid or unreachable — [message] explains why. */
    data class Error(val message: String) : KeyValidation()
}

/**
 * Makes a minimal live API call for each supported key type to verify the key works.
 *
 * All calls use the same [OkHttpClient] as the rest of the app so proxy / cert settings apply.
 */
@Singleton
class KeyValidator @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "KeyValidator"
        private val JSON = "application/json".toMediaType()
    }

    /** Validates a Gemini API key by sending a 1-token generateContent request. */
    suspend fun validateGemini(apiKey: String): KeyValidation = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to "hi")))),
                "generationConfig" to mapOf("maxOutputTokens" to 1)
            )
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                .post(gson.toJson(body).toRequestBody(JSON))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    AppLogger.d(TAG, "Gemini key OK")
                    KeyValidation.Ok("gemini-2.0-flash — working")
                } else {
                    val msg = parseGoogleError(response.body?.string())
                    AppLogger.w(TAG, "Gemini key invalid: $msg")
                    KeyValidation.Error(msg)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Gemini validation error: ${e.message}")
            KeyValidation.Error("שגיאת חיבור: ${e.message}")
        }
    }

    /** Validates a Claude API key by sending a 1-token messages request. */
    suspend fun validateClaude(apiKey: String): KeyValidation = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "model"      to "claude-haiku-4-5-20251001",
                "max_tokens" to 1,
                "messages"   to listOf(mapOf("role" to "user", "content" to "hi"))
            )
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .post(gson.toJson(body).toRequestBody(JSON))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    AppLogger.d(TAG, "Claude key OK")
                    KeyValidation.Ok("claude-haiku-4-5-20251001 — working")
                } else {
                    val msg = parseAnthropicError(response.body?.string())
                    AppLogger.w(TAG, "Claude key invalid: $msg")
                    KeyValidation.Error(msg)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Claude validation error: ${e.message}")
            KeyValidation.Error("שגיאת חיבור: ${e.message}")
        }
    }

    /** Validates a Google Cloud TTS key by synthesizing a short Hebrew word. */
    suspend fun validateGoogleTts(apiKey: String): KeyValidation = withContext(Dispatchers.IO) {
        // Fail fast for obviously wrong key format
        if (!apiKey.startsWith("AIza")) {
            return@withContext KeyValidation.Error(
                "מפתח שגוי — מפתח Google API חייב להתחיל ב-\"AIzaSy\". " +
                "בדוק ב-Google Cloud Console → Credentials → Create API key."
            )
        }

        try {
            val body = mapOf(
                "input"       to mapOf("ssml" to "<speak>שלום</speak>"),
                "voice"       to mapOf("languageCode" to "he-IL", "name" to "he-IL-Wavenet-A"),
                "audioConfig" to mapOf("audioEncoding" to "MP3", "speakingRate" to 0.9)
            )
            val request = Request.Builder()
                .url("https://texttospeech.googleapis.com/v1beta1/text:synthesize?key=$apiKey")
                .post(gson.toJson(body).toRequestBody(JSON))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    AppLogger.d(TAG, "Google TTS key OK")
                    KeyValidation.Ok("he-IL-Wavenet-A — working")
                } else {
                    val msg = parseGoogleError(response.body?.string())
                    AppLogger.w(TAG, "Google TTS key invalid: $msg")
                    KeyValidation.Error(msg)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Google TTS validation error: ${e.message}")
            KeyValidation.Error("שגיאת חיבור: ${e.message}")
        }
    }

    // ── Error parsing ──────────────────────────────────────────────────────────

    private fun parseGoogleError(body: String?): String {
        if (body.isNullOrBlank()) return "Unknown error"
        return try {
            @Suppress("UNCHECKED_CAST")
            val parsed = gson.fromJson(body, Map::class.java) as Map<String, Any>
            val error = parsed["error"] as? Map<*, *>
            val status  = error?.get("status") as? String ?: ""
            val message = error?.get("message") as? String ?: body.take(200)
            if (status.isNotBlank()) "$status: $message" else message
        } catch (_: Exception) {
            body.take(200)
        }
    }

    private fun parseAnthropicError(body: String?): String {
        if (body.isNullOrBlank()) return "Unknown error"
        return try {
            @Suppress("UNCHECKED_CAST")
            val parsed = gson.fromJson(body, Map::class.java) as Map<String, Any>
            val error = parsed["error"] as? Map<*, *>
            val message = error?.get("message") as? String ?: body.take(200)
            message
        } catch (_: Exception) {
            body.take(200)
        }
    }
}
