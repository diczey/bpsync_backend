package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.UserDto
import com.example.finalapp.data.repository.ProfileRepository
import com.example.finalapp.data.repository.RepositoryResult
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val name: String = "John Doe",
    val age: String = "--",
    val gender: String = "Male",
    val weight: String = "75",
    val height: String = "178",
    val email: String = "john@example.com",
    val healthId: String = "BP2024",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository = ProfileRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }

            when (val result = profileRepository.fetchProfile()) {
                is RepositoryResult.Success -> {
                    applyUser(result.data)
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, message = result.message)
                    }
                }
            }
        }
    }

    fun toggleEditing() {
        val isEditing = _uiState.value.isEditing
        if (isEditing) {
            saveProfile()
        } else {
            _uiState.update { it.copy(isEditing = true, message = null) }
        }
    }

    fun updateName(name: String) = updateField { it.copy(name = name) }
    fun updateAge(age: String) = updateField { it.copy(age = age) }
    fun updateGender(gender: String) = updateField { it.copy(gender = gender) }
    fun updateWeight(weight: String) = updateField { it.copy(weight = weight) }
    fun updateHeight(height: String) = updateField { it.copy(height = height) }
    fun updateEmail(email: String) = updateField { it.copy(email = email) }

    private fun saveProfile() {
        val currentState = _uiState.value
        when (
            val result = profileRepository.saveLocalProfile(
                name = currentState.name,
                email = currentState.email,
                gender = currentState.gender,
                weight = currentState.weight,
                height = currentState.height
            )
        ) {
            is RepositoryResult.Success -> {
                applyUser(result.data, message = "Profile saved locally.")
                _uiState.update { it.copy(isEditing = false) }
            }

            is RepositoryResult.Error -> {
                _uiState.update { it.copy(message = result.message) }
            }
        }
    }

    private fun applyUser(user: UserDto, message: String? = null) {
        _uiState.update {
            it.copy(
                name = user.name.ifBlank { it.name },
                age = calculateAge(user.dateOfBirth) ?: it.age,
                gender = user.gender?.ifBlank { null } ?: it.gender,
                weight = user.weight?.ifBlank { null } ?: it.weight,
                height = user.height?.ifBlank { null } ?: it.height,
                email = user.email.ifBlank { it.email },
                healthId = user.id.ifBlank { it.healthId },
                isLoading = false,
                message = message
            )
        }
    }

    private fun calculateAge(dateOfBirth: String?): String? {
        if (dateOfBirth.isNullOrBlank()) return null

        val formatters = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
        )

        val birthDate = formatters.firstNotNullOfOrNull { formatter ->
            try {
                LocalDate.parse(dateOfBirth, formatter)
            } catch (_: DateTimeParseException) {
                null
            }
        } ?: return null

        return Period.between(birthDate, LocalDate.now()).years.toString()
    }

    private fun updateField(update: (ProfileUiState) -> ProfileUiState) {
        _uiState.update { current ->
            update(current).copy(message = null)
        }
    }
}
