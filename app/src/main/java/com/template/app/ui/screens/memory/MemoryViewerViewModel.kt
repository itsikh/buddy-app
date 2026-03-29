package com.template.app.ui.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.template.app.data.models.MemoryFact
import com.template.app.data.repository.MemoryRepository
import com.template.app.data.repository.ProfileRepository
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

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile() ?: return@launch
            memoryRepository.observeAll(profile.id).collect { facts ->
                _uiState.update { it.copy(facts = facts) }
            }
        }
    }

    fun deleteFact(fact: MemoryFact) {
        viewModelScope.launch {
            memoryRepository.deleteFact(fact)
        }
    }
}
