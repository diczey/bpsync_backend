package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.TrendDataDto
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.data.repository.TrendsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendsUiState(
    val selectedPeriod: String = "weekly",
    val trends: List<TrendDataDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class TrendsViewModel(
    private val repository: TrendsRepository = TrendsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    init {
        loadTrends("week")
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
                            trends = result.data,
                            isLoading = false,
                            errorMessage = null
                        ) 
                    }
                }
                is RepositoryResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        ) 
                    }
                }
            }
        }
    }
}
