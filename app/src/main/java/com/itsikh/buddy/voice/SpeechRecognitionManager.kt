package com.itsikh.buddy.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.itsikh.buddy.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Android's SpeechRecognizer for push-to-talk voice input.
 *
 * The child holds the [VoiceButton] while speaking; [startListening] fires when the
 * button is pressed and [stopListening] fires when released.
 *
 * [SpeechRecognizer] must be created and used on the main thread — this class handles
 * that requirement internally.
 *
 * Emits [SpeechResult] events via a Kotlin Flow:
 *   - [SpeechResult.Partial]: real-time partial results shown in the UI while the child speaks
 *   - [SpeechResult.Final]: the finalized transcript sent to the AI
 *   - [SpeechResult.Error]: recognition failure (ask child to repeat)
 */
@Singleton
class SpeechRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SpeechRecognitionManager"
    }

    sealed class SpeechResult {
        data class Partial(val text: String) : SpeechResult()
        data class Final(val text: String)   : SpeechResult()
        data class Error(val code: Int, val message: String) : SpeechResult()
    }

    private var recognizer: SpeechRecognizer? = null

    /**
     * Starts listening and returns a Flow of [SpeechResult] events.
     * The flow completes when a final result is received or on error.
     *
     * Must be called from the main thread (or a Handler running on it).
     */
    fun listen(language: String = "en-US"): Flow<SpeechResult> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechResult.Error(-1, "Speech recognition not available on this device"))
            close()
            return@callbackFlow
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    AppLogger.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    AppLogger.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Could be used to animate a speaking indicator
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    AppLogger.d(TAG, "Speech ended")
                }

                override fun onError(error: Int) {
                    val msg = speechErrorMessage(error)
                    AppLogger.w(TAG, "Recognition error $error: $msg")
                    trySend(SpeechResult.Error(error, msg))
                    close()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    AppLogger.d(TAG, "Final result: $text")
                    if (text.isNotBlank()) {
                        trySend(SpeechResult.Final(text))
                    } else {
                        trySend(SpeechResult.Error(-2, "Empty recognition result"))
                    }
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    if (partial.isNotBlank()) {
                        trySend(SpeechResult.Partial(partial))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Hints improve accuracy for English learner contexts
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            }

            sr.startListening(intent)
        }

        awaitClose {
            recognizer?.apply {
                stopListening()
                destroy()
            }
            recognizer = null
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO              -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT             -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK            -> "Network error — check connection"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT    -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH           -> "No speech detected — try again"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY    -> "Recognition service busy"
        SpeechRecognizer.ERROR_SERVER             -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT     -> "No speech detected"
        else                                       -> "Unknown error ($error)"
    }
}
