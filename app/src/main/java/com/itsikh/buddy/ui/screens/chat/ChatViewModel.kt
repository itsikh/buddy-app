package com.itsikh.buddy.ui.screens.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.itsikh.buddy.AppConfig
import com.itsikh.buddy.ai.ConversationManager
import com.itsikh.buddy.ai.MemoryExtractor
import com.itsikh.buddy.data.models.*
import com.itsikh.buddy.data.repository.ConversationRepository
import com.itsikh.buddy.data.repository.ProfileRepository
import com.itsikh.buddy.data.repository.VocabularyRepository
import com.itsikh.buddy.drive.DriveSyncWorker
import com.itsikh.buddy.gamification.BadgeEvaluator
import com.itsikh.buddy.gamification.XpManager
import com.itsikh.buddy.logging.AppLogger
import com.itsikh.buddy.security.SecureKeyManager
import com.itsikh.buddy.voice.GoogleCloudTtsManager
import android.speech.SpeechRecognizer
import com.itsikh.buddy.voice.SpeechRecognitionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message>        = emptyList(),
    val profile: ChildProfile?         = null,
    val mode: ChatMode                 = ChatMode.FREE_CHAT,
    val voiceState: VoiceState         = VoiceState.IDLE,
    val partialSpeechText: String      = "",
    val streakDays: Int                = 0,
    val xpToday: Int                   = 0,
    val newBadges: List<String>        = emptyList(),  // badges earned this session
    val error: String?                 = null,
    val isSessionActive: Boolean       = false,
    val noApiKey: Boolean              = false,        // true when no AI provider key is configured
)

enum class VoiceState {
    IDLE,        // Waiting for push-to-talk
    LISTENING,   // Recording child's speech
    THINKING,    // AI is processing
    SPEAKING     // Buddy is playing TTS audio
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureKeyManager: SecureKeyManager,
    private val profileRepository: ProfileRepository,
    private val conversationRepository: ConversationRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val conversationManager: ConversationManager,
    private val memoryExtractor: MemoryExtractor,
    private val ttsManager: GoogleCloudTtsManager,
    private val sttManager: SpeechRecognitionManager,
    private val xpManager: XpManager,
    private val badgeEvaluator: BadgeEvaluator,
    private val workManager: WorkManager
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSessionLog: SessionLog? = null
    private var sessionStartTime: Long = 0L
    private var turnCount: Int = 0
    private var newWordsThisSession: Int = 0
    private var correctionsThisSession: Int = 0
    private var sessionXp: Int = 0

    // Tracks earned badge IDs — loaded from DataStore/preferences in a real implementation
    // For now kept in-memory per session
    private val earnedBadgeIds = mutableSetOf<String>()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            profileRepository.profile.collect { profile ->
                _uiState.update { it.copy(profile = profile, streakDays = profile?.streakDays ?: 0) }
            }
        }
    }

    /** Called when the user navigates to the chat screen. Starts the session + generates greeting. */
    fun startSession(mode: ChatMode = ChatMode.FREE_CHAT) {
        if (_uiState.value.isSessionActive) return

        // Guard: require at least one AI provider key before starting a session.
        val hasAiKey = secureKeyManager.hasKey(AppConfig.KEY_GEMINI_API) ||
                       secureKeyManager.hasKey(AppConfig.KEY_CLAUDE_API)
        if (!hasAiKey) {
            _uiState.update { it.copy(noApiKey = true) }
            return
        }
        _uiState.update { it.copy(noApiKey = false) }

        viewModelScope.launch {
            val profile = profileRepository.getProfile() ?: return@launch
            val session = conversationRepository.startSession(profile.id, mode)
            currentSessionLog = session
            sessionStartTime  = System.currentTimeMillis()
            turnCount         = 0
            newWordsThisSession = 0
            correctionsThisSession = 0
            sessionXp = 0

            _uiState.update { it.copy(
                mode           = mode,
                isSessionActive = true,
                messages       = emptyList(),
                voiceState     = VoiceState.THINKING
            )}

            // Load existing messages for this session (empty for new session)
            conversationRepository.observeSessionMessages(session.id).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }

        // Generate greeting in parallel
        viewModelScope.launch {
            try {
                val profile = profileRepository.getProfile() ?: return@launch
                val greeting = conversationManager.generateGreeting(profile, _uiState.value.mode)
                saveAndSpeakAssistantMessage(greeting)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Greeting failed: ${e.message}")
                _uiState.update { it.copy(voiceState = VoiceState.IDLE, error = null) }
            }
        }
    }

    fun switchMode(mode: ChatMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    /** Called when push-to-talk button is pressed down. */
    fun startListening() {
        if (_uiState.value.voiceState == VoiceState.SPEAKING) {
            ttsManager.stopSpeaking()
        }
        _uiState.update { it.copy(voiceState = VoiceState.LISTENING, partialSpeechText = "") }

        viewModelScope.launch(Dispatchers.Main) {
            sttManager.listen(language = "en-US")
                .catch { e ->
                    AppLogger.e(TAG, "STT flow error: ${e.message}")
                    _uiState.update { it.copy(voiceState = VoiceState.IDLE, error = "בעיה בזיהוי הדיבור — נסה שוב") }
                }
                .collect { result ->
                    when (result) {
                        is SpeechRecognitionManager.SpeechResult.Partial -> {
                            _uiState.update { it.copy(partialSpeechText = result.text) }
                        }
                        is SpeechRecognitionManager.SpeechResult.Final -> {
                            _uiState.update { it.copy(partialSpeechText = "", voiceState = VoiceState.THINKING) }
                            sendUserMessage(result.text)
                        }
                        is SpeechRecognitionManager.SpeechResult.Error -> {
                            val msg = if (result.code == SpeechRecognizer.ERROR_NO_MATCH ||
                                          result.code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                                "לא שמעתי — נסה שוב"
                            else
                                "בעיה בזיהוי הדיבור — נסה שוב"
                            _uiState.update { it.copy(voiceState = VoiceState.IDLE, partialSpeechText = "", error = msg) }
                        }
                    }
                }
        }
    }

    /** Called when push-to-talk button is released — STT will finalize naturally. */
    fun stopListening() {
        sttManager.stopListening()
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Sends a typed text message (for accessibility or testing). */
    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(voiceState = VoiceState.THINKING) }
        viewModelScope.launch { sendUserMessage(text) }
    }

    private suspend fun sendUserMessage(text: String) {
        val profile = profileRepository.getProfile() ?: return
        val session = currentSessionLog ?: return

        // Save user message to DB
        val userMsg = Message(
            profileId = profile.id,
            sessionId = session.id,
            role      = "user",
            text      = text
        )
        conversationRepository.addMessage(userMsg)
        turnCount++

        try {
            // Get AI response
            val response = conversationManager.sendMessage(
                profile     = profile,
                sessionId   = session.id,
                mode        = _uiState.value.mode,
                userMessage = text
            )
            saveAndSpeakAssistantMessage(response)
        } catch (e: Exception) {
            AppLogger.e(TAG, "AI response failed: ${e.message}")
            val errorMsg = if (e.message?.contains("API key") == true)
                "יש להגדיר מפתח API בהגדרות"
            else
                "בעיית חיבור — נסה שוב"
            _uiState.update { it.copy(voiceState = VoiceState.IDLE, error = errorMsg) }
        }
    }

    private suspend fun saveAndSpeakAssistantMessage(text: String) {
        val profile = profileRepository.getProfile() ?: return
        val session = currentSessionLog ?: return

        // Save to DB
        val assistantMsg = Message(
            profileId = profile.id,
            sessionId = session.id,
            role      = "assistant",
            text      = text
        )
        conversationRepository.addMessage(assistantMsg)

        // Speak via TTS
        _uiState.update { it.copy(voiceState = VoiceState.SPEAKING) }
        ttsManager.speak(text, language = "EN")
        _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
    }

    /** Called when the user leaves the chat screen or explicitly ends the session. */
    fun endSession() {
        if (!_uiState.value.isSessionActive) return
        viewModelScope.launch {
            ttsManager.stopSpeaking()
            sttManager.stopListening()

            val profile = profileRepository.getProfile() ?: return@launch
            val session = currentSessionLog ?: return@launch

            val durationMs      = System.currentTimeMillis() - sessionStartTime
            val durationMinutes = (durationMs / 60_000).toInt().coerceAtLeast(1)

            // Extract memory + words in background (don't block UI)
            val sessionMessages = conversationRepository.getSessionMessages(session.id)
            val extractionResult = withContext(Dispatchers.Default) {
                try {
                    memoryExtractor.extractFromSession(profile.id, profile.displayName, sessionMessages)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Memory extraction failed: ${e.message}")
                    null
                }
            }

            newWordsThisSession += extractionResult?.newWords?.size ?: 0

            // Award XP
            val updatedProfile = profileRepository.updateStreak(profile)
            sessionXp = xpManager.awardSessionXp(updatedProfile, durationMinutes, newWordsThisSession)

            // Evaluate badges
            val totalSessions   = conversationRepository.totalSessionCount(profile.id)
            val vocabMastered   = vocabularyRepository.countMastered(profile.id)
            val newBadges = badgeEvaluator.evaluate(
                profile          = updatedProfile,
                alreadyEarned    = earnedBadgeIds,
                sessionLog       = session,
                totalSessions    = totalSessions,
                vocabularyMastered = vocabMastered
            )
            earnedBadgeIds.addAll(newBadges)

            // Close session record
            conversationRepository.closeSession(
                sessionId   = session.id,
                durationMinutes = durationMinutes,
                turnCount   = turnCount,
                newWords    = newWordsThisSession,
                corrections = correctionsThisSession,
                summary     = extractionResult?.hebrewSummary,
                xp          = sessionXp
            )

            // Update vocabulary mastered count on profile
            profileRepository.recordSessionEnd(profile.id, durationMinutes)

            // Trigger Drive sync
            DriveSyncWorker.enqueue(workManager)

            _uiState.update { it.copy(
                isSessionActive = false,
                newBadges       = newBadges,
                xpToday         = sessionXp
            )}

            currentSessionLog = null
        }
    }

    /** Re-checks API key availability — call when returning from Settings. */
    fun recheckApiKey() {
        val hasAiKey = secureKeyManager.hasKey(AppConfig.KEY_GEMINI_API) ||
                       secureKeyManager.hasKey(AppConfig.KEY_CLAUDE_API)
        _uiState.update { it.copy(noApiKey = !hasAiKey) }
        if (hasAiKey && !_uiState.value.isSessionActive) {
            startSession(_uiState.value.mode)
        }
    }

    fun clearNewBadges() {
        _uiState.update { it.copy(newBadges = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stopSpeaking()
        sttManager.destroy()
        if (_uiState.value.isSessionActive) {
            viewModelScope.launch { endSession() }
        }
    }
}
