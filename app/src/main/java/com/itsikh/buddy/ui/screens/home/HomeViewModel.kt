package com.itsikh.buddy.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsikh.buddy.data.models.ChildProfile
import com.itsikh.buddy.data.repository.ConversationRepository
import com.itsikh.buddy.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val profile: ChildProfile? = null,
    val weekSessions: Int      = 0,   // sessions completed this week
    val weekGoal: Int          = 5,   // target sessions per week
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.profile.collect { profile ->
                _uiState.update { it.copy(profile = profile) }
                if (profile != null) loadWeekSessions(profile.id)
            }
        }
    }

    private suspend fun loadWeekSessions(profileId: String) {
        val total = conversationRepository.totalSessionCount(profileId)
        // Approximate weekly sessions: use recent sessions (capped at goal)
        val sessions = conversationRepository.getRecentSessions(profileId, limit = 7)
        val thisWeek = sessions.filter {
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            it.startedAt >= weekAgo
        }.size
        _uiState.update { it.copy(weekSessions = thisWeek) }
    }
}
