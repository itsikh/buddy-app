package com.template.app.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.template.app.data.models.Message
import com.template.app.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the Google Gemini SDK for conversational AI responses.
 *
 * Uses gemini-2.0-flash-exp by default — fast, cost-effective, strong multilingual support.
 * The system prompt sets Buddy's personality and is re-applied for each new conversation
 * context (Gemini stateless API).
 *
 * Setup: Set your Gemini API key in Settings → (admin mode) → API Configuration.
 * Get a key at: https://aistudio.google.com/app/apikey
 */
@Singleton
class GeminiApiClient @Inject constructor() {

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val MODEL_NAME = "gemini-2.0-flash-exp"
    }

    /**
     * Sends a conversation history to Gemini and returns the assistant's response text.
     *
     * @param apiKey    The Gemini API key from SecureKeyManager
     * @param systemPrompt Buddy's full persona and session instructions
     * @param history   All messages in this conversation context (chronological order)
     * @param userMessage The new user message to respond to
     */
    suspend fun chat(
        apiKey: String,
        systemPrompt: String,
        history: List<Message>,
        userMessage: String
    ): String {
        AppLogger.d(TAG, "Sending chat request, history size=${history.size}")

        val model = GenerativeModel(
            modelName = MODEL_NAME,
            apiKey    = apiKey,
            generationConfig = generationConfig {
                temperature   = 0.75f
                topK          = 40
                topP          = 0.95f
                maxOutputTokens = 400  // Keep responses concise for voice output
            },
            systemInstruction = content { text(systemPrompt) }
        )

        // Build chat history (exclude the latest user message — it's sent separately)
        val chatHistory = history.dropLast(1).map { msg ->
            content(role = if (msg.role == "user") "user" else "model") {
                text(msg.text)
            }
        }

        val chat = model.startChat(history = chatHistory)
        val response = chat.sendMessage(userMessage)

        val responseText = response.text
            ?: throw IllegalStateException("Gemini returned null response text")

        AppLogger.d(TAG, "Response received, length=${responseText.length}")
        return responseText.trim()
    }
}
