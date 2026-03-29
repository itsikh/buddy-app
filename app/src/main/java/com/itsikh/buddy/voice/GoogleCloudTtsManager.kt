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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * Which Google Cloud TTS tier is active, or whether we fell back.
 * CHIRP is higher-quality (Preview); WAVENET is the GA fallback.
 */
enum class TtsBackend { UNKNOWN, GOOGLE_CLOUD_CHIRP, GOOGLE_CLOUD_WAVENET, ANDROID_FALLBACK }

@Singleton
class GoogleCloudTtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val keyManager: SecureKeyManager,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "GoogleCloudTtsManager"
        private const val TTS_URL = "https://texttospeech.googleapis.com/v1beta1/text:synthesize"
        private val JSON = "application/json".toMediaType()

        // ── Chirp3-HD voices (Preview, higher quality) ─────────────────────
        // These are tried first. On any API error we fall back to WaveNet.
        private const val VOICE_HE_GIRL_CHIRP = "he-IL-Chirp3-HD-Aoede"  // warm female
        private const val VOICE_HE_BOY_CHIRP  = "he-IL-Chirp3-HD-Puck"   // friendly male
        private const val VOICE_EN_GIRL_CHIRP = "en-US-Chirp3-HD-Aoede"  // matching English female
        private const val VOICE_EN_BOY_CHIRP  = "en-US-Chirp3-HD-Puck"   // matching English male

        // ── WaveNet voices (GA, stable fallback) ───────────────────────────
        private const val VOICE_HE_GIRL_WAVENET = "he-IL-Wavenet-A"  // warm female
        private const val VOICE_HE_BOY_WAVENET  = "he-IL-Wavenet-B"  // natural male
        private const val VOICE_EN_GIRL_WAVENET = "en-US-Wavenet-F"  // warm female, clear for kids
        private const val VOICE_EN_BOY_WAVENET  = "en-US-Wavenet-D"  // friendly male

        private const val LANG_HE = "he-IL"

        private val HEBREW_RANGE = '\u05D0'..'\u05EA'
    }

    private val currentPlayer = AtomicReference<MediaPlayer?>(null)

    private val _ttsBackend = MutableStateFlow(TtsBackend.UNKNOWN)
    /** The backend actually used for the most recent speak() call. */
    val ttsBackend: StateFlow<TtsBackend> = _ttsBackend.asStateFlow()

    // Android built-in TTS — last-resort fallback when no Google Cloud key is set.
    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false

    init {
        androidTts = TextToSpeech(context) { status ->
            androidTtsReady = status == TextToSpeech.SUCCESS
            if (androidTtsReady) {
                androidTts?.language = Locale("he", "IL")
                androidTts?.setSpeechRate(0.88f)
            } else {
                AppLogger.w(TAG, "Android TTS init failed: $status")
            }
        }
    }

    /**
     * Speaks [rawText] aloud.
     *
     * Priority:
     *   1. Google Cloud Chirp3-HD (high quality, Preview)
     *   2. Google Cloud WaveNet (GA, stable)
     *   3. Android built-in TTS (no key needed)
     *
     * English segments are spoken by a **native English voice** via SSML `<voice>` tags
     * so children hear authentic English pronunciation — not a Hebrew voice approximating English.
     * Hebrew segments are left bare (no `<lang>` tag) because Google's engine silences
     * Semitic languages when wrapped in `<lang>` tags.
     */
    suspend fun speak(rawText: String, language: String = "HE") {
        stopSpeaking()
        val text = cleanForTts(rawText)
        if (text.isBlank()) return

        val apiKey = keyManager.getKey(AppConfig.KEY_GOOGLE_TTS)
        if (apiKey.isNullOrBlank()) {
            AppLogger.d(TAG, "No Google Cloud TTS key — using Android TTS fallback")
            _ttsBackend.value = TtsBackend.ANDROID_FALLBACK
            speakWithAndroidTts(text)
            return
        }

        // Try Chirp3-HD first — better quality but Preview
        try {
            val heVoice = buddyVoice(chirp = true, hebrew = true)
            val enVoice = buddyVoice(chirp = true, hebrew = false)
            val audioData = synthesize(apiKey, text, heVoice, enVoice)
            _ttsBackend.value = TtsBackend.GOOGLE_CLOUD_CHIRP
            AppLogger.d(TAG, "Chirp3-HD OK: $heVoice")
            playAudio(audioData)
            return
        } catch (e: Exception) {
            AppLogger.w(TAG, "Chirp3-HD failed (${e.message}) — trying WaveNet")
        }

        // WaveNet fallback — GA, always available
        try {
            val heVoice = buddyVoice(chirp = false, hebrew = true)
            val enVoice = buddyVoice(chirp = false, hebrew = false)
            val audioData = synthesize(apiKey, text, heVoice, enVoice)
            _ttsBackend.value = TtsBackend.GOOGLE_CLOUD_WAVENET
            AppLogger.d(TAG, "WaveNet OK: $heVoice")
            playAudio(audioData)
            return
        } catch (e: Exception) {
            AppLogger.e(TAG, "WaveNet failed (${e.message}) — falling back to Android TTS")
        }

        _ttsBackend.value = TtsBackend.ANDROID_FALLBACK
        speakWithAndroidTts(text)
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

    // ── Voice selection ────────────────────────────────────────────────────

    /**
     * Returns the voice name matching Buddy's gender and the requested tier/language.
     * @param chirp  true = Chirp3-HD, false = WaveNet
     * @param hebrew true = Hebrew voice, false = English voice
     */
    private fun buddyVoice(chirp: Boolean, hebrew: Boolean): String {
        val isBoy = keyManager.getKey(AppConfig.PREF_BUDDY_GENDER) == AppConfig.BUDDY_GENDER_BOY
        return if (chirp) {
            if (hebrew) {
                if (isBoy) VOICE_HE_BOY_CHIRP else VOICE_HE_GIRL_CHIRP
            } else {
                if (isBoy) VOICE_EN_BOY_CHIRP else VOICE_EN_GIRL_CHIRP
            }
        } else {
            if (hebrew) {
                if (isBoy) VOICE_HE_BOY_WAVENET else VOICE_HE_GIRL_WAVENET
            } else {
                if (isBoy) VOICE_EN_BOY_WAVENET else VOICE_EN_GIRL_WAVENET
            }
        }
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
     * Splits text into Hebrew/English segments.
     *
     * Hebrew segments: no wrapper — Google silences Semitic `<lang>` tags.
     * English segments: `<voice name="enVoiceName">...</voice>` — a **native English speaker**
     *   pronounces the English words, which is correct for a kids' English-teaching app.
     *
     * @param enVoiceName  The English voice name to use (must match tier of the primary voice).
     */
    private fun buildSsml(text: String, enVoiceName: String): String {
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
            val isHebrew = word.any { it in HEBREW_RANGE }
            if (currentIsHebrew == null) currentIsHebrew = isHebrew
            if (isHebrew != currentIsHebrew) flush()
            currentIsHebrew = isHebrew
            currentWords += word
        }
        flush()

        // Single-language text
        if (segments.size == 1) {
            return if (segments[0].isHebrew) {
                "<speak>${segments[0].text}</speak>"
            } else {
                "<speak><voice name=\"$enVoiceName\">${segments[0].text}</voice></speak>"
            }
        }

        // Mixed: Hebrew bare, English wrapped in <voice> for native pronunciation
        val sb = StringBuilder("<speak>")
        for (seg in segments) {
            if (seg.isHebrew) {
                sb.append(seg.text)
            } else {
                sb.append("<voice name=\"$enVoiceName\">")
                sb.append(seg.text)
                sb.append("</voice>")
            }
            sb.append(" ")
        }
        sb.append("</speak>")
        return sb.toString()
    }

    // ── Google Cloud TTS ───────────────────────────────────────────────────

    /**
     * Synthesizes [text] using [heVoiceName] as the primary Hebrew voice and
     * [enVoiceName] for English segments via SSML `<voice>` tags.
     */
    private suspend fun synthesize(
        apiKey: String,
        text: String,
        heVoiceName: String,
        enVoiceName: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val ssml = buildSsml(text, enVoiceName)
        AppLogger.d(TAG, "SSML [$heVoiceName]: $ssml")

        val requestBody = mapOf(
            "input"       to mapOf("ssml" to ssml),
            "voice"       to mapOf(
                "languageCode" to LANG_HE,
                "name"         to heVoiceName
            ),
            "audioConfig" to mapOf(
                "audioEncoding" to "MP3",
                "speakingRate"  to 0.9,
                "pitch"         to 2.0   // +2 semitones: warmer, friendlier for kids
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
                        continuation.resumeWithException(
                            IllegalStateException("MediaPlayer error $what/$extra")
                        )
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
