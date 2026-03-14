package com.example.bp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bp.data.api.RetrofitClient
import com.example.bp.data.local.TokenManager
import com.example.bp.data.model.LoginRequest
import com.example.bp.data.model.RegisterRequest
import com.example.bp.data.repository.AuthRepository
import com.example.bp.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

class AuthViewModel(private val tokenManager: TokenManager) : ViewModel() {

    private val repository = AuthRepository(RetrofitClient.instance)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    // Expose the saved token as a flow
    val savedToken = tokenManager.accessToken

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            when (val result = repository.login(email, password)) {
                is Result.Success -> {
                    tokenManager.saveToken(result.data.token!!)
                    _uiState.value = AuthUiState(isSuccess = true)
                }
                is Result.Error -> {
                    _uiState.value = AuthUiState(error = result.message)
                }
                else -> {}
            }
        }
    }

    fun register(request: RegisterRequest) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            when (val result = repository.register(request)) {
                is Result.Success -> {
                    _uiState.value = AuthUiState(isSuccess = true)
                }
                is Result.Error -> {
                    _uiState.value = AuthUiState(error = result.message)
                }
                else -> {}
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenManager.clearToken()
            _uiState.value = AuthUiState()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
