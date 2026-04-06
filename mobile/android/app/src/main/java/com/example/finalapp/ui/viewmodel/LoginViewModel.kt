package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.repository.AuthRepository
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.data.repository.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false
)

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private fun t(english: String, turkish: String): String {
        return if (SettingsStore.settings.value.language == "tr") turkish else english
    }

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun login() {
        val currentState = _uiState.value
        if (currentState.isLoading) return

        if (currentState.email.isBlank() || currentState.password.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = t("Email and password are required.", "E-posta ve şifre zorunludur."))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = authRepository.login(currentState.email, currentState.password)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
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
        _uiState.update { it.copy(isAuthenticated = false) }
    }
}
