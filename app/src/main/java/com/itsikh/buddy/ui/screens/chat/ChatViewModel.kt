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
import com.itsikh.buddy.logging.DebugSettings
import com.itsikh.buddy.voice.GoogleCloudTtsManager
import com.itsikh.buddy.voice.TtsBackend
import android.speech.SpeechRecognizer
import com.itsikh.buddy.voice.SpeechRecognitionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
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
    val adminMode: Boolean             = false,
    val ttsBackend: TtsBackend         = TtsBackend.UNKNOWN,
    val activeAiModel: String          = "",
    val totalCoins: Int                = 0,
    val coinsEarnedThisSession: Int    = 0,
    // Buddy's current text — set immediately when speaking starts so it shows
    // without waiting for the Room Flow to deliver the message. Cleared when
    // the next user turn begins (at which point lastBuddy in messages is set).
    val currentBuddyText: String?      = null,
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
    private val workManager: WorkManager,
    private val debugSettings: DebugSettings
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Separate scope for session teardown — not tied to viewModelScope so it survives onCleared()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentSessionLog: SessionLog? = null
    private var sessionStartTime: Long = 0L
    private var speakingJob: Job? = null
    private var turnCount: Int = 0
    private var newWordsThisSession: Int = 0
    private var correctionsThisSession: Int = 0
    private var sessionXp: Int = 0

    // Tracks earned badge IDs — loaded from DataStore/preferences in a real implementation
    // For now kept in-memory per session
    private val earnedBadgeIds = mutableSetOf<String>()

    init {
        loadProfile()
        collectAdminState()
    }

    private fun collectAdminState() {
        val geminiKey = secureKeyManager.getKey(AppConfig.KEY_GEMINI_API)
        val claudeKey = secureKeyManager.getKey(AppConfig.KEY_CLAUDE_API)
        val defaultProvider = secureKeyManager.getKey(AppConfig.PREF_AI_DEFAULT_PROVIDER)
            ?: AppConfig.AI_PROVIDER_GEMINI
        val activeModel = when {
            defaultProvider == AppConfig.AI_PROVIDER_CLAUDE && !claudeKey.isNullOrBlank() ->
                "claude-haiku-4-5-20251001"
            !geminiKey.isNullOrBlank() -> "gemini-2.5-flash"
            !claudeKey.isNullOrBlank() -> "claude-haiku-4-5-20251001"
            else -> "no key"
        }
        _uiState.update { it.copy(activeAiModel = activeModel) }

        viewModelScope.launch {
            debugSettings.adminMode.collect { admin ->
                _uiState.update { it.copy(adminMode = admin) }
            }
        }
        viewModelScope.launch {
            ttsManager.ttsBackend.collect { backend ->
                _uiState.update { it.copy(ttsBackend = backend) }
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            profileRepository.profile.collect { profile ->
                _uiState.update { it.copy(
                    profile    = profile,
                    streakDays = profile?.streakDays ?: 0,
                    totalCoins = profile?.coins ?: 0
                ) }
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

        // Pre-warm TTS connection while AI is generating the greeting
        viewModelScope.launch { ttsManager.warmUp() }

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
        if (_uiState.value.mode == mode) return
        // Stop any ongoing speech immediately
        speakingJob?.cancel()
        speakingJob = null
        ttsManager.stopSpeaking()
        // Reset session — user must tap Start to begin the new mode
        _uiState.update { it.copy(
            mode            = mode,
            messages        = emptyList(),
            isSessionActive = false,
            voiceState      = VoiceState.IDLE,
            error           = null
        )}
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

    /** Immediately stops Buddy speaking and returns to IDLE. */
    fun stopSpeaking() {
        speakingJob?.cancel()
        speakingJob = null
        ttsManager.stopSpeaking()
        _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
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

        // Save user message to DB; clear currentBuddyText so it transitions cleanly
        val userMsg = Message(
            profileId = profile.id,
            sessionId = session.id,
            role      = "user",
            text      = text
        )
        conversationRepository.addMessage(userMsg)
        _uiState.update { it.copy(currentBuddyText = null) }
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

        // Run incremental memory extraction every 3 turns so facts accumulate
        // during the session — not only when the session ends.
        if (turnCount % 3 == 0) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val recentMessages = conversationRepository.getSessionMessages(session.id)
                        .takeLast(6) // only the last 6 messages for speed
                    AppLogger.i(TAG, "Running incremental extraction at turn $turnCount")
                    memoryExtractor.extractFromSession(profile.id, profile.displayName, recentMessages)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Incremental extraction failed (non-critical): ${e.message}")
                }
            }
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

        // Fix Bug #11: set currentBuddyText immediately so the speech bubble shows
        // without waiting for the Room Flow to deliver the DB update.
        _uiState.update { it.copy(voiceState = VoiceState.SPEAKING, currentBuddyText = text) }
        speakingJob = viewModelScope.launch {
            ttsManager.speak(text)
            _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
        }
        speakingJob?.join()
    }

    /** Called when the user leaves the chat screen or explicitly ends the session. */
    fun endSession() {
        if (!_uiState.value.isSessionActive) return
        viewModelScope.launch { endSessionInternal() }
    }

    /**
     * The actual session-teardown logic, extracted so it can be called from either
     * [viewModelScope] (normal flow) or [cleanupScope] (app-kill / onCleared path).
     */
    private suspend fun endSessionInternal() {
        ttsManager.stopSpeaking()
        sttManager.stopListening()

        val profile = profileRepository.getProfile() ?: run {
            _uiState.update { it.copy(isSessionActive = false) }
            return
        }
        val session = currentSessionLog ?: run {
            _uiState.update { it.copy(isSessionActive = false) }
            return
        }

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

        var newBadges = emptyList<String>()
        var coinsEarned = 0
        try {
            // Award XP
            val updatedProfile = profileRepository.updateStreak(profile)
            sessionXp = xpManager.awardSessionXp(updatedProfile, durationMinutes, newWordsThisSession)

            // ── Award Buddy Coins ──────────────────────────────────────────
            val isFirstEverSession = profile.totalSessionMinutes == 0
            if (isFirstEverSession) {
                // First-ever session bonus — always awarded regardless of quality
                coinsEarned += 10
                AppLogger.i(TAG, "First-ever session! Awarding 10 bonus coins")
            }
            if (durationMinutes >= 10 && turnCount >= 5) {
                // Ask AI to verify the child genuinely engaged (not just gibberish)
                val childMessages = sessionMessages.filter { it.role == "user" }
                val isGenuine = conversationManager.evaluateEngagement(childMessages)
                if (isGenuine) {
                    val sessionCoins = (durationMinutes * 0.5).toInt().coerceAtMost(7)
                    coinsEarned += sessionCoins
                    AppLogger.i(TAG, "Genuine session: ${durationMinutes}min → $sessionCoins coins")
                } else {
                    AppLogger.i(TAG, "AI judged session not genuine — no coins awarded")
                }
            }
            if (coinsEarned > 0) {
                profileRepository.addCoins(profile.id, coinsEarned)
            }

            // Evaluate badges
            val totalSessions = conversationRepository.totalSessionCount(profile.id)
            val vocabMastered = vocabularyRepository.countMastered(profile.id)
            newBadges = badgeEvaluator.evaluate(
                profile            = updatedProfile,
                alreadyEarned      = earnedBadgeIds,
                sessionLog         = session,
                totalSessions      = totalSessions,
                vocabularyMastered = vocabMastered
            )
            earnedBadgeIds.addAll(newBadges)

            // Close session record
            conversationRepository.closeSession(
                sessionId       = session.id,
                durationMinutes = durationMinutes,
                turnCount       = turnCount,
                newWords        = newWordsThisSession,
                corrections     = correctionsThisSession,
                summary         = extractionResult?.hebrewSummary,
                xp              = sessionXp
            )

            // Update vocabulary mastered count on profile
            profileRepository.recordSessionEnd(profile.id, durationMinutes)

            // Trigger Drive sync (also persists coins)
            DriveSyncWorker.enqueue(workManager)

            // Prune history older than the configured depth
            val keepDays = debugSettings.historyDepthDays.first()
            conversationRepository.pruneOldHistory(profile.id, keepDays)

            AppLogger.i(TAG, "Session ended: ${durationMinutes}min, ${newWordsThisSession} new words, ${newBadges.size} badges, $coinsEarned coins")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error closing session: ${e.message}", e)
        } finally {
            _uiState.update { it.copy(
                isSessionActive        = false,
                newBadges              = newBadges,
                xpToday                = sessionXp,
                coinsEarnedThisSession = coinsEarned
            )}
            currentSessionLog = null
        }
    }

    fun clearCoinsEarned() {
        _uiState.update { it.copy(coinsEarnedThisSession = 0) }
    }

    /** Re-checks API key availability — call when returning from Settings. */
    fun recheckApiKey() {
        val hasAiKey = secureKeyManager.hasKey(AppConfig.KEY_GEMINI_API) ||
                       secureKeyManager.hasKey(AppConfig.KEY_CLAUDE_API)
        _uiState.update { it.copy(noApiKey = !hasAiKey) }
    }

    fun clearNewBadges() {
        _uiState.update { it.copy(newBadges = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()  // cancels viewModelScope
        ttsManager.stopSpeaking()
        sttManager.destroy()
        if (_uiState.value.isSessionActive) {
            // viewModelScope is already cancelled at this point — use cleanupScope instead
            cleanupScope.launch {
                endSessionInternal()
                cleanupScope.cancel()
            }
        } else {
            cleanupScope.cancel()
        }
    }
}
