package com.example.bp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bp.data.api.RetrofitClient
import com.example.bp.data.model.DashboardSummaryDto
import com.example.bp.data.model.HealthReadingDto
import com.example.bp.data.model.TrendDataDto
import com.example.bp.data.repository.HealthRepository
import com.example.bp.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val summary: DashboardSummaryDto? = null,
    val readings: List<HealthReadingDto> = emptyList(),
    val trends: List<TrendDataDto>? = null,
    val error: String? = null
)

class DashboardViewModel : ViewModel() {

    private val repository = HealthRepository(RetrofitClient.instance)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    fun loadAll(token: String, period: String = "week") {
        viewModelScope.launch {
            _uiState.value = DashboardUiState(isLoading = true)

            val summaryResult = repository.getDashboardSummary(token)
            val readingsResult = repository.getReadings(token)
            val trendsResult = repository.getTrends(token, period)

            val summary = if (summaryResult is Result.Success) summaryResult.data else null
            val readings = if (readingsResult is Result.Success) readingsResult.data else emptyList()
            val trends = if (trendsResult is Result.Success) trendsResult.data else null

            val error = when {
                summaryResult is Result.Error -> summaryResult.message
                readingsResult is Result.Error -> readingsResult.message
                else -> null
            }

            _uiState.value = DashboardUiState(
                summary = summary,
                readings = readings,
                trends = trends,
                error = error
            )
        }
    }

    fun loadTrends(token: String, period: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val trendsResult = repository.getTrends(token, period)
            if (trendsResult is Result.Success) {
                _uiState.value = _uiState.value.copy(isLoading = false, trends = trendsResult.data)
            } else if (trendsResult is Result.Error) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = trendsResult.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
