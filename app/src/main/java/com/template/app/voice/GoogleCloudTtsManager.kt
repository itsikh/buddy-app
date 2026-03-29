package com.template.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import com.google.gson.Gson
import com.template.app.AppConfig
import com.template.app.logging.AppLogger
import com.template.app.security.SecureKeyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Text-to-speech using Google Cloud TTS REST API.
 *
 * Produces high-quality, natural-sounding speech via WaveNet voices — far better quality
 * than Android's built-in TTS, which is critical for a language learning app where
 * the child needs to hear natural English prosody.
 *
 * Setup: Set your Google Cloud API key in Settings → (admin mode) → API Configuration.
 * Enable "Cloud Text-to-Speech API" in your Google Cloud Console project.
 *
 * Audio is streamed as base64-encoded LINEAR16 PCM, decoded to a temp file, and played
 * via MediaPlayer. Temp files are cleaned up after playback.
 */
@Singleton
class GoogleCloudTtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val keyManager: SecureKeyManager,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "GoogleCloudTtsManager"
        private const val TTS_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
        private val JSON = "application/json".toMediaType()

        // High-quality WaveNet voices — sounds natural for language learning
        private const val VOICE_EN = "en-US-Wavenet-D"  // Warm male voice, good for a friendly "buddy"
        private const val VOICE_HE = "he-IL-Wavenet-A"  // Hebrew voice for UI feedback
        private const val LANG_EN  = "en-US"
        private const val LANG_HE  = "he-IL"
    }

    private val currentPlayer = AtomicReference<MediaPlayer?>(null)

    /**
     * Speaks the given text aloud. Suspends until playback completes.
     * Cancels any currently playing audio before starting.
     *
     * @param text     The text to speak (English or Hebrew detected automatically by the voice)
     * @param language "EN" or "HE" — selects the appropriate WaveNet voice
     */
    suspend fun speak(text: String, language: String = "EN") {
        stopSpeaking()

        val apiKey = keyManager.getKey(AppConfig.KEY_GOOGLE_TTS)
        if (apiKey.isNullOrBlank()) {
            AppLogger.w(TAG, "No Google Cloud TTS API key configured — falling back to silent")
            return
        }

        try {
            val audioData = synthesize(apiKey, text, language)
            playAudio(audioData)
        } catch (e: Exception) {
            AppLogger.e(TAG, "TTS failed: ${e.message}")
        }
    }

    /** Stops any currently playing audio immediately. */
    fun stopSpeaking() {
        currentPlayer.getAndSet(null)?.apply {
            runCatching { stop() }
            runCatching { release() }
        }
    }

    private suspend fun synthesize(apiKey: String, text: String, language: String): ByteArray =
        withContext(Dispatchers.IO) {
            val voiceName = if (language == "HE") VOICE_HE else VOICE_EN
            val langCode  = if (language == "HE") LANG_HE else LANG_EN

            val requestBody = mapOf(
                "input"       to mapOf("text" to text),
                "voice"       to mapOf(
                    "languageCode" to langCode,
                    "name"         to voiceName
                ),
                "audioConfig" to mapOf(
                    "audioEncoding" to "MP3",
                    "speakingRate"  to 0.95,  // Slightly slower than native — easier for learners
                    "pitch"         to 0.0
                )
            )

            val request = Request.Builder()
                .url("$TTS_URL?key=$apiKey")
                .post(gson.toJson(requestBody).toRequestBody(JSON))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("TTS API error ${response.code}: ${response.body?.string()}")
                }
                val responseJson = response.body?.string()
                    ?: throw IllegalStateException("Empty TTS response")

                @Suppress("UNCHECKED_CAST")
                val parsed = gson.fromJson(responseJson, Map::class.java) as Map<String, Any>
                val encoded = parsed["audioContent"] as? String
                    ?: throw IllegalStateException("No audioContent in TTS response")

                Base64.decode(encoded, Base64.DEFAULT)
            }
        }

    private suspend fun playAudio(audioData: ByteArray) {
        // Write to a temp file — MediaPlayer works better with a file URI than in-memory bytes
        val tempFile = withContext(Dispatchers.IO) {
            File(context.cacheDir, "buddy_tts_${System.currentTimeMillis()}.mp3").also {
                it.writeBytes(audioData)
            }
        }

        suspendCancellableCoroutine { continuation ->
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                try {
                    setDataSource(tempFile.absolutePath)
                    prepare()
                } catch (e: Exception) {
                    tempFile.delete()
                    continuation.resumeWithException(e)
                    return@apply
                }

                setOnCompletionListener {
                    release()
                    tempFile.delete()
                    currentPlayer.compareAndSet(this, null)
                    if (continuation.isActive) continuation.resume(Unit)
                }

                setOnErrorListener { mp, what, extra ->
                    mp.release()
                    tempFile.delete()
                    currentPlayer.compareAndSet(mp, null)
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("MediaPlayer error $what/$extra"))
                    }
                    true
                }

                start()
            }

            currentPlayer.set(player)

            continuation.invokeOnCancellation {
                player.runCatching { stop(); release() }
                tempFile.delete()
                currentPlayer.compareAndSet(player, null)
            }
        }
    }
}
