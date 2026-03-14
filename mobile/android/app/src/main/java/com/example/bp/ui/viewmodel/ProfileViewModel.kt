package com.example.bp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bp.data.api.RetrofitClient
import com.example.bp.data.model.UserDto
import com.example.bp.data.model.ProfileUpdateRequest
import com.example.bp.data.repository.HealthRepository
import com.example.bp.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = false,
    val user: UserDto? = null,
    val error: String? = null,
    val isUpdated: Boolean = false
)

class ProfileViewModel : ViewModel() {

    private val repository = HealthRepository(RetrofitClient.instance)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    fun loadProfile(token: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState(isLoading = true)
            when (val result = repository.getProfile(token)) {
                is Result.Success -> {
                    _uiState.value = ProfileUiState(user = result.data)
                }
                is Result.Error -> {
                    _uiState.value = ProfileUiState(error = result.message)
                }
                else -> {}
            }
        }
    }

    fun updateProfile(token: String, request: ProfileUpdateRequest) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isUpdated = false)
            when (val result = repository.updateProfile(token, request)) {
                is Result.Success -> {
                    _uiState.value = ProfileUiState(user = result.data, isUpdated = true)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
                else -> {}
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
