package com.itsikh.buddy.ui.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsikh.buddy.data.models.MemoryFact
import com.itsikh.buddy.data.repository.MemoryRepository
import com.itsikh.buddy.data.repository.ProfileRepository
import com.itsikh.buddy.logging.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryViewerUiState(
    val facts: List<MemoryFact> = emptyList()
)

@HiltViewModel
class MemoryViewerViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryViewerUiState())
    val uiState: StateFlow<MemoryViewerUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "MemoryViewerViewModel"
    }

    init {
        viewModelScope.launch {
            try {
                val profile = profileRepository.getProfile() ?: return@launch
                AppLogger.d(TAG, "Observing memory facts for profile ${profile.id}")
                memoryRepository.observeAll(profile.id).collect { facts ->
                    AppLogger.d(TAG, "Memory facts updated: ${facts.size} items")
                    _uiState.update { it.copy(facts = facts) }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load memory facts: ${e.message}", e)
            }
        }
    }

    fun deleteFact(fact: MemoryFact) {
        viewModelScope.launch {
            try {
                memoryRepository.deleteFact(fact)
                AppLogger.d(TAG, "Fact deleted: ${fact.key}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to delete fact ${fact.key}: ${e.message}", e)
            }
        }
    }
}
