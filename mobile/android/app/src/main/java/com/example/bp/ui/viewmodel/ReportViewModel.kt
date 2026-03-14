package com.example.bp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bp.data.api.RetrofitClient
import com.example.bp.data.model.WeeklyReport
import com.example.bp.data.repository.HealthRepository
import com.example.bp.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ReportUiState(
    val isLoading: Boolean = false,
    val report: WeeklyReport? = null,
    val error: String? = null,
    val isMonthly: Boolean = false,
    val offset: Int = 0
)

class ReportViewModel : ViewModel() {

    private val repository = HealthRepository(RetrofitClient.instance)

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState

    fun loadReport(token: String, isMonthly: Boolean = false, offset: Int = 0) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isMonthly = isMonthly, offset = offset)
            val result = if (isMonthly) {
                repository.getMonthlyReport(token, offset)
            } else {
                repository.getWeeklyReport(token, offset)
            }

            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        report = result.data,
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
