package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.TrendDataDto
import com.example.finalapp.data.repository.ReadingRepository
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.data.repository.TrendsRepository
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

    init {
        loadTrends("week")
        observeNewReadings()
    }

    private fun observeNewReadings() {
        viewModelScope.launch {
            var prevCount = ReadingRepository.readings.value.size
            ReadingRepository.readings.collect { readings ->
                if (readings.size > prevCount) {
                    prevCount = readings.size
                    val backendPeriod = when (_uiState.value.selectedPeriod) {
                        "Daily" -> "day"
                        "Monthly" -> "month"
                        else -> "week"
                    }
                    loadTrends(backendPeriod)
                }
            }
        }
    }

    fun setPeriod(period: String) {
        // Map UI labels to backend labels (Daily -> day, Weekly -> week, Monthly -> month)
        val backendPeriod = when (period) {
            "Daily" -> "day"
            "Weekly" -> "week"
            "Monthly" -> "month"
            else -> "week"
        }
        
        _uiState.update { it.copy(selectedPeriod = period) }
        loadTrends(backendPeriod)
    }

    private fun loadTrends(period: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
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
        }
    }
}
