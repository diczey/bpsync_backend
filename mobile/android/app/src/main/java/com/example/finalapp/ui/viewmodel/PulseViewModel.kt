package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.PulseDataPoint
import com.example.finalapp.data.repository.PulseRepository
import com.example.finalapp.data.repository.RepositoryResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PulseUiState(
    val currentBpm: Int = 0,
    val restingHr: Int = 0,
    val minHr: Int = 0,
    val avgHr: Int = 0,
    val maxHr: Int = 0,
    val statusLabel: String = "Waiting",
    val pattern24h: List<PulseDataPoint> = emptyList(),
    val ecgWaveformPoints: List<Float> = emptyList(),
    val backendMessage: String? = null,
    val isMonitoring: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class PulseViewModel(
    private val repository: PulseRepository = PulseRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PulseUiState())
    val uiState: StateFlow<PulseUiState> = _uiState.asStateFlow()

    init {
        startPolling()
    }

    fun toggleMonitoring() {
        val currentState = _uiState.value.isMonitoring
        _uiState.update { it.copy(isMonitoring = !currentState) }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.isMonitoring) {
                    fetchPulseData()
                }
                delay(3000)
            }
        }
    }

    private fun fetchPulseData() {
        viewModelScope.launch {
            if (_uiState.value.ecgWaveformPoints.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            when (val result = repository.fetchPulseData()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            currentBpm = result.data.currentBpm,
                            restingHr = result.data.restingHr,
                            minHr = result.data.minHr,
                            avgHr = result.data.avgHr,
                            maxHr = result.data.maxHr,
                            statusLabel = result.data.statusLabel,
                            pattern24h = result.data.pattern24h,
                            ecgWaveformPoints = result.data.ecgWaveformPoints,
                            backendMessage = result.data.message,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (it.ecgWaveformPoints.isEmpty()) result.message else it.errorMessage
                        )
                    }
                }
            }
        }
    }
}
