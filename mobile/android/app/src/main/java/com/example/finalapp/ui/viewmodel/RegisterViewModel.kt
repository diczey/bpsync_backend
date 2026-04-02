package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.RegisterRequest
import com.example.finalapp.data.repository.AuthRepository
import com.example.finalapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val weight: String = "",
    val height: String = "",
    val gender: String = "",
    val dateOfBirth: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegistered: Boolean = false
)

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun updateName(name: String) = updateField { it.copy(name = name, errorMessage = null) }
    fun updateEmail(email: String) = updateField { it.copy(email = email, errorMessage = null) }
    fun updatePassword(password: String) = updateField { it.copy(password = password, errorMessage = null) }
    fun updateWeight(weight: String) = updateField { it.copy(weight = weight, errorMessage = null) }
    fun updateHeight(height: String) = updateField { it.copy(height = height, errorMessage = null) }
    fun updateGender(gender: String) = updateField { it.copy(gender = gender, errorMessage = null) }
    fun updateDateOfBirth(dateOfBirth: String) = updateField { it.copy(dateOfBirth = dateOfBirth, errorMessage = null) }

    fun register() {
        val currentState = _uiState.value
        if (currentState.isLoading) return

        if (currentState.name.isBlank() || currentState.email.isBlank() || currentState.password.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Name, email and password are required.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val request = RegisterRequest(
                email = currentState.email.trim(),
                password = currentState.password,
                name = currentState.name.trim(),
                dateOfBirth = currentState.dateOfBirth.ifBlank { null },
                gender = currentState.gender.ifBlank { null },
                weight = currentState.weight.ifBlank { null },
                height = currentState.height.ifBlank { null }
            )

            when (val result = authRepository.register(request)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isRegistered = true) }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(isRegistered = false) }
    }

    private fun updateField(update: (RegisterUiState) -> RegisterUiState) {
        _uiState.update(update)
    }
}
