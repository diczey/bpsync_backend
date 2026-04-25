package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.TrendDataDto
import com.example.finalapp.data.repository.BleRepository
import com.example.finalapp.data.repository.ReadingRepository
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.data.repository.TrendsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendsUiState(
    val selectedPeriod: String = "Weekly",
    val trends: List<TrendDataDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class TrendsViewModel(
    private val repository: TrendsRepository = TrendsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    private val bleRepository = BleRepository()
    private var trendsJob: Job? = null

    init {
        loadTrends("week")
        observeNewReadings()
        observeBleStreamingStop()
    }

    private fun observeNewReadings() {
        viewModelScope.launch {
            var prevCount = ReadingRepository.readings.value.size
            ReadingRepository.readings.collect { readings ->
                if (readings.size > prevCount) {
                    prevCount = readings.size
                    if (!_uiState.value.isLoading) {
                        val backendPeriod = backendPeriod()
                        loadTrends(backendPeriod)
                    }
                }
            }
        }
    }

    private fun observeBleStreamingStop() {
        viewModelScope.launch {
            var wasStreaming = false
            bleRepository.observeBleStatus().collect { status ->
                if (wasStreaming && !status.streaming) {
                    // Streaming just stopped — wait for backend to process, then refresh
                    delay(12000L)  // 10s window + 2s buffer
                    loadTrends(backendPeriod())
                }
                wasStreaming = status.streaming
            }
        }
    }

    private fun backendPeriod(): String = when (_uiState.value.selectedPeriod) {
        "Daily" -> "day"
        "Monthly" -> "month"
        else -> "week"
    }

    fun setPeriod(period: String) {
        val bp = when (period) {
            "Daily" -> "day"
            "Weekly" -> "week"
            "Monthly" -> "month"
            else -> "week"
        }
        _uiState.update { it.copy(selectedPeriod = period) }
        loadTrends(bp)
    }

    private fun loadTrends(period: String) {
        trendsJob?.cancel()
        trendsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                when (val result = repository.fetchTrends(period)) {
                    is RepositoryResult.Success -> {
                        _uiState.update {
                            it.copy(
                                trends = result.data.trends,
                                isLoading = false,
                                errorMessage = null,
                                infoMessage = result.data.message
                            )
                        }
                    }
                    is RepositoryResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message,
                                infoMessage = null
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = null, infoMessage = "No data for this period.")
                }
            }
        }
    }
}
