package com.itsikh.buddy.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsikh.buddy.data.models.SessionLog
import com.itsikh.buddy.data.repository.ConversationRepository
import com.itsikh.buddy.data.repository.ProfileRepository
import com.itsikh.buddy.data.repository.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressDashboardUiState(
    val isLoading: Boolean        = true,
    val cefrLevel: String         = "A1",
    val speakingLevel: String     = "A1",
    val vocabLevel: String        = "A1",
    val grammarLevel: String      = "A1",
    val totalSessions: Int        = 0,
    val vocabularyMastered: Int   = 0,
    val totalMinutes: Int         = 0,
    val xpTotal: Int              = 0,
    val streakDays: Int           = 0,
    val longestStreak: Int        = 0,
    val shieldsAvailable: Int     = 0,
    val earnedBadgeIds: List<String> = emptyList(),
    val recentSessions: List<SessionLog> = emptyList()
)

@HiltViewModel
class ProgressDashboardViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val conversationRepository: ConversationRepository,
    private val vocabularyRepository: VocabularyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressDashboardUiState())
    val uiState: StateFlow<ProgressDashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            if (profile == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val totalSessions   = conversationRepository.totalSessionCount(profile.id)
            val vocabMastered   = vocabularyRepository.countMastered(profile.id)
            val recentSessions  = conversationRepository.getRecentSessions(profile.id, 10)

            _uiState.update {
                it.copy(
                    isLoading          = false,
                    cefrLevel          = profile.cefrLevel,
                    speakingLevel      = profile.speakingLevel,
                    vocabLevel         = profile.vocabularyLevel,
                    grammarLevel       = profile.grammarLevel,
                    totalSessions      = totalSessions,
                    vocabularyMastered = vocabMastered,
                    totalMinutes       = profile.totalSessionMinutes,
                    xpTotal            = profile.xpTotal,
                    streakDays         = profile.streakDays,
                    longestStreak      = profile.longestStreak,
                    shieldsAvailable   = profile.streakShieldsAvailable,
                    recentSessions     = recentSessions
                )
            }
        }
    }
}
