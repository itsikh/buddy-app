package com.itsikh.buddy.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsikh.buddy.data.models.ChildProfile
import com.itsikh.buddy.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileSetupUiState(
    val name: String         = "",
    val ageText: String      = "",
    val gender: String       = "BOY",   // "BOY" or "GIRL"
    val nameError: String?   = null,
    val ageError: String?    = null,
    val isSaving: Boolean    = false,
    val profileSaved: Boolean = false
)

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onAgeChanged(ageText: String) {
        _uiState.update { it.copy(ageText = ageText, ageError = null) }
    }

    fun onGenderChanged(gender: String) {
        _uiState.update { it.copy(gender = gender) }
    }

    fun saveProfile() {
        val name = _uiState.value.name.trim()
        val age  = _uiState.value.ageText.toIntOrNull()

        var valid = true
        if (name.isBlank()) {
            _uiState.update { it.copy(nameError = "נא להזין שם") }
            valid = false
        }
        if (age == null || age < 5 || age > 18) {
            _uiState.update { it.copy(ageError = "נא להזין גיל בין 5-18") }
            valid = false
        }
        if (!valid) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val profile = ChildProfile(
                displayName        = name,
                age                = age!!,
                gender             = _uiState.value.gender,
                onboardingComplete  = true,
                parentConsentGiven  = true
            )
            profileRepository.saveProfile(profile)

            _uiState.update { it.copy(isSaving = false, profileSaved = true) }
        }
    }
}
