package com.itsikh.buddy.ui.screens.garden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsikh.buddy.data.models.VocabularyItem
import com.itsikh.buddy.data.repository.ProfileRepository
import com.itsikh.buddy.data.repository.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VocabularyGardenUiState(
    val items: List<VocabularyItem> = emptyList(),
    val masteredCount: Int          = 0,
    val growingCount: Int           = 0,
    val seedlingCount: Int          = 0
)

@HiltViewModel
class VocabularyGardenViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val vocabularyRepository: VocabularyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VocabularyGardenUiState())
    val uiState: StateFlow<VocabularyGardenUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile() ?: return@launch
            vocabularyRepository.observeAll(profile.id).collect { items ->
                _uiState.update {
                    it.copy(
                        items         = items.sortedByDescending { v -> v.masteryLevel },
                        masteredCount = items.count { v -> v.masteryLevel >= 4 },
                        growingCount  = items.count { v -> v.masteryLevel in 2..3 },
                        seedlingCount = items.count { v -> v.masteryLevel < 2 }
                    )
                }
            }
        }
    }
}
