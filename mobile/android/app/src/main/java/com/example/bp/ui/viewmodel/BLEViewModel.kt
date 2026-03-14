package com.example.bp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bp.data.api.RetrofitClient
import com.example.bp.data.model.BLEStatusResponse
import com.example.bp.data.model.ScanResult
import com.example.bp.data.repository.HealthRepository
import com.example.bp.data.repository.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BLEUiState(
    val isLoading: Boolean = false,
    val status: BLEStatusResponse? = null,
    val scanResult: ScanResult? = null,
    val error: String? = null,
    val isStreaming: Boolean = false,
    val message: String? = null
)

class BLEViewModel : ViewModel() {

    private val repository = HealthRepository(RetrofitClient.instance)

    private val _uiState = MutableStateFlow(BLEUiState())
    val uiState: StateFlow<BLEUiState> = _uiState

    fun loadStatus(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = repository.getBleStatus(token)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        status = result.data,
                        error = null
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun scan(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, scanResult = null, message = "Cihaz aranıyor...")
            when (val result = repository.scanBle(token)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        scanResult = result.data,
                        message = result.data.message
                    )
                    // Refresh status after scan
                    loadStatus(token)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun startStreaming(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = repository.startStreaming(token)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isStreaming = result.data.success,
                        message = result.data.message
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun stopStreaming(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = repository.stopStreaming(token)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isStreaming = !result.data.success,
                        message = result.data.message
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}
