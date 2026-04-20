package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.HealthReportDto
import com.example.finalapp.data.repository.ReportsRepository
import com.example.finalapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportsUiState(
    val selectedRange: String = "weekly",
    val report: HealthReportDto? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
)

class ReportsViewModel(
    private val repository: ReportsRepository = ReportsRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadWeekly()
    }

    fun selectRange(range: String) {
        if (_uiState.value.selectedRange == range) return
        _uiState.update { it.copy(selectedRange = range, report = null, message = null, errorMessage = null) }
        if (range == "monthly") loadMonthly() else loadWeekly()
    }

    fun refresh() {
        if (_uiState.value.selectedRange == "monthly") loadMonthly() else loadWeekly()
    }

    private fun loadWeekly() {
        load { repository.fetchWeeklyReport() }
    }

    private fun loadMonthly() {
        load { repository.fetchMonthlyReport() }
    }

    private fun load(block: suspend () -> RepositoryResult<HealthReportDto>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = block()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            report = result.data,
                            isLoading = false,
                            message = null,
                            errorMessage = null
                        )
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, report = null, errorMessage = result.message, message = result.message)
                    }
                }
            }
        }
    }
}
