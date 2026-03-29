package com.itsikh.buddy.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import com.google.gson.Gson
import com.itsikh.buddy.AppConfig
import com.itsikh.buddy.logging.AppLogger
import com.itsikh.buddy.security.SecureKeyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

        // WaveNet voices — primary Hebrew voice since Buddy speaks Hebrew+English mix.
        // SSML <lang> tags switch pronunciation to English for English segments.
        private const val VOICE_HE_GIRL = "he-IL-Wavenet-A"  // warm female — default Buddy voice
        private const val VOICE_HE_BOY  = "he-IL-Wavenet-B"  // natural male
        private const val VOICE_EN_GIRL = "en-US-Wavenet-F"  // warm female, clear for kids
        private const val VOICE_EN_BOY  = "en-US-Wavenet-D"  // friendly male
        private const val LANG_HE = "he-IL"
        private const val LANG_EN = "en-US"

        private val HEBREW_RANGE = '\u05D0'..'\u05EA'
    }

    private val currentPlayer = AtomicReference<MediaPlayer?>(null)

    // Android built-in TTS — fallback when no Google Cloud key is set.
    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false

    init {
        androidTts = TextToSpeech(context) { status ->
            androidTtsReady = status == TextToSpeech.SUCCESS
            if (androidTtsReady) {
                androidTts?.language = Locale("he", "IL")  // primary Hebrew
                androidTts?.setSpeechRate(0.88f)
            } else {
                AppLogger.w(TAG, "Android TTS init failed: $status")
            }
        }
    }

    /**
     * Speaks [text] aloud. Strips markdown/symbols, then uses SSML to switch
     * between Hebrew and English voices mid-sentence for natural bilingual speech.
     * Falls back to Android TTS when no Google Cloud key is configured.
     */
    suspend fun speak(rawText: String, language: String = "HE") {
        stopSpeaking()
        val text = cleanForTts(rawText)
        if (text.isBlank()) return

        val apiKey = keyManager.getKey(AppConfig.KEY_GOOGLE_TTS)
        if (apiKey.isNullOrBlank()) {
            AppLogger.d(TAG, "No Google Cloud TTS key — using Android TTS fallback")
            speakWithAndroidTts(text)
            return
        }

        try {
            val audioData = synthesizeWithSsml(apiKey, text)
            playAudio(audioData)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Google TTS failed: ${e.message} — falling back to Android TTS")
            speakWithAndroidTts(text)
        }
    }

    /** Stops any currently playing audio immediately. */
    fun stopSpeaking() {
        currentPlayer.getAndSet(null)?.apply {
            runCatching { stop() }
            runCatching { release() }
        }
        androidTts?.runCatching { stop() }
    }

    fun destroy() {
        stopSpeaking()
        androidTts?.shutdown()
        androidTts = null
    }

    // ── Text cleaning ──────────────────────────────────────────────────────

    /**
     * Strips markdown formatting and symbols that should not be spoken aloud.
     * Keeps letters (Hebrew + Latin), digits, basic punctuation, and spaces.
     */
    private fun cleanForTts(text: String): String = text
        .replace(Regex("```[\\s\\S]*?```"), "")           // fenced code blocks
        .replace(Regex("`[^`]+`"), "")                     // inline code
        .replace(Regex("\\*{1,3}([^*]+)\\*{1,3}"), "$1")  // **bold** / *italic*
        .replace(Regex("_{1,2}([^_]+)_{1,2}"), "$1")      // _italic_ / __bold__
        .replace(Regex("#{1,6}\\s*"), "")                  // # headings
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")   // [link](url) → text
        .replace(Regex("https?://\\S+"), "")               // bare URLs
        .replace(Regex("!\\[.*?]\\(.*?\\)"), "")           // images
        .replace(Regex("[*_~|>]"), "")                     // remaining markdown chars
        // Remove emoji and non-letter symbols, keep: Hebrew, Latin, digits, .,!?;:'"()-
        .replace(Regex("[^\\p{L}\\p{N}\\s\\u05D0-\\u05EA.,!?;:'\"()\\-–—]"), " ")
        .replace(Regex("[ \\t]+"), " ")
        .trim()

    // ── SSML building ──────────────────────────────────────────────────────

    /**
     * Splits text into Hebrew/English segments and wraps each in SSML <lang> tags
     * so the Hebrew WaveNet voice pronounces Hebrew correctly and switches to
     * English phonetics for English words — all in one API call, no choppy gaps.
     */
    private fun buildSsml(text: String): String {
        data class Segment(val text: String, val isHebrew: Boolean)

        val segments = mutableListOf<Segment>()
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        var currentWords = mutableListOf<String>()
        var currentIsHebrew: Boolean? = null

        fun flush() {
            if (currentWords.isNotEmpty() && currentIsHebrew != null) {
                segments += Segment(currentWords.joinToString(" "), currentIsHebrew!!)
                currentWords = mutableListOf()
            }
        }

        for (word in words) {
            val hebrewCount = word.count { it in HEBREW_RANGE }
            val isHebrew = hebrewCount > 0
            if (currentIsHebrew == null) currentIsHebrew = isHebrew
            if (isHebrew != currentIsHebrew) flush()
            currentIsHebrew = isHebrew
            currentWords += word
        }
        flush()

        // Single-language text — no SSML needed
        if (segments.size == 1) {
            return if (segments[0].isHebrew) {
                // Hebrew primary voice: no <lang> wrapper — Semitic languages don't support
                // the <lang> tag and produce silence. Let the voice speak natively.
                "<speak>${segments[0].text}</speak>"
            } else {
                "<speak><lang xml:lang=\"$LANG_EN\">${segments[0].text}</lang></speak>"
            }
        }

        // Mixed Hebrew+English: wrap only English segments in <lang en-US>.
        // Hebrew segments have NO <lang> tag — the primary he-IL voice already
        // handles Hebrew natively. Adding <lang he-IL> causes silence (Google limitation).
        val sb = StringBuilder("<speak>")
        for (seg in segments) {
            if (seg.isHebrew) {
                sb.append(seg.text)
            } else {
                sb.append("<lang xml:lang=\"$LANG_EN\">")
                sb.append(seg.text)
                sb.append("</lang>")
            }
            sb.append(" ")
        }
        sb.append("</speak>")
        return sb.toString()
    }

    // ── Google Cloud TTS ───────────────────────────────────────────────────

    /** Returns the Hebrew WaveNet voice name matching Buddy's configured gender. */
    private fun buddyHeVoice(): String {
        val gender = keyManager.getKey(com.itsikh.buddy.AppConfig.PREF_BUDDY_GENDER)
        return if (gender == com.itsikh.buddy.AppConfig.BUDDY_GENDER_BOY) VOICE_HE_BOY else VOICE_HE_GIRL
    }

    private suspend fun synthesizeWithSsml(apiKey: String, text: String): ByteArray =
        withContext(Dispatchers.IO) {
            val ssml = buildSsml(text)
            AppLogger.d(TAG, "SSML: $ssml")

            // Primary voice is Hebrew — SSML <lang> tags handle English segments
            val requestBody = mapOf(
                "input"       to mapOf("ssml" to ssml),
                "voice"       to mapOf(
                    "languageCode" to LANG_HE,
                    "name"         to buddyHeVoice()
                ),
                "audioConfig" to mapOf(
                    "audioEncoding" to "MP3",
                    "speakingRate"  to 0.9,
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
                    ?: throw IllegalStateException("No audioContent in response")

                Base64.decode(encoded, Base64.DEFAULT)
            }
        }

    // ── Android TTS fallback ───────────────────────────────────────────────

    private suspend fun speakWithAndroidTts(text: String) {
        val tts = androidTts ?: return
        if (!androidTtsReady) return

        val utteranceId = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(uid: String?) {}
                override fun onDone(uid: String?) { if (cont.isActive) cont.resume(Unit) }
                @Deprecated("Deprecated in Java")
                override fun onError(uid: String?) { if (cont.isActive) cont.resume(Unit) }
            })
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { tts.stop() }
        }
    }

    // ── MediaPlayer playback ───────────────────────────────────────────────

    private suspend fun playAudio(audioData: ByteArray) {
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
