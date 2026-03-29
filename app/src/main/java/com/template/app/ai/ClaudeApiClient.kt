package com.template.app.ai

import com.google.gson.Gson
import com.template.app.data.models.Message
import com.template.app.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls the Anthropic Claude API via OkHttp REST.
 *
 * Claude is used for two roles:
 *   1. Conversational fallback when Gemini is unavailable
 *   2. Structured tasks: memory extraction, lesson planning, parent report generation
 *      (where Claude's stronger instruction-following yields more reliable structured output)
 *
 * Setup: Set your Anthropic API key in Settings → (admin mode) → API Configuration.
 * Get a key at: https://console.anthropic.com/
 */
@Singleton
class ClaudeApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "ClaudeApiClient"
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val CHAT_MODEL     = "claude-haiku-4-5-20251001"   // Fast + cheap for conversation
        private const val ANALYSIS_MODEL = "claude-sonnet-4-6"           // Better structured output for analysis tasks
        private val JSON = "application/json".toMediaType()
    }

    /**
     * Conversational chat — used as fallback when Gemini is unavailable.
     */
    suspend fun chat(
        apiKey: String,
        systemPrompt: String,
        history: List<Message>,
        userMessage: String
    ): String = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Sending chat request, history=${history.size}")

        val messages = buildList {
            history.forEach { msg ->
                add(mapOf("role" to msg.role, "content" to msg.text))
            }
            add(mapOf("role" to "user", "content" to userMessage))
        }

        val body = mapOf(
            "model"      to CHAT_MODEL,
            "max_tokens" to 400,
            "system"     to systemPrompt,
            "messages"   to messages
        )

        callApi(apiKey, body)
    }

    /**
     * Structured analysis task — used for memory extraction, lesson planning, parent reports.
     * Uses the more capable Sonnet model for better adherence to structured output formats.
     */
    suspend fun analyze(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String
    ): String = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Sending analysis request")

        val body = mapOf(
            "model"      to ANALYSIS_MODEL,
            "max_tokens" to 1024,
            "system"     to systemPrompt,
            "messages"   to listOf(mapOf("role" to "user", "content" to userPrompt))
        )

        callApi(apiKey, body)
    }

    private fun callApi(apiKey: String, body: Map<String, Any>): String {
        val requestBody = gson.toJson(body).toRequestBody(JSON)
        val request = Request.Builder()
            .url(API_URL)
            .post(requestBody)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("content-type", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                AppLogger.e(TAG, "Claude API error ${response.code}: $errorBody")
                throw IllegalStateException("Claude API error ${response.code}")
            }

            val responseJson = response.body?.string()
                ?: throw IllegalStateException("Empty response from Claude")

            @Suppress("UNCHECKED_CAST")
            val parsed = gson.fromJson(responseJson, Map::class.java) as Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val content = (parsed["content"] as? List<Map<String, Any>>)?.firstOrNull()
                ?: throw IllegalStateException("No content in Claude response")

            return (content["text"] as? String)?.trim()
                ?: throw IllegalStateException("No text in Claude response content")
        }
    }
}
