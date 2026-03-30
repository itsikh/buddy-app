package com.itsikh.buddy.ui.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsikh.buddy.data.models.MemoryFact
import com.itsikh.buddy.data.repository.MemoryRepository
import com.itsikh.buddy.data.repository.ProfileRepository
import com.itsikh.buddy.logging.AppLogger
import com.itsikh.buddy.logging.DebugSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryViewerUiState(
    val facts: List<MemoryFact> = emptyList(),
    val adminMode: Boolean = false
)

@HiltViewModel
class MemoryViewerViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val memoryRepository: MemoryRepository,
    private val debugSettings: DebugSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryViewerUiState())
    val uiState: StateFlow<MemoryViewerUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "MemoryViewerViewModel"
    }

    init {
        viewModelScope.launch {
            debugSettings.adminMode.collect { admin ->
                _uiState.update { it.copy(adminMode = admin) }
            }
        }
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

    fun updateFact(fact: MemoryFact, newKey: String, newValue: String) {
        if (newKey.isBlank() || newValue.isBlank()) return
        viewModelScope.launch {
            try {
                memoryRepository.updateFact(fact, newKey, newValue)
                AppLogger.d(TAG, "Fact updated: ${fact.key} → $newKey = $newValue")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update fact ${fact.key}: ${e.message}", e)
            }
        }
    }
}
